/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.solacelabs.getstarted;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class ConfirmedPublish {

	final int count = 5;
    final CountDownLatch latch = new CountDownLatch(count); // used for synchronizing b/w threads

	/*
	 * A correlation structure. This structure is passed back to the
	 * publisher callback when the message is acknowledged or rejected.
	 */
	class MsgInfo {
		public volatile boolean acked = false;
		public volatile boolean publishedSuccessfully = false;
		public BytesXMLMessage sessionIndependentMessage = null;
		public final long id;

		public MsgInfo(long id) {
			this.id = id;
		}

		@Override
		public String toString() {
			return String.format("Message ID: %d, PubConf: %b, PubSuccessful: %b", id, acked, publishedSuccessfully);
		}
	}

	/*
	 * A streaming producer can provide this callback handler to handle acknowledgement
	 * events.
	 */
	class PubCallback implements JCSMPStreamingPublishCorrelatingEventHandler {

		public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
			if (key instanceof MsgInfo) {
				MsgInfo i = (MsgInfo) key;
				i.acked = true;
				System.out.printf("Message response (rejected) received for %s, error was %s \n", i, cause);
			}
			latch.countDown();
		}

		public void responseReceivedEx(Object key) {
			if (key instanceof MsgInfo) {
				MsgInfo i = (MsgInfo) key;
				i.acked = true;
				i.publishedSuccessfully = true;
				System.out.printf("Message response (accepted) received for %s \n", i);
			}
			latch.countDown();
		}

		public void handleError(String messageID, JCSMPException cause, long timestamp) {
			// Never called
		}

		public void responseReceived(String messageID) {
			// Never called
		}
	}

	public void run(String... args) throws JCSMPException, InterruptedException {
		final LinkedList<MsgInfo> msgList = new LinkedList<MsgInfo>();

        System.out.println("ConfirmedPublish initializing...");
        // Create a JCSMP Session
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);  // msg-backbone ip:port
        properties.setProperty(JCSMPProperties.VPN_NAME, "default"); // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, "queueTutorial"); // client-username (assumes no password)
        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();

        String queueName = "Q/tutorial";
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);

        /** Correlating event handler */
        final XMLMessageProducer prod = session.getMessageProducer(new PubCallback());

        // Publish-only session is now hooked up and running!
        System.out.printf("Connected. About to send " + count + " messages to queue '%s'...%n",queue.getName());

        for (int i = 1; i <= count; i++) {
	        TextMessage msg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
	        msg.setDeliveryMode(DeliveryMode.PERSISTENT);
	        String text = "Confirmed Publish Tutorial! Message ID: "+ i;
	        msg.setText(text);

	        // The application will wait and confirm the message is published successfully.
	        // In this case, wrap the message in a MsgInfo instance, and
			// use it as a correlation key.
			final MsgInfo msgCorrelationInfo = new MsgInfo(i);
			msgCorrelationInfo.sessionIndependentMessage = msg;
			msgList.add(msgCorrelationInfo);

			// Set the message's correlation key. This reference
			// is used when calling back to responseReceivedEx().
			msg.setCorrelationKey(msgCorrelationInfo);

	        // Send message directly to the queue
	        prod.send(msg, queue);
        }
        System.out.println("Messages sent. Processing replies.");
        try {
            latch.await(); // block here until message received, and latch will flip
        } catch (InterruptedException e) {
            System.out.println("I was awoken while waiting");
        }

        // Process the replies
        while (msgList.peek() != null) {
			final MsgInfo ackedMsgInfo = msgList.poll();
			System.out.printf("Removing acknowledged message (%s) from application list.\n", ackedMsgInfo);
		}

        // Close session
        session.closeSession();
	}

    public static void main(String... args) throws JCSMPException, InterruptedException {
    	// Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: ConfirmedPublish <msg_backbone_ip:port>");
            System.out.println();
            System.exit(-1);
        }

    	ConfirmedPublish app = new ConfirmedPublish();
		app.run(args);



    }
}
