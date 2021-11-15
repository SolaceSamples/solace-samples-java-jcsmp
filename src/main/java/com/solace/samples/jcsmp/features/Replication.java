/**
 * Replication.java
 *
 * This sample illustrates the use of an unacked list when using replication
 * with host lists.
 * 
 * Copyright 2012-2021 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import java.util.LinkedList;
import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solace.samples.jcsmp.features.common.SessionConfiguration.AuthenticationScheme;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.InvalidPropertiesException;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProducerEventHandler;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.ProducerEventArgs;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageProducer;

/**
 * 
 *
 */
public class Replication extends SampleApp implements JCSMPStreamingPublishCorrelatingEventHandler, JCSMPProducerEventHandler {
    
    public class UnackedList {
        
        private LinkedList<BytesXMLMessage> mylist = new LinkedList<BytesXMLMessage>();
        
        synchronized public void add(BytesXMLMessage msg) {
            mylist.add(msg);
        }
        
        synchronized public void remove(Object key) {
            for(int i = 0; i < mylist.size(); i++) {
                BytesXMLMessage msg = mylist.get(i);
                if ((msg.getCorrelationKey() != null) &&
                    (msg.getCorrelationKey().equals(key))) {
                    mylist.remove(i);
                    return;
                }
            }
            throw new IllegalArgumentException("Message for key \"" + key + "\" not found");
        }
        
        synchronized public BytesXMLMessage[] get() {
            BytesXMLMessage[] msgs = new BytesXMLMessage[mylist.size()];
            for(int i = 0; i < mylist.size(); i++) {
                msgs[i] = mylist.get(i);
            }
            return msgs;
        }
        
        synchronized public String toString() {
            if (mylist.size() == 0) {
                return "Unacked List Empty";
            } else {
                StringBuffer buf = new StringBuffer("UnackedList= ");
                for(int i = 0; i < mylist.size(); i++) {
                    if (i > 0) {
                        buf.append(",");
                    }
                    buf.append(mylist.get(i).getCorrelationKey());
                }
                return buf.toString();
            }
        }
    }

    XMLMessageProducer producer = null;
    SessionConfiguration conf = null;
    UnackedList unackedList = new UnackedList();
    private int numMsgResent = 0;
    private int numMsgsToSend = 100000;
    
    void createSession(String[] args) {
        // Parse command-line arguments
        ArgParser parser = new ArgParser();
        if (parser.parse(args) == 0)
            conf = parser.getConfig();
        else
            printUsage(parser.isSecure());
        
        JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, conf.getHost());
        properties.setProperty(JCSMPProperties.USERNAME, conf.getRouterUserVpn().get_user());
        if (conf.getRouterUserVpn().get_vpn() != null) {
            properties.setProperty(JCSMPProperties.VPN_NAME, conf.getRouterUserVpn().get_vpn());
        }
        properties.setProperty(JCSMPProperties.PASSWORD, conf.getRouterPassword());
        properties.setProperty(JCSMPProperties.MESSAGE_ACK_MODE, JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);
        properties.setProperty(JCSMPProperties.PUB_ACK_WINDOW_SIZE, 255);
        
        // Disable certificate checking
        properties.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);

        if (conf.getAuthenticationScheme().equals(AuthenticationScheme.BASIC)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_BASIC);   
        } else if (conf.getAuthenticationScheme().equals(AuthenticationScheme.KERBEROS)) {
            properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME, JCSMPProperties.AUTHENTICATION_SCHEME_GSS_KRB);   
        }

        // Channel properties
        JCSMPChannelProperties cp = (JCSMPChannelProperties) properties
            .getProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES);
        if (conf.isCompression()) {
            /*
             * Compression is set as a number from 0-9. 0 means
             * "disable compression" (the default) and 9 means max compression.
             * Selecting a non-zero compression level auto-selects the
             * compressed SMF port on the appliance, as long as no SMF port is
             * explicitly specified.
             */
            cp.setCompressionLevel(9);
        }
        cp.setReconnectRetries(-1);     // try reconnecting forever
        
        try {
            // Create session from JCSMPProperties. Validation is performed by
            // the API and it throws InvalidPropertiesException upon failure.
            System.out.println("About to create session.");
            System.out.println("Configuration: " + conf.toString());
            session = JCSMPFactory.onlyInstance().createSession(properties, null, new PrintingSessionEventHandler());
        } catch (InvalidPropertiesException ipe) {          
            System.err.println("Error during session creation: ");
            ipe.printStackTrace();
            System.exit(-1);
        }
    }

    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        System.out.println(strusage);
        finish(1);
    }

    public static void main(String[] args) {
        Replication rep = new Replication();
        rep.run(args);
    }

    public Replication() {
    }

    void run(String[] args) {
        Topic topic = null;

        try {
            // create the session
            createSession(args);

            session.connect();
            printRouterInfo();
            System.out.println("Connected!");
                
            topic = JCSMPFactory.onlyInstance().createTopic("replication_topic");

            // create the producer
            producer = session.getMessageProducer(this, this);
                                        
            for (int i = 0; i < numMsgsToSend; i++) {
	            BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
	            msg.setCorrelationKey("Message " + i);
	            unackedList.add(msg);
	            msg.setDeliveryMode(DeliveryMode.PERSISTENT);
	            producer.send(msg, topic);
	            System.out.println("SENT: " + msg.getCorrelationKey());
            }
        } catch (JCSMPException ex) {
            System.out.println(ex.getMessage());
            System.exit(1);     
        } catch (Exception ex) {
            System.err.println("Encountered an Exception... " + ex.getMessage());
            System.exit(1);
        }
        
        // Wait 5s for  all messages acknowledged by the router.
        try {
            Thread.sleep(5000);
        } catch (Exception ex) {
            System.err.println("Encountered an Exception... " + ex.getMessage());
        }
        
        if (unackedList.get().length > 0) {
            System.err.println(unackedList.get().length + " unacked messages");
        }
        else {
            System.out.println("Done: " + numMsgsToSend + " messages sent (with " +  numMsgResent + " messages renumbered and resent)!" );
        }
        System.exit(0);
    }

    public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
        if (key != null) {
            System.out.println("Error sending message with correlation key \"" + key + "\"");
        }
        cause.printStackTrace();
    }

    public void responseReceivedEx(Object key) {
        if (key != null) {
            unackedList.remove(key);
        }
    }
    public void handleEvent(ProducerEventArgs event) {
        System.out.println("Event= " + event.getEvent() +"; Info= " + event.getInfo());
        if (event.getEventObject() instanceof Integer) {
            numMsgResent += (Integer)event.getEventObject();
        }
    }
}
