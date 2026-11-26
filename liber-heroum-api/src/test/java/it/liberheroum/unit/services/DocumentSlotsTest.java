package it.liberheroum.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.Transaction;
import it.liberheroum.model.Diary;
import it.liberheroum.model.Document;
import it.liberheroum.services.DocumentService;
import it.liberheroum.services.DocumentSlots;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentSlotsTest {

    @Mock
    private Database database;
    @Mock
    private DocumentService documentService;
    @Mock
    private Transaction transaction;
    @Mock
    private Runnable lock;
    @Mock
    private Supplier<List<Document>> findDocuments;
    @Mock
    private Runnable delete;

    private DocumentSlots slots;

    @BeforeEach
    void setUp() {
        slots = new DocumentSlots(database, documentService);
        when(database.beginTransaction()).thenReturn(transaction);
    }

    @Test
    void replace_previousDocument_linksNewOneDeletesPreviousRecordBeforeCommitAndFileAfterIt() {
        Document previous = document();
        Document replacement = document();
        Diary owner = new Diary();
        owner.setCoverDocument(previous);

        Document result = slots.replace(owner, previous, owner::setCoverDocument, () -> replacement);

        InOrder order = inOrder(database, documentService, transaction);
        order.verify(database).save(owner);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
        assertThat(result).isSameAs(replacement).isSameAs(owner.getCoverDocument());
    }

    @Test
    void replace_saveFails_rethrowsDeletesNewFileAndKeepsPreviousFile() {
        Document previous = document();
        Document replacement = document();
        Diary owner = new Diary();
        doThrow(new IllegalStateException("db down")).when(database).save(owner);

        Throwable thrown = catchThrowable(() -> slots.replace(owner, previous, owner::setCoverDocument, () -> replacement));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(transaction, never()).commit();
        verify(documentService).deleteDocumentFile(replacement);
        verify(documentService, never()).deleteDocumentFile(previous);
    }

    @Test
    void replace_uploadFails_rethrowsWithoutSavingOrCommitting() {
        Document previous = document();
        Diary owner = new Diary();
        owner.setCoverDocument(previous);

        Throwable thrown = catchThrowable(() -> slots.replace(owner, previous, owner::setCoverDocument,
                () -> {
                    throw new IllegalStateException("storage down");
                }));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(database, never()).save(any());
        verify(transaction, never()).commit();
        verify(documentService, never()).deleteDocumentFile(previous);
        assertThat(owner.getCoverDocument()).isSameAs(previous);
    }

    @Test
    void clear_withDocument_unlinksDeletesRecordThenCommitsThenDeletesFile() {
        Document previous = document();
        Diary owner = new Diary();
        owner.setCoverDocument(previous);

        slots.clear(owner, previous, owner::setCoverDocument);

        InOrder order = inOrder(database, documentService, transaction);
        order.verify(database).save(owner);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
        assertThat(owner.getCoverDocument()).isNull();
    }

    @Test
    void clear_withoutDocument_savesOwnerAndCommits() {
        Diary owner = new Diary();

        slots.clear(owner, null, owner::setCoverDocument);

        verify(database).save(owner);
        verify(transaction).commit();
    }

    @Test
    void clear_saveFails_rethrowsAndKeepsFile() {
        Document previous = document();
        Diary owner = new Diary();
        doThrow(new IllegalStateException("db down")).when(database).save(owner);

        Throwable thrown = catchThrowable(() -> slots.clear(owner, previous, owner::setCoverDocument));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(transaction, never()).commit();
        verify(documentService, never()).deleteDocumentFile(previous);
    }

    @Test
    void deleteWithDocuments_documentsFound_locksDeletesRecordsBeforeCommitAndFilesAfterIt() {
        Document avatar = document();
        Document cover = document();
        when(findDocuments.get()).thenReturn(List.of(avatar, cover));

        slots.deleteWithDocuments(lock, findDocuments, delete);

        InOrder order = inOrder(lock, findDocuments, delete, documentService, transaction);
        order.verify(lock).run();
        order.verify(findDocuments).get();
        order.verify(delete).run();
        order.verify(documentService).deleteDocumentRecord(avatar);
        order.verify(documentService).deleteDocumentRecord(cover);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(avatar);
        order.verify(documentService).deleteDocumentFile(cover);
    }

    @Test
    void deleteWithDocuments_deleteFails_rethrowsAndKeepsFiles() {
        when(findDocuments.get()).thenReturn(List.of(document()));
        doThrow(new IllegalStateException("db down")).when(delete).run();

        Throwable thrown = catchThrowable(() -> slots.deleteWithDocuments(lock, findDocuments, delete));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(transaction, never()).commit();
        verify(documentService, never()).deleteDocumentFile(any());
    }

    private static Document document() {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        return document;
    }
}
