/*
 * Copyright 2021 Solace Corporation. All rights reserved.
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

package com.solace.samples.jcsmp.patterns;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;
import java.io.IOException;

public class DirectReplier {

    private static final String SAMPLE_NAME = DirectReplier.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JCSMP";

    private static volatile boolean isShutdown = false;

    /** Main method. */
    public static void main(String... args) throws JCSMPException, IOException {
        if (args.length < 3) {   // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(SAMPLE_NAME + " initializing...");
        
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1]);     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);      // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
        }
        properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);  // re-subscribe after reconnect
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);
        final JCSMPSession session;
        session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {  // could be reconnecting, connection lost, etc.
                System.out.printf("### Received a Session event: %s%n", event);
            }
        });
        session.connect();

        // Anonymous inner-class for handling publishing events
        final XMLMessageProducer producer;
        producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }
            
            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n",cause);
                if (cause instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                }
            }
        });

        // Anonymous inner-class for request handling
        final XMLMessageConsumer cons = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage requestMsg) {
                if (requestMsg.getDestination().getName().contains("direct/request") && requestMsg.getReplyTo() != null) {
                    System.out.printf(">> %s %s received request on '%s', generating response.%n",
                            API,SAMPLE_NAME,requestMsg.getDestination());
                    System.out.println(requestMsg.dump());
                    TextMessage replyMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);  // reply with a Text
                    if (requestMsg.getApplicationMessageId() != null) {
                        replyMsg.setApplicationMessageId(requestMsg.getApplicationMessageId());  // populate for traceability
                    }
                    final String text = "Hello! Here is a response to your message on topic '" + requestMsg.getDestination() + "'.";
                    replyMsg.setText(text);
                    try {
                        // only allowed to publish messages from API-owned (callback) thread when JCSMPProperties.MESSAGE_CALLBACK_ON_REACTOR == false
                        producer.sendReply(requestMsg, replyMsg);  // convenience method: copies in reply-to, correlationId, etc.
                    } catch (JCSMPException e) {
                        System.out.printf("### Caught while trying to producer.sendReply(): %s%n", e);
                        if (e instanceof JCSMPTransportException) {  // all reconnect attempts failed
                            isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                        }
                    }
                } else {
                    System.out.println("Received message without reply-to field");
                }

            }

            public void onException(JCSMPException e) {
                System.out.printf("Consumer received exception: %s%n", e);
            }
        });

        // subscribe to 'solace/samples/*/direct/request' ... the * is to match any pub
        session.addSubscription(JCSMPFactory.onlyInstance().createTopic(TOPIC_PREFIX + "*/direct/request"));
        // for use with Solace HTTP MicroGateway feature, will respond to REST GET request on same URI
        // try doing: curl -u default:default http://localhost:9000/solace/samples/rest/direct/request
        session.addSubscription(JCSMPFactory.onlyInstance().createTopic("GET/" + TOPIC_PREFIX + "*/direct/request"));
        cons.start();

        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !isShutdown) {
            try {
                Thread.sleep(1000);  // wait 1 second
            } catch (InterruptedException e) {
                // Thread.sleep() interrupted... probably getting shut down
            }
        }
        isShutdown = true;
        session.closeSession();  // will also close producer and consumer objects
        System.out.println("Main thread quitting.");
    }
}
