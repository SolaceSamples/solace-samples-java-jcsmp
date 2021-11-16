/**
 * SyncCacheRequest.java
 * 
 * This sample creates a JCSMPSession with an appliance running SolOS-TR, 
 * then creates a new CacheSession using it. The sample performs a synchronous 
 * cache request and asynchronously receives data on a consumer callback.
 * 
 * Sample Requirements:
 * - A Solace appliance running SolOS-TR with an active cache.
 * - A cache running and caching on a pattern that matches "my/sample/topic".
 * - The cache name must be known and passed to this program as a command line
 * argument.
 * 
 * Copyright 2006-2021 Solace Corporation. All rights reserved.
 */

package com.solace.samples.jcsmp.features;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.CacheSessionConfiguration;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CacheRequestResult;
import com.solacesystems.jcsmp.CacheSession;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageProducer;
import com.solacesystems.jcsmp.statistics.StatType;

public class SyncCacheRequest extends SampleApp {
    CacheSession cacheSession = null;
    XMLMessageProducer prod = null;
    XMLMessageConsumer cons = null;
    CacheSessionConfiguration conf = null;
    
    void createSession(String[] args) {
		// Parse command-line arguments
		ArgParser parser = new ArgParser();
		if (parser.parseCacheSampleArgs(args) == 0)
			conf = (CacheSessionConfiguration) parser.getConfig();
		else
			printUsage(parser.isSecure());
		session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
    }

    void printUsage(boolean secure) {
        StringBuffer buf = new StringBuffer();
        buf.append(ArgParser.getCacheArgUsage(secure));
		System.out.println(buf.toString());
		finish(1);
    }
    
    public class PubCallback implements JCSMPStreamingPublishCorrelatingEventHandler {
        public void handleErrorEx(Object key, JCSMPException cause,
            long timestamp) {
            System.err.println("Error occurred for message: " + key);
            cause.printStackTrace();
        }

        // Not Called - only errors are reported.
        public void responseReceivedEx(Object key) {
        }
    }
    
    public static void main(String[] args) {
        SyncCacheRequest cacheReq = new SyncCacheRequest();
        cacheReq.run(args);
    }

    public SyncCacheRequest() {

    }

    void run(String[] args) {
        createSession(args);

        try {
            /********************************/            
            System.out.println("About to send cache request.");

            // Connect, create a producer and a consumer, and start the consumer.
	        session.connect();
            prod = session.getMessageProducer(new PubCallback());
            cons = session.getMessageConsumer(new PrintingMessageHandler());
			printRouterInfo();
            cons.start();
            Topic topic = JCSMPFactory.onlyInstance().createTopic(SampleUtils.SAMPLE_TOPIC);

			// Publish a single message to make sure there is something cached.
			BytesXMLMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
			msg.writeAttachment("published message".getBytes());
			prod.send(msg, topic);
            
            // Create the cache session based on parameters on the command line.
            cacheSession = SampleUtils.newCacheSession(session, conf);
            
            // Perform the cache request.
            CacheRequestResult result = cacheSession.sendCacheRequest(1L, topic, 
                    conf.getSubscribe(), conf.getAction());
            System.out.println("Cache Request=" + result + ", Cached Messages Received=" + 
                    session.getSessionStats().getStat(StatType.CACHED_MSGS_RECVED));
            finish(0);
        } catch (IllegalArgumentException ex) {
            System.err.println("Illegal parameter... " + ex.getMessage());
            finish(1);
        } catch (JCSMPException ex) {
            System.err.println("Encountered a JCSMPException performing a cache request... " + ex.getMessage());
            finish(1);
        }
    }
}
