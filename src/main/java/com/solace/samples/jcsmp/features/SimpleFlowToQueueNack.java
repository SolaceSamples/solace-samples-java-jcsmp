/**
 * SimpleFlowToQueueNack.java
 * 
 * As SimpleFlowToQueue, this sample demonstrates how to create a Flow to a
 * durable or temporary Queue, and the use of a client acknowledgment of messages.
 * 
 * It also demonstrates how to handle:
 * 1. Automatic reconnections
 * 2. Non Ack messages
 * 3. Duplicates
 * 4. Messages that can't be Ack or Non-Ack
 * 
 * Usage:
 * ./AdvancedFlowToQueue -h tcp://localhost:55555 -u default@default -w default -d -q queue
 * 
 * To test:
 * Configure a queue with Maximum Redelivery Count: 1
 * Create its DMQ
 * 
 * Copyright 2009-2023 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SecureSessionConfiguration;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessage.Outcome;

public class SimpleFlowToQueueNack extends SampleApp implements XMLMessageListener {
    private SecureSessionConfiguration conf = null;
    private FlowReceiver receiver = null;
    private int msgCounter = 1;

    // remember to add log4j2.xml to your classpath
    private static final Logger logger = LogManager.getLogger(); // log4j2, but could also use SLF4J, JCL, etc.

    // XMLMessageListener
    public void onException(JCSMPException exception) {
        exception.printStackTrace();
    }

    /*
     * XMLMessageListener
     * The application should ensure that the callback method return promptly, so
     * that the calling thread is not blocked from processing subsequent
     * messages.
     */
    public void onReceive(BytesXMLMessage message) {
        try {
            /*
             * A message can be redelivered by the event broker, and may be
             * already processed by the consumer. In this case it would be a
             * duplicate. C.f.
             * https://docs.solace.com/API/API-Developer-Guide/Java-API-Best-Practices.htm#
             * Handling
             * E.g. 1) In case the consumer inserts the events into a database,
             * to avoid duplicates, it can check if they are already present, and
             * thus, either do nothing or overwrite the records.
             * E.g. 2) During a failover, the ongoing ACKs may not be received by
             * the event broker, which will subsequently redeliver them once the
             * consumer reconnects
             */
            if (message.getRedelivered()) {
                logger.info("Message redelivered");
            }

            // For the demo, we ack one msg every 3 msgs received
            if (msgCounter % 3 == 0) { // We ACK every 3 messages
                logger.info("Received Message: " + msgCounter + "\n" + message.dump());
                message.ackMessage();
                msgCounter++;
            } else {
                if (msgCounter % 3 == 2) { // We NACK FAILED every 3 messages
                    nackFailed(message); // On the queue stats "Messages Redelivered" will be incremented
                } else {
                    // Message is directly pushed to the DMQ
                    nackRejected(message); // We NACK REJECTED every 3 messages
                }
            }
        } catch (Exception e) {
            logger.error("Encountered an Exception, message will be NACK" + e.getMessage());
            nackFailed(message);
        }
    }

    /*
     * This Nack notifies the event broker that your client application did not
     * process the message. When the event broker receives Nack FAILED it
     * attempts to redeliver the message while adhering to delivery count limits
     *
     * E.g. The consumer attempts to insert the events into a backend, which is
     * temporarily unavailable
     */
    private void nackFailed(BytesXMLMessage message) {
        try {
            logger.info("Message NACK FAILED: " + msgCounter);
            message.settle(Outcome.FAILED); // Failed to process message, settle as FAILED
            msgCounter++;
        } catch (JCSMPException e1) {
            logger.error("Encountered a JCSMPException" + e1.getMessage());
        }
    }

    /*
     * This Nack notifies the event broker that your client application could
     * process the message but it was not accepted (for example, failed
     * validation). When the event broker receives this Nack it removes the
     * message from its queue and then moves the message to the Dead Message
     * Queue (DMQ) if it is configured.
     */
    private void nackRejected(BytesXMLMessage message) {
        try {
            logger.info("Message NACK REJECTED: " + msgCounter);
            message.settle(Outcome.REJECTED); // Failed to process message, settle as REJECTED
            msgCounter++;
        } catch (JCSMPException e1) {
            logger.error("Encountered a JCSMPException" + e1.getMessage());
        }
    }

    /*
     * In very special cases if you lost the context, and are not able to ACK of
     * NACK a message anymore, you will have to rebind the flow, so the broker
     * can redeliver the message.
     */
    private void flowRebind() throws JCSMPException {
        receiver.close();
        receiver.start();
    }

    void createSession(String[] args)
            throws InvalidPropertiesException, IOException, InterruptedException, ExecutionException {
        // Parse command-line arguments
        ArgParser parser = new ArgParser();
        if (parser.parseSecureSampleArgs(args) == 0)
            conf = (SecureSessionConfiguration) parser.getConfig();
        else
            printUsage();

        JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, conf.getHost());
        if (conf.getRouterUserVpn().get_vpn() != null) {
            properties.setProperty(JCSMPProperties.VPN_NAME, conf.getRouterUserVpn().get_vpn());
        }

        if (conf.getRouterUserVpn() != null) {
            if (!conf.getRouterUserVpn().get_user().trim().equals("")) {
                properties.setProperty(JCSMPProperties.USERNAME, conf.getRouterUserVpn().get_user());
            }
            if (conf.getRouterUserVpn().get_vpn() != null) {
                properties.setProperty(JCSMPProperties.VPN_NAME, conf.getRouterUserVpn().get_vpn());
            }
        }
        properties.setProperty(JCSMPProperties.PASSWORD, conf.getRouterPassword());

        /*
         * SUPPORTED_MESSAGE_ACK_CLIENT means that the received messages on the Flow
         * must be explicitly acknowledged, otherwise the messages are redelivered to
         * the client
         * when the Flow reconnects.
         * SUPPORTED_MESSAGE_ACK_CLIENT is used here to simply to show
         * SUPPORTED_MESSAGE_ACK_CLIENT. Clients can use SUPPORTED_MESSAGE_ACK_AUTO
         * instead to automatically acknowledge incoming Guaranteed messages.
         */
        properties.setProperty(JCSMPProperties.MESSAGE_ACK_MODE, JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

        // Disable certificate checking
        properties.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);

        // Channel properties
        JCSMPChannelProperties cp = (JCSMPChannelProperties) properties
                .getProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES);
        if (conf.isCompression()) {
            /*
             * Compression is set as a number from 0-9. 0 means
             * "disable compression" (the default) and 9 means max compression.
             * Selecting a non-zero compression level auto-selects the
             * compressed SMF port on the appliance, as long as no SMF port is
             * explicitly specified.
             */
            cp.setCompressionLevel(9);
        }

        // This is a best practice
        // c.f.
        // https://docs.solace.com/API/API-Developer-Guide/Configuring-Connection-T.htm
        cp.setConnectRetries(1);
        cp.setReconnectRetries(20);
        cp.setReconnectRetryWaitInMillis(3000);
        cp.setConnectRetriesPerHost(5);
        session = JCSMPFactory.onlyInstance().createSession(properties);
    }

    void printUsage() {
        StringBuffer buf = new StringBuffer();
        buf.append(ArgParser.getSecureArgUsage());
        logger.info(buf.toString());
        finish(1);
    }

    public void run(String[] args) {
        try {
            // Create the Session.
            createSession(args);

            boolean useDurable = false;
            useDurable = (conf.getArgBag().get("-d") != null || conf.getArgBag().get("--durable") != null);

            String queueName = SampleUtils.SAMPLE_QUEUE;
            if (conf.getArgBag().get("-q") != null) {
                queueName = conf.getArgBag().get("-q");
            }

            session.connect();

            // Create a Queue to receive messages.
            Queue queue;

            // Enable Nacks
            // A session has already been created with
            // JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT
            // Add the settlement outcomes - Outcome.ACCEPTED does not need to be added
            // because it is always included
            // The consumer can add multiple outcomes, for example
            // cfp.addRequiredSettlementOutcomes(Outcome.FAILED, Outcome.REJECTED)
            final ConsumerFlowProperties flowProp = new ConsumerFlowProperties();
            flowProp.addRequiredSettlementOutcomes(Outcome.FAILED);
            flowProp.addRequiredSettlementOutcomes(Outcome.REJECTED);

            if (useDurable) {
                // Create a durable Queue to receive messages.
                queue = JCSMPFactory.onlyInstance().createQueue(queueName);
                // Create a receiver.
                flowProp.setEndpoint(queue);
                receiver = session.createFlow(this, flowProp);
            } else {
                // Create a temporary Queue to receive messages.
                queue = session.createTemporaryQueue();

                // Provision the temporary Queue and create a receiver
                flowProp.setEndpoint(queue);

                EndpointProperties endpointProp = new EndpointProperties(EndpointProperties.ACCESSTYPE_EXCLUSIVE,
                        500000, EndpointProperties.PERMISSION_MODIFY_TOPIC, 100);
                endpointProp.setMaxMsgRedelivery(15);
                endpointProp.setDiscardBehavior(EndpointProperties.DISCARD_NOTIFY_SENDER_ON);

                receiver = session.createFlow(this, flowProp, endpointProp);
            }

            // Start the receiver
            receiver.start();

            System.out.println("Press ENTER to stop");
            Scanner in = new Scanner(System.in);
            if (in.hasNextLine()) {
                System.out.println("Closing the connection");
            }
            in.close();

            // Close the receiver.
            receiver.close();
            finish(0);
        } catch (JCSMPTransportException ex) {
            logger.error("Encountered a JCSMPTransportException, closing receiver... " + ex.getMessage());
            if (receiver != null) {
                receiver.close();
                // At this point the receiver handle is unusable; a new one
                // should be created.
            }
            finish(1);
        } catch (JCSMPException ex) {
            logger.error("Encountered a JCSMPException, closing receiver... " + ex.getMessage());
            // Possible causes:
            // - Authentication error: invalid username/password
            // - Invalid or unsupported properties specified
            if (receiver != null) {
                receiver.close();
                // At this point the receiver handle is unusable; a new one
                // should be created.
            }
            finish(1);
        } catch (Exception ex) {
            logger.error("Encountered an Exception... " + ex.getMessage());
            finish(1);
        }
    }

    public static void main(String[] args) {
        SimpleFlowToQueueNack app = new SimpleFlowToQueueNack();
        app.run(args);
    }
}