package org.keycloak.acr;

import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class DefaultAcrProviderFactory implements AcrProviderFactory {

    public static final String DEFAULT_ACR_PROVIDER_ID = "defaultAcr";

    @Override
    public AcrProvider create(KeycloakSession session) {
        return new DefaultAcrProvider(session);
    }

    @Override
    public void init(Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return DEFAULT_ACR_PROVIDER_ID;
    }

}
