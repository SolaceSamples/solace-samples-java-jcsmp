/*
 * Copyright 2021-2022 Solace Corporation. All rights reserved.
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

import com.solacesystems.jcsmp.JCSMPProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SessionEvent;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;

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
                session.setProperty(JCSMPProperties.OAUTH2_ACCESS_TOKEN, refreshAccessToken());
              } catch (JCSMPException e) {
                logger.error("Failed to refresh Access token");
              }

            }
          }
        });
  }

  private String refreshAccessToken() {
    // TODO: Get a new access token from the identity provider
    return "";
  }
}
