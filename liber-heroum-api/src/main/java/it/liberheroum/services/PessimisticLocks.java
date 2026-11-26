package it.liberheroum.services;

import io.ebean.Database;
import it.liberheroum.model.AppUser;
import it.liberheroum.model.Diary;
import it.liberheroum.model.GameCharacter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PessimisticLocks {

    static final String CHARACTER_NOT_FOUND = "Character not found";
    static final String DIARY_NOT_FOUND = "Diary not found";

    private final Database database;

    public AppUser lockAppUser(UUID id) {
        return database.find(AppUser.class)
                .setLabel("lockAppUser")
                .select("id")
                .forUpdate()
                .where().idEq(id)
                .findOne();
    }

    public GameCharacter lockGameCharacter(UUID id) {
        GameCharacter locked = database.find(GameCharacter.class)
                .setLabel("lockGameCharacter")
                .select("id")
                .forUpdate()
                .where().idEq(id)
                .findOne();
        if (locked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, CHARACTER_NOT_FOUND);
        }
        return locked;
    }

    public Diary lockDiary(UUID id) {
        return lockDiary(id, DIARY_NOT_FOUND);
    }

    public Diary lockDiary(UUID id, String notFoundMessage) {
        Diary locked = database.find(Diary.class)
                .setLabel("lockDiary")
                .select("id")
                .forUpdate()
                .where().idEq(id)
                .findOne();
        if (locked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        return locked;
    }
}
