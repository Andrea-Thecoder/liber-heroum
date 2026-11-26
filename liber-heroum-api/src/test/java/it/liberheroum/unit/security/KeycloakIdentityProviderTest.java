package it.liberheroum.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.liberheroum.config.KeycloakAdminConfig;
import it.liberheroum.security.AppRole;
import it.liberheroum.security.KeycloakIdentityProvider;
import it.liberheroum.services.Sleeper;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class KeycloakIdentityProviderTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final String REALM = "liber-heroum-dev";

    @Mock
    private Keycloak keycloak;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private Sleeper sleeper;

    private final KeycloakAdminConfig config = new KeycloakAdminConfig();

    private KeycloakIdentityProvider provider;

    @BeforeEach
    void setUp() {
        config.setRealm(REALM);
        provider = new KeycloakIdentityProvider(keycloak, config, sleeper);
        lenient().when(keycloak.realm(REALM)).thenReturn(realmResource);
        lenient().when(realmResource.users()).thenReturn(usersResource);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    private static Jwt jwtWithRealmAccess(Object realmAccess) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).claim("realm_access", realmAccess).build();
    }

    private static Jwt jwtWithRoles(String... roles) {
        return jwtWithRealmAccess(Map.of("roles", List.of(roles)));
    }

    private static Response response(int status) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    private void givenDeleteReturns(Response first, Response... others) {
        when(usersResource.delete(SUBJECT)).thenReturn(first, others);
    }

    @Test
    void deleteUser_noContent_callsKeycloakOnceWithoutWaitingAndClosesResponse() throws Exception {
        Response ok = response(204);
        givenDeleteReturns(ok);

        provider.deleteUser(SUBJECT);

        verify(usersResource, times(1)).delete(SUBJECT);
        verify(sleeper, never()).sleep(any());
        verify(ok).close();
    }

    @Test
    void deleteUser_notFound_isConsideredDone() throws Exception {
        givenDeleteReturns(response(404));

        provider.deleteUser(SUBJECT);

        verify(usersResource, times(1)).delete(SUBJECT);
        verify(sleeper, never()).sleep(any());
    }

    @Test
    void deleteUser_failsTwiceThenSucceeds_retriesWaitingTwiceAndClosesEveryResponse() throws Exception {
        Response first = response(500);
        Response second = response(503);
        Response third = response(204);
        givenDeleteReturns(first, second, third);

        provider.deleteUser(SUBJECT);

        verify(usersResource, times(3)).delete(SUBJECT);
        verify(sleeper, times(2)).sleep(any());
        verify(first).close();
        verify(second).close();
        verify(third).close();
    }

    @Test
    void deleteUser_alwaysFails_triesFourTimesWithDoublingDelaysThenThrowsBadGateway() throws Exception {
        givenDeleteReturns(response(500));

        Throwable thrown = catchThrowable(() -> provider.deleteUser(SUBJECT));

        verify(usersResource, times(4)).delete(SUBJECT);
        InOrder order = inOrder(sleeper);
        order.verify(sleeper).sleep(Duration.ofMillis(500));
        order.verify(sleeper).sleep(Duration.ofSeconds(1));
        order.verify(sleeper).sleep(Duration.ofSeconds(2));
        order.verifyNoMoreInteractions();
        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                        .containsExactly(HttpStatus.BAD_GATEWAY, "The account could not be deleted on the authentication server, try again later"));
    }

    @Test
    void deleteUser_customRetriesAndDelay_usesConfiguredValues() throws Exception {
        config.setDeleteRetries(1);
        config.setDeleteRetryInitialDelay(Duration.ofMillis(10));
        givenDeleteReturns(response(500));

        catchThrowable(() -> provider.deleteUser(SUBJECT));

        verify(usersResource, times(2)).delete(SUBJECT);
        verify(sleeper, times(1)).sleep(Duration.ofMillis(10));
    }

    @Test
    void deleteUser_zeroRetries_callsKeycloakOnceWithoutWaitingAndThrows() throws Exception {
        config.setDeleteRetries(0);
        givenDeleteReturns(response(500));

        Throwable thrown = catchThrowable(() -> provider.deleteUser(SUBJECT));

        verify(usersResource, times(1)).delete(SUBJECT);
        verify(sleeper, never()).sleep(any());
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401, 403, 409, 500, 502, 503})
    void deleteUser_unexpectedStatus_isRetried(int status) {
        givenDeleteReturns(response(status), response(204));

        provider.deleteUser(SUBJECT);

        verify(usersResource, times(2)).delete(SUBJECT);
    }

    static Stream<RuntimeException> clientFailures() {
        return Stream.of(new ProcessingException("connection refused"), new IllegalStateException("boom"));
    }

    @ParameterizedTest
    @MethodSource("clientFailures")
    void deleteUser_clientThrows_isRetried(RuntimeException failure) {
        when(usersResource.delete(SUBJECT)).thenThrow(failure).thenReturn(response(204));

        provider.deleteUser(SUBJECT);

        verify(usersResource, times(2)).delete(SUBJECT);
    }

    @Test
    void deleteUser_alwaysThrows_throwsBadGateway() {
        when(usersResource.delete(SUBJECT)).thenThrow(new ProcessingException("connection refused"));

        assertThatThrownBy(() -> provider.deleteUser(SUBJECT))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void deleteUser_interruptedWhileWaiting_stopsRetryingRestoresFlagAndThrowsBadGateway() throws Exception {
        givenDeleteReturns(response(500));
        doThrow(new InterruptedException()).when(sleeper).sleep(eq(Duration.ofMillis(500)));

        Throwable thrown = catchThrowable(() -> provider.deleteUser(SUBJECT));

        verify(usersResource, times(1)).delete(SUBJECT);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void readRoles_knownRealmRoles_returnsRoles() {
        Set<AppRole> roles = provider.readRoles(jwtWithRoles("user", "admin"));

        assertThat(roles).containsExactlyInAnyOrder(AppRole.USER, AppRole.ADMIN);
    }

    @Test
    void readRoles_mixedCaseRoles_matchesIgnoringCase() {
        Set<AppRole> roles = provider.readRoles(jwtWithRoles("User", "ADMIN"));

        assertThat(roles).containsExactlyInAnyOrder(AppRole.USER, AppRole.ADMIN);
    }

    @Test
    void readRoles_unknownRoles_discardsThem() {
        Set<AppRole> roles = provider.readRoles(jwtWithRoles("user", "offline_access", "uma_authorization", "master"));

        assertThat(roles).containsExactly(AppRole.USER);
    }

    @Test
    void readRoles_nonStringRoles_ignoresThem() {
        Set<AppRole> roles = provider.readRoles(jwtWithRealmAccess(Map.of("roles", List.of(1, Map.of("name", "admin"), "user"))));

        assertThat(roles).containsExactly(AppRole.USER);
    }

    @Test
    void readRoles_rolesNotAList_returnsEmpty() {
        Set<AppRole> roles = provider.readRoles(jwtWithRealmAccess(Map.of("roles", "admin")));

        assertThat(roles).isEmpty();
    }

    @Test
    void readRoles_realmAccessWithoutRoles_returnsEmpty() {
        Set<AppRole> roles = provider.readRoles(jwtWithRealmAccess(Map.of()));

        assertThat(roles).isEmpty();
    }

    @Test
    void readRoles_noRealmAccessClaim_returnsEmpty() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();

        Set<AppRole> roles = provider.readRoles(jwt);

        assertThat(roles).isEmpty();
    }
}
