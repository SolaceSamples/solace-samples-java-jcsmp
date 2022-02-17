/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.solace.samples.jcsmp.snippets;

import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;

/**
 * Demonstrates how to configure a Session using properties supporting OAuth 2.0 authentication.
 */
public class HowToConfigureSessionUsingOAUTH2 {


  public void createOauth2Session(JCSMPProperties sessionProperties, String accessToken, String issuerId) throws InvalidPropertiesException
  {
     sessionProperties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_OAUTH2);
     sessionProperties.setProperty(JCSMPProperties.OAUTH2_ACCESS_TOKEN, accessToken);
     sessionProperties.setProperty(JCSMPProperties.OAUTH2_ISSUER_IDENTIFIER, issuerId); 
     final JCSMPFactory sessionFactory = JCSMPFactory.onlyInstance();
     final JCSMPSession session = sessionFactory.createSession(sessionProperties);
  }
    
}
