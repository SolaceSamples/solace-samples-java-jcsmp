/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.solace.samples.jcsmp.snippets;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.ConsumerFlowProperties;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.FlowReceiver;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.Queue;
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.User_Cos;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

public class TestEveryMessageSetting {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static AtomicInteger directMessages = new AtomicInteger(0);
    private static AtomicInteger queueMessages = new AtomicInteger(0);
    
    /** Silly test to set as many message properties as possible. */
    @SuppressWarnings("deprecation")
    public static void main(String... args) throws JCSMPException, InterruptedException {

        // Check command line arguments
        if (args.length < 3) {
            System.out.println("Usage: " + TestEveryMessageSetting.class.getSimpleName()
                    + " <host:port> <message-vpn> <client-username> [client-password]");
            System.out.println();
            System.exit(-1);
        }

        System.out.println(TestEveryMessageSetting.class.getSimpleName() + " initializing...");

        // Create a JCSMP Session
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);     // host:port
        properties.setProperty(JCSMPProperties.USERNAME, args[1]); // client-username
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[2]); // message-vpn
        if (args.length > 3) { 
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]); // client-password
        }
        // Make sure that the session is tolerant of the subscription already existing on the queue.
        properties.setProperty(JCSMPProperties.IGNORE_DUPLICATE_SUBSCRIPTION_ERROR, true);

        final JCSMPSession session =  JCSMPFactory.onlyInstance().createSession(properties);

        session.connect();
        System.out.println("Connected.");

        // Anonymous inner-class for handling publishing events
        final XMLMessageProducer prod = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            @Override
            public void responseReceivedEx(Object key) {
                System.out.println("******** Producer received response for msg: " + key);
            }
            
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("********* Producer received error for msg: %s@%s - %s%n", key, timestamp, cause);
            }
        });

        final XMLMessageConsumer cons = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage msg) {
                System.out.printf("========================%nDIRECT MESSAGE #%d RECEIVED:%n%s%n",directMessages.incrementAndGet(),msg.dump());
            }

            @Override
            public void onException(JCSMPException e) {
                System.out.printf("******* Consumer received exception: %s%n",e);
            }
        });
        session.addSubscription(JCSMPFactory.onlyInstance().createTopic("test/>"));
        System.out.println("Connected. Awaiting message...");
        cons.start();

        // TEST1
        TextMessage msg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        msg.setText("Text Message Direct!");
        msg.setUserData("msg.setUserData()".getBytes(UTF_8));
        SDTMap map = JCSMPFactory.onlyInstance().createMap();
        map.putString("what is this","user properties");
        map.putInteger("an answer",42);
        msg.setProperties(map);
        msg.setAckImmediately(true);
        msg.setApplicationMessageId("msg.setApplicationMessageId()");
        msg.setApplicationMessageType("msg.setApplicationMessageType()");
        msg.setAsReplyMessage(true);
        msg.setCorrelationId("msg.setCorrelationId()");
        msg.setCorrelationKey(msg);
        msg.setCos(User_Cos.USER_COS_2);  // why not
        
        msg.setDeliverToOne(true);  // old school, deprecated now in favour of Shared Subscriptions
        msg.setDeliveryMode(DeliveryMode.DIRECT);
        msg.setDMQEligible(true);
        msg.setElidingEligible(true);
        msg.setExpiration(System.currentTimeMillis() + (1000 * 60 * 60 * 24));
        msg.setHTTPContentEncoding("msg.setHTTPContentEncoding()");
        msg.setHTTPContentType("msg.setHTTPContentType");
        msg.setPriority(254);
        msg.setReplyTo(JCSMPFactory.onlyInstance().createTopic("msg.setReplyTo()"));
        msg.setReplyToSuffix("msg.setReplyToSuffix()");  // overwrites previous reply-to with inbox topic

        msg.setSenderId("msg.setSenderId()");
        msg.setSenderTimestamp(System.currentTimeMillis());
        msg.setSequenceNumber(123456789);
        msg.setTimeToLive(1000 * 60);  // milliseconds
        msg.writeAttachment("msg.writeAttachment()".getBytes(UTF_8));  // binary payload, overwrites TextMsg body
        msg.writeBytes("msg.writeBytes()".getBytes(UTF_8));  // XML payload, don't use this, just a test
        System.out.println("Sending FULL Direct Text Message");
        System.out.println(msg.dump());
        prod.send(msg,JCSMPFactory.onlyInstance().createTopic("solace/samples/test/all"));
        Thread.sleep(1000);
        
        // TEST 2
        final String queueName = "q/test";
        System.out.printf("Attempting to provision the queue '%s' on the appliance.%n", queueName);
        final EndpointProperties endpointProps = new EndpointProperties();
        // set queue permissions to "consume" and access-type to "exclusive"
        endpointProps.setPermission(EndpointProperties.PERMISSION_CONSUME);
        endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
        // create the queue object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
        // Actually provision it, and do not fail if it already exists
        session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
        session.addSubscription(queue,JCSMPFactory.onlyInstance().createTopic("test/>"),JCSMPSession.WAIT_FOR_CONFIRM);
        
        // Create a Flow be able to bind to and consume messages from the Queue.
        final ConsumerFlowProperties flowProp = new ConsumerFlowProperties();
        flowProp.setEndpoint(queue);
        flowProp.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

        final EndpointProperties endpointProps2 = new EndpointProperties();
        //endpoint_props.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);

        final FlowReceiver flow = session.createFlow(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage msg) {
                System.out.printf("================%nMESSAGE #%d ON MY QUEUE RECEIVED:%n%s%n",
                        queueMessages.incrementAndGet(), msg.dump());
                msg.ackMessage();
            }

            @Override
            public void onException(JCSMPException e) {
                System.out.printf("Consumer received exception: %s%n", e);
            }
        }, flowProp, endpointProps2);
        flow.start();
        
        msg.setText("Now Persistent Message!");
        msg.setDeliveryMode(DeliveryMode.PERSISTENT);
        System.out.println("Sending FULL Persistent Text Message");
        System.out.println(msg.dump());
        prod.send(msg,JCSMPFactory.onlyInstance().createTopic("test/pers"));

        // TEST 3... resend the exact same message twice more, no reset.  Does it work?  What happens to MessageID?
        prod.send(msg,JCSMPFactory.onlyInstance().createTopic("test/pers"));
        prod.send(msg,JCSMPFactory.onlyInstance().createTopic("test/pers"));
        
        Thread.sleep(500);
        flow.stop();
        flow.close();
        Thread.sleep(500);
        session.deprovision(queue,JCSMPSession.WAIT_FOR_CONFIRM);  // clean up
        System.out.println("Exiting.");
        session.closeSession();
    }
}
