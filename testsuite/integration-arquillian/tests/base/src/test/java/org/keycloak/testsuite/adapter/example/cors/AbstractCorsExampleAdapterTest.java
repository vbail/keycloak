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

package org.keycloak.testsuite.adapter.example.cors;

import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.page.Page;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testsuite.adapter.AbstractExampleAdapterTest;
import org.keycloak.testsuite.adapter.page.AngularCorsProductTestApp;
import org.keycloak.testsuite.adapter.page.CorsDatabaseServiceTestApp;
import org.keycloak.testsuite.auth.page.account.Account;
import org.keycloak.testsuite.auth.page.login.OIDCLogin;
import org.keycloak.testsuite.util.JavascriptBrowser;
import org.keycloak.testsuite.util.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static junit.framework.TestCase.assertNotNull;
import static org.keycloak.testsuite.util.IOUtil.loadRealm;
import static org.keycloak.testsuite.util.URLAssert.assertCurrentUrlStartsWith;
import static org.keycloak.testsuite.util.WaitUtils.waitForPageToLoad;
import static org.keycloak.testsuite.util.WaitUtils.waitUntilElement;

/**
 * Created by fkiss.
 */
public abstract class AbstractCorsExampleAdapterTest extends AbstractExampleAdapterTest {

    public static final String CORS = "cors";
    public static final String AUTH_SERVER_HOST = "localhost-auth";
    private static String hostBackup;

    @ArquillianResource
    private Deployer deployer;

    // Javascript browser needed KEYCLOAK-4703
    @Drone
    @JavascriptBrowser
    protected WebDriver jsDriver;

    @Page
    @JavascriptBrowser
    protected OIDCLogin jsDriverTestRealmLoginPage;

    @Page
    @JavascriptBrowser
    private AngularCorsProductTestApp jsDriverAngularCorsProductPage;

    @Page
    @JavascriptBrowser
    private Account jsDriverTestRealmAccount;

    @Deployment(name = AngularCorsProductTestApp.DEPLOYMENT_NAME)
    private static WebArchive angularCorsProductExample() throws IOException {
        return exampleDeployment(AngularCorsProductTestApp.CLIENT_ID);
    }

    @Deployment(name = CorsDatabaseServiceTestApp.DEPLOYMENT_NAME, managed = false)
    private static WebArchive corsDatabaseServiceExample() throws IOException {
        return exampleDeployment(CorsDatabaseServiceTestApp.CLIENT_ID);
    }

    @Override
    public void addAdapterTestRealms(List<RealmRepresentation> testRealms) {
        testRealms.add(
                loadRealm(new File(TEST_APPS_HOME_DIR + "/cors/cors-realm.json")));
    }

    @Before
    public void onBefore() {
        deployer.deploy(CorsDatabaseServiceTestApp.DEPLOYMENT_NAME);
    }

    @After
    public void onAfter() {
        deployer.undeploy(CorsDatabaseServiceTestApp.DEPLOYMENT_NAME);
    }

    static{
        hostBackup = System.getProperty("auth.server.host", "localhost");
        System.setProperty("auth.server.host", AUTH_SERVER_HOST);
    }

    @Override
    public void setDefaultPageUriParameters() {
        super.setDefaultPageUriParameters();
        jsDriverTestRealmLoginPage.setAuthRealm(CORS);
        jsDriverTestRealmAccount.setAuthRealm(CORS);
    }

    @Test
    public void angularCorsProductTest() {
        jsDriverAngularCorsProductPage.navigateTo();
        jsDriverTestRealmLoginPage.form().login("bburke@redhat.com", "password");

        assertCurrentUrlStartsWith(jsDriverAngularCorsProductPage);
        jsDriverAngularCorsProductPage.reloadData();
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("iphone");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("ipad");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("ipod");
        waitUntilElement(jsDriverAngularCorsProductPage.getHeaders()).text().contains("\"x-custom1\":\"some-value\"");
        waitUntilElement(jsDriverAngularCorsProductPage.getHeaders()).text().contains("\"www-authenticate\":\"some-value\"");

        jsDriverAngularCorsProductPage.loadRoles();
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("user");

        jsDriverAngularCorsProductPage.addRole();
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("stuff");

        jsDriverAngularCorsProductPage.deleteRole();
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().not().contains("stuff");

        jsDriverAngularCorsProductPage.loadAvailableSocialProviders();
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("twitter");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("google");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("linkedin");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("facebook");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("stackoverflow");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("github");
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("microsoft");

        jsDriverAngularCorsProductPage.loadPublicRealmInfo();
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("Realm name: cors");

        String serverVersion = getAuthServerVersion();
        assertNotNull(serverVersion);

        jsDriverAngularCorsProductPage.navigateTo();
        waitForPageToLoad();

        jsDriverAngularCorsProductPage.loadVersion();
        waitUntilElement(jsDriverAngularCorsProductPage.getOutput()).text().contains("Keycloak version: " + serverVersion);
    }

    @Nullable
    private String getAuthServerVersion() {
        jsDriver.navigate().to(suiteContext.getAuthServerInfo().getContextRoot().toString() +
                "/auth/admin/master/console/#/server-info");
        jsDriverTestRealmLoginPage.form().login("admin", "admin");

        WaitUtils.waitUntilElement(By.tagName("body")).is().visible();
        Pattern pattern = Pattern.compile("<td [^>]+>Server Version</td>" +
                "\\s+<td [^>]+>([^<]+)</td>");
        Matcher matcher = pattern.matcher(jsDriver.getPageSource());

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    @AfterClass
    public static void afterCorsTest() {
        System.setProperty("auth.server.host", hostBackup);
    }
}