/*
 * Copyright 2025 Solace Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.solace.samples.jcsmp.features.serdes.generic;

import com.solace.serdes.Deserializer;
import com.solace.serdes.Serializer;
import com.solace.serdes.StringSerializer;
import com.solace.serdes.StringDeserializer;
import com.solacesystems.jcsmp.BytesMessage;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import com.solacesystems.jcsmp.XMLMessageProducer;

import java.io.IOException;

/**
 * HelloWorldJCSMPStringSerdes
 * This class demonstrates the usage of Solace JCSMP API with String serialization and deserialization.
 * It connects to a Solace message broker, publishes a message using String serialization, and consumes
 * the message using String deserialization.
 */
public class HelloWorldJCSMPStringSerdes {

    /**
     * The main method that demonstrates the Solace JCSMP API usage with String serialization/deserialization.
     *
     * @param args Command line arguments: <host:port> <message-vpn> <client-username> [password]
     * @throws Exception If any error occurs during execution
     */
    public static void main(String[] args) throws JCSMPException, IOException {
        Topic topic = JCSMPFactory.onlyInstance().createTopic("topic");

        final JCSMPSession consumerSession = getSession(args);
        XMLMessageConsumer consumer = consumerSession.getMessageConsumer((XMLMessageListener) null);
        consumerSession.addSubscription(topic);
        consumer.start();

        final JCSMPSession producerSession = getSession(args);
        XMLMessageProducer producer = producerSession.getMessageProducer(new ExampleProducer());

        BytesMessage msg = producer.createBytesMessage();
        String messageText = "Hello World";

        try (StringSerializer strSerializer = new StringSerializer()) {
            msg.setData(strSerializer.serialize(topic.getName(), messageText));
        }

        producer.send(msg, topic);

        System.out.println("Message Text sent: " + messageText);
        System.out.println("Data sent:\n" + msg.dump());

        BytesMessage receive = (BytesMessage) consumer.receive();
        byte[] recvData = receive.getData();
        String receivedText;
        try (StringDeserializer strDeserializer = new StringDeserializer()) {
            receivedText = strDeserializer.deserialize(receive.getDestination().getName(), recvData);
        }

        System.out.println("MessageText recv: " + receivedText);
        System.out.println("Recv data:\n" + receive.dump());

        msg = producer.createBytesMessage();
        messageText = "Hello World 2";

        try (Serializer<String> serializer = new StringSerializer()) {
            msg.setData(serializer.serialize(topic.getName(), messageText));
        }

        producer.send(msg, topic);

        System.out.println("Message Text sent: " + messageText);
        System.out.println("Data sent:\n" + msg.dump());

        receive = (BytesMessage) consumer.receive();
        recvData = receive.getData();
        try (Deserializer<String> deserializer = new StringDeserializer()) {
            receivedText = deserializer.deserialize(receive.getDestination().getName(), recvData);
        }

        System.out.println("MessageText recv: " + receivedText);
        System.out.println("Recv data:\n" + receive.dump());

        consumerSession.closeSession();
        producerSession.closeSession();
    }

    /**
     * Creates and returns a JCSMP session with the provided connection details.
     *
     * @param args Command line arguments: <host:port> <message-vpn> <client-username> [password]
     * @return A connected JCSMPSession
     * @throws JCSMPException If there's an error creating or connecting the session
     */
    private static JCSMPSession getSession(String[] args) throws JCSMPException {
        if (args.length < 3) {  // Check command line arguments
            System.out.println("Usage: <host:port> <message-vpn> <client-username> [password]");
            System.exit(-1);
        }

        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);
        properties.setProperty(JCSMPProperties.VPN_NAME, args[1]);
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);
        }
        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);

        session.connect();
        return session;
    }

    private static class ExampleProducer implements JCSMPStreamingPublishCorrelatingEventHandler {
        @Override
        public void responseReceivedEx(Object o) {/* not needed */}

        @Override
        public void handleErrorEx(Object o, JCSMPException e, long l) {/* not needed */}
    }
}
