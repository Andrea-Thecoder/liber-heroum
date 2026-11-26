package it.liberheroum.unit.security;

import static org.assertj.core.api.Assertions.assertThat;

import it.liberheroum.security.AppRole;
import it.liberheroum.security.JwtRoleConverter;
import java.util.Collection;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    private static final Jwt JWT = Jwt.withTokenValue("token").header("alg", "none").subject("sub").build();

    @Test
    void convert_rolesReadFromToken_returnsPrefixedAuthorities() {
        JwtRoleConverter converter = new JwtRoleConverter(jwt -> EnumSet.of(AppRole.USER, AppRole.ADMIN));

        Collection<GrantedAuthority> authorities = converter.convert(JWT);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void convert_noRolesReadFromToken_returnsNoAuthorities() {
        JwtRoleConverter converter = new JwtRoleConverter(jwt -> EnumSet.noneOf(AppRole.class));

        Collection<GrantedAuthority> authorities = converter.convert(JWT);

        assertThat(authorities).isEmpty();
    }
}
