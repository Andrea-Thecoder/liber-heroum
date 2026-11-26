package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.Query;
import it.liberheroum.model.AppUser;
import it.liberheroum.model.CharacterSheet;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPage;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.model.Payment;
import it.liberheroum.security.JwtInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ResourceAccessGuard {

    static final String CHARACTER_NOT_FOUND = "Character not found";
    static final String DIARY_NOT_FOUND = "Diary not found";
    static final String PAGE_NOT_FOUND = "Page not found";
    static final String SHEET_NOT_FOUND = "Sheet not found";
    static final String PAYMENT_NOT_FOUND = "Payment not found";
    static final String USER_NOT_FOUND = "User not found";
    private static final String CHARACTER_OWNER = "owner.subject";
    private static final String DIARY_OWNER = "character.owner.subject";
    private static final String PAGE_OWNER = "diary.character.owner.subject";
    private static final String SHEET_OWNER = "character.owner.subject";
    private static final String PAYMENT_OWNER = "appUser.subject";

    private final Database database;
    private final JwtInspector jwtInspector;

    public GameCharacter getGameCharacterByIdAndOwner(UUID id) {
        return owned(database.find(GameCharacter.class)
                .setLabel("getGameCharacterByIdAndOwner"), CHARACTER_OWNER, id, CHARACTER_NOT_FOUND);
    }

    public GameCharacter getGameCharacterWithAvatarByIdAndOwner(UUID id) {
        return owned(database.find(GameCharacter.class)
                .setLabel("getGameCharacterWithAvatarByIdAndOwner")
                .fetch("avatarDocument"), CHARACTER_OWNER, id, CHARACTER_NOT_FOUND);
    }

    public Diary getDiaryByIdAndOwner(UUID id) {
        return owned(database.find(Diary.class)
                .setLabel("getDiaryByIdAndOwner"), DIARY_OWNER, id, DIARY_NOT_FOUND);
    }

    public Diary getDiaryWithCoverByIdAndOwner(UUID id) {
        return owned(database.find(Diary.class)
                .setLabel("getDiaryWithCoverByIdAndOwner")
                .fetch("coverDocument"), DIARY_OWNER, id, DIARY_NOT_FOUND);
    }

    public DiaryPage getDiaryPageByIdAndOwner(UUID id) {
        return owned(database.find(DiaryPage.class)
                .setLabel("getDiaryPageByIdAndOwner"), PAGE_OWNER, id, PAGE_NOT_FOUND);
    }

    public DiaryPage getDiaryPageReferenceByIdAndOwner(UUID id) {
        return owned(database.find(DiaryPage.class)
                .setLabel("getDiaryPageReferenceByIdAndOwner")
                .select("id, diary"), PAGE_OWNER, id, PAGE_NOT_FOUND);
    }

    public Diary getDiaryWithBookmarkByIdAndOwner(UUID id) {
        return owned(database.find(Diary.class)
                .setLabel("getDiaryWithBookmarkByIdAndOwner")
                .fetch("bookmarkPage", "pageNumber"), DIARY_OWNER, id, DIARY_NOT_FOUND);
    }

    public CharacterSheet getCharacterSheetWithDocumentByIdAndOwner(UUID id) {
        return owned(database.find(CharacterSheet.class)
                .setLabel("getCharacterSheetWithDocumentByIdAndOwner")
                .fetch("document"), SHEET_OWNER, id, SHEET_NOT_FOUND);
    }

    public Payment getPaymentByIdAndOwner(UUID id) {
        return owned(database.find(Payment.class)
                .setLabel("getPaymentByIdAndOwner"), PAYMENT_OWNER, id, PAYMENT_NOT_FOUND);
    }

    public AppUser getCurrentAppUser() {
        AppUser user = database.find(AppUser.class)
                .setLabel("getCurrentAppUser")
                .fetch("avatarDocument")
                .where().eq("subject", jwtInspector.getSubject())
                .findOne();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND);
        }
        return user;
    }

    private <T> T owned(Query<T> query, String ownerPath, UUID id, String notFound) {
        T found = query.where().idEq(id).eq(ownerPath, jwtInspector.getSubject()).findOne();
        if (found == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFound);
        }
        return found;
    }
}
