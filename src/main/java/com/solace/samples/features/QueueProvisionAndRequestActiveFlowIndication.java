/**
 * QueueProvisionAndRequestActiveFlowIndication.java
 *
 * This sample shows how to provision an endpoint (in this case an 
 * exclusive Queue) on the appliance, then how to request active flow indication
 * when creating a flow.
 * 
 * Copyright 2009-2018 Solace Corporation. All rights reserved.
 */

package com.solace.samples.features;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowEventArgs;
import com.solacesystems.jcsmp.FlowEventHandler;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solace.samples.features.common.ArgParser;
import com.solace.samples.features.common.SampleApp;
import com.solace.samples.features.common.SampleUtils;
import com.solace.samples.features.common.SessionConfiguration;

public class QueueProvisionAndRequestActiveFlowIndication extends SampleApp implements XMLMessageListener, FlowEventHandler {

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
		QueueProvisionAndRequestActiveFlowIndication qsample = new QueueProvisionAndRequestActiveFlowIndication();
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

	void run(String[] args) {
		createSession(args);
		FlowReceiver flowOne = null;
		FlowReceiver flowTwo = null;
		try {
			// Connects the Session.
			session.connect();

			// Check capability to provision endpoints
			checkCapability(CapabilityType.ENDPOINT_MANAGEMENT);
			// Check capability for active flow indication
			checkCapability(CapabilityType.ACTIVE_FLOW_INDICATION);

			/*
			 * Provision a new Queue on the appliance, ignoring if it already
			 * exists. Set permissions, access type, and provisioning flags.
			 */
			EndpointProperties ep_provision = new EndpointProperties();
			// Set permissions to allow all.
			ep_provision.setPermission(EndpointProperties.PERMISSION_DELETE);
			// Set access type to exclusive.
			ep_provision.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
			String ep_qn = "sample_queue_ProvisionAndRequestActiveFlowIndication_" + String.valueOf(System.currentTimeMillis() % 10000);
			final String virtRouterName = (String) session.getProperty(JCSMPProperties.VIRTUAL_ROUTER_NAME);
			System.out.printf("Router's virtual router name: '%s'\n", virtRouterName);
			Queue ep_queue = JCSMPFactory.onlyInstance().createQueue(ep_qn);
			System.out.printf("Provision queue '%s' on the appliance...", ep_queue);
			session.provision(ep_queue, ep_provision, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
			System.out.println("OK");


			/*
			 * Create a flow "flowOne" from session and bind to
			 * the exclusive queue provisioned above.
			 */
			System.out.println("Create flow one");
			flowOne = session.createFlow(
					this, 
					new ConsumerFlowProperties().setEndpoint(ep_queue).setActiveFlowIndication(true),
					null,
					this);
			flowOne.start();
			
			/*
			 * Create a flow "flowTwo" from session2 and bind to
			 * the exclusive queue provisioned above.
			 */
			System.out.println("Create flow two");
			flowTwo = session.createFlow(
					this, 
					new ConsumerFlowProperties().setEndpoint(ep_queue).setActiveFlowIndication(true),
					null,
					this);
			flowTwo.start();

			Thread.sleep(5000);  // ADDED

			// Close the flow "flowOne"
			System.out.println("Close flow one");
			flowOne.close();
			
			// Waiting for flow events
			Thread.sleep(500);
			
			// Close the flow "flowTwo".
			System.out.println("Close flow two");
			flowTwo.close();
			
			// Deprovision the Queue.
			System.out.printf("Deprovision queue '%s'...", ep_queue);
			session.deprovision(ep_queue, 0);
			
			System.out.println("OK");

			finish(0);
		} catch (JCSMPTransportException ex) {
			System.err.println("Encountered a JCSMPTransportException, closing session... " + ex.getMessage());
			if (flowOne != null) {
				flowOne.close();
				// At this point the flow receiver handle is unusable, a new one
				// may be created by the application.
			}
			if (flowTwo != null) {
				flowTwo.close();
				// At this point the flow receiver handle is unusable, a new one
				// may be created by the application.
			}
			finish(1);
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing consumer channel... " + ex.getMessage());
			// Possible causes:
			// - Authentication error: invalid username/password
			// - Provisioning error: unable to add subscriptions from CSMP
			// - Invalid or unsupported properties specified
			if (flowOne != null) {
				flowOne.close();
				// At this point the flow receiver handle is unusable, a new one
				// may be created by the application.
			}
			if (flowTwo != null) {
				flowTwo.close();
				// At this point the flow receiver handle is unusable, a new one
				// may be created by the application.
			}
			finish(1);
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finish(1);
		}

	}

	// FlowEventHandler
	public void handleEvent(Object source, FlowEventArgs event) {
		System.out.println("Flow Event - " + event);
	}

    // XMLMessageListener
    public void onException(JCSMPException exception) {
        exception.printStackTrace();
    }

    // XMLMessageListener
    public void onReceive(BytesXMLMessage message) {
        System.out.println("Received Message:\n" + message.dump());
    }

}
