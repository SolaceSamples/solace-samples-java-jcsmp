/**
 * SubscribeOnBehalfOfClient.java
 * 
 * This sample shows how to subscribe on behalf of another client. Doing 
 * so requires knowledge of the target client name, as well as possession of
 * the subscription-manager permission.
 * 
 * Two Sessions are connected to the appliance, their ClientNames 
 * are extracted, and session #1 adds a Topic subscription on 
 * behalf of session #2. A message is then published on that Topic,
 * which is received by session #2.
 * 
 * Copyright 2011-2022 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.AccessDeniedException;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.ClientName;
import com.solacesystems.jcsmp.Consumer;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageProducer;

/**
 * This sample shows how to subscribe on behalf of another client. Doing 
 * so requires knowledge of the target client name, as well as possession of
 * the subscription-manager permission.
 * 
 * Two sessions are connected to the appliance, their ClientNames 
 * are extracted, and session #1 adds a Topic subscription on 
 * behalf of session #2. A message is then published on that Topic,
 * which will be received by session #2.
 *
 */
public class SubscribeOnBehalfOfClient extends SampleApp {
	Consumer cons = null, cons2 = null;
    XMLMessageProducer prod = null;
	SessionConfiguration conf = null;
	JCSMPSession session2 = null;
	
	void createSession(String[] args) {
		ArgParser parser = new ArgParser();
		
		// Setup defaults.
		SessionConfiguration sc = new SessionConfiguration();
		sc.setDeliveryMode(DeliveryMode.DIRECT);
		parser.setConfig(sc);
		
		// Parse command-line arguments.
		if (parser.parse(args) == 0)
			conf = parser.getConfig();
		else
			printUsage(parser.isSecure());

		// Create the default Session.
		session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(), null);
		session2 = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
	}
	
	void printUsage(boolean secure) {
		String strusage = ArgParser.getCommonUsage(secure);
		strusage += "This sample requires that the client-username used have the subscription-manager permission.\n";
		System.out.println(strusage);
		finish(1);
	}
	
	@Override
	protected void finish(int status) {
		System.out.println();
		if (session2 != null) {
			session2.closeSession();
			System.out.println("Client 2 stats:");
			printFinalSessionStats(session2);
		}
		System.out.println("Client 1 stats:");
		super.finish(status); // this contains a System.exit call, call last
	}
	
	public SubscribeOnBehalfOfClient() {
	}

	public static void main(String[] args) {
		SubscribeOnBehalfOfClient sampleapp = new SubscribeOnBehalfOfClient();
		sampleapp.run(args);
	}
	
	class NamedPrintingMessageHandler extends PrintingMessageHandler {
		final String n;
		public NamedPrintingMessageHandler(String name) {
			super();
			n = name;
		}
		
		@Override
		public void onReceive(BytesXMLMessage msg) {
			System.out.printf("Consumer '%s' received message.\n", n);
			super.onReceive(msg);
		}
	}
	
	void run(String[] args) {
		createSession(args);

		try {
			// Acquire a message consumer and open the data channel to
			// the appliance.
			System.out.println("About to connect to appliance.");
	        session.connect();
			prod = session.getMessageProducer(new PrintingPubCallback());
			printRouterInfo();
			// Validate that the connection is to an appliance running SolOS-TR that
			// supports subscription-manager.
			if (!session.isCapable(CapabilityType.SUBSCRIPTION_MANAGER)) {
				System.out.println("Requires an appliance supporting subscription management.");
				finish(1);
			}

			cons = session.getMessageConsumer(new NamedPrintingMessageHandler("Client 1"));
			cons2 = session2.getMessageConsumer(new NamedPrintingMessageHandler("Client 2"));
			cons.start();
			cons2.start();

			// Once a Session has connected and logged in, the client name 
			// can be extracted using getProperty(JCSMPProperties.CLIENT_NAME).
			JCSMPFactory fac = JCSMPFactory.onlyInstance();
			String strCn1 = (String) session.getProperty(JCSMPProperties.CLIENT_NAME);
			String strCn2 = (String) session2.getProperty(JCSMPProperties.CLIENT_NAME);
			ClientName cn1 = fac.createClientName(strCn1);
			ClientName cn2 = fac.createClientName(strCn2);
			System.out.printf("\nConnected 2 clients, '%s' and '%s'.\n", cn1, cn2);

			// The first client (subscription-manager) will add a Topic
			// subscription to the second client.
			Topic topic1 = fac.createTopic("sample/topic/pasta");
			System.out.printf("Client 1 (%s) adding subscription on behalf of client 2 (%s)...\n", cn1, cn2);
			try {
				session.addSubscription(cn2, topic1, JCSMPSession.WAIT_FOR_CONFIRM);
				System.out.println("done.");
			} catch (AccessDeniedException ex) {
				// Permission errors occur if the selected client-username does
				// not have the subscription-manager permission.
				ex.printStackTrace(System.out);
			}

			BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
			msg.writeBytes(SampleUtils.xmldoc.getBytes());
			// Sending message to topic1. The second session has a subscription
			// for it.
			prod.send(msg, topic1);
			System.out.printf("Message sent on topic '%s', should be received by Client 2\n", topic1);

			Thread.sleep(1000);
			cons.stop();
			cons2.stop();
			finish(0);
		} catch (JCSMPTransportException ex) {
			System.err.println("Encountered a JCSMPTransportException, closing consumer channel... " + ex.getMessage());
			if (cons != null) {
				cons.close();
				// At this point the consumer handle is unusable; a new one should be created 
				// by calling cons = session.getMessageConsumer(...) if the application 
				// logic requires the consumer channel to remain open.
			}
			finish(1);
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing consumer channel... " + ex.getMessage());
			// Possible causes: 
			// - Authentication error: invalid username/password 
			// - Provisioning error: unable to add subscriptions from CSMP
			// - Invalid or unsupported properties specified
			// - Endpoint not created on the appliance
			if (cons != null) {
				cons.close();
				// At this point the consumer handle is unusable; a new one should be created 
				// by calling cons = session.getMessageConsumer(...) if the application 
				// logic requires the consumer channel to remain open.
			}
			finish(1);
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finish(1);
		}
	}

}
