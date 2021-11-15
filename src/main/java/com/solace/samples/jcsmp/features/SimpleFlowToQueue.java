/**
 * SimpleFlowToQueue.java
 * 
 * This sample demonstrates how to create a Flow to a durable or temporary Queue, 
 * and the use of a client acknowledgment of messages.
 * 
 * For the case of a durable Queue, this sample requires that a durable Queue 
 * called 'my_sample_queue' be provisioned on the appliance with at least 
 * 'Consume' permissions.
 * 
 * Copyright 2009-2021 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solace.samples.jcsmp.features.common.SessionConfiguration.AuthenticationScheme;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class SimpleFlowToQueue extends SampleApp implements XMLMessageListener, JCSMPStreamingPublishCorrelatingEventHandler {
    SessionConfiguration conf = null;

    // XMLMessageListener
    public void onException(JCSMPException exception) {
        exception.printStackTrace();
    }

    // XMLMessageListener
    public void onReceive(BytesXMLMessage message) {
        System.out.println("Received Message:\n" + message.dump());
        message.ackMessage();
    }

    // JCSMPStreamingPublishCorrelatingEventHandler
    public void handleErrorEx(Object key, JCSMPException cause,
            long timestamp) {
        cause.printStackTrace();
    }

    // JCSMPStreamingPublishCorrelatingEventHandler
    public void responseReceivedEx(Object key) {        
    }

    void createSession(String[] args) throws InvalidPropertiesException {
        // Parse command-line arguments
        ArgParser parser = new ArgParser();
        if (parser.parse(args) == 0)
            conf = parser.getConfig();
        else
            printUsage(parser.isSecure());

        JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, conf.getHost());
        properties.setProperty(JCSMPProperties.USERNAME, conf.getRouterUserVpn().get_user());
        if (conf.getRouterUserVpn().get_vpn() != null) {
            properties.setProperty(JCSMPProperties.VPN_NAME, conf.getRouterUserVpn().get_vpn());
        }
        properties.setProperty(JCSMPProperties.PASSWORD, conf.getRouterPassword());
        /*
         *  SUPPORTED_MESSAGE_ACK_CLIENT means that the received messages on the Flow 
         *  must be explicitly acknowledged, otherwise the messages are redelivered to the client
         *  when the Flow reconnects.
         *  SUPPORTED_MESSAGE_ACK_CLIENT is used here to simply to show
         *  SUPPORTED_MESSAGE_ACK_CLIENT. Clients can use SUPPORTED_MESSAGE_ACK_AUTO 
         *  instead to automatically acknowledge incoming Guaranteed messages.
         */
        properties.setProperty(JCSMPProperties.MESSAGE_ACK_MODE, JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
                
        // Disable certificate checking
        properties.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);

        if (conf.getAuthenticationScheme().equals(AuthenticationScheme.BASIC)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_BASIC);   
        } else if (conf.getAuthenticationScheme().equals(AuthenticationScheme.KERBEROS)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_GSS_KRB);   
        }

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
        session = JCSMPFactory.onlyInstance().createSession(properties);
    }

    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        strusage += "This sample:\n";
        strusage += "\t[-d | --durable]\t Flow to a durable queue, default: temporary queue\n";
        System.out.println(strusage);
        finish(1);
    }

    public void run(String[] args) {
        FlowReceiver receiver = null;
        try {
            // Create the Session. 
            createSession(args);

			boolean useDurable = false;
			useDurable = (conf.getArgBag().get("-d") != null || conf.getArgBag().get("--durable") != null);
            
            // Open the data channel to the appliance.
            System.out.println("About to connect to appliance.");
            session.connect();
            printRouterInfo();
			final String virtRouterName = (String) session.getProperty(JCSMPProperties.VIRTUAL_ROUTER_NAME);
			System.out.printf("Router's virtual router name: '%s'\n", virtRouterName);
            System.out.println("Connected!");

            // Create a Queue to receive messages.
            Queue queue;
            if (useDurable) {
                // Create a durable Queue to receive messages.
                queue = JCSMPFactory.onlyInstance().createQueue(SampleUtils.SAMPLE_QUEUE);
                // Create a receiver.
                receiver = session.createFlow(queue, null, this);
            } else {
                // Create a temporary Queue to receive messages.
                queue = session.createTemporaryQueue();
            	
                // Provision the temporary Queue and create a receiver
                ConsumerFlowProperties flowProp = new ConsumerFlowProperties();
                flowProp.setEndpoint(queue);
                
                EndpointProperties endpointProp = new EndpointProperties(EndpointProperties.ACCESSTYPE_EXCLUSIVE, 500000, EndpointProperties.PERMISSION_MODIFY_TOPIC, 100);
                endpointProp.setMaxMsgRedelivery(15);
                endpointProp.setDiscardBehavior(EndpointProperties.DISCARD_NOTIFY_SENDER_ON);
                
                receiver = session.createFlow(this, flowProp, endpointProp);
            }

            // Start the receiver
            receiver.start();

            XMLMessageProducer producer = session.getMessageProducer(this);
            for (int i = 0; i < 10; i++) {
				BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
				msg.setDeliveryMode(DeliveryMode.PERSISTENT);
				producer.send(msg, queue);
                Thread.sleep(1000);
            }
            
            // Close the receiver.
            receiver.close();
            finish(0);
        } catch (JCSMPTransportException ex) {
            System.err.println("Encountered a JCSMPTransportException, closing receiver... " + ex.getMessage());
            if (receiver != null) {
                receiver.close();
                // At this point the receiver handle is unusable; a new one
                // should be created.
            }
            finish(1);
        } catch (JCSMPException ex) {
            System.err.println("Encountered a JCSMPException, closing receiver... " + ex.getMessage());
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
            System.err.println("Encountered an Exception... " + ex.getMessage());
            finish(1);
        }
    }

    public static void main(String[] args) {
        SimpleFlowToQueue app = new SimpleFlowToQueue();
        app.run(args);
    }
}
