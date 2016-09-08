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

package com.solace.samples;

import java.io.IOException;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class BasicReplier {

    public void run(String... args) throws JCSMPException {
        System.out.println("BasicReplier initializing...");
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]); // msg-backbone ip:port
        properties.setProperty(JCSMPProperties.VPN_NAME, "default"); // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, "clientUsername"); // client-username (assumes no password)
        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();

        final Topic topic = JCSMPFactory.onlyInstance().createTopic("tutorial/requests");

        /** Anonymous inner-class for handling publishing events */
        final XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
            public void responseReceived(String messageID) {
                System.out.println("Producer received response for msg: " + messageID);
            }

            public void handleError(String messageID, JCSMPException e, long timestamp) {
                System.out.printf("Producer received error for msg: %s@%s - %s%n", messageID, timestamp, e);
            }
        });

        /** Anonymous inner-class for request handling **/
        final XMLMessageConsumer cons = session.getMessageConsumer(new XMLMessageListener() {
            public void onReceive(BytesXMLMessage request) {

                if (request.getReplyTo() != null) {
                    System.out.println("Received request, generating response");
                    TextMessage reply = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);

                    final String text = "Sample response";
                    reply.setText(text);

                    try {
                        producer.sendReply(request, reply);
                    } catch (JCSMPException e) {
                        System.out.println("Error sending reply.");
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Received message without reply-to field");
                }

            }

            public void onException(JCSMPException e) {
                System.out.printf("Consumer received exception: %s%n", e);
            }
        });

        session.addSubscription(topic);
        cons.start();

        // Consume-only session is now hooked up and running!
        System.out.println("Listening for request messages on topic " + topic + " ... Press enter to exit");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close consumer
        cons.close();
        System.out.println("Exiting.");
        session.closeSession();

    }

    public static void main(String... args) throws JCSMPException {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: BasicReplier <msg_backbone_ip:port>");
            System.exit(-1);
        }

        BasicReplier replier = new BasicReplier();
        replier.run(args);
    }
}
