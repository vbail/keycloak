package org.keycloak.acr;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.authenticators.browser.CookieAuthenticatorFactory;
import org.keycloak.authentication.authenticators.browser.OTPFormAuthenticatorFactory;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordFormFactory;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.ErrorPage;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.CommonClientSessionModel.ExecutionStatus;

public class TdifAcrProvider implements AcrProvider {

    public static final String ACR_URN_MASK = "urn:id.gov.au:tdif:acr:ip{0}:cl{1}";
    private KeycloakSession session;

    @Override
    public void close() {

    }

    public TdifAcrProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public String buildAcrValue(UserModel user, AuthenticatedClientSessionModel clientSession) {

        String ipLevel = "1";
        if (user.getAttribute("ip") != null && !user.getAttribute("ip").isEmpty()) {
            ipLevel = user.getAttribute("ip").get(0);
        }
        String clLevel = (AuthenticationManager.isSSOAuthentication(clientSession)) ? "0" : "1";
        if (AuthenticationManager.isOTPAuthentication(clientSession)) {
            clLevel = "2";
        }
        String tokenAcr = MessageFormat.format(ACR_URN_MASK, ipLevel, clLevel);

        return tokenAcr.toString();
    }

    @Override
    public Response validateAcrCompliance(AuthenticationSessionModel authenticationSession, String flowPath, EventBuilder event,
        RealmModel realm) {
        String acr = authenticationSession.getClientNote(OAuth2Constants.ACR_VALUES);
        if (acr != null && !acr.equals("")) {

            // TODO handle multiple acr's
            String acrs[] = acr.split(" ");
            acr = acrs[0];

            MessageFormat fmt = new MessageFormat(ACR_URN_MASK);
            Object[] values = null;
            try {
                values = fmt.parse(acr);
            } catch (java.text.ParseException e) {
                ServicesLogger.LOGGER.debug("ACR requested " + acr + " not valid");
                event.error(Errors.INVALID_CONFIG);
                return ErrorPage.error(session, authenticationSession, Response.Status.FORBIDDEN, Messages.ACR_NOT_SATISFIED,
                    acr, "");
            }

            String clValue = null;
            String clSatisfied = "";
            String ipValue = null;
            if (values != null && values.length == 2) {
                ipValue = values[0].toString();
                clValue = values[1].toString();

                Map<String, ExecutionStatus> execStatus = authenticationSession.getExecutionStatus();
                Map<String, ExecutionStatus> execLeg = new HashMap<String, ExecutionStatus>();
                if (execStatus != null && !execStatus.isEmpty()) {
                    for (String idModel : execStatus.keySet()) {
                        AuthenticationExecutionModel model = realm.getAuthenticationExecutionById(idModel);
                        execLeg.put(model.getAuthenticator(), execStatus.get(idModel));
                    }
                }

                int clInt = Integer.parseInt(clValue.substring(clValue.length() - 1));
                int ipInt = Integer.parseInt(ipValue.substring(ipValue.length() - 1));
                String ipUser = null;
                if (authenticationSession.getAuthenticatedUser() != null) {
                    List<String> ipUserList = authenticationSession.getAuthenticatedUser().getAttribute("ip");
                    if (ipUserList != null && !ipUserList.isEmpty()) {
                        ipUser = ipUserList.get(0);
                    } else {
                        ipUser = "0";
                    }
                }

                String acrUser = "";
                ExecutionStatus cookiesExec = execLeg.get(CookieAuthenticatorFactory.PROVIDER_ID);
                if (cookiesExec != null && cookiesExec == ExecutionStatus.SUCCESS) {
                    clSatisfied = "cl0";
                }

                // If we come from registration page, we assume that the ACR level is the same as if the user was logged with
                // user/password
                boolean fromRegistrationPage = flowPath != null ? flowPath.equals("registration") : false;

                if (clInt > 0) {
                    // REQUIRES MORE THAN COOKIES
                    ExecutionStatus status = execLeg.get(UsernamePasswordFormFactory.PROVIDER_ID);
                    if (status != ExecutionStatus.SUCCESS && !fromRegistrationPage) {
                        // ERROR

                        // return session.getProvider(UsernamePasswordForm.class).authenticate(null);

                        // AuthenticationProcessor processor = new AuthenticationProcessor();
                        // processor.setAuthenticationSession(authenticationSession)
                        // .setFlowPath(flowPath)
                        // .setFlowId("")
                        // .setBrowserFlow(true)
                        // .setConnection(connection)
                        // .setEventBuilder(event)
                        // .setRealm(realm)
                        // .setSession(session)
                        // .setUriInfo(uriInfo)
                        // .setRequest(request);
                        //
                        // authenticationSession.setAuthNote(AuthenticationProcessor.CURRENT_FLOW_PATH, flowPath);
                        //
                        // return processor.authenticate();

                        // acrUser = MessageFormat.format(ACR_URN_MASK, ipUser, clSatisfied);
                        // ServicesLogger.LOGGER.debug("ACR value " + clInt + " not safisfied");
                        // event.error(Errors.ACR_NOT_SATISFIED);
                        // return ErrorPage.error(session, authenticationSession, Response.Status.FORBIDDEN,
                        // Messages.ACR_NOT_SATISFIED, acr, acrUser);
                    } else if (status == ExecutionStatus.SUCCESS) {
                        clSatisfied = "cl1";
                    }

                    if (clInt > 1) {
                        // REQUIRES OTP
                        ExecutionStatus statusOtp = execLeg.get(OTPFormAuthenticatorFactory.PROVIDER_ID);
                        if (statusOtp != ExecutionStatus.SUCCESS) {
                            // ERROR
                            acrUser = MessageFormat.format(ACR_URN_MASK, ipUser, clSatisfied);
                            ServicesLogger.LOGGER.debug("ACR value " + clInt + " not safisfied");
                            event.error(Errors.ACR_NOT_SATISFIED);
                            return ErrorPage.error(session, authenticationSession, Response.Status.FORBIDDEN,
                                Messages.ACR_NOT_SATISFIED, acr, acrUser);
                        } else if (status == ExecutionStatus.SUCCESS) {
                            clSatisfied = "cl2";
                        }
                    }
                }

                if (ipUser != null) {
                    int ipUserInt = Integer.parseInt(ipUser);
                    if (ipUserInt < ipInt) {
                        // ERROR
                        acrUser = MessageFormat.format(ACR_URN_MASK, ipUser, clSatisfied);
                        ServicesLogger.LOGGER.debug("ACR value " + clInt + " not safisfied");
                        event.error(Errors.ACR_NOT_SATISFIED);
                        return ErrorPage.error(session, authenticationSession, Response.Status.FORBIDDEN,
                            Messages.ACR_NOT_SATISFIED, acr, acrUser.toString());

                        /*
                         * OIDCRedirectUriBuilder errorResponseBuilder = OIDCRedirectUriBuilder.fromUri(redirectUri,
                         * responseMode).addParam(OAuth2Constants.ERROR, error); if (errorDescription != null) {
                         * errorResponseBuilder.addParam(OAuth2Constants.ERROR_DESCRIPTION, errorDescription); } if
                         * (request.getState() != null) { errorResponseBuilder.addParam(OAuth2Constants.STATE,
                         * request.getState()); } return errorResponseBuilder.build();
                         */
                    }
                }
            }
        }

        return null;
    }

}
