package dk.greenhour.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsRealmRolesToSpringRoles() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("user", "admin"))));

        assertThat(converter.convert(jwt))
            .extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void returnsNoRolesWhenClaimIsMissing() {
        assertThat(converter.convert(jwt(Map.of("scope", "openid")))).isEmpty();
    }

    @Test
    void ignoresMalformedRealmAccess() {
        assertThat(converter.convert(jwt(Map.of("realm_access", Map.of("roles", "admin"))))).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("someone")
            .issuedAt(Instant.now())
            .claims(c -> c.putAll(claims))
            .build();
    }
}
