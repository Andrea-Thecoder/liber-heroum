package it.liberheroum.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

public interface RoleClaimsReader {

    Set<AppRole> readRoles(Jwt jwt);
}
