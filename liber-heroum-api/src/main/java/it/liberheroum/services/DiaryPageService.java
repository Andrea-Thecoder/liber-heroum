package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.ExpressionList;
import io.ebean.PagedList;
import io.ebean.Transaction;
import it.liberheroum.config.DiaryPageConfig;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.diary_page.BaseDiaryPageDTO;
import it.liberheroum.dto.diary_page.InsertDiaryPageDTO;
import it.liberheroum.dto.diary_page.UpdateDiaryPageDTO;
import it.liberheroum.exception.ServiceException;
import it.liberheroum.exception.SqlStates;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPage;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryPageService {

    private static final int MAX_PAGES = 100;
    private static final int PAGES_PER_VIEW = 2;
    private static final Set<String> CONCURRENT_INSERT_STATES =
            Set.of(SqlStates.UNIQUE_VIOLATION, SqlStates.CHECK_VIOLATION, SqlStates.DEADLOCK);

    private final Database database;
    private final ObjectMapper objectMapper;
    private final DiaryPageConfig config;
    private final DiaryPageContentValidator contentValidator;
    private final DiaryPageTagService tagService;
    private final ResourceAccessGuard accessGuard;
    private final PessimisticLocks pessimisticLocks;

    public PagedResultDTO<BaseDiaryPageDTO> findAllDiaryPagesByDiary(UUID diaryId, int page) {
        if (page < 1) {
            throw new ServiceException("The page must be 1 or greater");
        }
        Diary diary = accessGuard.getDiaryByIdAndOwner(diaryId);
        ExpressionList<DiaryPage> query = database.find(DiaryPage.class)
                .setLabel("findAllDiaryPagesByDiary")
                .where().eq("diary.id", diary.getId())
                .orderBy("pageNumber")
                .setFirstRow((int) Math.min((page - 1L) * PAGES_PER_VIEW, Integer.MAX_VALUE))
                .setMaxRows(PAGES_PER_VIEW);
        PagedList<DiaryPage> pages = query.findPagedList();
        Map<UUID, String> tags = tagService.findTagByPages(pages.getList().stream().map(DiaryPage::getId).toList());
        return PagedResultDTO.of(pages, found -> BaseDiaryPageDTO.from(found, tags.get(found.getId())));
    }

    public BaseDiaryPageDTO createDiaryPage(UUID diaryId, InsertDiaryPageDTO request) {
        Diary diary = accessGuard.getDiaryByIdAndOwner(diaryId);
        validateContent(request.content());
        DiaryPage page = new DiaryPage();
        page.setDiary(diary);
        page.setContent(request.content());
        try (Transaction transaction = database.beginTransaction()) {
            pessimisticLocks.lockDiary(diary.getId());
            int count = database.find(DiaryPage.class).setLabel("createDiaryPage").where().eq("diary.id", diary.getId()).findCount();
            if (count >= MAX_PAGES) {
                throw new ServiceException("The diary is full (" + MAX_PAGES + " pages)");
            }
            page.setPageNumber((short) (count + 1));
            database.save(page);
            transaction.commit();
        } catch (PersistenceException e) {
            if (isConcurrentInsert(e)) {
                throw new ServiceException("Another page was added at the same time, try again");
            }
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found");
            }
            throw e;
        }
        return BaseDiaryPageDTO.from(page, null);
    }

    public BaseDiaryPageDTO updateDiaryPage(UUID id, UpdateDiaryPageDTO request) {
        DiaryPage page = accessGuard.getDiaryPageByIdAndOwner(id);
        if (!Objects.equals(page.getVersion(), request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The page was modified elsewhere, reload it");
        }
        validateContent(request.content());
        page.setContent(request.content());
        database.save(page);
        return BaseDiaryPageDTO.from(page, tagService.findTagByPages(List.of(page.getId())).get(page.getId()));
    }

    public void deleteDiaryPage(UUID id) {
        UUID diaryId = accessGuard.getDiaryPageByIdAndOwner(id).getDiary().getId();
        try (Transaction transaction = database.beginTransaction()) {
            pessimisticLocks.lockDiary(diaryId, "Page not found");
            DiaryPage page = accessGuard.getDiaryPageByIdAndOwner(id);
            database.delete(page);
            List<DiaryPage> following = database.find(DiaryPage.class)
                    .setLabel("deleteDiaryPage")
                    .select("pageNumber, version")
                    .where().eq("diary.id", diaryId).gt("pageNumber", page.getPageNumber())
                    .orderBy("pageNumber")
                    .findList();
            for (DiaryPage next : following) {
                next.setPageNumber((short) (next.getPageNumber() - 1));
            }
            database.saveAll(following);
            transaction.commit();
        }
    }

    private static boolean isConcurrentInsert(PersistenceException e) {
        return e instanceof DuplicateKeyException || SqlStates.hasSqlState(e, CONCURRENT_INSERT_STATES);
    }

    private void validateContent(Map<String, Object> content) {
        if (objectMapper.writeValueAsBytes(content).length > config.getMaxPageContentSize().toBytes()) {
            throw new ServiceException("Page content too large");
        }
        contentValidator.validate(content);
    }
}
