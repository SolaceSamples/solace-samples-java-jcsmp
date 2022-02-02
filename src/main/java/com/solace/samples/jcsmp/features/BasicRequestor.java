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

package com.solace.samples.jcsmp.features;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPRequestTimeoutException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.Requestor;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

/**
 * Deprecated sample, see patterns/DirectRequestorBlocking instead
 */
public class BasicRequestor {

    public static void main(String... args) throws JCSMPException {
        // Check command line arguments
        if (args.length < 2 || args[1].split("@").length != 2) {
            System.out.println("Usage: BasicRequestor <host:port> <client-username@message-vpn> [client-password]");
            System.out.println();
            System.exit(-1);
        }
        if (args[1].split("@")[0].isEmpty()) {
            System.out.println("No client-username entered");
            System.out.println();
            System.exit(-1);
        }
        if (args[1].split("@")[1].isEmpty()) {
            System.out.println("No message-vpn entered");
            System.out.println();
            System.exit(-1);
        }

        System.out.println("BasicRequestor initializing...");

        // Create a JCSMP Session
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);     // host:port
        properties.setProperty(JCSMPProperties.USERNAME, args[1].split("@")[0]); // client-username
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1].split("@")[1]); // message-vpn
        if (args.length > 2) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[2]); // client-password
        }
        final JCSMPSession session =  JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();

        //This will have the session create the producer and consumer required
        //by the Requestor used below.

        /** Anonymous inner-class for handling publishing events */
        @SuppressWarnings("unused")
        XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
			@Override
			public void responseReceivedEx(Object key) {
                System.out.println("Producer received response for msg: " + key.toString());
			}
			
			@Override
			public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("Producer received error for msg: %s@%s - %s%n", key.toString(), timestamp, cause);
			}
        });

        XMLMessageConsumer consumer = session.getMessageConsumer((XMLMessageListener)null);
        consumer.start();

        final Topic topic = JCSMPFactory.onlyInstance().createTopic("tutorial/requests");

        //Time to wait for a reply before timing out
        final int timeoutMs = 10000;
        TextMessage request = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        final String text = "Sample Request";
        request.setText(text);

        try {
            Requestor requestor = session.createRequestor();
            System.out.printf("Connected. About to send request message '%s' to topic '%s'...%n",text,topic.getName());
            BytesXMLMessage reply = requestor.request(request, timeoutMs, topic);

            // Process the reply
            if (reply instanceof TextMessage) {
                System.out.printf("TextMessage response received: '%s'%n",
                        ((TextMessage)reply).getText());
            }
            System.out.printf("Response Message Dump:%n%s%n",reply.dump());
        } catch (JCSMPRequestTimeoutException e) {
            System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
        }

        System.out.println("Exiting...");
        session.closeSession();
    }
}
