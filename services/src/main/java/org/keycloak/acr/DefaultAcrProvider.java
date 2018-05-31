package org.keycloak.acr;

import javax.ws.rs.core.Response;

import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.sessions.AuthenticationSessionModel;

public class DefaultAcrProvider implements AcrProvider {

    private KeycloakSession session;

    public DefaultAcrProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void close() {
    }

    @Override
    public String buildAcrValue(UserModel user, AuthenticatedClientSessionModel clientSession) {
        return (AuthenticationManager.isSSOAuthentication(clientSession)) ? "0" : "1";
    }

    @Override
    public Response validateAcrCompliance(AuthenticationSessionModel authenticationSession, String flowPath, EventBuilder event,
        RealmModel realm) {
        return null;
    }

}
