/**
 * Transactions.java
 * 
 * This sample uses a simple request/reply scenario to show the use of transactions.
 * 
 * A requestor sends a request to a queue the replier is bound to.
 * A replier receives the message and replies with a message sent to a queue
 * the requestor is bound to. 
 * 
 * Copyright 2009-2018 Solace Corporation. All rights reserved.
 */

package com.solace.samples.features;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPStreamingPublishEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.ProducerFlowProperties;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;
import com.solace.samples.features.common.ArgParser;
import com.solace.samples.features.common.SampleApp;
import com.solace.samples.features.common.SampleUtils;
import com.solace.samples.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.transaction.RollbackException;
import com.solacesystems.jcsmp.transaction.TransactedSession;

public class Transactions extends SampleApp implements JCSMPStreamingPublishEventHandler {

    public class Requestor implements JCSMPStreamingPublishEventHandler {
        public TransactedSession txSession;
        public XMLMessageProducer producer;
        public FlowReceiver receiver;
        public Queue queue;
        
        public Requestor() throws JCSMPException {
            txSession = session.createTransactedSession();

            queue = session.createTemporaryQueue();
            
            ProducerFlowProperties prodFlowProps = new ProducerFlowProperties();
            prodFlowProps.setWindowSize(100);
            producer = txSession.createProducer(prodFlowProps, this);
            
            ConsumerFlowProperties consFlowProps = new ConsumerFlowProperties();
            consFlowProps.setEndpoint(queue);
            consFlowProps.setStartState(true);
            EndpointProperties endpointProps = new EndpointProperties();
            endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
            receiver = txSession.createFlow(null, consFlowProps, endpointProps);
        }
        
        public void handleError(String messageID, JCSMPException cause,
                long timestamp) {
            System.err.println("Requestor handleError " + cause);
            cause.printStackTrace();
        }

        public void responseReceived(String messageID) {
            // Do Nothing
        } 
    }

    public class Replier implements JCSMPStreamingPublishEventHandler, XMLMessageListener {
        public TransactedSession txSession;
        public XMLMessageProducer producer;
        public FlowReceiver receiver;
        public Queue queue;
        
        public Replier() throws JCSMPException {
            txSession = session.createTransactedSession();

            queue = session.createTemporaryQueue();
            
            ProducerFlowProperties prodFlowProps = new ProducerFlowProperties();
            prodFlowProps.setWindowSize(100);
            producer = txSession.createProducer(prodFlowProps, this);
            
            ConsumerFlowProperties consFlowProps = new ConsumerFlowProperties();
            consFlowProps.setEndpoint(queue);
            consFlowProps.setStartState(true);
            EndpointProperties endpointProps = new EndpointProperties();
            endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
            receiver = txSession.createFlow(this, consFlowProps, endpointProps);
        }
        
        public void onReceive(BytesXMLMessage message) {
            try {
                System.out.println("Received Request Message From: " + message.getSenderId());
                BytesXMLMessage reply = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
                reply.setDeliveryMode(DeliveryMode.PERSISTENT);
                reply.setSenderId("Replier");
                producer.send(reply, message.getReplyTo());
                
                // this commit will acknowledge the received message and
                // deliver the sent message.
                txSession.commit();
            } catch (RollbackException e) {
                System.err.println("RollbackException: " + e);
                e.printStackTrace();
            } catch (JCSMPException e) {
                System.err.println("JCSMPException: " + e);
                e.printStackTrace();
            }
        }
        
        public void onException(JCSMPException exception) {
            System.err.println("Replier onException " + exception);
            exception.printStackTrace();            
        }
        
        public void handleError(String messageID, JCSMPException cause,
                long timestamp) {
            System.err.println("Replier handleError " + cause);
            cause.printStackTrace();
        }
        
        public void responseReceived(String messageID) {
            // Do Nothing
        }
    }
    
    private SessionConfiguration conf;


    public void handleError(String messageID, JCSMPException cause,
            long timestamp) {
        // Do Nothing
    }

    public void responseReceived(String messageID) {
        // Do Nothing
    }

    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        System.out.println(strusage);
        finish(1);
    }

    void createSession(String[] args) {
        // Parse command-line arguments.
        ArgParser parser = new ArgParser();
        if (parser.parse(args) == 0)
            conf = parser.getConfig();
        else
            printUsage(parser.isSecure());

        session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
    }

    public void run(String[] args) {
        createSession(args);
        try {
	        session.connect();
            session.getMessageProducer(this);

            // Create a "requestor" transacted session
            Requestor requestor = new Requestor();

            // Create a "replier" transacted session
            Replier replier = new Replier();
            
            // build and send a request to the Replier
            BytesXMLMessage request = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
            request.setDeliveryMode(DeliveryMode.PERSISTENT);
            request.setSenderId("Requestor");
            request.setReplyTo(requestor.queue);
            requestor.producer.send(request, replier.queue);

            // Need to commit to deliver the request message from a transacted session
            requestor.txSession.commit();

            // Wait for a reply from the replier
            BytesXMLMessage reply = requestor.receiver.receive(10000);
            if (reply == null) {
                System.err.println("Timeout waiting for reply");
            } else {
                System.out.println("Received Reply Message From: " + reply.getSenderId());
                // Make sure to commit to acknowledge receipt of the message.
                requestor.txSession.commit();
            }

            requestor.txSession.close();
            replier.txSession.close();
            
            finish(0);
        } catch (JCSMPTransportException ex) {
            System.err.println("Encountered a JCSMPTransportException, closing session... "
                + ex.getMessage());
            
            // At this point the producer and consumer handles are unusable
            // (closed). If application logic required their use, a new producer
            // and consumer should be acquired from the session.
            
            finish(1);
        } catch (JCSMPException ex) {
            System.err.println("Encountered a JCSMPException, closing session... "
                + ex.getMessage());
            // Possible causes:
            // - Authentication error: invalid username/password
            // - Provisioning error: publisher not entitled is a common error in
            // this category
            // - Invalid or unsupported properties specified
            finish(1);
        } catch (Exception ex) {
            System.err.println("Encountered an Exception... " + ex.getMessage());
            finish(1);
        }

    }

    public static void main(String[] args) {
        Transactions r = new Transactions();
        r.run(args);
    }
}
