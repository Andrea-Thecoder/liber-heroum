package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.ExpressionList;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.diary.BaseDiaryDTO;
import it.liberheroum.dto.diary.BookmarkDiaryDTO;
import it.liberheroum.dto.diary.DetailDiaryDTO;
import it.liberheroum.dto.diary.InsertDiaryDTO;
import it.liberheroum.dto.diary.UpdateBookmarkDiaryDTO;
import it.liberheroum.dto.diary.UpdateDiaryDTO;
import it.liberheroum.dto.search.DiarySearchRequest;
import it.liberheroum.exception.SqlStates;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPage;
import it.liberheroum.model.DiaryPageTag;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryService {

    private final Database database;
    private final JwtInspector jwtInspector;
    private final DocumentService documentService;
    private final ResourceAccessGuard accessGuard;
    private final DocumentSlots documentSlots;
    private final PessimisticLocks pessimisticLocks;

    public PagedResultDTO<BaseDiaryDTO> findAllDiariesByCharacter(UUID characterId, DiarySearchRequest request) {
        GameCharacter character = accessGuard.getGameCharacterByIdAndOwner(characterId);
        ExpressionList<Diary> query = database.find(Diary.class)
                .setLabel("findAllDiariesByCharacter")
                .fetch("coverDocument")
                .where().eq("character.id", character.getId());
        request.applyFilter(query);
        applyTagFilter(query, request.getTag());
        request.applySortAndPagination(query, "title");
        return PagedResultDTO.of(query.findPagedList(),
                diary -> BaseDiaryDTO.from(diary, documentService.getDocumentUrl(diary.getCoverDocument())));
    }

    private void applyTagFilter(ExpressionList<Diary> query, String tag) {
        if (StringUtils.isNotBlank(tag)) {
            query.in("id", database.find(DiaryPageTag.class)
                    .setLabel("findDiaryIdsByTag")
                    .select("diary.id")
                    .where().icontains("name", tag.trim()).eq("diary.character.owner.subject", jwtInspector.getSubject())
                    .query());
        }
    }

    public DetailDiaryDTO getDiaryById(UUID id) {
        return toDetailDiaryDTO(accessGuard.getDiaryWithCoverByIdAndOwner(id));
    }

    public DetailDiaryDTO createDiary(UUID characterId, InsertDiaryDTO request) {
        GameCharacter character = accessGuard.getGameCharacterByIdAndOwner(characterId);
        Diary diary = new Diary();
        diary.setCharacter(character);
        apply(diary, request.title(), request.description(), request.coverColor(), request.font(), request.fontSize());
        try {
            database.save(diary);
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
            }
            throw e;
        }
        return toDetailDiaryDTO(diary);
    }

    public DetailDiaryDTO updateDiary(UUID id, UpdateDiaryDTO request) {
        Diary diary = accessGuard.getDiaryWithCoverByIdAndOwner(id);
        apply(diary, request.title(), request.description(), request.coverColor(), request.font(), request.fontSize());
        database.save(diary);
        return toDetailDiaryDTO(diary);
    }

    public DetailDiaryDTO updateDiaryCover(UUID id, MultipartFile file) {
        Diary diary = accessGuard.getDiaryWithCoverByIdAndOwner(id);
        documentSlots.replace(diary, diary.getCoverDocument(), diary::setCoverDocument,
                () -> documentService.createImageDocument(file));
        return toDetailDiaryDTO(diary);
    }

    public void deleteDiaryCover(UUID id) {
        Diary diary = accessGuard.getDiaryWithCoverByIdAndOwner(id);
        documentSlots.clear(diary, diary.getCoverDocument(), diary::setCoverDocument);
    }

    public BookmarkDiaryDTO getDiaryBookmark(UUID id) {
        return BookmarkDiaryDTO.from(accessGuard.getDiaryWithBookmarkByIdAndOwner(id).getBookmarkPage());
    }

    public BookmarkDiaryDTO updateDiaryBookmark(UUID id, UpdateBookmarkDiaryDTO request) {
        Diary diary = accessGuard.getDiaryWithCoverByIdAndOwner(id);
        DiaryPage page = database.find(DiaryPage.class)
                .setLabel("getBookmarkPage")
                .where().idEq(request.pageId()).eq("diary.id", diary.getId())
                .findOne();
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        diary.setBookmarkPage(page);
        try {
            database.save(diary);
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
            }
            throw e;
        }
        return BookmarkDiaryDTO.from(page);
    }

    public void deleteDiaryBookmark(UUID id) {
        Diary diary = accessGuard.getDiaryWithCoverByIdAndOwner(id);
        diary.setBookmarkPage(null);
        database.save(diary);
    }

    public void deleteDiary(UUID id) {
        Diary diary = accessGuard.getDiaryByIdAndOwner(id);
        try {
            documentSlots.deleteWithDocuments(
                    () -> pessimisticLocks.lockDiary(diary.getId()),
                    () -> documentSlots.findDocumentsOfDiaries(List.of(diary.getId())),
                    () -> database.delete(diary));
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The diary changed while it was being deleted, try again");
            }
            throw e;
        }
    }

    private DetailDiaryDTO toDetailDiaryDTO(Diary diary) {
        return DetailDiaryDTO.from(diary, documentService.getDocumentUrl(diary.getCoverDocument()));
    }

    private void apply(Diary diary, String title, String description, String coverColor, String font,
                       Short fontSize) {
        diary.setTitle(title);
        diary.setDescription(description);
        diary.setCoverColor(coverColor);
        diary.setFont(font);
        diary.setFontSize(fontSize);
    }
}
