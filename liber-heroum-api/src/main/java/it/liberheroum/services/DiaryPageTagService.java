package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.diary_page_tag.BaseDiaryTagDTO;
import it.liberheroum.dto.diary_page_tag.DetailDiaryPageTagDTO;
import it.liberheroum.dto.diary_page_tag.UpdateDiaryPageTagDTO;
import it.liberheroum.dto.search.DiaryTagSearchRequest;
import it.liberheroum.exception.ServiceException;
import it.liberheroum.exception.SqlStates;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPageTag;
import it.liberheroum.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryPageTagService {

    /** Massimo numero di tag diversi (ignorando le maiuscole) in un diario. */
    static final int MAX_TAGS_PER_DIARY = 10;

    private final Database database;
    private final JwtInspector jwtInspector;
    private final ResourceAccessGuard accessGuard;
    private final PessimisticLocks pessimisticLocks;

    public PagedResultDTO<BaseDiaryTagDTO> findAllDiaryTagsByDiary(UUID diaryId, DiaryTagSearchRequest request) {
        accessGuard.getDiaryByIdAndOwner(diaryId);
        ExpressionList<DiaryPageTag> query = database.find(DiaryPageTag.class)
                .setLabel("findAllDiaryTagsByDiary")
                .select("name")
                .fetch("page", "pageNumber")
                .where()
                .eq("diary.id", diaryId)
                .eq("diary.character.owner.subject", jwtInspector.getSubject());
        request.applyFilter(query);
        List<DiaryPageTag> tags = query.orderBy("page.pageNumber").findList();
        Map<String, List<DiaryPageTag>> groups = new LinkedHashMap<>();
        for (DiaryPageTag tag : tags) {
            groups.computeIfAbsent(key(tag.getName()), name -> new ArrayList<>()).add(tag);
        }
        List<BaseDiaryTagDTO> all = groups.values().stream()
                .map(group -> new BaseDiaryTagDTO(group.getFirst().getName(),
                        group.stream().map(tag -> tag.getPage().getPageNumber()).toList()))
                .toList();
        int from = (int) Math.min((request.getPage() - 1L) * request.getSize(), all.size());
        int to = Math.min(from + request.getSize(), all.size());
        return PagedResultDTO.of(all.subList(from, to), all.size(), request.getPage(), request.getSize());
    }

    /** Tag delle pagine date (solo quelle con un tag), filtrati per proprietario. */
    public Map<UUID, String> findTagByPages(Collection<UUID> pageIds) {
        if (pageIds.isEmpty()) {
            return Map.of();
        }
        return database.find(DiaryPageTag.class)
                .setLabel("findTagByPages")
                .select("name, page")
                .where().in("page.id", pageIds).eq("diary.character.owner.subject", jwtInspector.getSubject())
                .findList().stream()
                .collect(Collectors.toMap(tag -> tag.getPage().getId(), DiaryPageTag::getName, (first, second) -> first));
    }

    public DetailDiaryPageTagDTO updateDiaryPageTag(UUID pageId, UpdateDiaryPageTagDTO request) {
        UUID diaryId = accessGuard.getDiaryPageReferenceByIdAndOwner(pageId).getDiary().getId();
        String requested = request.tag() == null ? null : request.tag().trim();
        String result;
        try (Transaction transaction = database.beginTransaction()) {
            Diary diary = pessimisticLocks.lockDiary(diaryId, "Page not found");
            accessGuard.getDiaryPageReferenceByIdAndOwner(pageId);
            DiaryPageTag current = database.find(DiaryPageTag.class)
                    .setLabel("updateDiaryPageTag")
                    .where().eq("page.id", pageId).eq("diary.character.owner.subject", jwtInspector.getSubject())
                    .findOne();
            if (requested == null) {
                if (current != null) {
                    database.delete(current);
                }
                result = null;
            } else {
                Map<String, String> others = findOtherTagsOfDiary(diaryId, pageId);
                String name = others.getOrDefault(key(requested), requested);
                if (!others.containsKey(key(name)) && others.size() >= MAX_TAGS_PER_DIARY) {
                    throw new ServiceException("A diary can have at most " + MAX_TAGS_PER_DIARY + " different tags");
                }
                if (current == null) {
                    current = new DiaryPageTag();
                    current.setDiary(diary);
                    current.setPage(accessGuard.getDiaryPageReferenceByIdAndOwner(pageId));
                }
                if (!name.equals(current.getName())) {
                    current.setName(name);
                    database.save(current);
                }
                result = name;
            }
            transaction.commit();
        } catch (PersistenceException e) {
            if (e instanceof DuplicateKeyException || SqlStates.hasSqlState(e, Set.of(SqlStates.UNIQUE_VIOLATION))) {
                throw new ServiceException("The tag of the page was changed at the same time, try again");
            }
            throw e;
        }
        return new DetailDiaryPageTagDTO(result);
    }

    /** Tag del diario usati da pagine diverse da quella data, per chiave (nome in minuscolo) con la grafia salvata. */
    private Map<String, String> findOtherTagsOfDiary(UUID diaryId, UUID pageId) {
        return database.find(DiaryPageTag.class)
                .setLabel("findOtherTagsOfDiary")
                .select("name")
                .where().eq("diary.id", diaryId).ne("page.id", pageId).eq("diary.character.owner.subject", jwtInspector.getSubject())
                .orderBy("name")
                .findList().stream()
                .collect(Collectors.toMap(tag -> key(tag.getName()), DiaryPageTag::getName, (first, second) -> first, LinkedHashMap::new));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
