/**
 * AdPubAck.java
 * 
 * This sample shows Guaranteed Delivery publishing with handling of message 
 * acknowledgments. Guaranteed Delivery is also known Assured Delivery.
 *
 * To accomplish this, the publisher makes use of the correlation key
 * in each message (XMLMessage#setCorrelationKey). 
 * The publisher adds a reference to a correlation structure
 * to the Solace message before sending. Then in the event callback 
 * the publisher can process acknowledgments and rejections to determine if the 
 * broker accepted the Guaranteed message.
 *
 * Specifically, in this sample, the publisher maintains a linked list
 * of outstanding messages not yet acknowledged by the broker. After sending,
 * the publisher checks to see if any of the messages have been 
 * acknowledged, and, if so, it frees the resources.
 *
 * In the event callback, the original reference to the correlation structure (MsgInfo)
 * is passed in as an argument, and the event callback updates the info to 
 * indicate whether the message has been acknowledged.
 * 
 * For simplicity, this sample treats both message acceptance and 
 * rejection the same way: the message is freed. In real world 
 * applications, the client should decide what to do in the failure
 * scenario.
 *
 * The reason the message is not processed in the event callback 
 * in this sample is because it is not possible to make blocking 
 * calls from within the event callback. Consequently, if an application 
 * wanted to resend rejected messages it would have to avoid doing
 * this in the callback.
 *
 * Copyright 2012-2021 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import java.util.LinkedList;
import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class AdPubAck extends SampleApp {
	XMLMessageProducer prod = null;
	SessionConfiguration conf = null;
	int count = 1;

	void createSession(String[] args) {
		// Parse command-line arguments
		ArgParser parser = new ArgParser();
		if (parser.parse(args) == 0)
			conf = parser.getConfig();
		else
			printUsage(parser.isSecure());

		this.count = 1;
		String strCount = conf.getArgBag().get("-n");
		if (strCount != null) {
			try {
				count = Integer.valueOf(strCount);
			} catch (NumberFormatException e) {
				printUsage(parser.isSecure());
			}
		}
		session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
	}

	void printUsage(boolean secure) {
		String strusage = ArgParser.getCommonUsage(secure);
		strusage += "This sample:\n";
		strusage += "\t[-n number]\t Number of messages to publish, default: 1\n";
		System.out.println(strusage);
		finish(1);
	}

	/*
	 * A correlation structure. This structure is passed back to the
	 * publisher event callback when the message is acknowledged or rejected.
	 */
	class MsgInfo {
		public volatile boolean acked = false;
		public BytesXMLMessage sessionIndependentMessage = null;
		public final long id;

		public MsgInfo(long id) {
			this.id = id;
		}

		@Override
		public String toString() {
			return String.valueOf(this.id);
		}
	}

	/*
	 * A streaming producer can provide this callback handler to handle ack
	 * events.
	 */
	class PubCallback implements JCSMPStreamingPublishCorrelatingEventHandler {

		public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
			if (key instanceof MsgInfo) {
				MsgInfo i = (MsgInfo) key;
				i.acked = true;
				System.out.printf("Message response (rejected) received for %s, error was %s \n", i, cause);
			}
		}

		public void responseReceivedEx(Object key) {
			if (key instanceof MsgInfo) {
				MsgInfo i = (MsgInfo) key;
				i.acked = true;
				System.out.printf("Message response (accepted) received for %s \n", i);
			}
		}
	}

	public void run(String[] args) {
		createSession(args);

		try {
			// Acquire a message producer and open the data channel to
			// the broker.
			System.out.println("About to connect to broker.");
	        session.connect();
			prod = session.getMessageProducer(new PubCallback());
			printRouterInfo();
			System.out.println("Connected!");

			final LinkedList<MsgInfo> msgList = new LinkedList<MsgInfo>();
			final Runnable processList = new Runnable() {
				public void run() {
					while (msgList.peek() != null && msgList.peek().acked) {
						final MsgInfo ackedMsgInfo = msgList.poll();
						System.out.printf("Removing acknowledged message (%s) from application list.\n", ackedMsgInfo);
					}
				}
			};
			final Topic t = JCSMPFactory.onlyInstance().createTopic(SampleUtils.SAMPLE_TOPIC);

			for (int i = 0; i < count; i++) {
				// Generate a unique session-independent message.
				BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);

				// Set message properties (payload, delivery mode...).
				msg.writeAttachment(SampleUtils.attachmentText.getBytes());
				msg.setDeliveryMode(DeliveryMode.PERSISTENT);

				// The application can keep track of published messages using a
				// list. In this case, wrap the message in a MsgInfo instance, and
				// use it as a correlation key.
				final MsgInfo msgCorrelationInfo = new MsgInfo(i + 1);
				msgCorrelationInfo.sessionIndependentMessage = msg;
				msgList.add(msgCorrelationInfo);

				// Set the message's correlation key. This reference
				// is used when calling back to responseReceivedEx().
				msg.setCorrelationKey(msgCorrelationInfo);

				// Send the Guaranteed message.
				prod.send(msg, t);
				System.out.printf("Sent message (%s)\n", msgCorrelationInfo);

				// Sleep to send at 1 msg/sec and wait for responses.
				Thread.sleep(1000);

				// Remove (and forget) all acked messages.
				processList.run();
			}

			finish(0);
		} catch (JCSMPTransportException ex) {
			System.err.println("Encountered a JCSMPTransportException, closing producer... " + ex.getMessage());
			if (prod != null) {
				prod.close();
				// At this point the producer handle is unusable, a new one
				// should be created by calling 
				// prod = session.getMessageProducer(...) if the
				// application logic requires the producer channel
				// to remain open.
			}
			finish(1);
		} catch (JCSMPException ex) {
			System.err.println("Encountered a JCSMPException, closing producer channel... " + ex.getMessage());
			// Possible causes:
			// - Authentication error: invalid username/password
			// - Provisioning error: publisher not entitled is a common error in
			// this category
			// - Invalid or unsupported properties specified
			if (prod != null) {
				prod.close();
				// At this point the producer handle is unusable; a new one
				// should be created by calling 
				// prod = session.getMessageProducer(...) if the
				// application logic requires the producer channel to
				// remain open.
			}
			finish(1);
		} catch (InterruptedException e) {
		} catch (Exception ex) {
			System.err.println("Encountered an Exception... " + ex.getMessage());
			finish(1);
		}
	}

	public static void main(String[] args) {
		AdPubAck app = new AdPubAck();
		app.run(args);
	}

}
