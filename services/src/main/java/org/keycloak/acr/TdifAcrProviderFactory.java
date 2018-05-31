package org.keycloak.acr;

import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class TdifAcrProviderFactory implements AcrProviderFactory {

    public static final String TDIF_ACR_PROVIDER_ID = "TDIF";

    @Override
    public AcrProvider create(KeycloakSession session) {
        return new TdifAcrProvider(session);
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
        return TDIF_ACR_PROVIDER_ID;
    }

}
