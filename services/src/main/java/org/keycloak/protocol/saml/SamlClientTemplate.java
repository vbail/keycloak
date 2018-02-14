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

package org.keycloak.protocol.saml;

import java.util.Objects;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.saml.SignatureAlgorithm;

/**
 * TODO:mposolda Check whether class can't be just removed
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class SamlClientTemplate {
    protected ClientScopeModel clientScope;

    public SamlClientTemplate(ClientScopeModel clientScope) {
        this.clientScope = clientScope;
    }

    public String getId() {
        return clientScope.getId();
    }

//

    public String getCanonicalizationMethod() {
        return clientScope.getAttribute(SamlConfigAttributes.SAML_CANONICALIZATION_METHOD_ATTRIBUTE);
    }

    public void setCanonicalizationMethod(String value) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_CANONICALIZATION_METHOD_ATTRIBUTE, value);
    }

    public SignatureAlgorithm getSignatureAlgorithm() {
        String alg = null;
        alg = clientScope.getAttribute(SamlConfigAttributes.SAML_CANONICALIZATION_METHOD_ATTRIBUTE);
        if (alg != null) {
            SignatureAlgorithm algorithm = SignatureAlgorithm.valueOf(alg);
            if (algorithm != null)
                return algorithm;
        }
        return SignatureAlgorithm.RSA_SHA256;
    }

    public void setSignatureAlgorithm(SignatureAlgorithm algorithm) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_SIGNATURE_ALGORITHM, algorithm.name());
    }

    public String getNameIDFormat() {
        return clientScope.getAttributes().get(SamlConfigAttributes.SAML_NAME_ID_FORMAT_ATTRIBUTE);
    }
    public void setNameIDFormat(String format) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_NAME_ID_FORMAT_ATTRIBUTE, format);
    }

    public boolean includeAuthnStatement() {
        return "true".equals(clientScope.getAttribute(SamlConfigAttributes.SAML_AUTHNSTATEMENT));
    }

    public void setIncludeAuthnStatement(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_AUTHNSTATEMENT, Boolean.toString(val));
    }

    public boolean forceNameIDFormat() {
        return "true".equals(clientScope.getAttribute(SamlConfigAttributes.SAML_FORCE_NAME_ID_FORMAT_ATTRIBUTE));

    }
    public void setForceNameIDFormat(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_FORCE_NAME_ID_FORMAT_ATTRIBUTE, Boolean.toString(val));
    }

    public boolean requiresRealmSignature() {
        return "true".equals(clientScope.getAttribute(SamlConfigAttributes.SAML_SERVER_SIGNATURE));
    }

    public void setRequiresRealmSignature(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_SERVER_SIGNATURE, Boolean.toString(val));
    }

    public boolean addExtensionsElementWithKeyInfo() {
        return Objects.equals("true", clientScope.getAttribute(SamlConfigAttributes.SAML_SERVER_SIGNATURE_KEYINFO_EXT));
    }

    public void setAddExtensionsElementWithKeyInfo(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_SERVER_SIGNATURE_KEYINFO_EXT, Boolean.toString(val));
    }

    public boolean forcePostBinding() {
        return "true".equals(clientScope.getAttribute(SamlConfigAttributes.SAML_FORCE_POST_BINDING));
    }

    public void setForcePostBinding(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_FORCE_POST_BINDING, Boolean.toString(val));

    }
    public boolean requiresAssertionSignature() {
        return "true".equals(clientScope.getAttribute(SamlConfigAttributes.SAML_ASSERTION_SIGNATURE));
    }

    public void setRequiresAssertionSignature(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_ASSERTION_SIGNATURE   , Boolean.toString(val));

    }
    public boolean requiresEncryption() {
        return "true".equals(clientScope.getAttribute(SamlConfigAttributes.SAML_ENCRYPT));
    }


    public void setRequiresEncryption(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_ENCRYPT, Boolean.toString(val));

    }

    public boolean requiresClientSignature() {
        return "true".equals(clientScope.getAttribute(SamlConfigAttributes.SAML_CLIENT_SIGNATURE_ATTRIBUTE));
    }

    public void setRequiresClientSignature(boolean val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_CLIENT_SIGNATURE_ATTRIBUTE   , Boolean.toString(val));

    }

    public String getClientSigningCertificate() {
        return clientScope.getAttribute(SamlConfigAttributes.SAML_SIGNING_CERTIFICATE_ATTRIBUTE);
    }

    public void setClientSigningCertificate(String val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_SIGNING_CERTIFICATE_ATTRIBUTE, val);

    }

    public String getClientSigningPrivateKey() {
        return clientScope.getAttribute(SamlConfigAttributes.SAML_SIGNING_PRIVATE_KEY);
    }

    public void setClientSigningPrivateKey(String val) {
        clientScope.setAttribute(SamlConfigAttributes.SAML_SIGNING_PRIVATE_KEY, val);

    }
}
