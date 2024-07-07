/*
 * Copyright 2024 Solace Corporation. All rights reserved.
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
 * Demonstrates how to configure a Session to connect to a broker using WebSockets
 * - set the transport schema to 'ws' (plain-text) or 'wss' (TLS)
 */
public class HowToConfigureSessionToUseWebsockets {


	public JCSMPSession create(JCSMPProperties sessionProperties) throws InvalidPropertiesException {

		sessionProperties.setProperty(JCSMPProperties.HOST, "ws://broker.domain.com");
		
		final JCSMPFactory sessionFactory = JCSMPFactory.onlyInstance();
		final JCSMPSession session = sessionFactory.createSession(sessionProperties);
		return session;
	}

}
