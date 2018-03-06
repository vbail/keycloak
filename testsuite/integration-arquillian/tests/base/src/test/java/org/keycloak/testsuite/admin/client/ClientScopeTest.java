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

package org.keycloak.testsuite.admin.client;

import org.junit.Assert;
import org.junit.Test;
import org.keycloak.admin.client.resource.ClientScopesResource;
import org.keycloak.admin.client.resource.ProtocolMappersResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.AccountRoles;
import org.keycloak.models.Constants;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.saml.SamlProtocol;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.ErrorRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.testsuite.util.AdminEventPaths;
import org.keycloak.testsuite.util.Matchers;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class ClientScopeTest extends AbstractClientTest {

    @Test
    public void testAddDuplicatedClientScope() {
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("scope1");
        String templateId = createClientScope(templateRep);

        templateRep = new ClientScopeRepresentation();
        templateRep.setName("scope1");
        Response response = clientScopes().create(templateRep);
        assertEquals(409, response.getStatus());

        ErrorRepresentation error = response.readEntity(ErrorRepresentation.class);
        Assert.assertEquals("Client Scope scope1 already exists", error.getErrorMessage());

        // Cleanup
        removeClientScope(templateId);
    }


    @Test (expected = NotFoundException.class)
    public void testGetUnknownTemplate() {
        clientScopes().get("unknown-id").toRepresentation();
    }


    private List<String> getClientScopeNames(List<ClientScopeRepresentation> scopes) {
        return scopes.stream().map((ClientScopeRepresentation clientScope) -> {

            return clientScope.getName();

        }).collect(Collectors.toList());
    }

    @Test
    public void testRemoveClientScope() {
        // Create scope1
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("scope1");
        String template1Id = createClientScope(templateRep);

        List<ClientScopeRepresentation> clientScopes = clientScopes().findAll();
        Assert.assertTrue(getClientScopeNames(clientScopes).contains("scope1"));

        // Create scope2
        templateRep = new ClientScopeRepresentation();
        templateRep.setName("scope2");
        String template2Id = createClientScope(templateRep);

        clientScopes = clientScopes().findAll();
        Assert.assertTrue(getClientScopeNames(clientScopes).contains("scope2"));

        // Remove scope1
        removeClientScope(template1Id);

        clientScopes = clientScopes().findAll();
        Assert.assertFalse(getClientScopeNames(clientScopes).contains("scope1"));
        Assert.assertTrue(getClientScopeNames(clientScopes).contains("scope2"));


        // Remove scope2
        removeClientScope(template2Id);

        clientScopes = clientScopes().findAll();
        Assert.assertFalse(getClientScopeNames(clientScopes).contains("scope1"));
        Assert.assertFalse(getClientScopeNames(clientScopes).contains("scope2"));
    }


    @Test
    public void testUpdateTemplate() {
        // Test creating
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("scope1");
        templateRep.setDescription("scope1-desc");
        templateRep.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        String template1Id = createClientScope(templateRep);

        // Assert created attributes
        templateRep = clientScopes().get(template1Id).toRepresentation();
        Assert.assertEquals("scope1", templateRep.getName());
        Assert.assertEquals("scope1-desc", templateRep.getDescription());
        Assert.assertEquals(OIDCLoginProtocol.LOGIN_PROTOCOL, templateRep.getProtocol());


        // Test updating
        templateRep.setName("scope1-updated");
        templateRep.setDescription("scope1-desc-updated");
        templateRep.setProtocol(SamlProtocol.LOGIN_PROTOCOL);

        clientScopes().get(template1Id).update(templateRep);

        assertAdminEvents.assertEvent(getRealmId(), OperationType.UPDATE, AdminEventPaths.clientScopeResourcePath(template1Id), templateRep, ResourceType.CLIENT_SCOPE);

        // Assert updated attributes
        templateRep = clientScopes().get(template1Id).toRepresentation();
        Assert.assertEquals("scope1-updated", templateRep.getName());
        Assert.assertEquals("scope1-desc-updated", templateRep.getDescription());
        Assert.assertEquals(SamlProtocol.LOGIN_PROTOCOL, templateRep.getProtocol());

        // Remove template1
        clientScopes().get(template1Id).remove();
    }


    @Test
    public void testRenameTemplate() {
        // Create two templates
        ClientScopeRepresentation template1Rep = new ClientScopeRepresentation();
        template1Rep.setName("template1");
        template1Rep.setDescription("template1-desc");
        template1Rep.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        createClientScope(template1Rep);

        ClientScopeRepresentation template2Rep = new ClientScopeRepresentation();
        template2Rep.setName("template2");
        template2Rep.setDescription("template2-desc");
        template2Rep.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        String template2Id = createClientScope(template2Rep);

        // Test updating
        template2Rep.setName("template1");

        try {
            clientScopes().get(template2Id).update(template2Rep);
        } catch (ClientErrorException ex) {
            assertThat(ex.getResponse(), Matchers.statusCodeIs(Status.CONFLICT));
        }
    }


    @Test
    public void testScopes() {
        // Add realm role1
        RoleRepresentation roleRep1 = createRealmRole("role1");

        // Add realm role2
        RoleRepresentation roleRep2 = createRealmRole("role2");

        // Add role2 as composite to role1
        testRealmResource().roles().get("role1").addComposites(Collections.singletonList(roleRep2));
        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.roleResourceCompositesPath("role1"), Collections.singletonList(roleRep2), ResourceType.REALM_ROLE);

        // create client template
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("bar-template");
        String templateId = createClientScope(templateRep);

        // update with some scopes
        String accountMgmtId = testRealmResource().clients().findByClientId(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID).get(0).getId();
        RoleRepresentation viewAccountRoleRep = testRealmResource().clients().get(accountMgmtId).roles().get(AccountRoles.VIEW_PROFILE).toRepresentation();
        RoleMappingResource scopesResource = clientScopes().get(templateId).getScopeMappings();

        scopesResource.realmLevel().add(Collections.singletonList(roleRep1));
        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.clientScopeRoleMappingsRealmLevelPath(templateId), Collections.singletonList(roleRep1), ResourceType.REALM_SCOPE_MAPPING);

        scopesResource.clientLevel(accountMgmtId).add(Collections.singletonList(viewAccountRoleRep));
        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.clientScopeRoleMappingsClientLevelPath(templateId, accountMgmtId), Collections.singletonList(viewAccountRoleRep), ResourceType.CLIENT_SCOPE_MAPPING);

        // test that scopes are available (also through composite role)
        List<RoleRepresentation> allRealm = scopesResource.realmLevel().listAll();
        List<RoleRepresentation> availableRealm = scopesResource.realmLevel().listAvailable();
        List<RoleRepresentation> effectiveRealm = scopesResource.realmLevel().listEffective();
        List<RoleRepresentation> accountRoles = scopesResource.clientLevel(accountMgmtId).listAll();

        assertRolesPresent(allRealm, "role1");
        assertRolesNotPresent(availableRealm, "role1", "role2");
        assertRolesPresent(effectiveRealm, "role1", "role2");
        assertRolesPresent(accountRoles, AccountRoles.VIEW_PROFILE);
        MappingsRepresentation mappingsRep = clientScopes().get(templateId).getScopeMappings().getAll();
        assertRolesPresent(mappingsRep.getRealmMappings(), "role1");
        assertRolesPresent(mappingsRep.getClientMappings().get(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID).getMappings(), AccountRoles.VIEW_PROFILE);


        // remove scopes
        scopesResource.realmLevel().remove(Collections.singletonList(roleRep1));
        assertAdminEvents.assertEvent(getRealmId(), OperationType.DELETE, AdminEventPaths.clientScopeRoleMappingsRealmLevelPath(templateId), Collections.singletonList(roleRep1), ResourceType.REALM_SCOPE_MAPPING);

        scopesResource.clientLevel(accountMgmtId).remove(Collections.singletonList(viewAccountRoleRep));
        assertAdminEvents.assertEvent(getRealmId(), OperationType.DELETE, AdminEventPaths.clientScopeRoleMappingsClientLevelPath(templateId, accountMgmtId), Collections.singletonList(viewAccountRoleRep), ResourceType.CLIENT_SCOPE_MAPPING);

        // assert scopes are removed
        allRealm = scopesResource.realmLevel().listAll();
        availableRealm = scopesResource.realmLevel().listAvailable();
        effectiveRealm = scopesResource.realmLevel().listEffective();
        accountRoles = scopesResource.clientLevel(accountMgmtId).listAll();
        assertRolesNotPresent(allRealm, "role1");
        assertRolesPresent(availableRealm, "role1", "role2");
        assertRolesNotPresent(effectiveRealm, "role1", "role2");
        assertRolesNotPresent(accountRoles, AccountRoles.VIEW_PROFILE);

        // remove template
        removeClientScope(templateId);
    }

    private void assertRolesPresent(List<RoleRepresentation> roles, String... expectedRoleNames) {
        List<String> expectedList = Arrays.asList(expectedRoleNames);

        Set<String> presentRoles = new HashSet<>();
        for (RoleRepresentation roleRep : roles) {
            presentRoles.add(roleRep.getName());
        }

        for (String expected : expectedList) {
            if (!presentRoles.contains(expected)) {
                Assert.fail("Expected role " + expected + " not available");
            }
        }
    }

    private void assertRolesNotPresent(List<RoleRepresentation> roles, String... notExpectedRoleNames) {
        List<String> notExpectedList = Arrays.asList(notExpectedRoleNames);
        for (RoleRepresentation roleRep : roles) {
            if (notExpectedList.contains(roleRep.getName())) {
                Assert.fail("Role " + roleRep.getName() + " wasn't expected to be available");
            }
        }
    }


    // KEYCLOAK-2809
    @Test
    public void testRemoveScopedRole() {
        // Add realm role
        RoleRepresentation roleRep = createRealmRole("foo-role");

        // Add client template
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("bar-template");
        String templateId = createClientScope(templateRep);

        // Add realm role to scopes of clientTemplate
        clientScopes().get(templateId).getScopeMappings().realmLevel().add(Collections.singletonList(roleRep));
        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.clientScopeRoleMappingsRealmLevelPath(templateId), Collections.singletonList(roleRep), ResourceType.REALM_SCOPE_MAPPING);

        List<RoleRepresentation> roleReps = clientScopes().get(templateId).getScopeMappings().realmLevel().listAll();
        Assert.assertEquals(1, roleReps.size());
        Assert.assertEquals("foo-role", roleReps.get(0).getName());

        // Remove realm role
        testRealmResource().roles().deleteRole("foo-role");
        assertAdminEvents.assertEvent(getRealmId(), OperationType.DELETE, AdminEventPaths.roleResourcePath("foo-role"), ResourceType.REALM_ROLE);

        // Get scope mappings
        roleReps = clientScopes().get(templateId).getScopeMappings().realmLevel().listAll();
        Assert.assertEquals(0, roleReps.size());

        // Cleanup
        removeClientScope(templateId);
    }

    private RoleRepresentation createRealmRole(String roleName) {
        RoleRepresentation roleRep = new RoleRepresentation();
        roleRep.setName(roleName);
        testRealmResource().roles().create(roleRep);

        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.roleResourcePath(roleName), roleRep, ResourceType.REALM_ROLE);

        return testRealmResource().roles().get(roleName).toRepresentation();
    }

    // KEYCLOAK-2844
    @Test
    public void testRemoveTemplateInUse() {
        // Add client template
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("foo-template");
        String templateId = createClientScope(templateRep);

        // Add client with the clientTemplate
        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId("bar-client");
        clientRep.setName("bar-client");
        clientRep.setRootUrl("foo");
        clientRep.setProtocol("openid-connect");
        clientRep.setDefaultClientScopes(Collections.singletonList("foo-template"));
        String clientDbId = createClient(clientRep);

        // Can't remove clientTemplate
        try {
            clientScopes().get(templateId).remove();
            Assert.fail("Not expected to successfully remove clientScope in use");
        } catch (BadRequestException bre) {
            ErrorRepresentation error = bre.getResponse().readEntity(ErrorRepresentation.class);
            Assert.assertEquals("Cannot remove client scope, it is currently in use", error.getErrorMessage());
            assertAdminEvents.assertEmpty();
        }

        // Remove client
        removeClient(clientDbId);

        // Can remove clientTemplate now
        removeClientScope(templateId);
    }


    @Test
    public void testRealmDefaultClientScopes() {
        // Create 2 client scopes
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("scope-def");
        templateRep.setProtocol("openid-connect");
        String scopeDefId = createClientScope(templateRep);
        getCleanup().addClientScopeId(scopeDefId);

        templateRep = new ClientScopeRepresentation();
        templateRep.setName("scope-opt");
        templateRep.setProtocol("openid-connect");
        String scopeOptId = createClientScope(templateRep);
        getCleanup().addClientScopeId(scopeOptId);

        // Add scope-def as default and scope-opt as optional client scope
        testRealmResource().addDefaultDefaultClientScope(scopeDefId);
        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.defaultDefaultClientScopePath(scopeDefId), ResourceType.CLIENT_SCOPE);
        testRealmResource().addDefaultOptionalClientScope(scopeOptId);
        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.defaultOptionalClientScopePath(scopeOptId), ResourceType.CLIENT_SCOPE);

        // Ensure defaults and optional scopes are here
        List<String> realmDefaultScopes = getClientScopeNames(testRealmResource().getDefaultDefaultClientScopes());
        List<String> realmOptionalScopes = getClientScopeNames(testRealmResource().getDefaultOptionalClientScopes());
        Assert.assertTrue(realmDefaultScopes.contains("scope-def"));
        Assert.assertFalse(realmOptionalScopes .contains("scope-def"));
        Assert.assertFalse(realmDefaultScopes.contains("scope-opt"));
        Assert.assertTrue(realmOptionalScopes .contains("scope-opt"));

        // create client. Ensure that it has scope-def and scope-opt scopes assigned
        ClientRepresentation clientRep = new ClientRepresentation();
        clientRep.setClientId("bar-client");
        clientRep.setProtocol("openid-connect");
        String clientUuid = createClient(clientRep);
        getCleanup().addClientUuid(clientUuid);

        List<String> clientDefaultScopes = getClientScopeNames(testRealmResource().clients().get(clientUuid).getDefaultClientScopes());
        List<String> clientOptionalScopes = getClientScopeNames(testRealmResource().clients().get(clientUuid).getOptionalClientScopes());
        Assert.assertTrue(clientDefaultScopes.contains("scope-def"));
        Assert.assertFalse(clientOptionalScopes .contains("scope-def"));
        Assert.assertFalse(clientDefaultScopes.contains("scope-opt"));
        Assert.assertTrue(clientOptionalScopes .contains("scope-opt"));

        // Unassign scope-def and scope-opt from realm
        testRealmResource().removeDefaultDefaultClientScope(scopeDefId);
        assertAdminEvents.assertEvent(getRealmId(), OperationType.DELETE, AdminEventPaths.defaultDefaultClientScopePath(scopeDefId), ResourceType.CLIENT_SCOPE);
        testRealmResource().removeDefaultOptionalClientScope(scopeOptId);
        assertAdminEvents.assertEvent(getRealmId(), OperationType.DELETE, AdminEventPaths.defaultOptionalClientScopePath(scopeOptId), ResourceType.CLIENT_SCOPE);

        realmDefaultScopes = getClientScopeNames(testRealmResource().getDefaultDefaultClientScopes());
        realmOptionalScopes = getClientScopeNames(testRealmResource().getDefaultOptionalClientScopes());
        Assert.assertFalse(realmDefaultScopes.contains("scope-def"));
        Assert.assertFalse(realmOptionalScopes .contains("scope-def"));
        Assert.assertFalse(realmDefaultScopes.contains("scope-opt"));
        Assert.assertFalse(realmOptionalScopes .contains("scope-opt"));

        // Create another client. Check it doesn't have scope-def and scope-opt scopes assigned
        clientRep = new ClientRepresentation();
        clientRep.setClientId("bar-client-2");
        clientRep.setProtocol("openid-connect");
        clientUuid = createClient(clientRep);
        getCleanup().addClientUuid(clientUuid);

        clientDefaultScopes = getClientScopeNames(testRealmResource().clients().get(clientUuid).getDefaultClientScopes());
        clientOptionalScopes = getClientScopeNames(testRealmResource().clients().get(clientUuid).getOptionalClientScopes());
        Assert.assertFalse(clientDefaultScopes.contains("scope-def"));
        Assert.assertFalse(clientOptionalScopes .contains("scope-def"));
        Assert.assertFalse(clientDefaultScopes.contains("scope-opt"));
        Assert.assertFalse(clientOptionalScopes .contains("scope-opt"));
    }


    // KEYCLOAK-5863
    @Test
    public void testUpdateProtocolMappers() {
        ClientScopeRepresentation templateRep = new ClientScopeRepresentation();
        templateRep.setName("testUpdateProtocolMappers");
        templateRep.setProtocol("openid-connect");


        String templateId = createClientScope(templateRep);

        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("test");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-usermodel-attribute-mapper");

        Map<String, String> m = new HashMap<>();
        m.put("user.attribute", "test");
        m.put("claim.name", "");
        m.put("jsonType.label", "");

        mapper.setConfig(m);

        ProtocolMappersResource protocolMappers = clientScopes().get(templateId).getProtocolMappers();

        Response response = protocolMappers.createMapper(mapper);
        String mapperId = ApiUtil.getCreatedId(response);

        mapper = protocolMappers.getMapperById(mapperId);

        mapper.getConfig().put("claim.name", "claim");

        protocolMappers.update(mapperId, mapper);

        List<ProtocolMapperRepresentation> mappers = protocolMappers.getMappers();
        assertEquals(1, mappers.size());
        assertEquals(2, mappers.get(0).getConfig().size());
        assertEquals("test", mappers.get(0).getConfig().get("user.attribute"));
        assertEquals("claim", mappers.get(0).getConfig().get("claim.name"));

        clientScopes().get(templateId).remove();
    }


    private ClientScopesResource clientScopes() {
        return testRealmResource().clientScopes();
    }

    private String createClientScope(ClientScopeRepresentation clientScopeRep) {
        Response resp = clientScopes().create(clientScopeRep);
        Assert.assertEquals(201, resp.getStatus());
        resp.close();
        String clientScopeId = ApiUtil.getCreatedId(resp);

        assertAdminEvents.assertEvent(getRealmId(), OperationType.CREATE, AdminEventPaths.clientScopeResourcePath(clientScopeId), clientScopeRep, ResourceType.CLIENT_SCOPE);

        return clientScopeId;
    }

    private void removeClientScope(String clientScopeId) {
        clientScopes().get(clientScopeId).remove();
        assertAdminEvents.assertEvent(getRealmId(), OperationType.DELETE, AdminEventPaths.clientScopeResourcePath(clientScopeId), ResourceType.CLIENT_SCOPE);
    }

}
