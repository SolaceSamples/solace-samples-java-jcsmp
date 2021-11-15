/**
 * RRGuaranteedRequester.java
 * 
 * This sample shows how to implement a Requester for guaranteed Request-Reply messaging, where 
 *
 *    RRGuaranteedRequester: A message Endpoint that sends a guaranteed request message and waits
 *                           to receive a reply message as a response.
 *    RRGuaranteedReplier:   A message Endpoint that waits to receive a request message and responses
 *                           to it by sending a guaranteed reply message.
 *             
 *  |-----------------------|  -- RequestQueue/RequestTopic --> |----------------------|
 *  | RRGuaranteedRequester |                                   | RRGuaranteedReplier  |
 *  |-----------------------|  <-------- ReplyQueue ----------  |----------------------|
 *
 * Notes: the RRGuaranteedReplier supports request queue or topic formats, but not both at the same time.
 *
 * Copyright 2013-2021 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import java.util.Map;
import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.SDTStream;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class RRGuaranteedRequester extends SampleApp {
    JCSMPSession session = null;
    SessionConfiguration conf = null;
    FlowReceiver flow = null;
    XMLMessageProducer prod = null;
    String requestTopic = null;
    String requestQueue = null;
    
    // Format for the arithmetic operation
    private final String ARITHMETIC_EXPRESSION = "\t=================================\n\t  %d %s %d = %s  \t\n\t=================================\n";
    
    enum Operation {
        PLUS,
        MINUS,
        TIMES,
        DIVIDE
    }

    //Time to wait for a reply before timing out
    private int timeoutMs = 2000;
    
    public RRGuaranteedRequester() {
        
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
        System.out.println("\t One of the following options: \n");
        System.out.println("\t -rt \t the topic to send the request message to (RRGuaranteedReplier should be listening on the same topic)\n");
        System.out.println("\t -rq \t the queue to send the request message to (RRGuaranteedReplier should be listening on the same queue)");
    }
    
    void doRequest(Destination requestDestination, Operation operation, int leftHandOperand, int rightHandOperand) throws Exception {
        Queue replyQueue = session.createTemporaryQueue();
        
        ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
        flowProps.setEndpoint(replyQueue);
        flow = session.createFlow(null, flowProps);
        flow.start();

        StreamMessage request = JCSMPFactory.onlyInstance().createMessage(StreamMessage.class);
        SDTStream stream = JCSMPFactory.onlyInstance().createStream();
        stream.writeByte(getOperationOrdinal(operation));
        stream.writeInteger(leftHandOperand);
        stream.writeInteger(rightHandOperand);
        request.setStream(stream);
        request.setDeliveryMode(DeliveryMode.PERSISTENT);
        request.setReplyTo(replyQueue);
        
        XMLMessageProducer messageProducer = session.getMessageProducer(new PrintingPubCallback());
        messageProducer.send(request, requestDestination);
        
        BytesXMLMessage message = flow.receive(timeoutMs);
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
        } else {
            System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
        }
    }
    
    public void run(String[] args) {
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
        if (extraArguments.containsKey("-rq")) {
            requestQueue = extraArguments.get("-rq");
        }
        if (requestTopic == null && requestQueue == null) {
            System.out.println("This sample requires -rt or -rq to be specified\n");
            printUsage(parser.isSecure());
            finish(1);
        }
        if (requestTopic != null && requestQueue != null) {
            System.out.println("You must specify -rt or -rq but not both\n");
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
            
        } catch (InvalidPropertiesException ipe) {
            System.err.println("Error during session creation: ");
            ipe.printStackTrace();
            finish(1);
        } catch (JCSMPException e) {
            e.printStackTrace();
            finish(1);
        }
        
        
        Destination requestDestination = null;
        if (requestTopic != null) {
            requestDestination = JCSMPFactory.onlyInstance().createTopic(requestTopic);
        }
        if (requestQueue != null) {
            requestDestination = JCSMPFactory.onlyInstance().createQueue(requestQueue);
        }
        try {
            doRequest(requestDestination, Operation.PLUS, 5, 4);
            Thread.sleep(1000);
            doRequest(requestDestination, Operation.MINUS, 5, 4);
            Thread.sleep(1000);
            doRequest(requestDestination, Operation.TIMES, 5, 4);
            Thread.sleep(1000);
            doRequest(requestDestination, Operation.DIVIDE, 5, 4);
        } catch (JCSMPException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (flow != null) {
                flow.close();
            }
            if (session != null) {
                session.closeSession();
            }
        }
    }
    
    public static void main(String[] args) {
        RRGuaranteedRequester instance = new RRGuaranteedRequester();
        
        instance.run(args);
    }
}
