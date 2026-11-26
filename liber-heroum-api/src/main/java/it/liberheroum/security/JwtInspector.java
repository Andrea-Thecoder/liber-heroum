package it.liberheroum.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Component
public class JwtInspector {

    public String getSubject() {
        return getJwt().getSubject();
    }

    public String getEmail() {
        return getJwt().getClaimAsString("email");
    }

    public Optional<Instant> getAuthTime() {
        return Optional.ofNullable(getJwt().getClaimAsInstant("auth_time"));
    }

    private Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
