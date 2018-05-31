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

package org.keycloak.protocol.oidc;

import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.common.ClientConnection;
import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.acr.AcrProvider;
import org.keycloak.acr.DefaultAcrProvider;
import org.keycloak.acr.DefaultAcrProviderFactory;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.jose.jws.Algorithm;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.jose.jws.crypto.HashProvider;
import org.keycloak.jose.jws.crypto.RSAProvider;
import org.keycloak.migration.migrators.MigrationUtils;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserConsentModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.protocol.oidc.utils.OIDCResponseType;
import org.keycloak.protocol.oidc.utils.WebOriginsUtils;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.RefreshToken;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.AuthenticationSessionManager;
import org.keycloak.services.managers.UserSessionCrossDCManager;
import org.keycloak.services.managers.UserSessionManager;
import org.keycloak.services.util.DefaultClientSessionContext;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.util.TokenUtil;
import org.keycloak.common.util.Time;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stateless object that creates tokens and manages oauth access codes
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class TokenManager {
    private static final Logger logger = Logger.getLogger(TokenManager.class);
    private static final String JWT = "JWT";

    // Harcoded for now
    Algorithm jwsAlgorithm = Algorithm.RS256;

    public static void applyScope(RoleModel role, RoleModel scope, Set<RoleModel> visited, Set<RoleModel> requested) {
        if (visited.contains(scope)) return;
        visited.add(scope);
        if (role.hasRole(scope)) {
            requested.add(scope);
            return;
        }
        if (!scope.isComposite()) return;

        for (RoleModel contained : scope.getComposites()) {
            applyScope(role, contained, visited, requested);
        }
    }

    public static class TokenValidation {
        public final UserModel user;
        public final UserSessionModel userSession;
        public final ClientSessionContext clientSessionCtx;
        public final AccessToken newToken;

        public TokenValidation(UserModel user, UserSessionModel userSession, ClientSessionContext clientSessionCtx, AccessToken newToken) {
            this.user = user;
            this.userSession = userSession;
            this.clientSessionCtx = clientSessionCtx;
            this.newToken = newToken;
        }
    }

    public TokenValidation validateToken(KeycloakSession session, UriInfo uriInfo, ClientConnection connection, RealmModel realm,
                                         RefreshToken oldToken, HttpHeaders headers) throws OAuthErrorException {
        UserSessionModel userSession = null;
        boolean offline = TokenUtil.TOKEN_TYPE_OFFLINE.equals(oldToken.getType());

        if (offline) {

            UserSessionManager sessionManager = new UserSessionManager(session);
            userSession = sessionManager.findOfflineUserSession(realm, oldToken.getSessionState());
            if (userSession != null) {

                // Revoke timeouted offline userSession
                if (!AuthenticationManager.isOfflineSessionValid(realm, userSession)) {
                    sessionManager.revokeOfflineUserSession(userSession);
                    throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Offline session not active", "Offline session not active");
                }

            } else {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Offline user session not found", "Offline user session not found");
            }
        } else {
            // Find userSession regularly for online tokens
            userSession = session.sessions().getUserSession(realm, oldToken.getSessionState());
            if (!AuthenticationManager.isSessionValid(realm, userSession)) {
                AuthenticationManager.backchannelLogout(session, realm, userSession, uriInfo, connection, headers, true);
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Session not active", "Session not active");
            }
        }

        UserModel user = userSession.getUser();
        if (user == null) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid refresh token", "Unknown user");
        }

        if (!user.isEnabled()) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "User disabled", "User disabled");
        }

        ClientModel client = session.getContext().getClient();
        AuthenticatedClientSessionModel clientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());

        // Can theoretically happen in cross-dc environment. Try to see if userSession with our client is available in remoteCache
        if (clientSession == null) {
            userSession = new UserSessionCrossDCManager(session).getUserSessionWithClient(realm, userSession.getId(), offline, client.getId());
            if (userSession != null) {
                clientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());
            } else {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Session doesn't have required client", "Session doesn't have required client");
            }
        }

        if (!client.getClientId().equals(oldToken.getIssuedFor())) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Unmatching clients", "Unmatching clients");
        }

        if (oldToken.getIssuedAt() < client.getNotBefore()) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Stale token");
        }
        if (oldToken.getIssuedAt() < realm.getNotBefore()) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Stale token");
        }
        if (oldToken.getIssuedAt() < session.users().getNotBeforeOfUser(realm, user)) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Stale token");
        }


        // Setup clientScopes from refresh token to the context
        Set<String> clientScopeIds = oldToken.getClientScopes();

        // Case when offline token is migrated from previous version
        if (clientScopeIds == null && userSession.isOffline()) {
            logger.debugf("Migrating offline token of user '%s' for client '%s' of realm '%s'", user.getUsername(), client.getClientId(), realm.getName());
            clientScopeIds = MigrationUtils.migrateOldOfflineToken(session, realm, client, user);
        }

        ClientSessionContext clientSessionCtx = DefaultClientSessionContext.fromClientSessionAndClientScopeIds(clientSession, clientScopeIds);

        // Check user didn't revoke granted consent
        if (!verifyConsentStillAvailable(session, user, client, clientSessionCtx.getClientScopes())) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_SCOPE, "Client no longer has requested consent from user");
        }

        // recreate token.
        AccessToken newToken = createClientAccessToken(session, realm, client, user, userSession, clientSessionCtx);
        verifyAccess(oldToken, newToken);

        return new TokenValidation(user, userSession, clientSessionCtx, newToken);
    }

    public boolean isTokenValid(KeycloakSession session, RealmModel realm, AccessToken token) throws OAuthErrorException {
        if (!token.isActive()) {
            return false;
        }

        if (token.getIssuedAt() < realm.getNotBefore()) {
            return false;
        }

        ClientModel client = realm.getClientByClientId(token.getIssuedFor());
        if (client == null || !client.isEnabled() || token.getIssuedAt() < client.getNotBefore()) {
            return false;
        }

        UserSessionModel userSession = new UserSessionCrossDCManager(session).getUserSessionWithClient(realm, token.getSessionState(), false, client.getId());
        if (AuthenticationManager.isSessionValid(realm, userSession)) {
            return isUserValid(session, realm, token, userSession);
        }

        userSession = new UserSessionCrossDCManager(session).getUserSessionWithClient(realm, token.getSessionState(), true, client.getId());
        if (AuthenticationManager.isOfflineSessionValid(realm, userSession)) {
            return isUserValid(session, realm, token, userSession);
        }

        return false;
    }

    private boolean isUserValid(KeycloakSession session, RealmModel realm, AccessToken token, UserSessionModel userSession) {
        UserModel user = userSession.getUser();
        if (user == null) {
            return false;
        }
        if (!user.isEnabled()) {
            return false;
        }
        if (token.getIssuedAt() < session.users().getNotBeforeOfUser(realm, user)) {
            return false;
        }
        return true;
    }


    public RefreshResult refreshAccessToken(KeycloakSession session, UriInfo uriInfo, ClientConnection connection, RealmModel realm, ClientModel authorizedClient,
                                            String encodedRefreshToken, EventBuilder event, HttpHeaders headers) throws OAuthErrorException {
        RefreshToken refreshToken = verifyRefreshToken(session, realm, encodedRefreshToken);

        event.user(refreshToken.getSubject()).session(refreshToken.getSessionState())
                .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                .detail(Details.REFRESH_TOKEN_TYPE, refreshToken.getType());

        TokenValidation validation = validateToken(session, uriInfo, connection, realm, refreshToken, headers);
        AuthenticatedClientSessionModel clientSession = validation.clientSessionCtx.getClientSession();

        // validate authorizedClient is same as validated client
        if (!clientSession.getClient().getId().equals(authorizedClient.getId())) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid refresh token. Token client and authorized client don't match");
        }

        validateTokenReuse(session, realm, refreshToken, validation);

        int currentTime = Time.currentTime();
        clientSession.setTimestamp(currentTime);
        validation.userSession.setLastSessionRefresh(currentTime);
        
        if (refreshToken.getAuthorization() != null) {
            validation.newToken.setAuthorization(refreshToken.getAuthorization());
        }

        AccessTokenResponseBuilder responseBuilder = responseBuilder(realm, authorizedClient, event, session, validation.userSession, validation.clientSessionCtx)
                .accessToken(validation.newToken)
                .generateRefreshToken();

        String scopeParam = clientSession.getNote(OAuth2Constants.SCOPE);
        if (TokenUtil.isOIDCRequest(scopeParam)) {
            responseBuilder.generateIDToken();
        }

        AccessTokenResponse res = responseBuilder.build();

        return new RefreshResult(res, TokenUtil.TOKEN_TYPE_OFFLINE.equals(refreshToken.getType()));
    }

    private void validateTokenReuse(KeycloakSession session, RealmModel realm, RefreshToken refreshToken,
            TokenValidation validation) throws OAuthErrorException {
        if (realm.isRevokeRefreshToken()) {
            AuthenticatedClientSessionModel clientSession = validation.clientSessionCtx.getClientSession();

            int clusterStartupTime = session.getProvider(ClusterProvider.class).getClusterStartupTime();

            if (clientSession.getCurrentRefreshToken() != null &&
                    !refreshToken.getId().equals(clientSession.getCurrentRefreshToken()) &&
                    refreshToken.getIssuedAt() < clientSession.getTimestamp() &&
                    clusterStartupTime != clientSession.getTimestamp()) {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Stale token");
            }


            if (!refreshToken.getId().equals(clientSession.getCurrentRefreshToken())) {
                clientSession.setCurrentRefreshToken(refreshToken.getId());
                clientSession.setCurrentRefreshTokenUseCount(0);
            }

            int currentCount = clientSession.getCurrentRefreshTokenUseCount();
            if (currentCount > realm.getRefreshTokenMaxReuse()) {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Maximum allowed refresh token reuse exceeded",
                        "Maximum allowed refresh token reuse exceeded");
            }
            clientSession.setCurrentRefreshTokenUseCount(currentCount + 1);
        }
    }

    public RefreshToken verifyRefreshToken(KeycloakSession session, RealmModel realm, String encodedRefreshToken) throws OAuthErrorException {
        return verifyRefreshToken(session, realm, encodedRefreshToken, true);
    }

    public RefreshToken verifyRefreshToken(KeycloakSession session, RealmModel realm, String encodedRefreshToken, boolean checkExpiration) throws OAuthErrorException {
        try {
            RefreshToken refreshToken = toRefreshToken(session, realm, encodedRefreshToken);

            if (!(TokenUtil.TOKEN_TYPE_REFRESH.equals(refreshToken.getType()) || TokenUtil.TOKEN_TYPE_OFFLINE.equals(refreshToken.getType()))) {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid refresh token");
            }

            if (checkExpiration) {
                if (refreshToken.getExpiration() != 0 && refreshToken.isExpired()) {
                    throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Refresh token expired");
                }

                if (refreshToken.getIssuedAt() < realm.getNotBefore()) {
                    throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Stale refresh token");
                }
            }

            return refreshToken;
        } catch (JWSInputException e) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid refresh token", e);
        }
    }

    public RefreshToken toRefreshToken(KeycloakSession session, RealmModel realm, String encodedRefreshToken) throws JWSInputException, OAuthErrorException {
        JWSInput jws = new JWSInput(encodedRefreshToken);

        PublicKey publicKey;

        // Backwards compatibility. Old offline tokens didn't have KID in the header
        if (jws.getHeader().getKeyId() == null && TokenUtil.isOfflineToken(encodedRefreshToken)) {
            logger.debugf("KID is null in offline token. Using the realm active key to verify token signature.");
            publicKey = session.keys().getActiveRsaKey(realm).getPublicKey();
        } else {
            publicKey = session.keys().getRsaPublicKey(realm, jws.getHeader().getKeyId());
        }

        if (!RSAProvider.verify(jws, publicKey)) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid refresh token");
        }

        return jws.readJsonContent(RefreshToken.class);
    }

    public IDToken verifyIDToken(KeycloakSession session, RealmModel realm, String encodedIDToken) throws OAuthErrorException {
        try {
            JWSInput jws = new JWSInput(encodedIDToken);
            IDToken idToken;
            if (!RSAProvider.verify(jws, session.keys().getRsaPublicKey(realm, jws.getHeader().getKeyId()))) {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid IDToken");
            }
            idToken = jws.readJsonContent(IDToken.class);

            if (idToken.isExpired()) {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "IDToken expired");
            }

            if (idToken.getIssuedAt() < realm.getNotBefore()) {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Stale IDToken");
            }
            return idToken;
        } catch (JWSInputException e) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid IDToken", e);
        }
    }

    public IDToken verifyIDTokenSignature(KeycloakSession session, RealmModel realm, String encodedIDToken) throws OAuthErrorException {
        try {
            JWSInput jws = new JWSInput(encodedIDToken);
            IDToken idToken;
            if (!RSAProvider.verify(jws, session.keys().getRsaPublicKey(realm, jws.getHeader().getKeyId()))) {
                throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid IDToken");
            }
            idToken = jws.readJsonContent(IDToken.class);

            return idToken;
        } catch (JWSInputException e) {
            throw new OAuthErrorException(OAuthErrorException.INVALID_GRANT, "Invalid IDToken", e);
        }
    }

    public AccessToken createClientAccessToken(KeycloakSession session, RealmModel realm, ClientModel client, UserModel user, UserSessionModel userSession,
                                               ClientSessionContext clientSessionCtx) {
        Set<RoleModel> requestedRoles = clientSessionCtx.getRoles();
        AccessToken token = initToken(realm, client, user, userSession, clientSessionCtx, session.getContext().getUri());
        for (RoleModel role : requestedRoles) {
            addComposites(token, role);
        }
        
        String acr = buildAcrValue(session, user, clientSessionCtx);
        token.setAcr(acr);
        
        token = transformAccessToken(session, token, userSession, clientSessionCtx);
        return token;
    }

	private String buildAcrValue(KeycloakSession session, UserModel user, ClientSessionContext clientSessionCtx) {
		// Best effort for "acr" value. Use 0 if clientSession was authenticated through cookie ( SSO )
        // TODO: Add better acr support. See KEYCLOAK-3314

		String acrCompliance = session.getContext().getClient().getAttribute(AcrProvider.ACR_ATTRIBUTE_ID);
		if (Validation.isEmpty(acrCompliance)) {
			acrCompliance = DefaultAcrProviderFactory.DEFAULT_ACR_PROVIDER_ID;
		}

		AcrProvider acrProvider = session.getProvider(AcrProvider.class, acrCompliance);
        String acr_value = acrProvider.buildAcrValue(user, clientSessionCtx.getClientSession());
        
        return acr_value;
	}

    public static ClientSessionContext attachAuthenticationSession(KeycloakSession session, UserSessionModel userSession, AuthenticationSessionModel authSession) {
        ClientModel client = authSession.getClient();

        AuthenticatedClientSessionModel clientSession = userSession.getAuthenticatedClientSessionByClient(client.getId());
        if (clientSession == null) {
            clientSession = session.sessions().createClientSession(userSession.getRealm(), client, userSession);
        }

        clientSession.setRedirectUri(authSession.getRedirectUri());
        clientSession.setProtocol(authSession.getProtocol());

        Set<String> clientScopeIds = authSession.getClientScopes();

        Map<String, String> transferredNotes = authSession.getClientNotes();
        for (Map.Entry<String, String> entry : transferredNotes.entrySet()) {
            clientSession.setNote(entry.getKey(), entry.getValue());
        }

        Map<String, String> transferredUserSessionNotes = authSession.getUserSessionNotes();
        for (Map.Entry<String, String> entry : transferredUserSessionNotes.entrySet()) {
            userSession.setNote(entry.getKey(), entry.getValue());
        }

        clientSession.setTimestamp(Time.currentTime());

        // Remove authentication session now
        new AuthenticationSessionManager(session).removeAuthenticationSession(userSession.getRealm(), authSession, true);

        return DefaultClientSessionContext.fromClientSessionAndClientScopeIds(clientSession, clientScopeIds);
    }


    public static void dettachClientSession(UserSessionProvider sessions, RealmModel realm, AuthenticatedClientSessionModel clientSession) {
        UserSessionModel userSession = clientSession.getUserSession();
        if (userSession == null) {
            return;
        }

        clientSession.detachFromUserSession();

        // TODO: Might need optimization to prevent loading client sessions from cache in getAuthenticatedClientSessions()
        if (userSession.getAuthenticatedClientSessions().isEmpty()) {
            sessions.removeUserSession(realm, userSession);
        }
    }


    private static void addGroupRoles(GroupModel group, Set<RoleModel> roleMappings) {
        roleMappings.addAll(group.getRoleMappings());
        if (group.getParentId() == null) return;
        addGroupRoles(group.getParent(), roleMappings);
    }


    public static Set<RoleModel> getAccess(UserModel user, ClientModel client, Set<ClientScopeModel> clientScopes) {
        Set<RoleModel> requestedRoles = new HashSet<RoleModel>();

        Set<RoleModel> mappings = user.getRoleMappings();
        Set<RoleModel> roleMappings = new HashSet<>();
        roleMappings.addAll(mappings);
        for (GroupModel group : user.getGroups()) {
            addGroupRoles(group, roleMappings);
        }

        if (client.isFullScopeAllowed()) {
            if (logger.isTraceEnabled()) {
                logger.tracef("Using full scope for client %s", client.getClientId());
            }
            requestedRoles = roleMappings;
        } else {
            Set<RoleModel> scopeMappings = new HashSet<>();

            // 1 - Client roles of this client itself
            scopeMappings.addAll(client.getRoles());

            // 2 - Role mappings of client itself + default client scopes + optional client scopes requested by scope parameter (if applyScopeParam is true)
            for (ClientScopeModel clientScope : clientScopes) {
                if (logger.isTraceEnabled()) {
                    logger.tracef("Adding client scope role mappings of client scope '%s' to client '%s'", clientScope.getName(), client.getClientId());
                }
                scopeMappings.addAll(clientScope.getScopeMappings());
            }

            for (RoleModel role : roleMappings) {
                for (RoleModel desiredRole : scopeMappings) {
                    Set<RoleModel> visited = new HashSet<RoleModel>();
                    applyScope(role, desiredRole, visited, requestedRoles);
                }
            }
        }

        return requestedRoles;
    }


    /** Return client itself + all default client scopes of client + optional client scopes requested by scope parameter **/
    public static Set<ClientScopeModel> getRequestedClientScopes(String scopeParam, ClientModel client) {
        // Add all default client scopes automatically
        Set<ClientScopeModel> clientScopes = new HashSet<>(client.getClientScopes(true, true).values());

        // Add client itself
        clientScopes.add(client);

        if (scopeParam == null) {
            return clientScopes;
        }

        // Add optional client scopes requested by scope parameter
        String[] scopes = scopeParam.split(" ");
        Collection<String> scopeParamParts = Arrays.asList(scopes);
        Map<String, ClientScopeModel> allOptionalScopes = client.getClientScopes(false, true);
        for (String scopeParamPart : scopeParamParts) {
            ClientScopeModel scope = allOptionalScopes.get(scopeParamPart);
            if (scope != null) {
                clientScopes.add(scope);
            }
        }

        return clientScopes;
    }

    // Check if user still has granted consents to all requested client scopes
    public static boolean verifyConsentStillAvailable(KeycloakSession session, UserModel user, ClientModel client, Set<ClientScopeModel> requestedClientScopes) {
        if (!client.isConsentRequired()) {
            return true;
        }

        UserConsentModel grantedConsent = session.users().getConsentByClient(client.getRealm(), user.getId(), client.getId());

        for (ClientScopeModel requestedScope : requestedClientScopes) {
            if (!requestedScope.isDisplayOnConsentScreen()) {
                continue;
            }

            if (!grantedConsent.getGrantedClientScopes().contains(requestedScope)) {
                logger.debugf("Client '%s' no longer has requested consent from user '%s' for client scope '%s'",
                        client.getClientId(), user.getUsername(), requestedScope.getName());
                return false;
            }
        }

        return true;
    }

    // TODO: Remove this check entirely? It should be sufficient to check granted consents (client scopes) during refresh token
    private void verifyAccess(AccessToken token, AccessToken newToken) throws OAuthErrorException {
        if (token.getRealmAccess() != null) {
            if (newToken.getRealmAccess() == null) throw new OAuthErrorException(OAuthErrorException.INVALID_SCOPE, "User no long has permission for realm roles");

            for (String roleName : token.getRealmAccess().getRoles()) {
                if (!newToken.getRealmAccess().getRoles().contains(roleName)) {
                    throw new OAuthErrorException(OAuthErrorException.INVALID_SCOPE, "User no long has permission for realm role: " + roleName);
                }
            }
        }
        if (token.getResourceAccess() != null) {
            for (Map.Entry<String, AccessToken.Access> entry : token.getResourceAccess().entrySet()) {
                AccessToken.Access appAccess = newToken.getResourceAccess(entry.getKey());
                if (appAccess == null && !entry.getValue().getRoles().isEmpty()) {
                    throw new OAuthErrorException(OAuthErrorException.INVALID_SCOPE, "User or client no longer has role permissions for client key: " + entry.getKey());

                }
                for (String roleName : entry.getValue().getRoles()) {
                    if (!appAccess.getRoles().contains(roleName)) {
                        throw new OAuthErrorException(OAuthErrorException.INVALID_SCOPE, "User no long has permission for client role " + roleName);
                    }
                }
            }
        }
    }

    public AccessToken transformAccessToken(KeycloakSession session, AccessToken token,
                                            UserSessionModel userSession, ClientSessionContext clientSessionCtx) {
        Set<ProtocolMapperModel> mappings = clientSessionCtx.getProtocolMappers();
        KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();
        for (ProtocolMapperModel mapping : mappings) {

            ProtocolMapper mapper = (ProtocolMapper)sessionFactory.getProviderFactory(ProtocolMapper.class, mapping.getProtocolMapper());
            if (mapper instanceof OIDCAccessTokenMapper) {
                token = ((OIDCAccessTokenMapper) mapper).transformAccessToken(token, mapping, session, userSession, clientSessionCtx.getClientSession());
            }
        }

        return token;
    }

    public AccessToken transformUserInfoAccessToken(KeycloakSession session, AccessToken token,
                                            UserSessionModel userSession, ClientSessionContext clientSessionCtx) {
        Set<ProtocolMapperModel> mappings = clientSessionCtx.getProtocolMappers();
        KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();
        for (ProtocolMapperModel mapping : mappings) {

            ProtocolMapper mapper = (ProtocolMapper)sessionFactory.getProviderFactory(ProtocolMapper.class, mapping.getProtocolMapper());
            if (mapper instanceof UserInfoTokenMapper) {
                token = ((UserInfoTokenMapper) mapper).transformUserInfoToken(token, mapping, session, userSession, clientSessionCtx.getClientSession());
            }
        }

        return token;
    }

    public void transformIDToken(KeycloakSession session, IDToken token,
                                      UserSessionModel userSession, ClientSessionContext clientSessionCtx) {
        Set<ProtocolMapperModel> mappings = clientSessionCtx.getProtocolMappers();
        KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();
        for (ProtocolMapperModel mapping : mappings) {

            ProtocolMapper mapper = (ProtocolMapper)sessionFactory.getProviderFactory(ProtocolMapper.class, mapping.getProtocolMapper());
            if (mapper instanceof OIDCIDTokenMapper) {
                token = ((OIDCIDTokenMapper) mapper).transformIDToken(token, mapping, session, userSession, clientSessionCtx.getClientSession());
            }
        }
    }

    protected AccessToken initToken(RealmModel realm, ClientModel client, UserModel user, UserSessionModel session,
                                    ClientSessionContext clientSessionCtx, UriInfo uriInfo) {
        AccessToken token = new AccessToken();
        token.id(KeycloakModelUtils.generateId());
        token.type(TokenUtil.TOKEN_TYPE_BEARER);
        token.subject(user.getId());
        token.audience(client.getClientId());
        token.issuedNow();
        token.issuedFor(client.getClientId());

        AuthenticatedClientSessionModel clientSession = clientSessionCtx.getClientSession();
        token.issuer(clientSession.getNote(OIDCLoginProtocol.ISSUER));
        token.setNonce(clientSession.getNote(OIDCLoginProtocol.NONCE_PARAM));

//        // Best effort for "acr" value. Use 0 if clientSession was authenticated through cookie ( SSO )
//        // TODO: Add better acr support. See KEYCLOAK-3314
//        String acr = (AuthenticationManager.isSSOAuthentication(clientSession)) ? "0" : "1";
//        token.setAcr(acr);

        String authTime = session.getNote(AuthenticationManager.AUTH_TIME);
        if (authTime != null) {
            token.setAuthTime(Integer.parseInt(authTime));
        }


        token.setSessionState(session.getId());
        token.expiration(getTokenExpiration(realm, session, clientSession));

        Set<String> allowedOrigins = client.getWebOrigins();
        if (allowedOrigins != null) {
            token.setAllowedOrigins(WebOriginsUtils.resolveValidWebOrigins(uriInfo, client));
        }
        return token;
    }

    private int getTokenExpiration(RealmModel realm, UserSessionModel userSession, AuthenticatedClientSessionModel clientSession) {
        boolean implicitFlow = false;
        String responseType = clientSession.getNote(OIDCLoginProtocol.RESPONSE_TYPE_PARAM);
        if (responseType != null) {
            implicitFlow = OIDCResponseType.parse(responseType).isImplicitFlow();
        }
        int tokenLifespan = implicitFlow ? realm.getAccessTokenLifespanForImplicitFlow() : realm.getAccessTokenLifespan();

        int expiration = Time.currentTime() + tokenLifespan;

        if (!userSession.isOffline()) {
            int sessionExpires = userSession.getStarted() + realm.getSsoSessionMaxLifespan();
            expiration = expiration <= sessionExpires ? expiration : sessionExpires;
        }

        return expiration;
    }

    protected void addComposites(AccessToken token, RoleModel role) {
        AccessToken.Access access = null;
        if (role.getContainer() instanceof RealmModel) {
            access = token.getRealmAccess();
            if (token.getRealmAccess() == null) {
                access = new AccessToken.Access();
                token.setRealmAccess(access);
            } else if (token.getRealmAccess().getRoles() != null && token.getRealmAccess().isUserInRole(role.getName()))
                return;

        } else {
            ClientModel app = (ClientModel) role.getContainer();
            access = token.getResourceAccess(app.getClientId());
            if (access == null) {
                access = token.addAccess(app.getClientId());
                if (app.isSurrogateAuthRequired()) access.verifyCaller(true);
            } else if (access.isUserInRole(role.getName())) return;

        }
        access.addRole(role.getName());
        if (!role.isComposite()) return;

        for (RoleModel composite : role.getComposites()) {
            addComposites(token, composite);
        }

    }

    public String encodeToken(KeycloakSession session, RealmModel realm, Object token) {
        KeyManager.ActiveRsaKey activeRsaKey = session.keys().getActiveRsaKey(realm);
        return new JWSBuilder().type(JWT).kid(activeRsaKey.getKid()).jsonContent(token).sign(jwsAlgorithm, activeRsaKey.getPrivateKey());
    }

    public AccessTokenResponseBuilder responseBuilder(RealmModel realm, ClientModel client, EventBuilder event, KeycloakSession session,
                                                      UserSessionModel userSession, ClientSessionContext clientSessionCtx) {
        return new AccessTokenResponseBuilder(realm, client, event, session, userSession, clientSessionCtx);
    }

    public class AccessTokenResponseBuilder {
        RealmModel realm;
        ClientModel client;
        EventBuilder event;
        KeycloakSession session;
        UserSessionModel userSession;
        ClientSessionContext clientSessionCtx;

        AccessToken accessToken;
        RefreshToken refreshToken;
        IDToken idToken;

        boolean generateAccessTokenHash = false;
        String codeHash;

        String stateHash;

        public AccessTokenResponseBuilder(RealmModel realm, ClientModel client, EventBuilder event, KeycloakSession session,
                                          UserSessionModel userSession, ClientSessionContext clientSessionCtx) {
            this.realm = realm;
            this.client = client;
            this.event = event;
            this.session = session;
            this.userSession = userSession;
            this.clientSessionCtx = clientSessionCtx;
        }

        public AccessToken getAccessToken() {
            return accessToken;
        }

        public RefreshToken getRefreshToken() {
            return refreshToken;
        }

        public IDToken getIdToken() {
            return idToken;
        }

        public AccessTokenResponseBuilder accessToken(AccessToken accessToken) {
            this.accessToken = accessToken;
            return this;
        }
        public AccessTokenResponseBuilder refreshToken(RefreshToken refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public AccessTokenResponseBuilder generateAccessToken() {
            UserModel user = userSession.getUser();
            accessToken = createClientAccessToken(session, realm, client, user, userSession, clientSessionCtx);
            return this;
        }

        public AccessTokenResponseBuilder generateRefreshToken() {
            if (accessToken == null) {
                throw new IllegalStateException("accessToken not set");
            }

            ClientScopeModel offlineAccessScope = KeycloakModelUtils.getClientScopeByName(realm, OAuth2Constants.OFFLINE_ACCESS);
            boolean offlineTokenRequested = offlineAccessScope==null ? false : clientSessionCtx.getClientScopeIds().contains(offlineAccessScope.getId());
            if (offlineTokenRequested) {
                UserSessionManager sessionManager = new UserSessionManager(session);
                if (!sessionManager.isOfflineTokenAllowed(clientSessionCtx)) {
                    event.error(Errors.NOT_ALLOWED);
                    throw new ErrorResponseException("not_allowed", "Offline tokens not allowed for the user or client", Response.Status.BAD_REQUEST);
                }

                refreshToken = new RefreshToken(accessToken);
                refreshToken.type(TokenUtil.TOKEN_TYPE_OFFLINE);
                sessionManager.createOrUpdateOfflineSession(clientSessionCtx.getClientSession(), userSession);
            } else {
                refreshToken = new RefreshToken(accessToken);
                refreshToken.expiration(getRefreshExpiration());
            }
            refreshToken.id(KeycloakModelUtils.generateId());
            refreshToken.issuedNow();
            refreshToken.setClientScopes(clientSessionCtx.getClientScopeIds());
            return this;
        }

        private int getRefreshExpiration() {
            int sessionExpires = userSession.getStarted() + realm.getSsoSessionMaxLifespan();
            int expiration = Time.currentTime() + realm.getSsoSessionIdleTimeout();
            return expiration <= sessionExpires ? expiration : sessionExpires;
        }

        public AccessTokenResponseBuilder generateIDToken() {
            if (accessToken == null) {
                throw new IllegalStateException("accessToken not set");
            }
            idToken = new IDToken();
            idToken.id(KeycloakModelUtils.generateId());
            idToken.type(TokenUtil.TOKEN_TYPE_ID);
            idToken.subject(accessToken.getSubject());
            idToken.audience(client.getClientId());
            idToken.issuedNow();
            idToken.issuedFor(accessToken.getIssuedFor());
            idToken.issuer(accessToken.getIssuer());
            idToken.setNonce(accessToken.getNonce());
            idToken.setAuthTime(accessToken.getAuthTime());
            idToken.setSessionState(accessToken.getSessionState());
            idToken.expiration(accessToken.getExpiration());
            idToken.setAcr(accessToken.getAcr());
            transformIDToken(session, idToken, userSession, clientSessionCtx);
            return this;
        }

        public AccessTokenResponseBuilder generateAccessTokenHash() {
            generateAccessTokenHash = true;
            return this;
        }

        public AccessTokenResponseBuilder generateCodeHash(String code) {
            codeHash = HashProvider.oidcHash(jwsAlgorithm, code);
            return this;
        }

        // Financial API - Part 2: Read and Write API Security Profile
        // http://openid.net/specs/openid-financial-api-part-2.html#authorization-server
        public AccessTokenResponseBuilder generateStateHash(String state) {
            stateHash = HashProvider.oidcHash(jwsAlgorithm, state);
            return this;
        }

        public AccessTokenResponse build() {
            KeyManager.ActiveRsaKey activeRsaKey = session.keys().getActiveRsaKey(realm);

            if (accessToken != null) {
                event.detail(Details.TOKEN_ID, accessToken.getId());
            }

            if (refreshToken != null) {
                if (event.getEvent().getDetails().containsKey(Details.REFRESH_TOKEN_ID)) {
                    event.detail(Details.UPDATED_REFRESH_TOKEN_ID, refreshToken.getId());
                } else {
                    event.detail(Details.REFRESH_TOKEN_ID, refreshToken.getId());
                }
                event.detail(Details.REFRESH_TOKEN_TYPE, refreshToken.getType());
            }

            AccessTokenResponse res = new AccessTokenResponse();
            if (accessToken != null) {
                String encodedToken = new JWSBuilder().type(JWT).kid(activeRsaKey.getKid()).jsonContent(accessToken).sign(jwsAlgorithm, activeRsaKey.getPrivateKey());
                res.setToken(encodedToken);
                res.setTokenType("bearer");
                res.setSessionState(accessToken.getSessionState());
                if (accessToken.getExpiration() != 0) {
                    res.setExpiresIn(accessToken.getExpiration() - Time.currentTime());
                }
            }

            if (generateAccessTokenHash) {
                String atHash = HashProvider.oidcHash(jwsAlgorithm, res.getToken());
                idToken.setAccessTokenHash(atHash);
            }
            if (codeHash != null) {
                idToken.setCodeHash(codeHash);
            }
            // Financial API - Part 2: Read and Write API Security Profile
            // http://openid.net/specs/openid-financial-api-part-2.html#authorization-server
            if (stateHash != null) {
                idToken.setStateHash(stateHash);
            }
            if (idToken != null) {
                String encodedToken = new JWSBuilder().type(JWT).kid(activeRsaKey.getKid()).jsonContent(idToken).sign(jwsAlgorithm, activeRsaKey.getPrivateKey());
                res.setIdToken(encodedToken);
            }
            if (refreshToken != null) {
                String encodedToken = new JWSBuilder().type(JWT).kid(activeRsaKey.getKid()).jsonContent(refreshToken).sign(jwsAlgorithm, activeRsaKey.getPrivateKey());
                res.setRefreshToken(encodedToken);
                if (refreshToken.getExpiration() != 0) {
                    res.setRefreshExpiresIn(refreshToken.getExpiration() - Time.currentTime());
                }
            }

            int notBefore = realm.getNotBefore();
            if (client.getNotBefore() > notBefore) notBefore = client.getNotBefore();
            int userNotBefore = session.users().getNotBeforeOfUser(realm, userSession.getUser());
            if (userNotBefore > notBefore) notBefore = userNotBefore;
            res.setNotBeforePolicy(notBefore);

            // OIDC Financial API Read Only Profile : scope MUST be returned in the response from Token Endpoint
            String scopeParam = getScopeParameterValue();
            res.setScope(scopeParam);

            return res;
        }


        private String getScopeParameterValue() {
            StringBuilder builder = new StringBuilder();

            // Add both default and optional scopes to scope parameter. Don't add client itself
            boolean first = true;
            for (ClientScopeModel clientScope : clientSessionCtx.getClientScopes()) {
                if (clientScope instanceof ClientModel) {
                    continue;
                }

                if (first) {
                    first = false;
                } else {
                    builder.append(" ");
                }
                builder.append(clientScope.getName());
            }

            String scopeParam = builder.toString();

            // See if "openid" scope is requested
            if (idToken != null) {
                scopeParam = TokenUtil.attachOIDCScope(scopeParam);
            }

            return scopeParam;
        }

    }

    public class RefreshResult {

        private final AccessTokenResponse response;
        private final boolean offlineToken;

        private RefreshResult(AccessTokenResponse response, boolean offlineToken) {
            this.response = response;
            this.offlineToken = offlineToken;
        }

        public AccessTokenResponse getResponse() {
            return response;
        }

        public boolean isOfflineToken() {
            return offlineToken;
        }
    }

}
