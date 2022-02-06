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
import com.solacesystems.jcsmp.JCSMPRequestTimeoutException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Requestor;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import java.io.IOException;

/** Direct Messaging sample to demonstrate initiating a request-reply flow.
 *  Makes use of the blocking convenience function Requestor.request();
 */
public class DirectRequestorBlocking {

    private static final String SAMPLE_NAME = DirectRequestorBlocking.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JCSMP";
    private static final int REQUEST_TIMEOUT_MS = 3000;  // time to wait for a reply before timing out

    private static volatile int msgSentCounter = 0;                   // num messages sent
    private static volatile boolean isShutdown = false;

    /** Main method. */
    public static void main(String... args) throws JCSMPException, IOException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");

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
        session.connect();  // connect to the broker

        // Simple anonymous inner-class for handling publishing events
        session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }

            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                }
            }
        });
        
        // less common use of SYNChronous blocking consumer mode (null callback)
        final XMLMessageConsumer consumer = session.getMessageConsumer((XMLMessageListener)null);
        consumer.start();  // needed to receive the responses

        System.out.println(SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        TextMessage requestMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        while (System.in.available() == 0 && !isShutdown) {
            try {
                requestMsg.setText(String.format("Hello, this is reqeust #%d", msgSentCounter));
                // topic should look like 'solace/samples/jcsmp/direct/request'
                String topicString = TOPIC_PREFIX + API.toLowerCase() + "/direct/request";
                Topic topic = JCSMPFactory.onlyInstance().createTopic(topicString);
                System.out.printf(">> About to send request message #%d to topic '%s'...%n", msgSentCounter, topic.getName());
                Requestor requestor = session.createRequestor();  // create the useful Requestor object, Direct only
                msgSentCounter++;  // add one
                BytesXMLMessage replyMsg = requestor.request(requestMsg, REQUEST_TIMEOUT_MS, topic);  // send and receive in one blocking call
                System.out.printf("vv Response Message Dump:%n%s%n", replyMsg.dump());
                requestMsg.reset();  // reuse this message, to avoid having to recreate it: better performance
                Thread.sleep(1000);  // approx. one request per second
            } catch (JCSMPRequestTimeoutException e) {
                System.out.println("Failed to receive a reply in " + REQUEST_TIMEOUT_MS + " msecs");
            } catch (JCSMPException e) {  // threw from send(), only thing that is throwing here, but keep trying (unless shutdown?)
                System.out.printf("### Caught while trying to producer.send(): %s%n", e);
                if (e instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
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
