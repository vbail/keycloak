package org.keycloak.acr;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class AcrSpi implements Spi {

    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "Acr Spi";
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return AcrProvider.class;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return AcrProviderFactory.class;
    }

}
