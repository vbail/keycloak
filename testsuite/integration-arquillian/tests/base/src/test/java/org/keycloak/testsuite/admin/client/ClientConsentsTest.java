package org.keycloak.testsuite.admin.client;

import org.jboss.arquillian.graphene.page.Page;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.testsuite.ProfileAssume;
import org.keycloak.testsuite.pages.AppPage;
import org.keycloak.testsuite.pages.LoginPage;
import org.keycloak.testsuite.pages.OAuthGrantPage;

public class ClientConsentsTest extends AbstractClientTest {
	
	
    @Page
    protected AppPage appPage;

    @Page
    protected LoginPage loginPage;

    @Page
    protected OAuthGrantPage grantPage;

    @Test
    public void TestConsentScreen() {
        ProfileAssume.assumeCommunity();

        // Set client, which requires consent
        oauth.clientId("third-party");

        loginPage.open();

        loginPage.login("test-user@localhost", "password");

        grantPage.assertCurrent();
        Assert.assertEquals("English", grantPage.getLanguageDropdownText());


        // Confirm grant
        grantPage.accept();

        Assert.assertEquals(AppPage.RequestType.AUTH_RESPONSE, appPage.getRequestType());
        Assert.assertNotNull(oauth.getCurrentQuery().get(OAuth2Constants.CODE));

        // Revert client
        oauth.clientId("test-app");
    }

}
