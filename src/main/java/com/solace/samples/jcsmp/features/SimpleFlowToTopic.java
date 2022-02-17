/**
 * SimpleFlowToTopic.java
 * 
 * This sample demonstrates creating a flow to a durable or non-durable Topic 
 * Endpoint, and the use of the auto-acknowledgment of messages.
 * 
 * It also demonstrates the creation of temporary topics.
 * 
 * For the case of a durable Topic Endpoint, this sample requires that a durable
 * Topic Endpoint called 'my_sample_topicendpoint' be provisioned on the appliance
 * with at least 'Modify Topic' permissions.
 * 
 * Copyright 2007-2022 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solace.samples.jcsmp.features.common.SessionConfiguration.AuthenticationScheme;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.DurableTopicEndpoint;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.TopicEndpoint;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class SimpleFlowToTopic extends SampleApp implements XMLMessageListener, JCSMPStreamingPublishCorrelatingEventHandler {
    SessionConfiguration conf = null;

    // XMLMessageListener
    public void onException(JCSMPException exception) {
        exception.printStackTrace();
    }

    // XMLMessageListener
    public void onReceive(BytesXMLMessage message) {
        System.out.println("Received Message:\n" + message.dump());
    }

    // JCSMPStreamingPublishEventHandler
    public void handleErrorEx(Object key, JCSMPException cause,
            long timestamp) {
        cause.printStackTrace();
    }

    // JCSMPStreamingPublishEventHandler
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
         * SUPPORTED_MESSAGE_ACK_AUTO means that the received messages on the Flow
         * are implicitly acknowledged on return from the onReceive() of the XMLMessageListener
         * specified in createFlow().
         */
        properties.setProperty(JCSMPProperties.MESSAGE_ACK_MODE, JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);
                
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
             * "disable compression" (the default), and 9 means use maximum 
			 * compression.
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
        strusage += "\t[-d | --durable]\t Flow to a durable topic endpoint, default: non-durable\n";
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
            session.getMessageConsumer((XMLMessageListener)null);

            printRouterInfo();
            System.out.println("Connected!");

			// Create Topic Endpoints and Topics to receive messages.
			TopicEndpoint topicEndpoint;
			Topic topic;
			if (useDurable) {
				topicEndpoint = JCSMPFactory.onlyInstance().createDurableTopicEndpointEx(SampleUtils.SAMPLE_TOPICENDPOINT);
				topic = JCSMPFactory.onlyInstance().createTopic(SampleUtils.SAMPLE_TOPIC);
			} else {
				// Creating non-durable Topic Endpoint and temporary Topic.
				topicEndpoint = session.createNonDurableTopicEndpoint();
				topic = session.createTemporaryTopic();
			}
            
            
            // Create and start the receiver.
            receiver = session.createFlow(topicEndpoint, topic, this);
            receiver.start();
            
            XMLMessageProducer producer = session.getMessageProducer(this);
            for (int i = 0; i < 10; i++) {
                BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
				msg.setDeliveryMode(DeliveryMode.PERSISTENT);
				msg.setCorrelationKey(msg);  // correlation key for receiving ACKs
                producer.send(msg, topic);
                Thread.sleep(1000);
            }

            // Close the receiver.
            receiver.close();

            /*
             * Durable Topic Endpoints continue to get messages on the registered Topic
             * subscription if client applications do not unsubscribe.
             * Non-durable Topic Endpoints are cleaned up automatically after client applications
             * dispose the flows bound to them.
             *
             * The following code block demonstrates how to unsubscribe or remove subscribed Topic on 
             * the durable Topic Endpoint.
             * Two conditions must be met:
             * - The durable Topic Endpoint must have at least 'Modify Topic' permission enabled.
             * - No flows are currently bound to the given durable Topic Endpoint.
             */
            if (useDurable) {
                System.out.println("About to unsubscribe from durable topic endpoint " + topicEndpoint);
                session.unsubscribeDurableTopicEndpoint((DurableTopicEndpoint)topicEndpoint);
            }
            
            finish(0);
        } catch (JCSMPTransportException ex) {
            System.err.println("Encountered a JCSMPTransportException, closing receiver... " + ex.getMessage());
            if (receiver != null) {
                receiver.close();
                // At this point the receiver handle is unusable, a new one
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
        SimpleFlowToTopic app = new SimpleFlowToTopic();
        app.run(args);
    }
}
