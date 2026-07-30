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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProducerEventHandler;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPReconnectEventHandler;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.ProducerEventArgs;
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class GuaranteedPublisher {

    private static final String SAMPLE_NAME = GuaranteedPublisher.class.getSimpleName();
    static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JCSMP";
    private static final int PUBLISH_WINDOW_SIZE = 50;
    private static final int APPROX_MSG_RATE_PER_SEC = 100;
    private static final int PAYLOAD_SIZE = 512;

    // remember to add log4j2.xml to your classpath
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.

    private static volatile int msgSentCounter = 0;                   // num messages sent
    private static volatile boolean isShutdown = false;
    private static volatile boolean isConnected = true;               // tracks transport state via reconnect events
    private static JCSMPSession session;
    private static XMLMessageProducer producer;
    private static ScheduledExecutorService statsPrintingThread;

    /** Main. */
    public static void main(String... args) throws JCSMPException, IOException, InterruptedException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");
        try {
            setupSolace(args);
            // graceful shutdown: a SIGINT (Ctrl-C) signals the main loop to stop, then the
            // hook joins the main thread so the cleanup in teardownSolace() (finish
            // outstanding ACKs, close the session) runs to completion before the JVM halts.
            // The JVM exits as soon as all shutdown hooks return, so the hook must wait,
            // not just set a flag.
            final Thread mainThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown signal received, stopping publisher...");
                isShutdown = true;
                try {
                    mainThread.join(5000);  // wait for the main thread's ACK drain and session close
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
        properties.setProperty(JCSMPProperties.PUB_ACK_WINDOW_SIZE, PUBLISH_WINDOW_SIZE);
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
                logger.info("### Received a Session event: " + event);
                switch (event.getEvent()) {
                    case RECONNECTING:  // session went down, automatic reconnect attempt in progress
                        isConnected = false;
                        break;
                    case RECONNECTED:   // automatic reconnect succeeded, session re-established
                        isConnected = true;
                        break;
                    case DOWN_ERROR:    // session was up and then went down; reconnects exhausted
                        logger.error("Session is down and reconnect attempts are exhausted, quitting.");
                        // the session will not recover; end the main loop so teardownSolace()
                        // runs in main's finally
                        isShutdown = true;
                        break;
                    default:
                        break;
                }
            }
        });
        session.connect();

        producer = session.getMessageProducer(new PublishCallbackHandler(), new JCSMPProducerEventHandler() {
            @Override
            public void handleEvent(ProducerEventArgs event) {
                // as of JCSMP v10.10, this event only occurs when republishing unACKed messages on an unknown flow (DR failover)
                logger.info("*** Received a producer event: " + event);
            }
        });

        // best practice for publish-only applications: transport reconnect events are only
        // exposed through a consumer, so acquire an empty one (never started) and register a
        // reconnect event handler to pause publishing while the connection is re-established
        session.getMessageConsumer(new JCSMPReconnectEventHandler() {
            @Override
            public boolean preReconnect() throws JCSMPException {
                logger.info("### preReconnect(): transport down, pausing publishing");
                isConnected = false;
                return true;  // true means let the API proceed with its reconnect attempts
            }

            @Override
            public void postReconnect() throws JCSMPException {
                logger.info("### postReconnect(): transport restored, resuming publishing");
                isConnected = true;
            }
        }, null);  // no message listener: this consumer exists only to surface transport events
    }

    private static void runPublishLoop() throws JCSMPException, IOException, InterruptedException {
        statsPrintingThread = Executors.newSingleThreadScheduledExecutor();
        statsPrintingThread.scheduleAtFixedRate(() -> {
            System.out.printf("%s %s Published msgs/s: %,d%n",API,SAMPLE_NAME,msgSentCounter);  // simple way of calculating message rates
            msgSentCounter = 0;
        }, 1, 1, TimeUnit.SECONDS);

        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] or Ctrl-C to quit.");
        byte[] payload = new byte[PAYLOAD_SIZE];  // preallocate
        BytesMessage message = JCSMPFactory.onlyInstance().createMessage(BytesMessage.class);  // preallocate
        System.out.println("Publishing to topic '"+ TOPIC_PREFIX + API.toLowerCase() +
                "/pers/pub/...', please ensure queue has matching subscription.");
        while (System.in.available() == 0 && !isShutdown) {  // loop until ENTER pressed, or shutdown flag
            if (!isConnected) {  // transport is down: wait for the API's automatic reconnect
                Thread.sleep(100);
                continue;
            }
            message.reset();  // ready for reuse
            // each loop, change the payload as an example
            char chosenCharacter = (char)(Math.round(msgSentCounter % 26) + 65);  // choose a "random" letter [A-Z]
            Arrays.fill(payload,(byte)chosenCharacter);  // fill the payload completely with that char
            // use a BytesMessage this sample, instead of TextMessage
            message.setData(payload);
            message.setDeliveryMode(DeliveryMode.PERSISTENT);  // required for Guaranteed
            String msgId = UUID.randomUUID().toString();
            message.setApplicationMessageId(msgId);  // as an example
            // as another example, let's define a user property!
            SDTMap map = JCSMPFactory.onlyInstance().createMap();
            map.putString("sample",API + "_" + SAMPLE_NAME);
            message.setProperties(map);
            // correlation key for local ACK/NACK correlation: use an immutable per-send
            // identifier, NOT the reused message object, which this loop keeps mutating
            // while up to PUBLISH_WINDOW_SIZE sends are still awaiting their ACK
            message.setCorrelationKey(msgId);
            String topicString = new StringBuilder(TOPIC_PREFIX).append(API.toLowerCase())
            		.append("/pers/pub/").append(chosenCharacter).toString();
            // NOTE: publishing to topic, so make sure GuaranteedSubscriber queue is subscribed to same topic,
            //       or enable "Reject Message to Sender on No Subscription Match" the client-profile
            Topic topic = JCSMPFactory.onlyInstance().createTopic(topicString);
            try {
                producer.send(message, topic);
                msgSentCounter++;
            } catch (JCSMPException e) {  // threw from send(), only thing that is throwing here, but keep trying (unless shutdown?)
                logger.warn("### Caught while trying to producer.send()",e);
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
        if (session != null) {
            // gracefully finish outstanding ACKs before exit: give the broker time to
            // confirm in-flight PERSISTENT messages before the session is closed
            try {
                Thread.sleep(1500);  // give time for the ACKs to arrive from the broker
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // preserve interrupt; still close the session below
            }
            session.closeSession();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    
    /** Very simple static inner class, used for handling publish ACKs/NACKs from broker. **/
    private static class PublishCallbackHandler implements JCSMPStreamingPublishCorrelatingEventHandler {

        @Override
        public void responseReceivedEx(Object key) {
            assert key != null;  // this shouldn't happen, this should only get called for an ACK
            logger.debug(String.format("ACK for Message %s", key));  // good enough, the broker has it now
        }

        @Override
        public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
            if (key != null) {  // NACK
                logger.warn(String.format("NACK for Message %s - %s", key, cause));
                // probably want to do something here.  some error handling possibilities:
                //  - send the message again
                //  - send it somewhere else (error handling queue?)
                //  - log and continue
                //  - pause and retry (backoff) - maybe set a flag to slow down the publisher
            } else {  // not a NACK, but some other error (ACL violation, connection loss, message too big, ...)
                logger.warn("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPTransportException) {  // all reconnect attempts failed
                    isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                } else if (cause instanceof JCSMPErrorResponseException) {  // might have some extra info
                    JCSMPErrorResponseException e = (JCSMPErrorResponseException)cause;
                    logger.warn("Specifics: " + JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e.getSubcodeEx()) + ": " + e.getResponsePhrase());
                }
            }
        }
    }
}
