/**
 * QueueProvisionAndBrowse.java
 *
 * This sample shows how to provision an endpoint (in this case a 
 * Queue) on the appliance, then how to use a Browser to browse the Queue's contents. 
 * Every message received through the Browser interface is dumped to screen 
 * using the XMLMessage.dump() convenience function for review.
 * 
 * On the publishing side, this sample shows the use of 
 * Producer-Independent Messages, which can be reused and resent multiple times 
 * with different data payloads.
 * 
 * Copyright 2009-2018 Solace Corporation. All rights reserved.
 */

package com.solace.samples.features;

import com.solacesystems.jcsmp.Browser;
import com.solacesystems.jcsmp.BrowserProperties;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageProducer;
import com.solace.samples.features.common.ArgParser;
import com.solace.samples.features.common.SampleApp;
import com.solace.samples.features.common.SampleUtils;
import com.solace.samples.features.common.SessionConfiguration;

public class QueueProvisionAndBrowse extends SampleApp {
	XMLMessageProducer prod = null;
	SessionConfiguration conf = null;

	void createSession(String[] args) {
		ArgParser parser = new ArgParser();

		// Parse command-line arguments
		if (parser.parse(args) == 0)
			conf = parser.getConfig();
		else
			printUsage(parser.isSecure());

		session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
	}

	void printUsage(boolean secure) {
		String strusage = ArgParser.getCommonUsage(secure);
		System.out.println(strusage);
		finish(1);
	}

	public static void main(String[] args) {
		QueueProvisionAndBrowse qsample = new QueueProvisionAndBrowse();
		qsample.run(args);
	}

	void checkCapability(final CapabilityType cap) {
		System.out.printf("Checking for capability %s...", cap);
		if (session.isCapable(cap)) {
			System.out.println("OK");
		} else {
			System.out.println("FAILED");
			finish(1);
		}
	}

	byte[] getBinaryData(int len) {
		final byte[] tmpdata = "the quick brown fox jumps over the lazy dog / flying spaghetti monster ".getBytes();
		final byte[] ret_data = new byte[len];
		for (int i = 0; i < len; i++)
			ret_data[i] = tmpdata[i % tmpdata.length];
		return ret_data;
	}

	void run(String[] args) {
		createSession(args);
		try {
			// Connects the Session and acquires a message producer.
	        session.connect();
			prod = session.getMessageProducer(new PrintingPubCallback());

			// Check capability to provision endpoints
			checkCapability(CapabilityType.ENDPOINT_MANAGEMENT);
			// Check capability to browse queues
			checkCapability(CapabilityType.BROWSER);

			/*
			 * Provision a new Queue on the appliance, ignoring if it already
			 * exists. Set permissions, access type, and provisioning flags.
			 */
			EndpointProperties ep_provision = new EndpointProperties();
			// Set permissions to allow all.
			ep_provision.setPermission(EndpointProperties.PERMISSION_DELETE);
			// Set access type to exclusive.
			ep_provision.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
			// Set Quota to 100 MB.
			ep_provision.setQuota(100);
			String ep_qn = "sample_queue_ProvisionAndBrowse_" + String.valueOf(System.currentTimeMillis() % 10000);
			final String virtRouterName = (String) session.getProperty(JCSMPProperties.VIRTUAL_ROUTER_NAME);
			System.out.printf("Router's virtual router name: '%s'\n", virtRouterName);
			Queue ep_queue = JCSMPFactory.onlyInstance().createQueue(ep_qn);
			System.out.printf("Provision queue '%s' on the appliance...", ep_queue);
			session.provision(ep_queue, ep_provision, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
			System.out.println("OK");

			/*
			 * Publish some messages to this Queue using
			 * producer-independent messages acquired from JCSMPFactory.
			 */
			BytesXMLMessage m = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
			m.setDeliveryMode(DeliveryMode.PERSISTENT);
			for (int i = 1; i <= 5; i++) {
				m.setUserData(String.valueOf(i).getBytes());
				m.writeAttachment(getBinaryData(i * 20));
				prod.send(m, ep_queue);
			}
			Thread.sleep(500);
			System.out.println("Sent messages.");

			/*
			 * Now browse messages on the Queue and selectively remove
			 * them.
			 */
			BrowserProperties br_prop = new BrowserProperties();
			br_prop.setEndpoint(ep_queue);
			br_prop.setTransportWindowSize(1);
			br_prop.setWaitTimeout(1000);
			Browser myBrowser = session.createBrowser(br_prop);
			BytesXMLMessage rx_msg = null;
			int rx_msg_count = 0;
			do {
				rx_msg = myBrowser.getNext();
				if (rx_msg != null) {
					rx_msg_count++;
					System.out.println("Browser got message... dumping:");
					System.out.println(rx_msg.dump());
					if (rx_msg_count == 3) {
						System.out.print("Removing message from queue...");
						myBrowser.remove(rx_msg);
						System.out.println("OK");
					}
				}
			} while (rx_msg != null);
			System.out.println("Finished browsing.");

			System.out.printf("Deprovision queue '%s'...", ep_queue);
			// Close the Browser.
			myBrowser.close();
			// Deprovision the Queue.
			session.deprovision(ep_queue, 0);
			System.out.println("OK");

			finish(0);
		} catch (JCSMPTransportException ex) {
			System.err.println("Encountered a JCSMPTransportException, closing session... " + ex.getMessage());
			if (prod != null) {
				prod.close();
				// At this point the producer handle is unusable, a new one
				// may be created by the application.
			}
			finish(1);
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing consumer channel... " + ex.getMessage());
			// Possible causes:
			// - Authentication error: invalid username/password
			// - Provisioning error: unable to add subscriptions from CSMP
			// - Invalid or unsupported properties specified
			if (prod != null) {
				prod.close();
				// At this point the producer handle is unusable, a new one
				// may be created by the application.
			}
			finish(1);
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finish(1);
		}

	}

}
