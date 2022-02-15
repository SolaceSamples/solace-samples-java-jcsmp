/**
 * RRGuaranteedReplier.java
 * 
 * This sample shows how to implement a Replier for guaranteed Request-Reply messaging, where 
 *
 *    RRGuaranteedRequester: A message Endpoint that sends a guaranteed request message and waits to receive
 *                           a reply message as a response.
 *    RRGuaranteedReplier:   A message Endpoint that waits to receive a request message and responses to it 
 *                           by sending a guaranteed reply message.
 *             
 *  |-----------------------|  ---RequestQueue/RequestTopic --> |----------------------|
 *  | RRGuaranteedRequester |                                   | RRGuaranteedReplier  |
 *  |-----------------------|  <-------- ReplyQueue ----------  |----------------------|
 *
 * Notes: the RRGuaranteedReplier supports request queue or topic formats, but not both at the same time.
 *
 * Copyright 2013-2022 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import java.util.Calendar;
import java.util.Map;
import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.Endpoint;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowReceiver;
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
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class RRGuaranteedReplier extends SampleApp {
    JCSMPSession session = null;
    SessionConfiguration conf = null;
    XMLMessageProducer producer = null;
    String requestTopic = null;
    String requestQueue = null;
    FlowReceiver flow = null;
    
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
    
    public RRGuaranteedReplier() {
        
    }
       
    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        System.out.println(strusage);
        System.out.println("Extra arguments for this sample:\n");
        System.out.println("\t One of the following options:");
        System.out.println("\t -rt \t the topic to listen on (RRGuaranteedRequester should be sending the request message to the same topic)");
        System.out.println("\t -rq  \t the queue to listen on (RRGuaranteedRequester should be sending the request message to the same queue)\n");
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
            replyMessage.setDeliveryMode(DeliveryMode.PERSISTENT);
            replyMessage.setCorrelationKey(replyMessage);  // correlation key for receiving ACKs
            return replyMessage;
        }
        
        //Create a failure reply with no result
        private XMLMessage createReplyMessage(BytesXMLMessage request) throws JCSMPException {
            StreamMessage replyMessage = JCSMPFactory.onlyInstance().createMessage(StreamMessage.class);
            SDTStream stream = JCSMPFactory.onlyInstance().createStream();
            //We do not have a result, thus we indicate a failure
            stream.writeBoolean(false);
            replyMessage.setStream(stream);
            replyMessage.setDeliveryMode(DeliveryMode.PERSISTENT);
            replyMessage.setCorrelationKey(replyMessage);  // correlation key for receiving ACKs
            return replyMessage;
        }
        
        private void sendReply(XMLMessage request, XMLMessage reply) throws Exception {
            Destination replyDestination = null;
            replyDestination = request.getReplyTo();

            if (replyDestination == null) {
                System.out.println("Failed to parse the request message : Missing replyto destination.");
                System.out.println("Here's a message dump:" + request.toString());
                
                throw new Exception("Missing replyto destination");
            }            
            
            producer.send(reply, replyDestination);
        }
        
        public void onReceive(BytesXMLMessage message) {
            System.out.println("Received request message, trying to parse it");
            
            if (message instanceof StreamMessage) {
                StreamMessage request = (StreamMessage) message;
                SDTStream stream = request.getStream();
                try {
                    byte operationOrdinal = stream.readByte();
                    int leftOperand = stream.readInteger();
                    int rightOperand = stream.readInteger();

                    Operation operation = null;
                    try {
                        operation = getOperationEnum(operationOrdinal);
                    } catch(Exception e) {
                        System.out.println(String.format(ARITHMETIC_EXPRESSION, leftOperand, "UNKNOWN", rightOperand, "operation failed"));
                        XMLMessage reply = createReplyMessage(request);
                        sendReply(request, reply);
                        return;
                    }
                    
                    double result = computeOperation(operation, leftOperand, rightOperand);
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
       
       Endpoint requestEndpoint = null;
       EndpointProperties endpointProperties = new EndpointProperties();
       if (requestTopic != null) {
           requestEndpoint = JCSMPFactory.onlyInstance().createDurableTopicEndpoint("sample_" + Calendar.getInstance().getTimeInMillis());
       }
       if (requestQueue != null) {
           requestEndpoint = JCSMPFactory.onlyInstance().createQueue(requestQueue);
       }
       
       try {
           session.provision(requestEndpoint, endpointProperties, 0);
           
           ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
           flowProps.setEndpoint(requestEndpoint);
           if (requestTopic!=null) {
               flowProps.setNewSubscription(JCSMPFactory.onlyInstance().createTopic(requestTopic));
           }
           producer = session.getMessageProducer(new PrintingPubCallback());
           flow = session.createFlow(new RequestHandler(), flowProps);
           flow.start();

           System.out.println("Listening for request messages ... Press enter to exit");
           System.in.read();
           
           if (flow != null) {
               flow.close();
               session.deprovision(requestEndpoint, JCSMPSession.FLAG_IGNORE_DOES_NOT_EXIST);
           }
       } catch (JCSMPException e) {
           e.printStackTrace();
       } catch (Exception e) {
           e.printStackTrace();
       } finally {
           if (session != null) {
               session.closeSession();
           }
       }
   }
   
   public static void main(String[] args) {
       RRGuaranteedReplier instance = new RRGuaranteedReplier();
       
       instance.run(args);
   }
}
