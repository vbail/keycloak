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
import org.keycloak.OAuth2Constants;
import org.keycloak.common.constants.KerberosConstants;
import org.keycloak.common.util.UriUtils;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.protocol.AbstractLoginProtocolFactory;
import org.keycloak.protocol.LoginProtocol;
import org.keycloak.protocol.oidc.mappers.AddressMapper;
import org.keycloak.protocol.oidc.mappers.FullNameMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.UserAttributeMapper;
import org.keycloak.protocol.oidc.mappers.UserPropertyMapper;
import org.keycloak.protocol.oidc.mappers.UserSessionNoteMapper;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.services.ServicesLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class OIDCLoginProtocolFactory extends AbstractLoginProtocolFactory {
    private static final Logger logger = Logger.getLogger(OIDCLoginProtocolFactory.class);

    public static final String USERNAME = "username";
    public static final String EMAIL = "email";
    public static final String EMAIL_VERIFIED = "email verified";
    public static final String GIVEN_NAME = "given name";
    public static final String FAMILY_NAME = "family name";
    public static final String MIDDLE_NAME = "middle name";
    public static final String NICKNAME = "nickname";
    public static final String PROFILE_CLAIM = "profile";
    public static final String PICTURE = "picture";
    public static final String WEBSITE = "website";
    public static final String GENDER = "gender";
    public static final String BIRTHDATE = "birthdate";
    public static final String ZONEINFO = "zoneinfo";
    public static final String UPDATED_AT = "updated at";
    public static final String FULL_NAME = "full name";
    public static final String LOCALE = "locale";
    public static final String ADDRESS = "address";
    public static final String PHONE_NUMBER = "phone number";
    public static final String PHONE_NUMBER_VERIFIED = "phone number verified";

    public static final String PROFILE_SCOPE_CONSENT_TEXT = "${profileScopeConsentText}";
    public static final String EMAIL_SCOPE_CONSENT_TEXT = "${emailScopeConsentText}";
    public static final String ADDRESS_SCOPE_CONSENT_TEXT = "${addressScopeConsentText}";
    public static final String PHONE_SCOPE_CONSENT_TEXT = "${phoneScopeConsentText}";
    public static final String OFFLINE_ACCESS_SCOPE_CONSENT_TEXT = "${offlineAccessScopeConsentText}";

    public static final String USERNAME_CONSENT_TEXT = "${username}";
    public static final String EMAIL_CONSENT_TEXT = "${email}";
    public static final String EMAIL_VERIFIED_CONSENT_TEXT = "${emailVerified}";
    public static final String GIVEN_NAME_CONSENT_TEXT = "${givenName}";
    public static final String FAMILY_NAME_CONSENT_TEXT = "${familyName}";
    public static final String FULL_NAME_CONSENT_TEXT = "${fullName}";
    public static final String LOCALE_CONSENT_TEXT = "${locale}";


    @Override
    public LoginProtocol create(KeycloakSession session) {
        return new OIDCLoginProtocol().setSession(session);
    }

    @Override
    public Map<String, ProtocolMapperModel> getBuiltinMappers() {
        return builtins;
    }

    @Override
    public List<ProtocolMapperModel> getDefaultBuiltinMappers() {
        return Collections.emptyList();
    }

    static Map<String, ProtocolMapperModel> builtins = new HashMap<>();

    static {
                ProtocolMapperModel model;
        model = UserPropertyMapper.createClaimMapper(USERNAME,
                "username",
                "preferred_username", "String",
                true, USERNAME_CONSENT_TEXT,
                true, true);
        builtins.put(USERNAME, model);

        model = UserPropertyMapper.createClaimMapper(EMAIL,
                "email",
                "email", "String",
                true, EMAIL_CONSENT_TEXT,
                true, true);
        builtins.put(EMAIL, model);

        model = UserPropertyMapper.createClaimMapper(GIVEN_NAME,
                "firstName",
                "given_name", "String",
                true, GIVEN_NAME_CONSENT_TEXT,
                true, true);
        builtins.put(GIVEN_NAME, model);

        model = UserPropertyMapper.createClaimMapper(FAMILY_NAME,
                "lastName",
                "family_name", "String",
                true, FAMILY_NAME_CONSENT_TEXT,
                true, true);
        builtins.put(FAMILY_NAME, model);

        createUserAttributeMapper(MIDDLE_NAME, "middleName", IDToken.MIDDLE_NAME, "String");
        createUserAttributeMapper(NICKNAME, "nickname", IDToken.NICKNAME, "String");
        createUserAttributeMapper(PROFILE_CLAIM, "profile", IDToken.PROFILE, "String");
        createUserAttributeMapper(PICTURE, "picture", IDToken.PICTURE, "String");
        createUserAttributeMapper(WEBSITE, "website", IDToken.WEBSITE, "String");
        createUserAttributeMapper(GENDER, "gender", IDToken.GENDER, "String");
        createUserAttributeMapper(BIRTHDATE, "birthdate", IDToken.BIRTHDATE, "String");
        createUserAttributeMapper(ZONEINFO, "zoneinfo", IDToken.ZONEINFO, "String");
        createUserAttributeMapper(UPDATED_AT, "updatedAt", IDToken.UPDATED_AT, "String");
        createUserAttributeMapper(LOCALE, "locale", IDToken.LOCALE, "String");

        createUserAttributeMapper(PHONE_NUMBER, "phoneNumber", IDToken.PHONE_NUMBER, "String");
        createUserAttributeMapper(PHONE_NUMBER_VERIFIED, "phoneNumberVerified", IDToken.PHONE_NUMBER_VERIFIED, "boolean");

        model = UserPropertyMapper.createClaimMapper(EMAIL_VERIFIED,
                "emailVerified",
                "email_verified", "boolean",
                false, EMAIL_VERIFIED_CONSENT_TEXT,
                true, true);
        builtins.put(EMAIL_VERIFIED, model);

        ProtocolMapperModel fullName = new ProtocolMapperModel();
        fullName.setName(FULL_NAME);
        fullName.setProtocolMapper(FullNameMapper.PROVIDER_ID);
        fullName.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        fullName.setConsentRequired(true);
        fullName.setConsentText(FULL_NAME_CONSENT_TEXT);
        Map<String, String> config = new HashMap<String, String>();
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN, "true");
        fullName.setConfig(config);
        builtins.put(FULL_NAME, fullName);

        ProtocolMapperModel address = AddressMapper.createAddressMapper();
        builtins.put(ADDRESS, address);

        model = UserSessionNoteMapper.createClaimMapper(KerberosConstants.GSS_DELEGATION_CREDENTIAL_DISPLAY_NAME,
                KerberosConstants.GSS_DELEGATION_CREDENTIAL,
                KerberosConstants.GSS_DELEGATION_CREDENTIAL, "String",
                true, "${gssDelegationCredential}",
                true, false);
        builtins.put(KerberosConstants.GSS_DELEGATION_CREDENTIAL, model);
    }

    private static void createUserAttributeMapper(String name, String attrName, String claimName, String type) {
        ProtocolMapperModel model = UserAttributeMapper.createClaimMapper(name,
                attrName,
                claimName, type,
                true, attrName,
                true, true, false);
        builtins.put(name, model);
    }

    @Override
    protected void createDefaultClientScopes(RealmModel newRealm) {
        //name, family_name, given_name, middle_name, nickname, preferred_username, profile, picture, website, gender, birthdate, zoneinfo, locale, and updated_at.
        ClientScopeModel profileScope = newRealm.addClientScope(OAuth2Constants.SCOPE_PROFILE);
        profileScope.setDescription("OpenID Connect built-in scope: profile");
        profileScope.setDisplayOnConsentScreen(true);
        profileScope.setConsentScreenText(PROFILE_SCOPE_CONSENT_TEXT);
        profileScope.setProtocol(getId());
        profileScope.addProtocolMapper(builtins.get(FULL_NAME));
        profileScope.addProtocolMapper(builtins.get(FAMILY_NAME));
        profileScope.addProtocolMapper(builtins.get(GIVEN_NAME));
        profileScope.addProtocolMapper(builtins.get(MIDDLE_NAME));
        profileScope.addProtocolMapper(builtins.get(NICKNAME));
        profileScope.addProtocolMapper(builtins.get(USERNAME));
        profileScope.addProtocolMapper(builtins.get(PROFILE_CLAIM));
        profileScope.addProtocolMapper(builtins.get(PICTURE));
        profileScope.addProtocolMapper(builtins.get(WEBSITE));
        profileScope.addProtocolMapper(builtins.get(GENDER));
        profileScope.addProtocolMapper(builtins.get(BIRTHDATE));
        profileScope.addProtocolMapper(builtins.get(ZONEINFO));
        profileScope.addProtocolMapper(builtins.get(LOCALE));
        profileScope.addProtocolMapper(builtins.get(UPDATED_AT));

        ClientScopeModel emailScope = newRealm.addClientScope(OAuth2Constants.SCOPE_EMAIL);
        emailScope.setDescription("OpenID Connect built-in scope: email");
        emailScope.setDisplayOnConsentScreen(true);
        emailScope.setConsentScreenText(EMAIL_SCOPE_CONSENT_TEXT);
        emailScope.setProtocol(getId());
        emailScope.addProtocolMapper(builtins.get(EMAIL));
        emailScope.addProtocolMapper(builtins.get(EMAIL_VERIFIED));

        ClientScopeModel addressScope = newRealm.addClientScope(OAuth2Constants.SCOPE_ADDRESS);
        addressScope.setDescription("OpenID Connect built-in scope: address");
        addressScope.setDisplayOnConsentScreen(true);
        addressScope.setConsentScreenText(ADDRESS_SCOPE_CONSENT_TEXT);
        addressScope.setProtocol(getId());
        addressScope.addProtocolMapper(builtins.get(ADDRESS));

        ClientScopeModel phoneScope = newRealm.addClientScope(OAuth2Constants.SCOPE_PHONE);
        phoneScope.setDescription("OpenID Connect built-in scope: phone");
        phoneScope.setDisplayOnConsentScreen(true);
        phoneScope.setConsentScreenText(PHONE_SCOPE_CONSENT_TEXT);
        phoneScope.setProtocol(getId());
        phoneScope.addProtocolMapper(builtins.get(PHONE_NUMBER));
        phoneScope.addProtocolMapper(builtins.get(PHONE_NUMBER_VERIFIED));

        // 'profile' and 'email' will be default scopes for now. 'address' and 'phone' will be optional scopes
        newRealm.addDefaultClientScope(profileScope, true);
        newRealm.addDefaultClientScope(emailScope, true);
        newRealm.addDefaultClientScope(addressScope, false);
        newRealm.addDefaultClientScope(phoneScope, false);

        RoleModel offlineRole = newRealm.getRole(OAuth2Constants.OFFLINE_ACCESS);
        if (offlineRole != null) {
            ClientScopeModel offlineAccessScope = newRealm.addClientScope(OAuth2Constants.OFFLINE_ACCESS);
            offlineAccessScope.setDescription("OpenID Connect built-in scope: offline_access");
            offlineAccessScope.setDisplayOnConsentScreen(true);
            offlineAccessScope.setConsentScreenText(OFFLINE_ACCESS_SCOPE_CONSENT_TEXT);
            offlineAccessScope.setProtocol(getId());
            offlineAccessScope.addScopeMapping(offlineRole);

            // Optional scope. Needs to be requested by scope parameter
            newRealm.addDefaultClientScope(offlineAccessScope, false);
        }
    }

    @Override
    protected void addDefaults(ClientModel client) {
    }

    @Override
    public Object createProtocolEndpoint(RealmModel realm, EventBuilder event) {
        return new OIDCLoginProtocolService(realm, event);
    }

    @Override
    public String getId() {
        return OIDCLoginProtocol.LOGIN_PROTOCOL;
    }

    @Override
    public void setupClientDefaults(ClientRepresentation rep, ClientModel newClient) {
        if (rep.getRootUrl() != null && (rep.getRedirectUris() == null || rep.getRedirectUris().isEmpty())) {
            String root = rep.getRootUrl();
            if (root.endsWith("/")) root = root + "*";
            else root = root + "/*";
            newClient.addRedirectUri(root);

            Set<String> origins = new HashSet<String>();
            String origin = UriUtils.getOrigin(root);
            logger.debugv("adding default client origin: {0}" , origin);
            origins.add(origin);
            newClient.setWebOrigins(origins);
        }
        if (rep.isBearerOnly() == null
                && rep.isPublicClient() == null) {
            newClient.setPublicClient(true);
        }
        if (rep.isBearerOnly() == null) newClient.setBearerOnly(false);
        if (rep.getAdminUrl() == null && rep.getRootUrl() != null) {
            newClient.setManagementUrl(rep.getRootUrl());
        }


        // Backwards compatibility only
        if (rep.isDirectGrantsOnly() != null) {
            ServicesLogger.LOGGER.usingDeprecatedDirectGrantsOnly();
            newClient.setStandardFlowEnabled(!rep.isDirectGrantsOnly());
            newClient.setDirectAccessGrantsEnabled(rep.isDirectGrantsOnly());
        } else {
            if (rep.isStandardFlowEnabled() == null) newClient.setStandardFlowEnabled(true);
            if (rep.isDirectAccessGrantsEnabled() == null) newClient.setDirectAccessGrantsEnabled(true);

        }

        if (rep.isImplicitFlowEnabled() == null) newClient.setImplicitFlowEnabled(false);
        if (rep.isPublicClient() == null) newClient.setPublicClient(true);
        if (rep.isFrontchannelLogout() == null) newClient.setFrontchannelLogout(false);
    }

}
