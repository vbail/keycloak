/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.migration.migrators;

import java.util.List;

import org.keycloak.component.ComponentModel;
import org.keycloak.migration.ModelVersion;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.idm.RealmRepresentation;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class MigrateTo4_0_0 implements Migration {

    public static final ModelVersion VERSION = new ModelVersion("4.0.0");

    @Override
    public ModelVersion getVersion() {
        return VERSION;
    }

    @Override
    public void migrate(KeycloakSession session) {
        session.realms().getRealms().stream().forEach(
                r -> {
                    migrateRealm(r);
                }
        );
    }

    @Override
    public void migrateImport(KeycloakSession session, RealmModel realm, RealmRepresentation rep, boolean skipUserDependent) {
        migrateRealm(realm);
    }

    // TODO:mposolda Test this migration (maybe write automated test)
    protected void migrateRealm(RealmModel realm) {
        // Upgrade names of clientScopes to not contain space
        for (ClientScopeModel clientScope : realm.getClientScopes()) {
            if (clientScope.getName().contains(" ")) {
                String replacedName = clientScope.getName().replaceAll(" ", "_");
                clientScope.setName(replacedName);
            }
        }

        for (ComponentModel component : realm.getComponents(realm.getId(), "org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy")) {
            if ("allowed-client-templates".equals(component.getProviderId())) {
                component.setProviderId("allowed-client-scopes");

                List<String> configVal = component.getConfig().remove("allowed-client-templates");
                if (configVal != null) {
                    component.getConfig().put("allowed-client-scopes", configVal);
                }
                component.put("allow-default-scopes", true);

                realm.updateComponent(component);
            }
        }

    }
}
