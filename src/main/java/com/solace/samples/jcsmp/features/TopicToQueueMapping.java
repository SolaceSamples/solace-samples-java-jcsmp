/*
 * Copyright 2021-2022 Solace Corporation. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.solace.samples.jcsmp.features;

import com.solace.samples.jcsmp.features.common.ArgParser;
import com.solace.samples.jcsmp.features.common.SampleApp;
import com.solace.samples.jcsmp.features.common.SampleUtils;
import com.solace.samples.jcsmp.features.common.SessionConfiguration;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.CapabilityType;
import com.solacesystems.jcsmp.Consumer;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

/**
 * NOTE: this sample does not illustrate Solace best practices for topic-to-queue subscription
 * management.  Ideally, best practice is to admin-configure queues with topic subscriptions
 * using SEMP or PubSub+ Manager.  See example in folder "semp-rest-api".
 * However, if you want your messaging API to be able to add/remove topic subscriptions on
 * queues, this sample shows that.  Note that your queue needs "modify topic" permissions
 * to work, rather than the default "consume" permissions.
 */
public class TopicToQueueMapping extends SampleApp {
    Consumer cons = null;
    XMLMessageProducer prod = null;
    SessionConfiguration conf = null;
	static int rx_msg_count = 0;
	
    void createSession(String[] args) {
        ArgParser parser = new ArgParser();

        // Parse command-line arguments.
        if (parser.parse(args) == 0)
            conf = parser.getConfig();
        else
            printUsage(parser.isSecure());

        session = SampleUtils.newSession(conf, new PrintingSessionEventHandler(),null);
    }

    void printUsage(boolean secure) {
        String strusage = ArgParser.getCommonUsage(secure);
        System.out.println(strusage);
        finish(1);
    }

    public static void main(String[] args) {
        TopicToQueueMapping qsample = new TopicToQueueMapping();
        qsample.run(args);
    }

    void checkCapability(final CapabilityType cap) {
        System.out.printf("Checking for capability %s...", cap);
        if (session.isCapable(cap)) {
            System.out.println("OK");
        } else {
            System.out.println("FAILED");
            finish(1);
        }
    }

    byte[] getBinaryData(int len) {
        final byte[] tmpdata = "the quick brown fox jumps over the lazy dog / flying spaghetti monster ".getBytes();
        final byte[] ret_data = new byte[len];
        for (int i = 0; i < len; i++)
            ret_data[i] = tmpdata[i % tmpdata.length];
        return ret_data;
    }

    void run(String[] args) {
        createSession(args);
        Queue ep_queue = null;
        int finishCode = 0;
        try {
            // Connects the Session and acquires a message producer.
			session.connect();
			prod = session.getMessageProducer(new PrintingPubCallback());
			final String virtRouterName = (String) session.getProperty(JCSMPProperties.VIRTUAL_ROUTER_NAME);
			System.out.printf("Router's virtual router name: '%s'\n", virtRouterName);

            // Check capability to provision endpoints.
            checkCapability(CapabilityType.ENDPOINT_MANAGEMENT);
            // Check capability to add subscriptions to queues.
            checkCapability(CapabilityType.QUEUE_SUBSCRIPTIONS);

            /*
             * Provision a new queue on the appliance, ignoring if it already
             * exists. Set permissions, access type, and provisioning flags.
             */
            EndpointProperties ep_provision = new EndpointProperties();
            // Set permissions to allow all.
            ep_provision.setPermission(EndpointProperties.PERMISSION_DELETE);
            // Set access type to exclusive.
            ep_provision.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
            // Set Quota to 100 MB.
            ep_provision.setQuota(100);
			ep_queue = JCSMPFactory.onlyInstance().createQueue("sample_queue_TopicToQueueMapping");
            System.out.printf("Provision queue '%s' on the appliance...", ep_queue);
            session.provision(ep_queue, ep_provision, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
            System.out.println("OK");

            // Add the Topic to the Queue.
            Topic addedTopic = JCSMPFactory.onlyInstance().createTopic("sample_queue_AddedTopic");
            session.addSubscription(ep_queue, addedTopic, JCSMPSession.WAIT_FOR_CONFIRM);
            
            /*
             * Publish some messages to this Queue. Use
             * producer-independent messages acquired from JCSMPFactory.
             */
            BytesXMLMessage m = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
            m.setDeliveryMode(DeliveryMode.PERSISTENT);
            for (int i = 1; i <= 5; i++) {
                m.setUserData(String.valueOf(i).getBytes());
                m.writeAttachment(getBinaryData(i * 20));
                m.setCorrelationKey(i);
                if (i == 1) {
                    prod.send(m, ep_queue);
                } else {
                    prod.send(m, addedTopic);
                }
            }
            Thread.sleep(500);
            System.out.println("Sent messages.");

			/*
			 * Create a Flow to consume messages on the Queue. There should be 
			 * five messages on the Queue.
			 */
			rx_msg_count = 0;
			ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
			flow_prop.setEndpoint(ep_queue);
			cons = session.createFlow(new MessageDumpListener(), flow_prop);
			cons.start();
			Thread.sleep(2000);
			System.out.printf("Finished consuming messages, the number of message on the queue is '%s'.\n", rx_msg_count);
        } catch (JCSMPTransportException ex) {
            System.err.println("Encountered a JCSMPTransportException, closing consumer channel... " + ex.getMessage());
            if (cons != null) {
                cons.close();
                // At this point the consumer handle is unusable; a new one
                // should be created by calling 
                // cons = session.getMessageConsumer(...) if the
                // application logic requires the consumer channel to remain open.
            }
            finishCode = 1;
        } catch (JCSMPException ex) {
            System.err.println("Encountered a JCSMPException, closing consumer channel... " + ex.getMessage());
            // Possible causes:
            // - Authentication error: invalid username/password
            // - Provisioning error: unable to add subscriptions from CSMP
            // - Invalid or unsupported properties specified
            if (cons != null) {
                cons.close();
                // At this point the consumer handle is unusable, a new one
                // should be created
                // by calling cons = session.getMessageConsumer(...) if the
                // application
                // logic requires the consumer channel to remain open.
            }
            finishCode = 1;
        } catch (Exception ex) {
            System.err.println("Encountered an Exception... " + ex.getMessage());
            ex.printStackTrace();
            finishCode = 1;
        } finally {
            if (cons != null) {
                cons.close();
            }
            if (ep_queue != null) {
                System.out.printf("Deprovision queue '%s'...", ep_queue);
                try {
                    session.deprovision(ep_queue, JCSMPSession.FLAG_IGNORE_DOES_NOT_EXIST);
                } catch(JCSMPException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("OK");
            finish(finishCode);
        }
    }
    
	static class MessageDumpListener implements XMLMessageListener {
		public void onException(JCSMPException exception) {
			exception.printStackTrace();
		}

		public void onReceive(BytesXMLMessage message) {
			System.out.println("\n======== Received message ======== \n" + message.dump());
			rx_msg_count++;
		}
	}
}
