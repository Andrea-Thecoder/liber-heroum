package it.liberheroum.security;

import it.liberheroum.config.KeycloakAdminConfig;
import it.liberheroum.services.Sleeper;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakIdentityProvider implements RoleClaimsReader, IdentityProviderAdmin {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final int NO_CONTENT = 204;
    private static final int NOT_FOUND = 404;

    private final Keycloak keycloak;
    private final KeycloakAdminConfig config;
    private final Sleeper sleeper;

    @Override
    public Set<AppRole> readRoles(Jwt jwt) {
        Set<AppRole> result = EnumSet.noneOf(AppRole.class);
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess != null && realmAccess.get(ROLES) instanceof List<?> roles) {
            for (Object role : roles) {
                if (role instanceof String name) {
                    AppRole.fromClaim(name).ifPresent(result::add);
                }
            }
        }
        return result;
    }

    @Override
    public void deleteUser(String subject) {
        int attempts = config.getDeleteRetries() + 1;
        Duration delay = config.getDeleteRetryInitialDelay();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (tryDeleteUser(subject, attempt, attempts)) {
                return;
            }
            if (attempt < attempts) {
                if (!pause(delay)) {
                    break;
                }
                delay = delay.multipliedBy(2);
            }
        }
        log.error("Keycloak user {} could not be deleted, giving up", subject);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The account could not be deleted on the authentication server, try again later");
    }

    private boolean tryDeleteUser(String subject, int attempt, int attempts) {
        try (Response response = keycloak.realm(config.getRealm()).users().delete(subject)) {
            int status = response.getStatus();
            if (status == NO_CONTENT || status == NOT_FOUND) {
                return true;
            }
            log.warn("Keycloak user {} not deleted (attempt {}/{}): HTTP {}", subject, attempt, attempts, status);
        } catch (RuntimeException e) {
            log.warn("Keycloak user {} not deleted (attempt {}/{}): {}", subject, attempt, attempts, e.toString());
        }
        return false;
    }

    private boolean pause(Duration delay) {
        try {
            sleeper.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to retry the Keycloak user deletion");
            return false;
        }
    }
}
