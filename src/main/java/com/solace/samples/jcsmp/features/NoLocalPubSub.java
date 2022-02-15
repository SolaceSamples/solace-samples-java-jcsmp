/**
 * NoLocalPubSub.java
 * 
 * This sample demonstrates the use of the NO_LOCAL Session and Flow properties. With
 * these properties enabled, messages published on a Session cannot be received on that same 
 * session or on a Flow on that Session even when there is a matching subscription.
 *
 * This sample will:
 *  - Subscribe to a Topic for Direct messages on a Session with No Local delivery enabled.
 *  - Create a Flow to a Queue with No Local Delivery enabled on the Flow, but not on the Session.
 *  - Publish a Direct message on each Session, and verify it is not delivered locally.
 *  - Publish a message to the Queue on each Session, and verify it is not delivered locally.
 *
 * Copyright 2009-2022 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solace.samples.jcsmp.features.common.SessionConfiguration.AuthenticationScheme;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class NoLocalPubSub extends SampleApp implements JCSMPStreamingPublishCorrelatingEventHandler {
	SessionConfiguration conf = null;
	JCSMPSession session2 = null;

	// JCSMPStreamingPublishEventHandler
	public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
		System.out.println("Publish Error callback handler ('400: NoLocal Discard' is normal): " + cause);
	}

	// JCSMPStreamingPublishEventHandler
	public void responseReceivedEx(Object key) {
	}

	@Override
	protected void finish(int status) {
		super.finish(status);
		if (this.session2 != null) {
			session2.closeSession();
		}
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

        // Disable certificate checking
        properties.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);

        if (conf.getAuthenticationScheme().equals(AuthenticationScheme.BASIC)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_BASIC);   
        } else if (conf.getAuthenticationScheme().equals(AuthenticationScheme.KERBEROS)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_GSS_KRB);   
        }

		// Channel properties
		JCSMPChannelProperties cp = (JCSMPChannelProperties) properties.getProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES);
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

		// Create two sessions with the same credentials.
		// session2 has "no_local" option set.
		session = JCSMPFactory.onlyInstance().createSession(properties);
		properties.setBooleanProperty(JCSMPProperties.NO_LOCAL, true);
		session2 = JCSMPFactory.onlyInstance().createSession(properties);
	}

	void printUsage(boolean secure) {
		String strusage = ArgParser.getCommonUsage(secure);
		System.out.println(strusage);
		finish(1);
	}

	void send(final XMLMessageProducer prod, final Destination dest, final DeliveryMode delMode) throws JCSMPException {
		BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
		byte[] data = "Attachment data. Sample 'NoLocalPubSub'".getBytes();
		msg.writeAttachment(data);
		msg.setDeliveryMode(delMode);
		if (delMode == DeliveryMode.PERSISTENT) msg.setCorrelationKey(msg);  // correlation key for receiving ACKs
		prod.send(msg, dest);
	}

	public void run(String[] args) {
		XMLMessageConsumer directConsumer = null;
		FlowReceiver queueReceiver = null;
		XMLMessageProducer producer1 = null, producer2 = null;

		try {
			// Create the session.
			createSession(args);

			// Open the data channel to the appliance.
			System.out.println("About to connect to appliance.");
			session.connect();
			printRouterInfo();
			final String virtRouterName = (String) session.getProperty(JCSMPProperties.VIRTUAL_ROUTER_NAME);
			System.out.printf("Router's virtual router name: '%s'\n", virtRouterName);
			if (!session.isCapable(CapabilityType.NO_LOCAL)) {
				System.out.println("This sample requires an appliance with support for NO_LOCAL.");
				finish(1);
			}
			session2.connect();
			System.out.println("Connected!");

			Queue q = session.createTemporaryQueue();
			Topic t = JCSMPFactory.onlyInstance().createTopic(SampleUtils.SAMPLE_TOPIC);

			// directConsumer (on session2), is on a session with the NO_LOCAL
			// option.
			session2.addSubscription(t);
			directConsumer = session2.getMessageConsumer((XMLMessageListener) null);
			directConsumer.start();

			// The Receiver is a flow with the NO_LOCAL option.
			queueReceiver = session.createFlow(null, new ConsumerFlowProperties().setEndpoint(q).setNoLocal(true));
			queueReceiver.start();

			// Create producers.
			producer1 = session.getMessageProducer(this);
			producer2 = session2.getMessageProducer(this);

			// First session: publish a DIRECT message to SAMPLE_TOPIC and a
			// persistent message to Q.
			// session2 gets message 1; session1 does NOT get message 2 on
			// the queue.
			send(producer1, t, DeliveryMode.DIRECT);
			send(producer1, q, DeliveryMode.PERSISTENT);

			BytesXMLMessage incoming = null;
			incoming = directConsumer.receive(250);
			System.out.println("Session 2, directConsumer received: " + String.valueOf(incoming));
			incoming = queueReceiver.receive(250);
			System.out.println("Session 1, queue receiver received: " + String.valueOf(incoming));

			// Second session: publish a DIRECT message to SAMPLE_TOPIC and a
			// persistent message to Q.
			// session2 does NOT get message 1; session1 does get message 2 on Q.
			send(producer2, t, DeliveryMode.DIRECT);
			send(producer2, q, DeliveryMode.PERSISTENT);
			incoming = directConsumer.receive(250);
			System.out.println("Session 2, directConsumer received: " + String.valueOf(incoming));
			incoming = queueReceiver.receive(250);
			System.out.println("Session 1, queue receiver received: " + String.valueOf(incoming));

			// Close consumer flows and release temporary endpoint resources
			queueReceiver.close();
			directConsumer.close();
			
			finish(0);
		} catch (JCSMPTransportException ex) {
			System.err.println("Encountered a JCSMPTransportException, closing receiver... " + ex.getMessage());
			if (queueReceiver != null) {
				queueReceiver.close();
				// At this point the receiver handle is unusable; a new one
				// should be created.
			}
			if (directConsumer != null) directConsumer.close();
			finish(1);
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing receiver... " + ex.getMessage());
			// Possible causes:
			// - Authentication error: invalid username/password
			// - Invalid or unsupported properties specified
			if (queueReceiver != null) {
				queueReceiver.close();
				// At this point the receiver handle is unusable; a new one
				// should be created.
			}
			if (directConsumer != null) directConsumer.close();
			finish(1);
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finish(1);
		}
	}

	public static void main(String[] args) {
		NoLocalPubSub app = new NoLocalPubSub();
		app.run(args);
	}

}
