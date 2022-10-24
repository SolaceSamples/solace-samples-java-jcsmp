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
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;

/**
 * Demonstrates how to configure a TLS Session with a specific truststore.
 */
public class HowToConfigureSessionTlsKeystore {


	public JCSMPSession create(JCSMPProperties sessionProperties) throws InvalidPropertiesException {

		sessionProperties.setProperty(JCSMPProperties.SSL_TRUST_STORE, "/Users/myself/path/to/truststore.jks");
		sessionProperties.setProperty(JCSMPProperties.SSL_TRUST_STORE_PASSWORD, "tsPassword");
		
		final JCSMPFactory sessionFactory = JCSMPFactory.onlyInstance();
		final JCSMPSession session = sessionFactory.createSession(sessionProperties);
		return session;
	}

}
