package it.liberheroum.unit.services;

import static it.liberheroum.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import it.liberheroum.model.AppUser;
import it.liberheroum.model.Diary;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.services.PessimisticLocks;
import it.liberheroum.unit.support.QueryStub;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PessimisticLocksTest {

    private static final UUID ID = UUID.fromString("5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4");

    @Mock
    private Database database;

    private PessimisticLocks locks;

    @BeforeEach
    void setUp() {
        locks = new PessimisticLocks(database);
    }

    @Test
    void lockDiary_existingRow_returnsLockedDiary() {
        Diary diary = new Diary();
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(diary);

        Diary result = locks.lockDiary(ID);

        assertThat(result).isSameAs(diary);
    }

    @Test
    void lockDiary_missingRow_throwsDiaryNotFound() {
        stubFind(database, Diary.class);

        Throwable thrown = catchThrowable(() -> locks.lockDiary(ID));

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                        .containsExactly(HttpStatus.NOT_FOUND, "Diary not found"));
    }

    @Test
    void lockDiary_missingRowWithMessage_throwsNotFoundWithThatMessage() {
        stubFind(database, Diary.class);

        Throwable thrown = catchThrowable(() -> locks.lockDiary(ID, "Page not found"));

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                        .containsExactly(HttpStatus.NOT_FOUND, "Page not found"));
    }

    @Test
    void lockGameCharacter_missingRow_throwsCharacterNotFound() {
        stubFind(database, GameCharacter.class);

        Throwable thrown = catchThrowable(() -> locks.lockGameCharacter(ID));

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                        .containsExactly(HttpStatus.NOT_FOUND, "Character not found"));
    }

    @Test
    void lockAppUser_missingRow_returnsNullWithoutThrowing() {
        stubFind(database, AppUser.class);

        AppUser result = locks.lockAppUser(ID);

        assertThat(result).isNull();
    }
}
