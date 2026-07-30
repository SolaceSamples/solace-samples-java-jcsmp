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

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import java.io.IOException;

/** This is a more detailed subscriber sample. */
public class DirectSubscriber {

    private static final String SAMPLE_NAME = DirectSubscriber.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JCSMP";

    private static volatile int msgRecvCounter = 0;              // num messages received
    private static volatile boolean hasDetectedDiscard = false;  // detected any discards yet?
    private static volatile boolean isShutdown = false;          // are we done yet?
    private static JCSMPSession session;

    /** the main method. */
    public static void main(String... args) throws JCSMPException, IOException, InterruptedException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");
        try {
            setupSolace(args);
            // graceful shutdown: a SIGINT (Ctrl-C) signals the main loop to stop, then the
            // hook joins the main thread so the cleanup in teardownSolace() (close the
            // session) runs to completion before the JVM halts. The JVM exits as soon as all
            // shutdown hooks return, so the hook must wait, not just set a flag.
            final Thread mainThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown signal received, stopping subscriber...");
                isShutdown = true;
                try {
                    mainThread.join(5000);  // wait for the main thread's session close
                } catch (InterruptedException e) {
                    // nothing more we can do; the JVM is halting
                }
            }));
            awaitMessages();
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
        properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);  // subscribe Direct subs after reconnect
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
                        System.out.println("Session reconnecting; direct delivery is paused until re-established");
                        break;
                    case RECONNECTED:   // automatic reconnect succeeded, session re-established
                        System.out.println("Session reconnected; the subscription is restored and delivery resumes");
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

        // Anonymous inner-class for MessageListener, this demonstrates the async threaded message callback
        final XMLMessageConsumer consumer = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage message) {
                // do not print anything to console... too slow!
                msgRecvCounter++;
                // do some message processing here... validate the payload, increment some counters, update some graphics, trigger another event
                if (message.getDiscardIndication()) {  // since Direct messages, check if there have been any lost any messages
                    // If the consumer is being over-driven (i.e. publish rates too high), the broker might discard some messages for this consumer
                    // check this flag to know if that's happened
                    // to avoid discards:
                    //  a) reduce publish rate
                    //  b) use multiple-threads or shared subscriptions for parallel processing
                    //  c) increase size of consumer's D-1 egress buffers (check client-profile) (helps more with bursts)
                    hasDetectedDiscard = true;  // set my own flag
                }
            }

            @Override
            public void onException(JCSMPException e) {  // uh oh!
                System.out.printf("### MessageListener's onException(): %s%n",e);
                if (e instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                }
            }
        });

        session.addSubscription(JCSMPFactory.onlyInstance().createTopic(TOPIC_PREFIX + "*/direct/>"));
        // add more subscriptions here if you want
        consumer.start();
        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] or Ctrl-C to quit.");
    }

    private static void awaitMessages() throws IOException, InterruptedException {
        while (System.in.available() == 0 && !isShutdown) {
            Thread.sleep(1000);  // wait 1 second
            System.out.printf("%s %s Received msgs/s: %,d%n",API,SAMPLE_NAME,msgRecvCounter);  // simple way of calculating message rates
            msgRecvCounter = 0;
            if (hasDetectedDiscard) {
                System.out.println("*** Egress discard detected *** : "
                        + SAMPLE_NAME + " unable to keep up with full message rate");
                hasDetectedDiscard = false;  // only show the error once per second
            }
        }
        isShutdown = true;
    }

    private static void teardownSolace() {
        // teardownSolace() runs in main's finally on EVERY exit path (normal quit, ENTER,
        // SIGINT, DOWN_ERROR, or an exception), so Solace teardown is never skipped.
        isShutdown = true;
        // direct is at-most-once: no acknowledgements to drain before exit, so close directly
        if (session != null) {
            session.closeSession();  // will also close consumer object
        }
    }
}
