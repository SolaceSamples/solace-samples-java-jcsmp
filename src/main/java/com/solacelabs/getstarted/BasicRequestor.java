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

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPRequestTimeoutException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishEventHandler;
import com.solacesystems.jcsmp.Requestor;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class BasicRequestor {
    
    public static void main(String... args) throws JCSMPException {
    	// Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: BasicRequestor <msg_backbone_ip:port>");
            System.exit(-1);
        }
        System.out.println("BasicRequestor initializing...");

    	// Create a JCSMP Session
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);      // msg-backbone ip:port
        properties.setProperty(JCSMPProperties.VPN_NAME, "default"); // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, "clientUsername"); // client-username (assumes no password)
        final JCSMPSession session =  JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();
        
        //This will have the session create the producer and consumer required
        //by the Requestor used below.
        
        /** Anonymous inner-class for handling publishing events */
        @SuppressWarnings("unused")
		XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
            public void responseReceived(String messageID) {
                System.out.println("Producer received response for msg: " + messageID);
            }
            public void handleError(String messageID, JCSMPException e, long timestamp) {
                System.out.printf("Producer received error for msg: %s@%s - %s%n",
                        messageID,timestamp,e);
            }
        });
        
        XMLMessageConsumer consumer = session.getMessageConsumer((XMLMessageListener)null);
        consumer.start();
        
        final Topic topic = JCSMPFactory.onlyInstance().createTopic("tutorial/requests");
        
        //Time to wait for a reply before timing out
        final int timeoutMs = 10000;
        TextMessage request = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        final String text = "Sample Request";
        request.setText(text);
        
        try {
        	Requestor requestor = session.createRequestor();
        	System.out.printf("Connected. About to send request message '%s' to topic '%s'...%n",text,topic.getName());
            BytesXMLMessage reply = requestor.request(request, timeoutMs, topic);
            
            // Process the reply
        	if (reply instanceof TextMessage) {
                System.out.printf("TextMessage response received: '%s'%n",
                        ((TextMessage)reply).getText());
            } 
            System.out.printf("Response Message Dump:%n%s%n",reply.dump());
        } catch (JCSMPRequestTimeoutException e) {
            System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
        }
        
        System.out.println("Exiting...");
        session.closeSession();
    }
}
