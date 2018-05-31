package org.keycloak.acr;

import javax.ws.rs.core.Response;

import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.Provider;
import org.keycloak.sessions.AuthenticationSessionModel;

public interface AcrProvider extends Provider {

    public static final String ACR_ATTRIBUTE_ID = "acrCompliance";

    public String buildAcrValue(UserModel user, AuthenticatedClientSessionModel clientSession);

    public Response validateAcrCompliance(AuthenticationSessionModel authenticationSession, String flowPath, EventBuilder event,
        RealmModel realm);

}
