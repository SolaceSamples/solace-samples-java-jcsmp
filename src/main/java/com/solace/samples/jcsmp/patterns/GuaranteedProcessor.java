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
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.FlowEventArgs;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProducerEventHandler;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.OperationNotSupportedException;
import com.solacesystems.jcsmp.ProducerEventArgs;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuaranteedProcessor {

    private static final String SAMPLE_NAME = GuaranteedProcessor.class.getSimpleName();
    static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JCSMP";
    private static final int PUBLISH_WINDOW_SIZE = 100;
    private static final String QUEUE_NAME = "q_jcsmp_processor";
    
    private static volatile int msgSentCounter = 0;                 // num messages sent
    private static volatile int msgRecvCounter = 0;                 // num messages received
    private static volatile boolean isShutdown = false;             // are we done?
    private static FlowReceiver flowQueueReceiver;

    // remember to add log4j2.xml to your classpath
    private static final Logger logger = LogManager.getLogger();  // log4j2, but could also use SLF4J, JCL, etc.

    /** This is the main app.  Use this type of app for receiving Guaranteed messages (e.g. via a queue endpoint),
     *  doing some processing (translation, decoration, etc.) and then republishing to a new destination. */
    public static void main(String... args) throws JCSMPException, InterruptedException, IOException {
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
        properties.setProperty(JCSMPProperties.PUB_ACK_WINDOW_SIZE, PUBLISH_WINDOW_SIZE);
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);
        final JCSMPSession session;
        session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {  // could be reconnecting, connection lost, etc.
                logger.info("### Received a Session event: " + event);
            }
        });
        session.connect();
        
        XMLMessageProducer producer = session.getMessageProducer(new PublishCallbackHandler(), new JCSMPProducerEventHandler() {
            @Override
            public void handleEvent(ProducerEventArgs event) {
                // as of v10.10, this event only occurs when republishing unACKed messages on an unknown flow (DR failover)
                logger.info("*** Received a producer event: " + event);
            }
        });

        // configure the queue API object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(QUEUE_NAME);
        // Create a Flow be able to bind to and consume messages from the Queue.
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);  // best practice
        flow_prop.setActiveFlowIndication(true);  // Flow events will advise when 

        System.out.printf("Attempting to bind to queue '%s' on the broker.%n", QUEUE_NAME);
        try {
            // passing null for Listener (1st param), so example of using a blocking SYNChronous receive(), 
            flowQueueReceiver = session.createFlow(null, flow_prop, null, new FlowEventHandler() {
                @Override
                public void handleEvent(Object source, FlowEventArgs event) {
                    // Flow events are usually: active, reconnecting (i.e. unbound), reconnected
                    logger.info("### Received a Flow event: " + event);
                }
            });
        } catch (OperationNotSupportedException e) {  // not allowed to do this
            throw e;
        } catch (JCSMPErrorResponseException e) {  // something else went wrong: queue not exist, queue shutdown, etc.
            logger.error(e);
            System.err.printf("%n*** Could not establish a connection to queue '%s': %s%n", QUEUE_NAME, e.getMessage());
            System.err.println("Create queue using PubSub+ Manager WebGUI, and add subscription "+TOPIC_PREFIX+"*/pers/pub/>");
            System.err.println("  or see the SEMP CURL scripts inside the 'semp-rest-api' directory.");
            // could also try to retry, loop and retry until successfully able to connect to the queue
            System.err.println("NOTE: see QueueProvision sample for how to construct queue with consumer app.");
            System.err.println("Exiting.");
            return;
        }
        // tell the broker to start sending messages on this queue receiver
        flowQueueReceiver.start();
        // sync/blocking queue receive working now, so time to wait until done...
        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        System.out.println(" * Remember to modify the queue topic subscriptions to match Publisher and Processor");
        BytesXMLMessage inboundMsg;

        // make a thread for printing message rate stats
        ScheduledExecutorService statsPrintingThread = Executors.newSingleThreadScheduledExecutor();
        statsPrintingThread.scheduleAtFixedRate(() -> {
            System.out.printf("%s %s Received -> Published msgs/s: %,d -> %,d%n",
                    API, SAMPLE_NAME, msgRecvCounter, msgSentCounter);  // simple way of calculating message rates
            msgRecvCounter = 0;
            msgSentCounter = 0;
        }, 1, 1, TimeUnit.SECONDS);
        
        while (System.in.available() == 0 && !isShutdown) {
            inboundMsg = flowQueueReceiver.receive(1000);  // blocking receive a message
            if (inboundMsg == null) {  // receive() either got interrupted, or timed out
                continue;
            }
            msgRecvCounter++;
            String inboundTopic = inboundMsg.getDestination().getName();
            if (inboundTopic.matches(TOPIC_PREFIX + ".+?/pers/pub/.*")) {  // use of regex to match variable API level
                // how to "process" the incoming message? maybe do a DB lookup? add some additional properties? or change the payload?
                TextMessage outboundMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
                final String upperCaseTopic = inboundTopic.toUpperCase();  // as a silly example of "processing"
                outboundMsg.setText(upperCaseTopic);
                if (inboundMsg.getApplicationMessageId() != null) {
                    outboundMsg.setApplicationMessageId(inboundMsg.getApplicationMessageId());  // populate for traceability
                }
                outboundMsg.setDeliveryMode(DeliveryMode.PERSISTENT);
                outboundMsg.setCorrelationKey(new ProcessorCorrelationKey(inboundMsg, outboundMsg));  // need to wait for publish ACK
                String [] inboundTopicLevels = inboundTopic.split("/",6);
                String onwardsTopic = new StringBuilder(TOPIC_PREFIX).append("jcsmp/pers/upper/").append(inboundTopicLevels[5]).toString();
                try {
                    producer.send(outboundMsg, JCSMPFactory.onlyInstance().createTopic(onwardsTopic));
                    msgSentCounter++;
                } catch (JCSMPException e) {  // threw from send(), only thing that is throwing here, but keep trying (unless shutdown?)
                    System.out.printf("### Caught while trying to producer.send(): %s%n",e);
                    if (e instanceof JCSMPTransportException) {  // all reconnect attempts failed
                        isShutdown = true;  // let's quit; or, could initiate a new connection attempt
                    }
                }
            } else {  // unexpected. either log or something
                logger.info("Received an unexpected message with topic "+inboundTopic+".  Ignoring");
                inboundMsg.ackMessage();
            }
        }
        isShutdown = true;
        flowQueueReceiver.stop();
        statsPrintingThread.shutdown();  // stop printing stats
        Thread.sleep(1500);  // give time for the ACKs to arrive to/from the broker
        session.closeSession();  // will also close consumer object
        System.out.println("Main thread quitting.");
    }

    
    ////////////////////////////////////////////////////////////////////////////
    
    /** Hold onto both messages, wait for outbound ACK to come back, and then ACK inbound message */
    private static class ProcessorCorrelationKey {
        
        private final BytesXMLMessage inboundMsg;
        private final BytesXMLMessage outboundMsg;
        
        private ProcessorCorrelationKey(BytesXMLMessage inboundMsg, BytesXMLMessage outboundMsg) {
            this.inboundMsg = inboundMsg;
            this.outboundMsg = outboundMsg;
        }
    }
    
    /** Very simple static inner class, used for handling publish ACKs/NACKs from broker. **/
    private static class PublishCallbackHandler implements JCSMPStreamingPublishCorrelatingEventHandler {

        @Override
        public void responseReceivedEx(Object key) {
            assert key != null;  // this shouldn't happen, this should only get called for an ACK
            assert key instanceof ProcessorCorrelationKey;
            ProcessorCorrelationKey ck = (ProcessorCorrelationKey)key;
            ck.inboundMsg.ackMessage();  // ONLY ACK inbound msg of my queue once outbound msg is Guaranteed
            logger.debug(String.format("ACK for Message %s", ck));  // good enough, the broker has it now
        }
        
        @Override
        public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
            if (key != null) {  // NACK
                assert key instanceof ProcessorCorrelationKey;
                ProcessorCorrelationKey ck = (ProcessorCorrelationKey)key;
                logger.warn(String.format("NACK for Message %s - %s", ck.outboundMsg, cause));
                // probably want to do something here.  some error handling possibilities:
                //  - send the message again
                //  - send it somewhere else (error handling queue?)
                //  - log and continue
                //  - pause and retry (backoff) - maybe set a flag to slow down the publisher
            } else {  // not a NACK, but some other error (ACL violation, connection loss, ...)
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
