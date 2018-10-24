/**
 * MessageTTLAndDeadMessageQueue.java
 *
 * This sample demonstrates how to provision an endpoint (in this case a 
 * Queue) on the appliance which supports message TTL; how to send a message
 * with TTL enabled; how to allow an expired message to be collected by
 * the Dead Message Queue (DMQ).
 * 
 * Copyright 2011-2018 Solace Corporation. All rights reserved.
 */

package com.solace.samples.features;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.Consumer;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;
import com.solace.samples.features.common.ArgParser;
import com.solace.samples.features.common.SampleApp;
import com.solace.samples.features.common.SampleUtils;
import com.solace.samples.features.common.SessionConfiguration;

public class MessageTTLAndDeadMessageQueue extends SampleApp {
	Consumer cons = null;
	XMLMessageProducer prod = null;
	SessionConfiguration conf = null;
	static int rx_msg_count = 0;
	private static String DMQ_NAME = "#DEAD_MSG_QUEUE";
	private Queue deadMsgQ = null;
	
	void createSession(String[] args) {
		ArgParser parser = new ArgParser();

		// Parse command-line arguments.
		if (parser.parse(args) == 0)
			conf = parser.getConfig();
		else
			printUsage(parser.isSecure());
		
		Map<String, Object> extraProperties = new HashMap<String, Object>();
		if (conf.getArgBag().containsKey("-c")) {
			extraProperties.put(JCSMPProperties.CALCULATE_MESSAGE_EXPIRATION, Boolean.TRUE);
		}
		session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),extraProperties);
	}
	
	void printUsage(boolean secure) {
		String strusage = ArgParser.getCommonUsage(secure);
		strusage += "This sample:\n";
		strusage += "\t[-c]             Enable calculation of message expiration time\n";

		System.out.println(strusage);
		finish(1);
	}

	public static void main(String[] args) {
		MessageTTLAndDeadMessageQueue qsample = new MessageTTLAndDeadMessageQueue();
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
		int finishCode = 0;
		Queue ep_queue = null;
		try {
			// Connects the Session and acquires a message producer.
			session.connect();
			prod = session.getMessageProducer(new PrintingPubCallback());

			// Check capability for message expiration.
			checkCapability(CapabilityType.ENDPOINT_MESSAGE_TTL);
			// Check capability to provision endpoints.
			checkCapability(CapabilityType.ENDPOINT_MANAGEMENT);

			final String virtRouterName = (String) session.getProperty(JCSMPProperties.VIRTUAL_ROUTER_NAME);
			System.out.printf("Router's virtual router name: '%s'\n", virtRouterName);
			
			/*
			 * Provision the DMQ, if it is not already provisioned.
			 */
			EndpointProperties dmq_provision = new EndpointProperties();
			dmq_provision.setPermission(EndpointProperties.PERMISSION_DELETE);
			dmq_provision.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
			deadMsgQ = JCSMPFactory.onlyInstance().createQueue(DMQ_NAME);
			session.provision(deadMsgQ,dmq_provision,JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
			
			/*
			 * Provision a new Queue on the appliance, ignoring if it already
			 * exists. Set permissions, access type, and provisioning flags.
			 */
			EndpointProperties ep_provision = new EndpointProperties();
			// Set permissions to allow all
			ep_provision.setPermission(EndpointProperties.PERMISSION_DELETE);
			// Set access type to exclusive
			ep_provision.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
			// Quota to 100MB
			ep_provision.setQuota(100);
			// Set queue to respect message TTL
			ep_provision.setRespectsMsgTTL(Boolean.TRUE);
			
			String ep_qn = "sample_queue_MessageTTLAndDMQ_" + String.valueOf(System.currentTimeMillis() % 10000);
			ep_queue = JCSMPFactory.onlyInstance().createQueue(ep_qn);
			System.out.printf("Provision queue '%s' on the appliance...", ep_queue);
			session.provision(ep_queue, ep_provision, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
			System.out.println("OK");

			/*
			 * Publish five messages to the Queue using
			 * producer-independent messages acquired from JCSMPFactory.
			 */
			BytesXMLMessage m = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
			m.setDeliveryMode(DeliveryMode.PERSISTENT);
			for (int i = 1; i <= 5; i++) {
				m.setUserData(String.valueOf(i).getBytes());
				m.writeAttachment(getBinaryData(i * 20));
				// Set message TTL to 3000 milliseconds for two messages and set
				// one of the two messages to be Dead Message Queue eligible.
				if (i%2 == 0) {
					m.setTimeToLive(3000);
					m.setDMQEligible(i%4 == 0);
				}
				else {
					m.setTimeToLive(0);
					m.setDMQEligible(false);
				}
				prod.send(m, ep_queue);
			}
			Thread.sleep(1000);
			System.out.println("Sent five messages.");
			
			/*
			 * Create a flow to consume messages on the Queue. There should be 
			 * five messages on the Queue.
			 */
			rx_msg_count = 0;
			ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
			flow_prop.setEndpoint(ep_queue);
			cons = session.createFlow(new MessageDumpListener(), flow_prop);
			cons.start();
			Thread.sleep(2000);
			System.out.printf("Finished consuming messages, the number of message on the queue is '%s'.\n", rx_msg_count);
			
			/*
			 * Close the Flow.
			 */
			cons.close();
			
			/*
			 * Now publish another five messages.
			 */
			for (int i = 1; i <= 5; i++) {
				m.setUserData(String.valueOf(i).getBytes());
				m.writeAttachment(getBinaryData(i * 20));
				// Set message TTL to 3000 milliseconds for two messages, and set
				// one of the two messages to be Dead Message Queue eligible.
				if (i%2 == 0) {
					m.setTimeToLive(3000);
					m.setDMQEligible(i%4 == 0);
				}
				else {
					m.setTimeToLive(0);
					m.setDMQEligible(false);
				}
				prod.send(m, ep_queue);
			}
			Thread.sleep(500);
			System.out.println("Sent another five messages.");
			
			/*
			 * Wait for the messages to expire.
			 */
			Thread.sleep(5000);
			
			/*
			 * Create a new Flow to consume messages on the Queue again. There should be only 
			 * three messages on the Queue.
			 */
			rx_msg_count = 0;
			flow_prop = new ConsumerFlowProperties();
			flow_prop.setEndpoint(ep_queue);
			cons = session.createFlow(new MessageDumpListener(), flow_prop);
			cons.start();
			Thread.sleep(2000);
			System.out.printf("Finished consuming messages, the number of message on the queue is '%s'.\n", rx_msg_count);
			
			/*
			 * Close the Flow.
			 */
			cons.close();
			
			/*
			 * We can also consume messages from the DMQ if it is properly configured 
			 * on the appliance. There should be one expired message on the DMQ. The other 
			 * expired message is discarded since it is not DMQ-eligible.
			 */
	        ConsumerFlowProperties dmq_props = new ConsumerFlowProperties();
	        dmq_props.setEndpoint(deadMsgQ);	        
	        rx_msg_count = 0;
	        try {
	        	cons = session.createFlow(new MessageDumpListener(), dmq_props);
	        	cons.start();
		        Thread.sleep(1000);
				System.out.printf("Finished browsing the DMQ, the number of message on the DMQ is '%s'.\n", rx_msg_count);
				/*
				 * Close the Flow.
				 */
				cons.close();
	        } catch (JCSMPErrorResponseException ex) {
	        	System.out.println("DMQ is not properly configured on the appliance: " + ex.getMessage());
	        }
	        
	        /*
	         * The client application can also set message expiration explicitly as long as 
	         * Time-To-Live is not set for the message.
	         */
	        
	        // Acquire a session-independent message.
	        m = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);	        
			m.setDeliveryMode(DeliveryMode.PERSISTENT);
	        m.setUserData(String.valueOf(1).getBytes());
			m.writeAttachment(getBinaryData(20));
			// Set the message expiration explicitly to December 2, 1981 00:00:00
			Calendar cal = Calendar.getInstance(TimeZone.getDefault());
	        cal.set(1981, 11, 2, 0, 0, 0);  
			m.setExpiration(cal.getTimeInMillis());
			prod.send(m, ep_queue);
			Thread.sleep(500);
			System.out.println("Sent message with message expiration set.");

			/*
			 * We can now create a new Flow to consume messages on the Queue again, and there should be only 
			 * three messages on the Queue whose message expiration value is the same as the client-set value.
			 */
			rx_msg_count = 0;
			flow_prop = new ConsumerFlowProperties();
			flow_prop.setEndpoint(ep_queue);
			cons = session.createFlow(new MessageDumpListener(), flow_prop);
			cons.start();
			Thread.sleep(1000);
			System.out.printf("Finished consuming messages, the number of message on the queue is '%s'.\n", rx_msg_count);

		} catch (JCSMPTransportException ex) {
			System.err.println("Encountered a JCSMPTransportException, closing consumer channel... " + ex.getMessage());
			if (cons != null) {
				cons.close();
				// At this point the consumer handle is unusable; a new one
				// should be created by calling
				// cons = session.getMessageConsumer(...) if the
				// application logic requires the consumer channel to remain open.
			}
			finishCode = 1;
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing consumer channel... " + ex.getMessage());
			// Possible causes:
			// - Authentication error: invalid username/password
			// - Provisioning error: unable to add subscriptions from CSMP
			// - Invalid or unsupported properties specified
			if (cons != null) {
				cons.close();
				// At this point the consumer handle is unusable; a new one
				// should be created
				// by calling cons = session.getMessageConsumer(...) if the
				// application
				// logic requires the consumer channel to remain open.
			}
			finishCode = 1;
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finishCode = 1;
		} finally {
            if (cons != null) {
                cons.close();
            }
            if (ep_queue != null) {
                System.out.printf("Deprovision queue '%s'...", ep_queue);
                try {
                    session.deprovision(ep_queue, JCSMPSession.FLAG_IGNORE_DOES_NOT_EXIST);
                } catch(JCSMPException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("OK");
            finish(finishCode);
		}

	}
	
	static class MessageDumpListener implements XMLMessageListener {
		public void onException(JCSMPException exception) {
			exception.printStackTrace();
		}

		public void onReceive(BytesXMLMessage message) {
			System.out.println("\n======== Received message ======== \n" + message.dump());
			rx_msg_count++;
		}
	}

}
