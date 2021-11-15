package com.solace.samples.jcsmp.snippets;

import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPReconnectEventHandler;
import com.solacesystems.jcsmp.JCSMPSession;

public class HowToListenToReconnectionEvents {

    /**
     * This method demonstrates a snippet of how to specify the JCSMP reconnection event listener on a session.
     * 
     * Make sure to check out: https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
     */
    public void registerReconnectionListener() throws JCSMPException {
        
        JCSMPSession session = JCSMPFactory.onlyInstance().createSession(null);  // dummy session
        
        session.getMessageConsumer(new JCSMPReconnectEventHandler() {  // reconnect handler on consumer
            
            @Override
            public boolean preReconnect() throws JCSMPException {
                System.out.println("This will run just before every reconnect attempt.");
                System.out.println("So this is the fastest way to find out you have been disconnected.");
                System.out.println("If you return false from here, all reconnection attempts stop.");
                return true;
            }
            
            @Override
            public void postReconnect() throws JCSMPException {
                System.out.println("This method will be run once you've successfully reconnected");
            }
        }, null);  // either null for Blocking receive, or new XMLMessageListener() for Async receive
        
        
        // don't even need to start the consumer for reconnect handler to work, great for publish-only applications
    }
    
}
