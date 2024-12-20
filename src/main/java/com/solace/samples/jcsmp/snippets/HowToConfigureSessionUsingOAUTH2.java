/*
 * Copyright 2021-2024 Solace Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.solace.samples.jcsmp.snippets;

import com.solacesystems.jcsmp.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

/**
 * Demonstrates how to configure a Session using properties supporting OAuth 2.0
 * authentication and how to refresh the tokens.
 */
public class HowToConfigureSessionUsingOAUTH2 {
    private JCSMPSession session = null;

    // remember to add log4j2.xml to your classpath
    private static final Logger logger = LogManager.getLogger(); // log4j2, but could also use SLF4J, JCL, etc.

    public void createOauth2Session(JCSMPProperties sessionProperties, String accessToken, String issuerId)
            throws InvalidPropertiesException {
        sessionProperties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_OAUTH2);
        sessionProperties.setProperty(JCSMPProperties.OAUTH2_ACCESS_TOKEN, accessToken);
        sessionProperties.setProperty(JCSMPProperties.OAUTH2_ISSUER_IDENTIFIER, issuerId);

        // When the Access token has expired, the client will be disconnected, and
        // will try to reconnect. Refresh the token before it reconnects.
        session = JCSMPFactory.onlyInstance().createSession(sessionProperties, null,
                new SessionEventHandler() {
                    @Override
                    public void handleEvent(SessionEventArgs event) {
                        if (event.getEvent() == SessionEvent.RECONNECTING) {
                            logger.info("Session Reconnecting - Refresh Access Token");
                            try {
                                session.setProperty(JCSMPProperties.OAUTH2_ACCESS_TOKEN, refreshAccessToken(issuerId));
                            } catch (Exception e) {
                                logger.error("Failed to refresh Access token");
                            }

                        }
                    }
                });
    }

    private synchronized String refreshAccessToken(String issuerId) throws Exception {
        // Simulated API call to refresh the access token using a refresh token
        logger.info("Attempting to refresh Access Token...");
        String newAccessToken = fetchNewAccessToken();

        if (newAccessToken == null) {
            throw new Exception("Failed to obtain new access token");
        }

        // Validate token claims before using it
        if (!validateTokenClaims(newAccessToken, issuerId)) {
            throw new Exception("Token validation failed");
        }

        logger.info("Access Token successfully refreshed");
        return newAccessToken;
    }

    private String fetchNewAccessToken() {
        // Simulate a call to the identity provider
        logger.info("Using Refresh Token to fetch new Access Token...");
        // TODO: Implement real API call to the identity provider here
        return "newAccessTokenFromIDP";
    }

    private boolean validateTokenClaims(String token, String expectedIssuer) {
        logger.info("Validating token claims...");

        // Simulate token validation (replace with a real JWT library)
        boolean isTokenExpired = isTokenExpired(token);
        boolean isIssuerValid = isIssuerValid(token, expectedIssuer);
        boolean isAudienceValid = isAudienceValid(token);

        if (!isTokenExpired && isIssuerValid && isAudienceValid) {
            logger.info("Token validation succeeded");
            return true;
        } else {
            logger.error("Token validation failed: expired={}, issuerValid={}, audienceValid={}", isTokenExpired, isIssuerValid, isAudienceValid);
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        // Simulate checking expiration (use a real JWT parser to check expiration claim)
        Instant now = Instant.now();
        Instant expirationTime = Instant.now().plusSeconds(3600); // Simulated expiration time
        return now.isAfter(expirationTime);
    }

    private boolean isIssuerValid(String token, String expectedIssuer) {
        // Simulate checking issuer claim (use a real JWT parser to extract claims)
        String issuer = "https://example.com"; // Simulated issuer
        return expectedIssuer.equals(issuer);
    }

    private boolean isAudienceValid(String token) {
        // Simulate checking audience claim
        String audience = "your-audience"; // Simulated audience
        return "your-audience".equals(audience);
    }

}
