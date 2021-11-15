/**
 * RRDirectRequester.java
 * 
 * This sample shows how to implement a Requester for direct Request-Reply messaging, where 
 *
 *    RRDirectRequester: A message Endpoint that sends a request message and waits to receive
 *                       a reply message as a response.
 *    RRDirectReplier:   A message Endpoint that waits to receive a request message and responses
 *                       to it by sending a reply message.
 * 
 *  |-------------------|  ---RequestTopic --> |------------------|
 *  | RRDirectRequester |                      | RRDirectReplier  |
 *  |-------------------|  <--ReplyToTopic---- |------------------|
 *
 * Copyright 2013-2021 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import java.util.Map;
import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPRequestTimeoutException;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.Requestor;
import com.solacesystems.jcsmp.SDTStream;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;

public class RRDirectRequester extends SampleApp {
    JCSMPSession session = null;
    SessionConfiguration conf = null;
    
    // Format for the arithmetic operation
    private final String ARITHMETIC_EXPRESSION = "\t=================================\n\t  %d %s %d = %s  \t\n\t=================================\n";
    
    enum Operation {
        PLUS,
        MINUS,
        TIMES,
        DIVIDE
    }
    
    Operation getOperationEnum(byte ordinal) throws Exception {
        switch(ordinal) {
        case 1:
            return Operation.PLUS;
        case 2:
            return Operation.MINUS;
        case 3:
            return Operation.TIMES;
        case 4:
            return Operation.DIVIDE;
        default:
            throw new Exception("Unknown operation value");
        }
    }
    
    //Time to wait for a reply before timing out
    private int timeoutMs = 2000;
    
    public RRDirectRequester() {
        
    }
    
    byte getOperationOrdinal(Operation operation) throws Exception {
        switch(operation) {
        case PLUS:
            return 1;
        case MINUS:
            return 2;
        case TIMES:
            return 3;
        case DIVIDE:
            return 4;
        default:
            throw new Exception("Unknown operation value");
        }
    }
    
    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        System.out.println(strusage);
        System.out.println("Extra arguments for this sample:");
        System.out.println("\t -rt \t the topic to send the request message to (RRDirectReplier should be listeneing on the same topic)\n");
    }
    
    public void doRequest(String requestTopic, Operation operation, int leftHandOperand, int rightHandOperand) throws Exception {
        StreamMessage request = JCSMPFactory.onlyInstance().createMessage(StreamMessage.class);
        SDTStream stream = JCSMPFactory.onlyInstance().createStream();
        
        stream.writeByte(getOperationOrdinal(operation));
        stream.writeInteger(leftHandOperand);
        stream.writeInteger(rightHandOperand);
        request.setStream(stream);
        request.setDeliveryMode(DeliveryMode.DIRECT);
        
        Requestor requestor = session.createRequestor();
        Topic topic = JCSMPFactory.onlyInstance().createTopic(requestTopic);
        BytesXMLMessage message = requestor.request(request, timeoutMs, topic);
        if (message != null) {
            if (message instanceof StreamMessage) {
                StreamMessage replyMessage = (StreamMessage) message;
                stream = replyMessage.getStream();
                if (stream.readBoolean()) {
                    System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, Double.toString(stream.readDouble())));
                } else {
                    System.out.println(String.format(ARITHMETIC_EXPRESSION, leftHandOperand, operation.toString(), rightHandOperand, "operation failed"));
                }
            } else {
                System.out.println("operation failed");
            }
        }
    }
    
    public void run(String[] args) {
        String requestTopic = null;
        // Parse command-line arguments.
        ArgParser parser = new ArgParser();
        if (parser.parse(args) != 0) {
            printUsage(parser.isSecure());
        } else {
            conf = parser.getConfig();
        }
        if (conf == null)
            finish(1);
        Map<String,String> extraArguments = conf.getArgBag();
        if (extraArguments.containsKey("-rt")) {
            requestTopic = extraArguments.get("-rt");
        }

        if (requestTopic == null) {
            System.out.println("This sample requires -rt to be specified\n");
            printUsage(parser.isSecure());
            finish(1);
        }

     // Create a new Session. The Session properties are extracted from the
        // SessionConfiguration that was populated by the command line parser.
        //
        // Note: In other samples, a common method is used to create the Sessions.
        // However, to emphasize the most basic properties for Session creation,
        // this method is directly included in this sample.
        try {
            // Create session from JCSMPProperties. Validation is performed by
            // the API, and it throws InvalidPropertiesException upon failure.
            System.out.println("About to create session.");
            System.out.println("Configuration: " + conf.toString());            
            
            JCSMPProperties properties = new JCSMPProperties();

            properties.setProperty(JCSMPProperties.HOST, conf.getHost());
            properties.setProperty(JCSMPProperties.USERNAME, conf.getRouterUserVpn().get_user());
            
            if (conf.getRouterUserVpn().get_vpn() != null) {
                properties.setProperty(JCSMPProperties.VPN_NAME, conf.getRouterUserVpn().get_vpn());
            }
            
            properties.setProperty(JCSMPProperties.PASSWORD, conf.getRouterPassword());
            
            // With reapply subscriptions enabled, the API maintains a
            // cache of added subscriptions in memory. These subscriptions
            // are automatically reapplied following a channel reconnect.
            properties.setBooleanProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);

            // Disable certificate checking
            properties.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);

            // Channel properties
            JCSMPChannelProperties cp = (JCSMPChannelProperties) properties
                .getProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES);
            
            if (conf.isCompression()) {
                // Compression is set as a number from 0-9 where 0 means "disable
                // compression" and 9 means max compression. The default is no
                // compression.
                // Selecting a non-zero compression level auto-selects the
                // compressed SMF port on the appliance, as long as no SMF port is
                // explicitly specified.
                cp.setCompressionLevel(9);
            }
            
            session =  JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();
            
            //This will have the session create the producer and consumer required
            //by the Requestor used below.
            session.getMessageProducer(new PrintingPubCallback());
            XMLMessageConsumer consumer = session.getMessageConsumer((XMLMessageListener)null);
            consumer.start();
        } catch (InvalidPropertiesException ipe) {
            System.err.println("Error during session creation: ");
            ipe.printStackTrace();
            finish(1);
        } catch (JCSMPException e) {
            e.printStackTrace();
            finish(1);
        }
        
        try {
            doRequest(requestTopic, Operation.PLUS, 5, 4);
            Thread.sleep(2000);
            doRequest(requestTopic, Operation.MINUS, 5, 4);
            Thread.sleep(2000);
            doRequest(requestTopic, Operation.TIMES, 5, 4);
            Thread.sleep(2000);
            doRequest(requestTopic, Operation.DIVIDE, 5, 4);
        } catch (JCSMPRequestTimeoutException e) {
            System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (session != null) {
                session.closeSession();
            }
        }
        
    }
    
    public static void main(String[] args) {
        RRDirectRequester instance = new RRDirectRequester();
        
        instance.run(args);
    }
}
