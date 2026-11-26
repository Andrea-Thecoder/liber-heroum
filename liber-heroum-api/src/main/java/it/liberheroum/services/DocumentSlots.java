package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.PersistenceContextScope;
import io.ebean.Transaction;
import it.liberheroum.model.AppUser;
import it.liberheroum.model.CharacterSheet;
import it.liberheroum.model.Diary;
import it.liberheroum.model.Document;
import it.liberheroum.model.GameCharacter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DocumentSlots {

    private final Database database;
    private final DocumentService documentService;

    public Document replace(Object owner, Document previous, Consumer<Document> link, Supplier<Document> upload) {
        Document replacement = null;
        try (Transaction transaction = database.beginTransaction()) {
            replacement = upload.get();
            link.accept(replacement);
            database.save(owner);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        } catch (RuntimeException e) {
            documentService.deleteDocumentFile(replacement);
            throw e;
        }
        documentService.deleteDocumentFile(previous);
        return replacement;
    }

    public void clear(Object owner, Document previous, Consumer<Document> link) {
        try (Transaction transaction = database.beginTransaction()) {
            link.accept(null);
            database.save(owner);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        }
        documentService.deleteDocumentFile(previous);
    }

    public void deleteWithDocuments(List<Document> documents, Runnable delete) {
        deleteWithDocuments(() -> { }, () -> documents, delete);
    }

    public void deleteWithDocuments(Runnable lock, Supplier<List<Document>> findDocuments, Runnable delete) {
        List<Document> documents;
        try (Transaction transaction = database.beginTransaction()) {
            lock.run();
            documents = findDocuments.get();
            delete.run();
            documents.forEach(documentService::deleteDocumentRecord);
            transaction.commit();
        }
        documents.forEach(documentService::deleteDocumentFile);
    }

    public List<Document> findDocumentsOfAppUser(UUID appUserId) {
        AppUser user = database.find(AppUser.class)
                .setLabel("findDocumentsOfAppUser")
                .setPersistenceContextScope(PersistenceContextScope.QUERY)
                .select("id")
                .fetch("avatarDocument")
                .where().idEq(appUserId)
                .findOne();
        List<UUID> characterIds = database.find(GameCharacter.class)
                .setLabel("findGameCharacterIdsOfAppUser")
                .where().eq("owner.id", appUserId)
                .findIds();
        List<Document> documents = new ArrayList<>();
        if (user != null && user.getAvatarDocument() != null) {
            documents.add(user.getAvatarDocument());
        }
        documents.addAll(findDocumentsOfGameCharacters(characterIds));
        return documents;
    }

    public List<Document> findDocumentsOfGameCharacters(Collection<UUID> characterIds) {
        if (characterIds.isEmpty()) {
            return List.of();
        }
        List<Document> documents = new ArrayList<>();
        database.find(GameCharacter.class)
                .setLabel("findAvatarsOfGameCharacters")
                .setPersistenceContextScope(PersistenceContextScope.QUERY)
                .select("id")
                .fetch("avatarDocument")
                .where().in("id", characterIds)
                .findList().stream()
                .map(GameCharacter::getAvatarDocument)
                .filter(Objects::nonNull)
                .forEach(documents::add);
        database.find(CharacterSheet.class)
                .setLabel("findSheetsOfGameCharacters")
                .setPersistenceContextScope(PersistenceContextScope.QUERY)
                .select("id")
                .fetch("document")
                .where().in("character.id", characterIds)
                .findList().stream()
                .map(CharacterSheet::getDocument)
                .forEach(documents::add);
        database.find(Diary.class)
                .setLabel("findCoversOfGameCharacters")
                .setPersistenceContextScope(PersistenceContextScope.QUERY)
                .select("id")
                .fetch("coverDocument")
                .where().in("character.id", characterIds)
                .findList().stream()
                .map(Diary::getCoverDocument)
                .filter(Objects::nonNull)
                .forEach(documents::add);
        return documents;
    }

    public List<Document> findDocumentsOfDiaries(Collection<UUID> diaryIds) {
        if (diaryIds.isEmpty()) {
            return List.of();
        }
        return database.find(Diary.class)
                .setLabel("findDocumentsOfDiaries")
                .setPersistenceContextScope(PersistenceContextScope.QUERY)
                .select("id")
                .fetch("coverDocument")
                .where().in("id", diaryIds)
                .findList().stream()
                .map(Diary::getCoverDocument)
                .filter(Objects::nonNull)
                .toList();
    }
}
