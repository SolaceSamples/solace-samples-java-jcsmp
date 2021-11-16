package com.solace.samples.jcsmp.features;

// This example demonstrates how to implement a blocking, synchronous send
// method using the JCSMP API's non-blocking, asynchronous send method and
// associated event callbacks. The example's blockingSend() method blocks
// until an acknowledgement (ACK) or negative acknowledgment (NACK) is
// received from the broker for the published message.

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

public class BlockingSynchronousSendGuaranteed implements SessionEventHandler, JCSMPStreamingPublishCorrelatingEventHandler {

    JCSMPSession session;
    XMLMessageProducer producer;
    volatile Exception publishException;  // Used to pass the exception associated with a NACK back to the application
    
    public BlockingSynchronousSendGuaranteed() {
    }

    public void setup() throws JCSMPException {
    }
    
    public void run() throws Exception  {
    	
        JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, "192.168.164.178");
        properties.setProperty(JCSMPProperties.USERNAME, "solace");
        properties.setProperty(JCSMPProperties.PASSWORD, "password");
        properties.setProperty(JCSMPProperties.VPN_NAME, "solace");
        
        // Set the publish window size to 1. As the blockingSend() call blocks until
        // an ACK or NACK is received only one message can be in flight at a time.
        properties.setProperty(JCSMPProperties.PUB_ACK_WINDOW_SIZE, 1);  
        
        session = JCSMPFactory.onlyInstance().createSession(properties, null, this);
        producer = session.getMessageProducer(this);

        Topic topic = JCSMPFactory.onlyInstance().createTopic("my/sample/topic");

        TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        message.setText("Sample Text");
        message.setDeliveryMode(DeliveryMode.PERSISTENT);
        
    	try {
    		blockingSend(message, topic);
    		System.out.println("Message accepted by broker.");
    	} catch (Exception e) {
    		System.out.println("Message rejected or another error occurred. See exception:");
    		e.printStackTrace();
    	}

    	producer.close();
    	session.closeSession();
    }

    // This is the blocking, synchronous send method implemented using the JCSMP API's non-blocking, 
    // asynchronous send method and associated event callbacks. Calls to blockingSend() can
    // block indefinitely waiting for receipt of an ACK or NACK for the message sent. 
    // If the API fails to receive either an ACK or NACK from the broker within the
    // the JCSMPProperties.PUB_ACK_TIME timeout (default 2000ms) the API retransmits the
    // message. The API will retransmit the message every JCSMPProperties.PUB_ACK_TIME milliseconds 
    // until an ACK or NACK is received. Typically the broker only fails to respond with an ACK
    // or NACK if it is out of disk space to persist the received message.
    //
    // This method uses a CyclicBarrier to synchronize between sending the message and receiving
    // either an ACK, in the ResponseReceivedEx callback, or a NACK, in the
    // handleErrorEx callback.
    public synchronized void blockingSend(XMLMessage message, Topic topic) throws Exception {
    	publishException = null;
        CyclicBarrier barrier = new CyclicBarrier(2);
		message.setCorrelationKey(barrier);
		producer.send(message, topic);
		
		try {
			barrier.await();
		} catch (InterruptedException e) {
			throw(e);
		} catch (BrokenBarrierException e) {
			throw(e);
		}

		if (publishException != null) {
			throw publishException; 
		}
    }
    
    public void handleEvent(SessionEventArgs event) {
        System.out.println(event);
    }
    
    // This callback is invoked if a NACK is received for the message because the
    // broker has rejected it. The reason for the rejection is passed back to 
    // the application in the exception thrown from the call to blockingSend().
	public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
		if (key instanceof CyclicBarrier) {
			try {
	            this.publishException = cause;
				((CyclicBarrier)key).await();
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        } catch (BrokenBarrierException e) {
	            e.printStackTrace();
	        }
		}
	}

	// This callback is invoked if an ACK is received for the message.
	public void responseReceivedEx(Object key) {
		if (key instanceof CyclicBarrier) {
			try {
				((CyclicBarrier)key).await();
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        } catch (BrokenBarrierException e) {
	            e.printStackTrace();
	        }
		}
	}

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            BlockingSynchronousSendGuaranteed publisher = new BlockingSynchronousSendGuaranteed();
            publisher.setup();
            publisher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}