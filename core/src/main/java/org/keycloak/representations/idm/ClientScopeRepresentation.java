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

package org.keycloak.representations.idm;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ClientScopeRepresentation {
    /**
     * Use this value in ClientRepresentation.setClientTemplate when you want to clear this value
     */
    public static final String NONE = "NONE";
    protected String id;
    protected String name;
    protected String description;
    protected String protocol;
    protected Map<String, String> attributes;

    protected List<ProtocolMapperRepresentation> protocolMappers;

    @Deprecated
    protected Boolean fullScopeAllowed;
    @Deprecated
    protected Boolean bearerOnly;
    @Deprecated
    protected Boolean consentRequired;
    @Deprecated
    protected Boolean standardFlowEnabled;
    @Deprecated
    protected Boolean implicitFlowEnabled;
    @Deprecated
    protected Boolean directAccessGrantsEnabled;
    @Deprecated
    protected Boolean serviceAccountsEnabled;
    @Deprecated
    protected Boolean publicClient;
    @Deprecated
    protected Boolean frontchannelLogout;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public List<ProtocolMapperRepresentation> getProtocolMappers() {
        return protocolMappers;
    }

    public void setProtocolMappers(List<ProtocolMapperRepresentation> protocolMappers) {
        this.protocolMappers = protocolMappers;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Deprecated
    public Boolean isFullScopeAllowed() {
        return fullScopeAllowed;
    }

    @Deprecated
    public Boolean isBearerOnly() {
        return bearerOnly;
    }

    @Deprecated
    public Boolean isConsentRequired() {
        return consentRequired;
    }

    @Deprecated
    public Boolean isStandardFlowEnabled() {
        return standardFlowEnabled;
    }

    @Deprecated
    public Boolean isImplicitFlowEnabled() {
        return implicitFlowEnabled;
    }

    @Deprecated
    public Boolean isDirectAccessGrantsEnabled() {
        return directAccessGrantsEnabled;
    }

    @Deprecated
    public Boolean isServiceAccountsEnabled() {
        return serviceAccountsEnabled;
    }

    @Deprecated
    public Boolean isPublicClient() {
        return publicClient;
    }

    @Deprecated
    public Boolean isFrontchannelLogout() {
        return frontchannelLogout;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
