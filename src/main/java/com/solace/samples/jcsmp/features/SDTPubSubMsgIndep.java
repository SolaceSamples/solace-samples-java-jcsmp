/**
 * SDTPubSubMsgIndep.java
 *
 * This sample demonstrates:
 *  - Subscribing to a Topic.
 *  - Publishing structured data type (SDT) stream messages to the Topic.
 * 
 * This sample publishes stream messages on a Topic and dumps
 * the received messages to System.out.
 * Note that the SDTMap and SDTStream can be reused and modified for 
 * multiple sends.
 * 
 * Message-independent streams and map can be reused in 
 * multiple messages. The client application is responsible for 
 * managing the memory that is allocated for message-independent 
 * streams and maps.
 *
 * Copyright 2006-2021 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

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
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.SDTStream;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class SDTPubSubMsgIndep extends SampleApp implements
        JCSMPStreamingPublishCorrelatingEventHandler, XMLMessageListener {

    Topic topic = null;
    XMLMessageProducer prod = null;
    XMLMessageConsumer cons = null;
    SessionConfiguration conf = null;
    
    void createSession(String[] args) {
        // Parse command-line arguments.
        ArgParser parser = new ArgParser();
        if (parser.parse(args) == 0)
            conf = parser.getConfig();
        else
            printUsage(parser.isSecure());
        
        if (conf.getDeliveryMode() == null)
            conf.setDeliveryMode(DeliveryMode.DIRECT);
        session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
    }

    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        System.out.println(strusage);
        finish(1);
    }
    
    public static void main(String[] args) {
        SDTPubSubMsgIndep bpubsub = new SDTPubSubMsgIndep();
        bpubsub.run(args);
    }

    public SDTPubSubMsgIndep() {
    }

    public void handleErrorEx(Object key, JCSMPException cause,
            long timestamp) {
        System.err.println("Error occurred for message: " + key);
        cause.printStackTrace();
    }

    public void responseReceivedEx(Object key) {
    }

    public void onException(JCSMPException exception) {
        exception.printStackTrace();
    }

    public void onReceive(BytesXMLMessage message) {
    	System.out.println("Received message...");
        System.out.println(message.dump());
        System.out.println("------");
    }

    void run(String[] args) {
        int status = 0;
        createSession(args);
        try {
            // Acquire a blocking message producer and open the data channel to
            // the appliance.
            topic = JCSMPFactory.onlyInstance().createTopic(SampleUtils.SAMPLE_TOPIC); 
            System.out.println("About to connect to appliance.");
	        session.connect();
            prod = session.getMessageProducer(this);
            cons = session.getMessageConsumer(this);
            cons.start();
            printRouterInfo();
            System.out.println("Connected!");

            // Add the Topic subscription.
            session.addSubscription(topic);

			/*
			 * This sample uses SDT data which can impact performance. Here we
			 * demonstrate the use of SDTStream and SDTMap, the latter to send
			 * user properties in the message.
			 */
            SDTStream stream = JCSMPFactory.onlyInstance().createStream();
            stream.writeDouble(3.141592654);
            stream.writeString("message");
            
            // Set a user properties map.
            SDTMap map = JCSMPFactory.onlyInstance().createMap();
            map.putInteger("mersenne", 43112609);

            final int numMsgsToSend = 10;
            System.out.println("About to send " + numMsgsToSend +  " messages ...");
            for (int i = 0; i < numMsgsToSend; i++) {
                // Create the message.
                StreamMessage streamMsg = JCSMPFactory.onlyInstance().createMessage(StreamMessage.class);
                
                // Set the message delivery mode.
                streamMsg.setDeliveryMode(DeliveryMode.DIRECT);
                
                // Overwrite the "message" field, set the map and send.
                map.putString("message", "message" + (i+1));
                stream.writeInteger(i + 1);
                
                streamMsg.setProperties(map);
                streamMsg.setStream(stream);
                prod.send(streamMsg, topic);
            }
            
            Thread.sleep(500); // wait for 0.5 seconds
            System.out.println("\nDone");
        } catch (JCSMPTransportException ex) {
            System.err.println("Encountered a JCSMPTransportException, closing producer and consumer... " + ex.getMessage());
            status = 1;
        } catch (JCSMPException ex) {
            System.err.println("Encountered a JCSMPException, closing producer and consumer... " + ex.getMessage());
            // Possible causes: 
            // - Authentication error: invalid username/password 
            // - Provisioning error: publisher not entitled is a common error in this category 
            // - Invalid or unsupported properties specified
            status = 1;
        } catch (Exception ex) {
            System.err.println("Encountered an Exception... " + ex.getMessage());
            status = 1;
        } finally {
            // Remove the subscription.
            if ((session != null) && (topic != null)) {
                try {
                    session.removeSubscription(topic);
                } catch(JCSMPException e) {
                    // ignore errors
                }
            }
            if (prod != null) {
                prod.close();
                // At this point the producer handle is unusable; a new one should be created 
                // by calling prod = session.getMessageProducer(...) if the application 
                // logic requires the producer channel to remain open.
            }
            if (cons != null) {
                cons.close();
                // At this point the consumer handle is unusable; a new one should be created 
                // by calling cons = session.getMessageConsumer(...) if the application 
                // logic requires the consumer channel to remain open.
            }
        }
        finish(status);
    }
}
