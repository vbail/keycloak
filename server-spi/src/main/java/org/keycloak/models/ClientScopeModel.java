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

package org.keycloak.models;

import java.util.Map;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public interface ClientScopeModel extends ProtocolMapperContainerModel, ScopeContainerModel {
    String getId();

    String getName();

    RealmModel getRealm();
    void setName(String name);

    String getDescription();

    void setDescription(String description);

    String getProtocol();
    void setProtocol(String protocol);

    void setAttribute(String name, String value);
    void removeAttribute(String name);
    String getAttribute(String name);
    Map<String, String> getAttributes();


    // CONFIGS

    String DISPLAY_ON_CONSENT_SCREEN = "display.on.consent.screen";
    String CONSENT_SCREEN_TEXT = "consent.screen.text";

    default boolean isDisplayOnConsentScreen() {
        String displayVal = getAttribute(DISPLAY_ON_CONSENT_SCREEN);
        return displayVal==null ? true : Boolean.parseBoolean(displayVal);
    }

    default void setDisplayOnConsentScreen(boolean displayOnConsentScreen) {
        setAttribute(DISPLAY_ON_CONSENT_SCREEN, String.valueOf(displayOnConsentScreen));
    }

    default String getConsentScreenText() {
        return getAttribute(CONSENT_SCREEN_TEXT);
    }

    default void setConsentScreenText(String consentScreenText) {
        setAttribute(CONSENT_SCREEN_TEXT, consentScreenText);
    }


}
