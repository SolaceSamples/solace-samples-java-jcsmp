/**
 * EventMonitor.java
 * 
 * This sample demonstrates monitoring appliance events using a relevant appliance 
 * event subscription.
 *
 * Sample Requirements:
 *  - SolOS-TR enabled appliance
 *  - The CLI configuration "Publish Client Event Messages" must be enabled 
 *    in the client's Message VPN on the appliance.
 *
 * In this sample, we subscribe to the appliance event topic for Client Connect
 * events:
 *
 *       #LOG/INFO/CLIENT/<appliance hostname>/CLIENT_CLIENT_CONNECT/>
 *
 * With "Publish Client Event Messages" enabled for the Message VPN,
 * all client events are published as messages. By subscribing to the above
 * topic, we are asking to receive all CLIENT_CLIENT_CONNECT event messages
 * from the specified appliance.
 *
 * Event Message topics are treated as regular topics in that wildcarding can be
 * used in the same manner as typical topics. For example, if you want to
 * receive all Client Events, regardless of Event Level, the following topic
 * could be used:
 */
//      #LOG/*/CLIENT/<appliance hostname>/>
/*
 * This sample triggers a CLIENT_CLIENT_CONNECT event by connecting a second
 * time to the appliance (triggerSecondaryConnection()).
 * 
 * Copyright 2009-2022 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class BrokerEventLogSubscriber extends SampleApp implements XMLMessageListener {

	XMLMessageProducer prod = null;
	XMLMessageConsumer cons = null;
	private SessionConfiguration conf;

	public void onException(JCSMPException exception) {
		System.err.println("onException " + exception);
		exception.printStackTrace();
	}

	public void onReceive(BytesXMLMessage message) {
		if (message.getAttachmentContentLength() > 0 && message.getDestination() != null) {
			byte[] attachment = new byte[message.getAttachmentContentLength()];
			message.readAttachmentBytes(attachment);
			// chomp last byte (null-terminated string)
			String strAttachment = new String(attachment, 0, attachment.length - 1);
			System.out.println("\n*** Event Message Received ***");
			System.out.println("Topic: " + message.getDestination());
			System.out.println("Event: " + strAttachment);
		}
	}

	void printUsage(boolean secure) {
		String strusage = ArgParser.getCommonUsage(secure);
		System.out.println(strusage);
		finish(1);
	}

	void createSession(String[] args) {
		// Parse command-line arguments.
		ArgParser parser = new ArgParser();
		if (parser.parse(args) == 0)
			conf = parser.getConfig();
		else
			printUsage(parser.isSecure());

		session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
	}

	void triggerSecondaryConnection() throws Exception {
		Topic dummyTopic = JCSMPFactory.onlyInstance().createTopic("dummy/topic");
		String uid = String.valueOf(System.currentTimeMillis());
		uid = uid.substring(uid.length() - 5);
		// Autogenerate a new CLIENT_NAME.
		JCSMPSession jsession = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
		jsession.addSubscription(dummyTopic);
		jsession.closeSession();
	}
	
	public void run(String[] args) {
		createSession(args);

		try {
			cons = session.getMessageConsumer(this);
			printRouterInfo();
			
			System.out.println("Connected!");
			
			// Build an event monitoring topic for client connect events and
			// subscribe to it.
			final String routerHostname = (String) session.getCapability(CapabilityType.PEER_ROUTER_NAME);
			final String strEventTopic = String.format("#LOG/INFO/CLIENT/%s/CLIENT_CLIENT_CONNECT/>", routerHostname);
			final Topic eventTopic = JCSMPFactory.onlyInstance().createTopic(strEventTopic);

			System.out.printf("Adding subscription to '%s'...", eventTopic.getName());
			session.addSubscription(eventTopic);
			System.out.println("OK");
			
			cons.start();
			System.out.println("Waiting to receive events...");
			triggerSecondaryConnection();
		    // Sleep to allow reception of event.
			Thread.sleep(1000);
			cons.stop();
			finish(0);
			
		} catch (JCSMPTransportException ex) {
			System.err.println("Encountered a JCSMPTransportException, closing session... "
				+ ex.getMessage());
			
			// At this point the producer and consumer handles are unusable
			// (closed). If application logic requires their use, a new producer
			// and consumer should be acquired from the Session.
			
			finish(1);
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing session... "
				+ ex.getMessage());
			// Possible causes:
			// - Authentication error: invalid username/password
			// - Provisioning error: publisher not entitled is a common error in
			// this category
			// - Invalid or unsupported properties specified
			finish(1);
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finish(1);
		}
		
	}

	public static void main(String[] args) {
		BrokerEventLogSubscriber em = new BrokerEventLogSubscriber();
		em.run(args);
	}
}
