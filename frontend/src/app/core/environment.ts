// The browser talks to Keycloak directly, so this is the address as seen from the
// user's machine, not the Docker network name.
export const environment = {
  keycloakUrl: 'http://localhost:8081',
  keycloakRealm: 'greenhour',
  keycloakClientId: 'greenhour-web',
};
