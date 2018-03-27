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

package org.keycloak.services.managers;

import org.keycloak.OAuth2Constants;
import org.keycloak.common.util.Time;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperContainerModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.sessions.CommonClientSessionModel;

import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ClientSessionCode<CLIENT_SESSION extends CommonClientSessionModel> {

    private KeycloakSession session;
    private final RealmModel realm;
    private final CLIENT_SESSION commonLoginSession;

    public enum ActionType {
        CLIENT,
        LOGIN,
        USER
    }

    public ClientSessionCode(KeycloakSession session, RealmModel realm, CLIENT_SESSION commonLoginSession) {
        this.session = session;
        this.realm = realm;
        this.commonLoginSession = commonLoginSession;
    }

    public static class ParseResult<CLIENT_SESSION extends CommonClientSessionModel> {
        ClientSessionCode<CLIENT_SESSION> code;
        boolean authSessionNotFound;
        boolean illegalHash;
        boolean expiredToken;
        CLIENT_SESSION clientSession;

        public ClientSessionCode<CLIENT_SESSION> getCode() {
            return code;
        }

        public boolean isAuthSessionNotFound() {
            return authSessionNotFound;
        }

        public boolean isIllegalHash() {
            return illegalHash;
        }

        public boolean isExpiredToken() {
            return expiredToken;
        }

        public CLIENT_SESSION getClientSession() {
            return clientSession;
        }
    }

    public static <CLIENT_SESSION extends CommonClientSessionModel> ParseResult<CLIENT_SESSION> parseResult(String code, String tabId,
                                                                                                            KeycloakSession session, RealmModel realm, ClientModel client,
                                                                                                            EventBuilder event, Class<CLIENT_SESSION> sessionClass) {
        ParseResult<CLIENT_SESSION> result = new ParseResult<>();
        if (code == null) {
            result.illegalHash = true;
            return result;
        }
        try {
            CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser = CodeGenerateUtil.getParser(sessionClass);
            result.clientSession = getClientSession(code, tabId, session, realm, client, event, clientSessionParser);
            return parseResult(code, session, realm, result, clientSessionParser);
        } catch (RuntimeException e) {
            result.illegalHash = true;
            return result;
        }
    }

    public static <CLIENT_SESSION extends CommonClientSessionModel> ParseResult<CLIENT_SESSION> parseResult(String code, String tabId,
                                                                                                            KeycloakSession session, RealmModel realm, ClientModel client,
                                                                                                            EventBuilder event, CLIENT_SESSION clientSession) {
        ParseResult<CLIENT_SESSION> result = new ParseResult<>();
        result.clientSession = clientSession;
        if (code == null) {
            result.illegalHash = true;
            return result;
        }
        try {
            CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser = CodeGenerateUtil.getParser((Class<CLIENT_SESSION>)clientSession.getClass());
            return parseResult(code, session, realm, result, clientSessionParser);
        } catch (RuntimeException e) {
            result.illegalHash = true;
            return result;
        }
    }

    private static <CLIENT_SESSION extends CommonClientSessionModel> ParseResult<CLIENT_SESSION> parseResult(String code, KeycloakSession session, RealmModel realm, ParseResult<CLIENT_SESSION> result, CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser) {
        if (result.clientSession == null) {
            result.authSessionNotFound = true;
            return result;
        }

        if (!clientSessionParser.verifyCode(session, code, result.clientSession)) {
            result.illegalHash = true;
            return result;
        }

        if (clientSessionParser.isExpired(session, code, result.clientSession)) {
            result.expiredToken = true;
            return result;
        }

        result.code = new ClientSessionCode<CLIENT_SESSION>(session, realm, result.clientSession);
        return result;
    }


    public static <CLIENT_SESSION extends CommonClientSessionModel> CLIENT_SESSION getClientSession(String code, String tabId, KeycloakSession session, RealmModel realm, ClientModel client,
                                                                                                    EventBuilder event, Class<CLIENT_SESSION> sessionClass) {
        CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser = CodeGenerateUtil.getParser(sessionClass);
        return getClientSession(code, tabId, session, realm, client, event, clientSessionParser);
    }


    private static <CLIENT_SESSION extends CommonClientSessionModel> CLIENT_SESSION getClientSession(String code, String tabId, KeycloakSession session, RealmModel realm, ClientModel client, EventBuilder event,
                                                                                                     CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser) {
        return clientSessionParser.parseSession(code, tabId, session, realm, client, event);
    }


    public CLIENT_SESSION getClientSession() {
        return commonLoginSession;
    }

    public boolean isValid(String requestedAction, ActionType actionType) {
        if (!isValidAction(requestedAction)) return false;
        return isActionActive(actionType);
    }

    public boolean isActionActive(ActionType actionType) {
        CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser = (CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION>) CodeGenerateUtil.getParser(commonLoginSession.getClass());
        int timestamp = clientSessionParser.getTimestamp(commonLoginSession);

        int lifespan;
        switch (actionType) {
            case CLIENT:
                lifespan = realm.getAccessCodeLifespan();
                break;
            case LOGIN:
                lifespan = realm.getAccessCodeLifespanLogin() > 0 ? realm.getAccessCodeLifespanLogin() : realm.getAccessCodeLifespanUserAction();
                break;
            case USER:
                lifespan = realm.getAccessCodeLifespanUserAction();
                break;
            default:
                throw new IllegalArgumentException();
        }

        return timestamp + lifespan > Time.currentTime();
    }

    public boolean isValidAction(String requestedAction) {
        String action = commonLoginSession.getAction();
        if (action == null) {
            return false;
        }
        if (!action.equals(requestedAction)) {
            return false;
        }
        return true;
    }

    public void removeExpiredClientSession() {
        CodeGenerateUtil.ClientSessionParser parser = CodeGenerateUtil.getParser(commonLoginSession.getClass());
        parser.removeExpiredSession(session, commonLoginSession);
    }


    public Set<RoleModel> getRequestedRoles() {
        return getRequestedRoles(commonLoginSession, realm);
    }

    public static Set<RoleModel> getRequestedRoles(CommonClientSessionModel clientSession, RealmModel realm) {
        Set<RoleModel> requestedRoles = new HashSet<>();
        for (String roleId : clientSession.getRoles()) {
            RoleModel role = realm.getRoleById(roleId);
            if (role != null) {
                requestedRoles.add(role);
            }
        }
        return requestedRoles;
    }

    public Set<ProtocolMapperModel> getRequestedProtocolMappers() {
        return getRequestedProtocolMappers(commonLoginSession);
    }

    // TODO:mposolda maybe remove this method as long as clientSession.getRoles is removed as well
    public static <CLIENT_SESSION extends CommonClientSessionModel> Set<ProtocolMapperModel> getRequestedProtocolMappers(CLIENT_SESSION clientSession) {
        Set<String> protocolMapperIds = clientSession.getProtocolMappers();
        ClientModel client = clientSession.getClient();
        CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser = (CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION>) CodeGenerateUtil.getParser(clientSession.getClass());
        String scopeParam = clientSessionParser.getClientNote(clientSession, OAuth2Constants.SCOPE);

        Set<ProtocolMapperModel> requestedProtocolMappers = new HashSet<>();

        // Add client and all default clientScopes + possibly optional client scopes (if requested by scope parameter)
        Set<ProtocolMapperContainerModel> protocolMapperContainers  = new HashSet<>(TokenManager.getRequestedClientScopes(scopeParam, client));
        protocolMapperContainers.add(client);

        if (protocolMapperIds != null) {
            for (String protocolMapperId : protocolMapperIds) {
                for (ProtocolMapperContainerModel protocolMapperContainer : protocolMapperContainers) {
                    ProtocolMapperModel protocolMapper = protocolMapperContainer.getProtocolMapperById(protocolMapperId);
                    if (protocolMapper != null) {
                        requestedProtocolMappers.add(protocolMapper);
                    }
                }
            }
        }

        return requestedProtocolMappers;
    }

    public void setAction(String action) {
        commonLoginSession.setAction(action);

        CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION> clientSessionParser = (CodeGenerateUtil.ClientSessionParser<CLIENT_SESSION>) CodeGenerateUtil.getParser(commonLoginSession.getClass());
        clientSessionParser.setTimestamp(commonLoginSession, Time.currentTime());
    }

    public String getOrGenerateCode() {
        CodeGenerateUtil.ClientSessionParser parser = CodeGenerateUtil.getParser(commonLoginSession.getClass());
        return parser.retrieveCode(session, commonLoginSession);
    }

}
