/**
 * RRDirectReplier.java
 * 
 * This sample shows how to implement a Requester for direct Request-Reply messaging, where 
 *
 *    RRDirectRequester: A message Endpoint that sends a request message and waits to receive a
 *                       reply message as a response.
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
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.SDTException;
import com.solacesystems.jcsmp.SDTStream;
import com.solacesystems.jcsmp.StreamMessage;
import com.solacesystems.jcsmp.XMLMessage;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class RRDirectReplier extends SampleApp {
    JCSMPSession session = null;
    SessionConfiguration conf = null;
    XMLMessageProducer producer = null;
    XMLMessageConsumer consumer = null;

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
    
    public RRDirectReplier() {
    }
    
    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        System.out.println(strusage);
        System.out.println("Extra arguments for this sample:");
        System.out.println("\t -rt \t the topic to send the request message to (RRDirectReplier should be listeneing on the same topic)\n");
    }
    
    class RequestHandler implements XMLMessageListener {
        //Create a success reply with a result
        private XMLMessage createReplyMessage(BytesXMLMessage request, double result) {
            StreamMessage replyMessage = JCSMPFactory.onlyInstance().createMessage(StreamMessage.class);
            SDTStream stream = JCSMPFactory.onlyInstance().createStream();
            //We have a result, thus we indicate a success
            stream.writeBoolean(true);
            stream.writeDouble(result);
            replyMessage.setStream(stream);
            replyMessage.setDeliveryMode(DeliveryMode.DIRECT);
            
            return replyMessage;
        }
        
        //Create a failure reply with no result
        private XMLMessage createReplyMessage(BytesXMLMessage request) throws JCSMPException {
            StreamMessage replyMessage = JCSMPFactory.onlyInstance().createMessage(StreamMessage.class);
            SDTStream stream = JCSMPFactory.onlyInstance().createStream();
            //We do not have a result, thus we indicate a failure
            stream.writeBoolean(false);
            replyMessage.setStream(stream);
            replyMessage.setDeliveryMode(DeliveryMode.DIRECT);
            
            return replyMessage;
        }
        
        //Reply to a request
        private void sendReply(XMLMessage request, XMLMessage reply) throws JCSMPException {
            producer.sendReply(request, reply);
        }
        
        public void onReceive(BytesXMLMessage message) {
            System.out.println("Received request message, trying to parse it");
            
            if (message instanceof StreamMessage) {
                StreamMessage request = (StreamMessage) message;
                SDTStream stream = request.getStream();
                try {
                    byte operationOrdinal = stream.readByte();
                    
                    //Retrieve operands
                    int leftOperand = stream.readInteger();
                    int rightOperand = stream.readInteger();
                    
                    Operation operation = null;
                    try {
                        operation = getOperationEnum(operationOrdinal);
                    } catch (Exception e) {
                        //This is an invalid operation
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, "UNKNOWN", rightOperand, "operation failed"));
                        XMLMessage reply = createReplyMessage(request);
                        sendReply(request, reply);
                        return;
                    }
                    
                    //Compute the operation
                    double result = computeOperation(operation, leftOperand, rightOperand);
                    
                    //Print the result and send it.
                    if (Double.isInfinite(result) || Double.isNaN(result)) {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, operation.toString(), rightOperand, "operation failed"));
                        
                        XMLMessage reply = createReplyMessage(request); 
                        sendReply(request, reply);
                    } else {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, operation.toString(), rightOperand, Double.toString(result)));
                        
                        XMLMessage reply = createReplyMessage(request, result);
                        sendReply(request, reply);
                    }
                    
                } catch (SDTException e) {
                    System.out.println("operation failed : Invalid request message");
                    System.out.println("Here's a message dump:" + request.toString());
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            } else {
                System.out.println("Failed to parse the request message, here's a message dump:" + message.toString());
            }
        }
        
        public void onException(JCSMPException exception) {
            exception.printStackTrace();
        }
        
        private double computeOperation(Operation operation, int leftOperand, int rightOperand) throws Exception {
            switch(operation) {
                case PLUS:
                    return (double)(leftOperand + rightOperand);
                case MINUS:
                    return (double)(leftOperand - rightOperand);
                case TIMES:
                    return (double)(leftOperand * rightOperand);
                case DIVIDE:
                    return (double)leftOperand / (double)rightOperand;
                default:
                    throw new Exception("Unkown operation");
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

            session = JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();

        } catch (InvalidPropertiesException ipe) {
            System.err.println("Error during session creation: ");
            ipe.printStackTrace();
            finish(1);
        } catch (JCSMPException e) {
            e.printStackTrace();
            finish(1);
        }

        try {
            consumer = session.getMessageConsumer(new RequestHandler());
            producer = session.getMessageProducer(new PrintingPubCallback());
            consumer.start();
            session.addSubscription(JCSMPFactory.onlyInstance().createTopic(requestTopic), true);
            
            System.out.println("Listening for request messages ... Press enter to exit");
            System.in.read();
            
        } catch (JCSMPException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (consumer != null) {
                consumer.close();
            }
            if (session != null) {
                session.closeSession();
            }
        }
    }

    public static void main(String[] args) {
        RRDirectReplier instance = new RRDirectReplier();

        instance.run(args);
    }
}
