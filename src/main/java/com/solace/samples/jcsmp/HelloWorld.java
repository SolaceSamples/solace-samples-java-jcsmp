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

package com.solace.samples.jcsmp;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This simple introductory sample shows an application that both publishes and subscribes.
 */
public class HelloWorld {
    
    private static final String SAMPLE_NAME = HelloWorld.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JCSMP";
    private static volatile boolean isShutdown = false;           // are we done yet?

    /** Simple application for doing pub/sub publish-subscribe  */
    public static void main(String... args) throws JCSMPException, IOException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n", SAMPLE_NAME);
            System.out.printf("  e.g. %s localhost default default%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        // User prompt, what is your name??, to use in the topic
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String uniqueName = "";
        while (uniqueName.isEmpty()) {
            System.out.printf("Hello! Enter your name, or a unique word: ");
            uniqueName = reader.readLine().trim().replaceAll("\\s+", "_");  // clean up whitespace
        }
        
        System.out.println(API + " " + SAMPLE_NAME + " JCSMP initializing...");
        // Build the properties object for initializing the JCSMP Session
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1]);     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);      // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
        }
        properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);  // subscribe Direct subs after reconnect
        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();  // connect to the broker
        
        // setup Producer callbacks config: simple anonymous inner-class for handling publishing events
        final XMLMessageProducer producer;
        producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }

            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPTransportException) {  // unrecoverable, all reconnect attempts failed
                    isShutdown = true;
                }
            }
        });

        // setup Consumer callbacks next: anonymous inner-class for Listener async threaded callbacks
        final XMLMessageConsumer consumer = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage message) {
                // could be 4 different message types: 3 SMF ones (Text, Map, Stream) and just plain binary
                System.out.printf("vvv RECEIVED A MESSAGE vvv%n%s===%n",message.dump());  // just print
            }

            @Override
            public void onException(JCSMPException e) {  // uh oh!
                System.out.printf("### MessageListener's onException(): %s%n",e);
                if (e instanceof JCSMPTransportException) {  // unrecoverable, all reconnect attempts failed
                    isShutdown = true;  // let's quit
                }
            }
        });

        // Ready to start the application, just add one subscription
        session.addSubscription(JCSMPFactory.onlyInstance().createTopic(TOPIC_PREFIX + "*/hello/>"));    // use wildcards
        consumer.start();  // turn on the subs, and start receiving data
        System.out.printf("%nConnected and subscribed. Ready to publish. Press [ENTER] to quit.%n");
        System.out.printf(" ~ Run this sample twice splitscreen to see true publish-subscribe. ~%n%n");

        TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        while (System.in.available() == 0 && !isShutdown) {  // loop now, just use main thread
            try {
                Thread.sleep(5000);  // take a pause
                // specify a text payload
                message.setText(String.format("Hello World from %s!",uniqueName));
                // make a dynamic topic: solace/samples/jcsmp/hello/[uniqueName]
                String topicString = TOPIC_PREFIX + API.toLowerCase() + "/hello/" + uniqueName.toLowerCase();
                System.out.printf(">> Calling send() on %s%n",topicString);
                producer.send(message, JCSMPFactory.onlyInstance().createTopic(topicString));
                message.reset();     // reuse this message on the next loop, to avoid having to recreate it
            } catch (JCSMPException e) {
                System.out.printf("### Exception caught during producer.send(): %s%n",e);
                if (e instanceof JCSMPTransportException) {  // unrecoverable
                    isShutdown = true;
                }
            } catch (InterruptedException e) {
                // Thread.sleep() interrupted... probably getting shut down
            }
        }
        isShutdown = true;
        session.closeSession();  // will also close producer and consumer objects
        System.out.println("Main thread quitting.");
    }
}
