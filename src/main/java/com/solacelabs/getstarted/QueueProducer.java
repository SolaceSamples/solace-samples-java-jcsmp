/**
 *  Copyright 2015-2016 Solace Systems, Inc. All rights reserved.
 * 
 *  http://www.solacesystems.com
 * 
 *  This source is distributed under the terms and conditions of
 *  any contract or license agreement between Solace Systems, Inc.
 *  ("Solace") and you or your company. If there are no licenses or
 *  contracts in place use of this source is not authorized. This 
 *  source is provided as is and is not supported by Solace unless
 *  such support is provided for under an agreement signed between 
 *  you and Solace.
 */

package com.solacelabs.getstarted;

import java.text.DateFormat;
import java.util.Date;

import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishEventHandler;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class QueueProducer {

    public static void main(String... args) throws JCSMPException, InterruptedException {
        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: QueueProducer <msg_backbone_ip:port>");
            System.out.println();
            System.exit(-1);
        }
        System.out.println("QueueProducer initializing...");
        // Create a JCSMP Session
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);  // msg-backbone ip:port
        properties.setProperty(JCSMPProperties.VPN_NAME, "default"); // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, "queueTutorial"); // client-username (assumes no password)
        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();

        String queueName = "Q/tutorial";
        System.out.printf("Attempting to provision the queue '%s' on the appliance.%n", queueName);
		final EndpointProperties endpointProps = new EndpointProperties();
		// set queue permissions to "consume" and access-type to "exclusive"
		endpointProps.setPermission(EndpointProperties.PERMISSION_CONSUME);
		endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
		// create the queue object locally
		final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
        // Actually provision it, and do not fail if it already exists
		session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
        
        /** Anonymous inner-class for handling publishing events */
        final XMLMessageProducer prod = session.getMessageProducer(
                new JCSMPStreamingPublishEventHandler() {
                    public void responseReceived(String messageID) {
                        System.out.printf("Producer received response for msg ID #%s%n",messageID);
                    }
                    public void handleError(String messageID, JCSMPException e, long timestamp) {
                        System.out.printf("Producer received error for msg ID %s @ %s - %s%n",
                                messageID,timestamp,e);
                    }
                });

        // Publish-only session is now hooked up and running!
        System.out.printf("Connected. About to send message to queue '%s'...%n",queue.getName());
        
        TextMessage msg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        msg.setDeliveryMode(DeliveryMode.PERSISTENT);
        String text = "Persistent Queue Tutorial! "+DateFormat.getDateTimeInstance().format(new Date());
        msg.setText(text);
        
        // Send message directly to the queue
        prod.send(msg, queue);
        System.out.println("Message sent. Exiting.");

        // Close session
        session.closeSession();
    }
}
