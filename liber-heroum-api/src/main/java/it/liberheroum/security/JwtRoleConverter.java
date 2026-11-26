package it.liberheroum.security;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

@RequiredArgsConstructor
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String PREFIX = "ROLE_";

    private final RoleClaimsReader roleClaimsReader;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return roleClaimsReader.readRoles(jwt).stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIX + role.name()))
                .toList();
    }
}
