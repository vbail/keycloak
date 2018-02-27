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

package org.keycloak.services.util;

import java.util.HashSet;
import java.util.Set;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.oidc.TokenManager;

/**
 * Not thread safe. It's single request object
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class DefaultClientSessionContext implements ClientSessionContext {

    private final AuthenticatedClientSessionModel clientSession;
    private final Set<String> clientScopeIds;

    private Set<ClientScopeModel> clientScopes;
    private Set<RoleModel> roles;
    private Set<ProtocolMapperModel> protocolMappers;

    private DefaultClientSessionContext(AuthenticatedClientSessionModel clientSession, Set<String> clientScopeIds) {
        this.clientSession = clientSession;
        this.clientScopeIds = clientScopeIds;
    }


    public static DefaultClientSessionContext fromClientScopeIds(AuthenticatedClientSessionModel clientSession, Set<String> clientScopeIds) {
        return new DefaultClientSessionContext(clientSession, clientScopeIds);
    }


    public static DefaultClientSessionContext fromClientScopes(AuthenticatedClientSessionModel clientSession, Set<ClientScopeModel> clientScopes) {
        Set<String> clientScopeIds = new HashSet<>();
        for (ClientScopeModel clientScope : clientScopes) {
            clientScopeIds.add(clientScope.getId());
        }

        DefaultClientSessionContext ctx = new DefaultClientSessionContext(clientSession, clientScopeIds);
        ctx.clientScopes = new HashSet<>(clientScopes);
        return ctx;
    }


    public AuthenticatedClientSessionModel getClientSession() {
        return clientSession;
    }


    public Set<String> getClientScopeIds() {
        return clientScopeIds;
    }


    public Set<ClientScopeModel> getClientScopes() {
        // Load client scopes if not yet present
        if (clientScopes == null) {
            clientScopes = loadClientScopes();
        }
        return clientScopes;
    }


    public Set<RoleModel> getRoles() {
        // Load roles if not yet present
        if (roles == null) {
            roles = loadRoles();
        }
        return roles;
    }


    public Set<ProtocolMapperModel> getProtocolMappers() {
        // Load roles if not yet present
        if (protocolMappers == null) {
            protocolMappers = loadProtocolMappers();
        }
        return protocolMappers;
    }


    // Loading data

    private Set<ClientScopeModel> loadClientScopes() {
        Set<ClientScopeModel> clientScopes = new HashSet<>();
        for (String scopeId : clientScopeIds) {
            ClientScopeModel clientScope = KeycloakModelUtils.findClientScopeById(clientSession.getClient().getRealm(), scopeId);
            if (clientScope != null) {
                clientScopes.add(clientScope);
            }
        }
        return clientScopes;
    }


    private Set<RoleModel> loadRoles() {
        UserModel user = clientSession.getUserSession().getUser();
        ClientModel client = clientSession.getClient();

        Set<ClientScopeModel> clientScopes = getClientScopes();

        return TokenManager.getAccess(user, client, clientScopes);
    }


    private Set<ProtocolMapperModel> loadProtocolMappers() {
        Set<ClientScopeModel> clientScopes = getClientScopes();
        String protocol = clientSession.getClient().getProtocol();

        Set<ProtocolMapperModel> protocolMappers = new HashSet<>();
        for (ClientScopeModel clientScope : clientScopes) {
            Set<ProtocolMapperModel> currentMappers = clientScope.getProtocolMappers();
            for (ProtocolMapperModel currentMapper : currentMappers) {
                if (protocol.equals(currentMapper.getProtocol())) {
                    protocolMappers.add(currentMapper);
                }
            }
        }

        return protocolMappers;
    }

}
