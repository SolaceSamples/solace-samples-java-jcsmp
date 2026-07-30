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

package com.solace.samples.jcsmp.patterns;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPReconnectEventHandler;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageProducer;

/**
 * A more performant sample that shows an application that publishes.
 */
public class DirectPublisher {

    private static final String SAMPLE_NAME = DirectPublisher.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JCSMP";
    private static final int APPROX_MSG_RATE_PER_SEC = 100;
    private static final int PAYLOAD_SIZE = 100;

    private static volatile int msgSentCounter = 0;                   // num messages sent
    private static volatile boolean isShutdown = false;
    private static volatile boolean isConnected = true;               // tracks transport state via reconnect events
    private static JCSMPSession session;
    private static XMLMessageProducer producer;
    private static ScheduledExecutorService statsPrintingThread;

    /** Main method. */
    public static void main(String... args) throws JCSMPException, IOException, InterruptedException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");
        try {
            setupSolace(args);
            // graceful shutdown: a SIGINT (Ctrl-C) signals the main loop to stop, then the
            // hook joins the main thread so the cleanup in teardownSolace() (stop publishing,
            // close the session) runs to completion before the JVM halts. The JVM exits as
            // soon as all shutdown hooks return, so the hook must wait, not just set a flag.
            final Thread mainThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown signal received, stopping publisher...");
                isShutdown = true;
                try {
                    mainThread.join(5000);  // wait for the main thread's session close
                } catch (InterruptedException e) {
                    // nothing more we can do; the JVM is halting
                }
            }));
            runPublishLoop();
        } finally {
            teardownSolace();
            System.out.println("Main thread quitting.");
        }
    }

    private static void setupSolace(String[] args) throws JCSMPException {
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1]);     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);      // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
        }
        properties.setProperty(JCSMPProperties.GENERATE_SEQUENCE_NUMBERS, true);  // not required, but interesting
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);
        // best practice: register a session event handler at session creation and handle
        // each event appropriately, rather than only logging it
        session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {
                System.out.printf("### Received a Session event: %s%n", event);
                switch (event.getEvent()) {
                    case RECONNECTING:  // session went down, automatic reconnect attempt in progress
                        isConnected = false;
                        break;
                    case RECONNECTED:   // automatic reconnect succeeded, session re-established
                        isConnected = true;
                        break;
                    case DOWN_ERROR:    // session was up and then went down; reconnects exhausted
                        System.out.println("Session is down and reconnect attempts are exhausted, quitting.");
                        // the session will not recover; end the main loop so teardownSolace()
                        // runs in main's finally
                        isShutdown = true;
                        break;
                    default:
                        break;
                }
            }
        });
        session.connect();  // connect to the broker

        // Simple anonymous inner-class for handling publishing events
        producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }

            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                } else if (cause instanceof JCSMPErrorResponseException) {  // might have some extra info
                    JCSMPErrorResponseException e = (JCSMPErrorResponseException)cause;
                    System.out.println(JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e.getSubcodeEx())
                            + ": " + e.getResponsePhrase());
                    System.out.println(cause);
                }
            }
        });

        // best practice for publish-only applications: transport reconnect events are only
        // exposed through a consumer, so acquire an empty one (never started) and register a
        // reconnect event handler to pause publishing while the connection is re-established
        session.getMessageConsumer(new JCSMPReconnectEventHandler() {
            @Override
            public boolean preReconnect() throws JCSMPException {
                System.out.println("### preReconnect(): transport down, pausing publishing");
                isConnected = false;
                return true;  // true means let the API proceed with its reconnect attempts
            }

            @Override
            public void postReconnect() throws JCSMPException {
                System.out.println("### postReconnect(): transport restored, resuming publishing");
                isConnected = true;
            }
        }, null);  // no message listener: this consumer exists only to surface transport events
    }

    private static void runPublishLoop() throws IOException, InterruptedException {
        statsPrintingThread = Executors.newSingleThreadScheduledExecutor();
        statsPrintingThread.scheduleAtFixedRate(() -> {
            System.out.printf("%s %s Published msgs/s: %,d%n",API,SAMPLE_NAME,msgSentCounter);  // simple way of calculating message rates
            msgSentCounter = 0;
        }, 1, 1, TimeUnit.SECONDS);

        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] or Ctrl-C to quit.");
        // preallocate a binary message, reuse it each loop, for performance
        final BytesMessage message = JCSMPFactory.onlyInstance().createMessage(BytesMessage.class);
        byte[] payload = new byte[PAYLOAD_SIZE];  // preallocate memory, for reuse, for performance
        // loop the main thread, waiting for a quit signal
        while (System.in.available() == 0 && !isShutdown) {
            if (!isConnected) {  // transport is down: wait for the API's automatic reconnect
                Thread.sleep(100);
                continue;
            }
            try {
                // each loop, change the payload, less trivial example than static payload
                char chosenCharacter = (char)(Math.round(msgSentCounter % 26) + 65);  // rotate through letters [A-Z]
                Arrays.fill(payload,(byte)chosenCharacter);  // fill the payload completely with that char
                message.setData(payload);
                message.setDeliveryMode(DeliveryMode.DIRECT);  // at-most-once; DIRECT is also the API default
                message.setApplicationMessageId(UUID.randomUUID().toString());  // as an example of a header
                // dynamic topics!!  "solace/samples/jcsmp/direct/pub/A"
                String topicString = new StringBuilder(TOPIC_PREFIX).append(API.toLowerCase())
                        .append("/direct/pub/").append(chosenCharacter).toString();  // StringBuilder faster than +
                producer.send(message,JCSMPFactory.onlyInstance().createTopic(topicString));  // send the message
                msgSentCounter++;  // add one
                message.reset();   // reuse this message, to avoid having to recreate it: better performance
            } catch (JCSMPException e) {  // threw from send(), only thing that is throwing here, but keep trying (unless shutdown?)
                System.out.printf("### Caught while trying to producer.send(): %s%n",e);
                if (e instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                }
            } finally {  // add a delay between messages
                try {
                    Thread.sleep(1000 / APPROX_MSG_RATE_PER_SEC);  // do Thread.sleep(0) for max speed
                    // Note: STANDARD Edition Solace PubSub+ broker is limited to 10k msg/s max ingress
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();  // restore the interrupt status, do not swallow it
                    isShutdown = true;
                }
            }
        }
        isShutdown = true;
    }

    private static void teardownSolace() {
        // teardownSolace() runs in main's finally on EVERY exit path (normal quit, ENTER,
        // SIGINT, DOWN_ERROR, or an exception), so Solace teardown is never skipped.
        isShutdown = true;
        if (statsPrintingThread != null) {
            statsPrintingThread.shutdown();  // stop printing stats
        }
        // direct is at-most-once: no outstanding broker ACKs to drain, so close immediately
        if (session != null) {
            session.closeSession();  // will also close producer object
        }
    }
}
