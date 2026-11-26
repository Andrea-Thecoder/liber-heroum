package it.liberheroum.services;

import io.ebean.Database;
import io.ebean.ExpressionList;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.game_character.BaseGameCharacterDTO;
import it.liberheroum.dto.game_character.DetailGameCharacterDTO;
import it.liberheroum.dto.game_character.InsertGameCharacterDTO;
import it.liberheroum.dto.game_character.UpdateGameCharacterDTO;
import it.liberheroum.dto.search.GameCharacterSearchRequest;
import it.liberheroum.exception.SqlStates;
import it.liberheroum.model.AppUser;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameCharacterService {

    private final Database database;
    private final JwtInspector jwtInspector;
    private final DocumentService documentService;
    private final CharacterBackgroundService backgroundService;
    private final ResourceAccessGuard accessGuard;
    private final DocumentSlots documentSlots;
    private final PessimisticLocks pessimisticLocks;

    public PagedResultDTO<BaseGameCharacterDTO> findAllGameCharacters(GameCharacterSearchRequest request) {
        ExpressionList<GameCharacter> query = database.find(GameCharacter.class)
                .setLabel("findAllGameCharacters")
                .fetch("avatarDocument")
                .where()
                .eq("owner.subject", jwtInspector.getSubject());
        request.applyFilter(query);
        request.applySortAndPagination(query, "name");
        return PagedResultDTO.of(query.findPagedList(),
                character -> BaseGameCharacterDTO.from(character,
                        documentService.getDocumentUrl(character.getAvatarDocument())));
    }

    public DetailGameCharacterDTO getGameCharacterById(UUID id) {
        return toDetailGameCharacterDTO(accessGuard.getGameCharacterWithAvatarByIdAndOwner(id));
    }

    public DetailGameCharacterDTO createGameCharacter(InsertGameCharacterDTO request) {
        AppUser user = accessGuard.getCurrentAppUser();
        GameCharacter character = new GameCharacter();
        character.setOwner(user);
        character.setKind(request.kind());
        apply(character, request.name(), request.campaignName(), request.backgroundColor());
        try {
            database.save(character);
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            throw e;
        }
        return toDetailGameCharacterDTO(character);
    }

    public DetailGameCharacterDTO updateGameCharacter(UUID id, UpdateGameCharacterDTO request) {
        GameCharacter character = accessGuard.getGameCharacterWithAvatarByIdAndOwner(id);
        apply(character, request.name(), request.campaignName(), request.backgroundColor());
        database.save(character);
        return toDetailGameCharacterDTO(character);
    }

    public DetailGameCharacterDTO updateGameCharacterAvatar(UUID id, MultipartFile file) {
        GameCharacter character = accessGuard.getGameCharacterWithAvatarByIdAndOwner(id);
        documentSlots.replace(character, character.getAvatarDocument(), character::setAvatarDocument,
                () -> documentService.createImageDocument(file));
        return toDetailGameCharacterDTO(character);
    }

    public void deleteGameCharacterAvatar(UUID id) {
        GameCharacter character = accessGuard.getGameCharacterWithAvatarByIdAndOwner(id);
        documentSlots.clear(character, character.getAvatarDocument(), character::setAvatarDocument);
    }

    public void deleteGameCharacter(UUID id) {
        GameCharacter character = accessGuard.getGameCharacterByIdAndOwner(id);
        try {
            documentSlots.deleteWithDocuments(
                    () -> pessimisticLocks.lockGameCharacter(character.getId()),
                    () -> documentSlots.findDocumentsOfGameCharacters(List.of(character.getId())),
                    () -> database.delete(character));
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The character changed while it was being deleted, try again");
            }
            throw e;
        }
    }


    private DetailGameCharacterDTO toDetailGameCharacterDTO(GameCharacter character) {
        return DetailGameCharacterDTO.from(character,
                documentService.getDocumentUrl(character.getAvatarDocument()),
                backgroundService.findCharacterBackgroundPreview(character.getId()));
    }

    private void apply(GameCharacter character, String name, String campaignName, String backgroundColor) {
        character.setName(name);
        character.setCampaignName(campaignName);
        character.setBackgroundColor(backgroundColor);
    }
}
