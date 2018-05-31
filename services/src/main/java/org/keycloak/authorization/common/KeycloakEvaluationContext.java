/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.authorization.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.keycloak.authorization.identity.Identity;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.AccessToken;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class KeycloakEvaluationContext extends DefaultEvaluationContext {

    private final KeycloakIdentity identity;

    public KeycloakEvaluationContext(KeycloakIdentity identity, KeycloakSession keycloakSession) {
        this(identity, null, keycloakSession);
    }

    public KeycloakEvaluationContext(KeycloakIdentity identity, Map<String, List<String>> claims, KeycloakSession keycloakSession) {
        super(identity, claims, keycloakSession);
        this.identity = identity;
    }

    @Override
    public Identity getIdentity() {
        return this.identity;
    }

    @Override
    public Map<String, Collection<String>> getBaseAttributes() {
        Map<String, Collection<String>> attributes = super.getBaseAttributes();
        AccessToken accessToken = this.identity.getAccessToken();

        if (accessToken != null) {
            attributes.put("kc.client.id", Arrays.asList(accessToken.getIssuedFor()));
        }
        return attributes;
    }
}
