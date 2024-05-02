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

package com.solace.samples.jcsmp.features.distributedtracing;
import com.solace.messaging.trace.propagation.SolaceJCSMPTextMapGetter;
import com.solacesystems.jcsmp.*;
import java.io.IOException;
import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.opentelemetry.context.Context;

public class QueueSubscriberWithManualInstrumentation {
    // remember to add log4j2.xml to your classpath
    private static final Logger logger = LogManager.getLogger(); // log4j2, but could also use SLF4J, JCL, etc.
    private static final String SERVICE_NAME = "ACME Distribution [DEV]";
    private static final String SAMPLE_NAME = QueueSubscriberWithManualInstrumentation.class.getSimpleName();
    private static final String QUEUE_NAME = "q.product_to_distribution";
    private static final String API = "JCSMP";
    private static volatile int msgRecvCounter = 0; // num messages received
    private static volatile boolean hasDetectedRedelivery = false; // detected any messages being redelivered?
    private static volatile boolean isShutdown = false; // are we done?
    private static FlowReceiver flowQueueReceiver;

    static {
        //Setup OpenTelemetry
        TracingUtil.initManualTracing(SERVICE_NAME);
    }

    /**
     * This is the main app.  Use this type of app for receiving Guaranteed messages (e.g. via a queue endpoint).
     */
    public static void main(String...args) throws JCSMPException, InterruptedException, IOException {
        if (args.length < 3) { // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");

        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]); // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME, args[1]); // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]); // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]); // client-password
        }
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20); // recommended settings
        channelProps.setConnectRetriesPerHost(5); // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);
        final JCSMPSession session;
        session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) { // could be reconnecting, connection lost, etc.
                logger.info("### Received a Session event: " + event);
            }
        });
        session.connect();

        // configure the queue API object locally
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(QUEUE_NAME);
        // Create a Flow be able to bind to and consume messages from the Queue.
        final ConsumerFlowProperties flow_prop = new ConsumerFlowProperties();
        flow_prop.setEndpoint(queue);
        flow_prop.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT); // best practice
        flow_prop.setActiveFlowIndication(true); // Flow events will advise when

        System.out.printf("Attempting to bind to queue '%s' on the broker.%n", QUEUE_NAME);
        try {
            // see bottom of file for QueueFlowListener class, which receives the messages from the queue
            flowQueueReceiver = session.createFlow(new QueueFlowListener(), flow_prop, null, new FlowEventHandler() {
                @Override
                public void handleEvent(Object source, FlowEventArgs event) {
                    // Flow events are usually: active, reconnecting (i.e. unbound), reconnected, active
                    logger.info("### Received a Flow event: " + event);
                    // try disabling and re-enabling the queue to see in action
                }
            });
        } catch (OperationNotSupportedException e) { // not allowed to do this
            throw e;
        } catch (JCSMPErrorResponseException e) { // something else went wrong: queue not exist, queue shutdown, etc.
            logger.error(e);
            System.err.printf("%n*** Could not establish a connection to queue '%s': %s%n", QUEUE_NAME, e.getMessage());
            System.err.println("Create queue using PubSub+ Manager WebGUI, and add subscription solace/tracing ");
            System.err.println("  or see the SEMP CURL scripts inside the 'semp-rest-api' directory.");
            // could also try to retry, loop and retry until successfully able to connect to the queue
            System.err.println("NOTE: see QueueProvision sample for how to construct queue with consumer app.");
            System.err.println("Exiting.");
            return;
        }
        // tell the broker to start sending messages on this queue receiver
        flowQueueReceiver.start();
        // async queue receive working now, so time to wait until done...
        System.out.println(SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !isShutdown) {
            Thread.sleep(1000); // wait 1 second
            System.out.printf("%s %s Received msgs/s: %,d%n", API, SAMPLE_NAME, msgRecvCounter); // simple way of calculating message rates
            msgRecvCounter = 0;
            if (hasDetectedRedelivery) { // try shutting -> enabling the queue on the broker to see this
                System.out.println("*** Redelivery detected ***");
                hasDetectedRedelivery = false; // only show the error once per second
            }
        }
        isShutdown = true;
        flowQueueReceiver.stop();
        Thread.sleep(1000);
        session.closeSession(); // will also close consumer object
        System.out.println("Main thread quitting.");
    }

    ////////////////////////////////////////////////////////////////////////////

    /**
     * Very simple static inner class, used for receives messages from Queue Flows.
     **/
    private static class QueueFlowListener implements XMLMessageListener {

        @Override
        public void onReceive(BytesXMLMessage message) {

            final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
            final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);
            final SolaceJCSMPTextMapGetter getter = new SolaceJCSMPTextMapGetter();

            // Extract tracing context from message, if any using the SolaceJCSMPTextMapGetter
            // It is always advised to extract context before injecting new one

            final Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), message, getter);
            
            

            // Set the extracted context as current context
            try (Scope scope = extractedContext.makeCurrent()) {

                // Create a child span to signal the receive and set extracted/current context as parent of this span
                final Span receiveSpan = tracer.
                spanBuilder("Product Update > Received")
                    .setSpanKind(SpanKind.CONSUMER)

                    // Optional: user defined Span attributes
                    // dot separated, snake_case is the convention, keeping to a fixed 'something.*' name space too for custom ones.
                    // See: https://opentelemetry.io/docs/specs/semconv/general/attribute-naming/

                    // Some runtime attributes to include:
                    .setAttribute("env", "Development")
                    .setAttribute("user.name", System.getProperty("user.name"))
                    .setAttribute("java.version", System.getProperty("java.version"))
                    .setAttribute("os.name", System.getProperty("os.name"))

                    // Some transport attributes to include, in the SemanticAttributes name space:
                    // See: https://opentelemetry.io/docs/specs/semconv/general/trace/

                    .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "solace")
                    .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
                    .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, message.getDestination().getName())
                    .setAttribute(SemanticAttributes.NET_PROTOCOL_NAME, "smf")

                    .setAttribute("com.acme.product_update.receive_key.1", "myValue1")
                    //.setAttribute(...)
                    .setParent(extractedContext).startSpan();

                //... and then we do some processing and have another span to signal that

                Baggage receivedTelemetryBaggage = Baggage.fromContext(extractedContext);
                String receivedTelemetryBaggageStr = "";
                
                for (Map.Entry<String, BaggageEntry> entry : receivedTelemetryBaggage.asMap().entrySet()) {
                	receivedTelemetryBaggageStr = receivedTelemetryBaggageStr +
                			entry.getKey() + "=" + receivedTelemetryBaggage.getEntryValue(entry.getKey()) + 
                			",";
                }

                System.out.println("Received a message with OTEL Trace ID: " + Span.current().getSpanContext().getTraceId() + 
                		" with " + receivedTelemetryBaggage.size() + " keys found in telemetry baggage. " + receivedTelemetryBaggageStr);

                try {
                    final Span processingSpan = tracer
                        .spanBuilder("Product Update > Processed")
                        .setSpanKind(SpanKind.SERVER)

                        // Set more attributes as needed
                        .setAttribute("com.acme.product_update.processing_key.1", "postProcessingInformation")

                        //.setAttribute(...)
                        .setParent(Context.current().with(receiveSpan)) // make RECEIVE span be the parent
                        .startSpan();
                    try {
                        msgRecvCounter++;
                        if (message.getRedelivered()) { // useful check
                            // this is the broker telling the consumer that this message has been sent and not ACKed before.
                            // this can happen if an exception is thrown, or the broker restarts, or the network disconnects
                            // perhaps an error in processing? Should do extra checks to avoid duplicate processing
                            hasDetectedRedelivery = true;
                        }
                        // Messages are removed from the broker queue when the ACK is received.
                        // Therefore, DO NOT ACK until all processing/storing of this message is complete.
                        // NOTE that messages can be acknowledged from a different thread.
                        message.ackMessage(); // ACKs are asynchronous
                    } catch (Exception e) {
                        processingSpan.recordException(e); //Span can record exception if any
                        processingSpan.setStatus(StatusCode.ERROR, e.getMessage()); //Set span status as ERROR/FAILED
                    } finally {
                        processingSpan.end(); //End processSpan. Span data is exported when span.end() is called.
                    }
                } finally {
                    receiveSpan.end(); //End receiveSpan. Span data is exported when span.end() is called.
                }
            }
        }

        @Override
        public void onException(JCSMPException e) {
            logger.warn("### Queue " + QUEUE_NAME + " Flow handler received exception.  Stopping!!", e);
            if (e instanceof JCSMPTransportException) { // all reconnect attempts failed
                isShutdown = true; // let's quit; or, could initiate a new connection attempt
            } else {
                // Generally unrecoverable exception, probably need to recreate and restart the flow
                flowQueueReceiver.close();
                // add logic in main thread to restart FlowReceiver, or can exit the program
            }
        }
    }
}