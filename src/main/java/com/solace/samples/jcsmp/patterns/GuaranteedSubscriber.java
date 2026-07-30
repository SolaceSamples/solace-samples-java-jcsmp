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

import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowEventArgs;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuaranteedSubscriber {

    private static final String SAMPLE_NAME = GuaranteedSubscriber.class.getSimpleName();
    private static final String QUEUE_NAME = "q_jcsmp_sub";
    // topic to map onto the queue; matches the publisher sample's topic root
    private static final String TOPIC_NAME = "solace/samples/jcsmp/pers/pub/>";
    private static final String API = "JCSMP";

    private static volatile int msgRecvCounter = 0;                 // num messages received
    private static volatile boolean hasDetectedRedelivery = false;  // detected any messages being redelivered?
    private static volatile boolean isShutdown = false;             // are we done?
    private static FlowReceiver flowQueueReceiver;
    private static JCSMPSession session;

    // remember to add log4j2.xml to your classpath
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.

    /** This is the main app.  Use this type of app for receiving Guaranteed messages (e.g. via a queue endpoint). */
    public static void main(String... args) throws JCSMPException, InterruptedException, IOException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");
        try {
            if (!setupSolace(args)) {
                return;
            }
            // graceful shutdown: a SIGINT (Ctrl-C) signals the main loop to stop, then the
            // hook joins the main thread so the cleanup in teardownSolace() (stop the flow,
            // finish outstanding ACKs, close the session) runs to completion before the JVM
            // halts. The JVM exits as soon as all shutdown hooks return, so the hook must
            // wait, not just set a flag.
            final Thread mainThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown signal received, stopping consumer...");
                isShutdown = true;
                try {
                    mainThread.join(5000);  // wait for the main thread's flow stop, ACK drain, and session close
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

    private static boolean setupSolace(String[] args) throws JCSMPException {
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1]);     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);      // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
        }
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);
        // re-run idempotency: addSubscription() on the queue below throws
        // "Subscription Already Exists" on every run after the first unless the session
        // ignores duplicate-subscription errors (this property defaults to false)
        properties.setProperty(JCSMPProperties.IGNORE_DUPLICATE_SUBSCRIPTION_ERROR, true);
        // best practice: register a session event handler at session creation and handle
        // each event appropriately, rather than only logging it
        session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {
                logger.info("### Received a Session event: " + event);
                switch (event.getEvent()) {
                    case RECONNECTING:  // session went down, automatic reconnect attempt in progress
                        logger.warn("Session reconnecting; message delivery is paused until re-established");
                        break;
                    case RECONNECTED:   // automatic reconnect succeeded, session re-established
                        logger.info("Session reconnected; the flow rebinds and delivery resumes");
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

        // configure the queue API object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(QUEUE_NAME);

        // provision the durable queue in-process and map the topic onto it at startup, so a
        // fresh broker works out of the box (rather than erroring out if the queue is absent).
        // best practice: confirm the broker allows client-side endpoint management first
        if (!session.isCapable(CapabilityType.ENDPOINT_MANAGEMENT)) {
            System.err.println("This client/broker does not allow client-side endpoint management; "
                    + "provision the queue out-of-band or grant the capability. Exiting.");
            return false;  // teardownSolace() in main's finally closes the session
        }
        EndpointProperties endpointProps = new EndpointProperties();
        endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);  // single-consumer sample
        endpointProps.setPermission(EndpointProperties.PERMISSION_CONSUME);
        // provision the durable queue idempotently so a re-run is safe
        session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
        // map the topic onto the queue with a subscription (pub/sub onto a queue);
        // WAIT_FOR_CONFIRM blocks until the broker confirms the subscription is in place
        Topic topic = JCSMPFactory.onlyInstance().createTopic(TOPIC_NAME);
        session.addSubscription(queue, topic, JCSMPSession.WAIT_FOR_CONFIRM);

        // Create a Flow be able to bind to and consume messages from the Queue.
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);  // best practice
        flow_prop.setActiveFlowIndication(true);  // Flow events will advise when active/inactive

        System.out.printf("Attempting to bind to queue '%s' on the broker.%n", QUEUE_NAME);
        // best practice: register a flow event handler at flow creation and handle each
        // event appropriately, rather than only logging it
        // see bottom of file for QueueFlowListener class, which receives the messages from the queue
        flowQueueReceiver = session.createFlow(new QueueFlowListener(), flow_prop, null, new FlowEventHandler() {
            @Override
            public void handleEvent(Object source, FlowEventArgs event) {
                logger.info("### Received a Flow event: " + event);
                switch (event.getEvent()) {
                    case FLOW_ACTIVE:        // bound and actively receiving from the queue
                        logger.info("Flow active: consuming from queue " + QUEUE_NAME);
                        break;
                    case FLOW_INACTIVE:      // flow is up but this consumer is not the active one
                        logger.warn("Flow inactive: bound but not receiving (another consumer holds the active flow on this exclusive queue)");
                        break;
                    case FLOW_RECONNECTING:  // flow unbound (e.g. queue disabled); API is rebinding
                        logger.warn("Flow reconnecting: delivery paused while the API rebinds");
                        break;
                    case FLOW_RECONNECTED:   // flow rebind succeeded, delivery resumes
                        logger.info("Flow reconnected: delivery resumed");
                        break;
                    case FLOW_UP:            // the flow is established
                        logger.info("Flow up: bound to queue " + QUEUE_NAME);
                        break;
                    case FLOW_DOWN:          // the flow was established and then went down
                        logger.error("Flow down: delivery from queue " + QUEUE_NAME + " has stopped");
                        // decide here whether to recreate the flow or shut down; this sample
                        // shuts down so teardownSolace() runs in main's finally
                        isShutdown = true;
                        break;
                    default:
                        break;
                }
                // try disabling and re-enabling the queue to see these events in action
            }
        });
        return true;
    }

    private static void awaitMessages() throws JCSMPException, IOException, InterruptedException {
        // tell the broker to start sending messages on this queue receiver
        flowQueueReceiver.start();
        // async queue receive working now, so time to wait until done...
        System.out.println(SAMPLE_NAME + " connected, and running. Press [ENTER] or Ctrl-C to quit.");
        while (System.in.available() == 0 && !isShutdown) {
            Thread.sleep(1000);  // wait 1 second
            System.out.printf("%s %s Received msgs/s: %,d%n",API,SAMPLE_NAME,msgRecvCounter);  // simple way of calculating message rates
            msgRecvCounter = 0;
            if (hasDetectedRedelivery) {  // try shutting -> enabling the queue on the broker to see this
                System.out.println("*** Redelivery detected ***");
                hasDetectedRedelivery = false;  // only show the error once per second
            }
        }
        isShutdown = true;
    }

    private static void teardownSolace() {
        // teardownSolace() runs in main's finally on EVERY exit path (normal quit, ENTER,
        // SIGINT, DOWN_ERROR, or an exception), so Solace teardown is never skipped.
        isShutdown = true;
        if (flowQueueReceiver != null) {
            // gracefully stop the flow and finish outstanding ACKs before exit
            flowQueueReceiver.stop();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // preserve interrupt; still close the session below
            }
        }
        if (session != null) {
            session.closeSession();  // will also close consumer object
        }
    }

    ////////////////////////////////////////////////////////////////////////////

    /** Very simple static inner class, used for receives messages from Queue Flows. **/
    private static class QueueFlowListener implements XMLMessageListener {

        @Override
        public void onReceive(BytesXMLMessage msg) {
            msgRecvCounter++;
            // Payload lives in the binary attachment: the publisher writes it with setData(),
            // so read it with getData(), NOT getBytes() (the separate, empty XML-content part).
            if (msg instanceof BytesMessage) {
                logger.debug(API + " " + SAMPLE_NAME + " received: "
                        + new String(((BytesMessage) msg).getData(), StandardCharsets.UTF_8));
            }
            if (msg.getRedelivered()) {  // useful check
                // this is the broker telling the consumer that this message has been sent and not ACKed before.
                // this can happen if an exception is thrown, or the broker restarts, or the netowrk disconnects
                // perhaps an error in processing? Should do extra checks to avoid duplicate processing
                hasDetectedRedelivery = true;
            }
            // Messages are removed from the broker queue when the ACK is received.
            // Therefore, DO NOT ACK until all processing/storing of this message is complete.
            // NOTE that messages can be acknowledged from a different thread.
            msg.ackMessage();  // ACKs are asynchronous
        }

        @Override
        public void onException(JCSMPException e) {
            logger.warn("### Queue " + QUEUE_NAME + " Flow handler received exception.  Stopping!!", e);
            if (e instanceof JCSMPTransportException) {  // all reconnect attempts failed
                isShutdown = true;  // let's quit; or, could initiate a new connection attempt
            } else {
                if (e instanceof JCSMPErrorResponseException) {  // broker error response carries extra detail
                    JCSMPErrorResponseException ere = (JCSMPErrorResponseException) e;
                    logger.warn("Specifics: " + JCSMPErrorResponseSubcodeEx.getSubcodeAsString(ere.getSubcodeEx()) + ": " + ere.getResponsePhrase());
                }
                // Generally unrecoverable exception, probably need to recreate and restart the flow
                flowQueueReceiver.close();
                // add logic in main thread to restart FlowReceiver, or can exit the program
            }
        }
    }
}
