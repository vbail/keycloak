/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.services.clientregistration.policy.impl;

import java.util.List;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.clientregistration.ClientRegistrationContext;
import org.keycloak.services.clientregistration.ClientRegistrationProvider;
import org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy;
import org.keycloak.services.clientregistration.policy.ClientRegistrationPolicyException;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class ClientScopesClientRegistrationPolicy implements ClientRegistrationPolicy {

    private final KeycloakSession session;
    private final ComponentModel componentModel;

    public ClientScopesClientRegistrationPolicy(KeycloakSession session, ComponentModel componentModel) {
        this.session = session;
        this.componentModel = componentModel;
    }

    @Override
    public void beforeRegister(ClientRegistrationContext context) throws ClientRegistrationPolicyException {
        String clientTemplate = context.getClient().getClientTemplate();
        if (!isClientScopeAllowed(clientTemplate)) {
            throw new ClientRegistrationPolicyException("Not permitted to use specified clientScope");
        }
    }

    @Override
    public void afterRegister(ClientRegistrationContext context, ClientModel clientModel) {

    }

    @Override
    public void beforeUpdate(ClientRegistrationContext context, ClientModel clientModel) throws ClientRegistrationPolicyException {
        String newScope = context.getClient().getClientTemplate();

        // Check if template was already set before. Then we allow update
        // TODO:mposolda re-evaluate this implementation. Should check all clientScopes probably. Also should have a flag on policy if defaultClientScopes should be count or not
//        ClientScopeModel currentClientScope = clientModel.getClientTemplate();
//        if (currentClientScope == null || !currentClientScope.getName().equals(newScope)) {
//            if (!isClientScopeAllowed(newScope)) {
//                throw new ClientRegistrationPolicyException("Not permitted to use specified clientScope");
//            }
//        }
    }

    @Override
    public void afterUpdate(ClientRegistrationContext context, ClientModel clientModel) {

    }

    @Override
    public void beforeView(ClientRegistrationProvider provider, ClientModel clientModel) throws ClientRegistrationPolicyException {

    }

    @Override
    public void beforeDelete(ClientRegistrationProvider provider, ClientModel clientModel) throws ClientRegistrationPolicyException {

    }

    private boolean isClientScopeAllowed(String clientScope) {
        if (clientScope == null) {
            return true;
        } else {
            List<String> allowedTemplates = componentModel.getConfig().getList(ClientScopesClientRegistrationPolicyFactory.ALLOWED_CLIENT_TEMPLATES);
            return allowedTemplates.contains(clientScope);
        }
    }
}
