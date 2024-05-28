/*
 * Copyright 2022-2023 Solace Corporation. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

import com.solace.messaging.trace.propagation.SolaceJCSMPTextMapSetter;
import com.solacesystems.jcsmp.DeliveryMode;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.SDTMap;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;

// OpenTelemetry Instrumentation Imports:
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.BaggageUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/**
 * A sample that shows how to generate a Publisher/send span with Solace OpenTelemetry Integration
 * for JCSMP.
 * <p>
 * Setup a Solace PubSub+ Broker and OpenTelemetry Collector as per tutorial  >
 * https://codelabs.solace.dev/codelabs/dt-otel/index.html
 * <p>
 * This is the Publisher in the Publish-Subscribe messaging pattern.
 */
public class DirectPublisherWithCustomPropagation {

    private static final String SERVICE_NAME = "Standalone Scheduled Processor [DEV]";
    private static final String SAMPLE_NAME = DirectPublisherWithCustomPropagation.class.getSimpleName();
    private static final String TOPIC_NAME = "acme/plm/product/confirmed/001";
    private static final String API = "JCSMP";

    static {
    	// "configure an instance of the OpenTelemetrySdk as early as possible in your application."
    	// Ref: https://opentelemetry.io/docs/languages/java/instrumentation/
        TracingUtil.initManualTracing(SERVICE_NAME);
    }

    public void run(String...args) throws JCSMPException, InterruptedException {
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]); // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME, args[1]); // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]); // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]); // client-password
        }

        final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
        session.connect(); // connect to the broker

        // Simple anonymous inner-class for handling publishing events
        final XMLMessageProducer producer = session.getMessageProducer(
            new JCSMPStreamingPublishCorrelatingEventHandler() {
                // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
                @Override
                public void responseReceivedEx(Object key) {}

                // can be called for ACL violations, connection loss, and Persistent NACKs
                @Override
                public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                    log("### Producer handleErrorEx() callback: %s%n", cause);
                }
            });

        log(API + " " + SAMPLE_NAME + " connected");

        final Topic topic = JCSMPFactory.onlyInstance().createTopic(TOPIC_NAME);
        final TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        message.setText("Hello World!!");
        message.setDeliveryMode(DeliveryMode.PERSISTENT);		// Distributed Tracing only covers persistent messaging

        final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
        final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);

        //TRACE MESSAGE PUBLISH
        messagePublisherTracer(message, producer, topic, openTelemetry, tracer);

        Thread.sleep(1000);

        session.closeSession(); // will also close producer object
    }

    private void messagePublisherTracer(XMLMessage message, XMLMessageProducer messagePublisher,
        Destination messageDestination, OpenTelemetry openTelemetry, Tracer tracer) {
    	
    	// Create a Telemetry Context with parent trace and span IDs provided through non-native propagation means
    	// e.g. A scheduled job processor starting based on file-based input, where this information is passed via file.
    	String parentTraceId = "35000d7d366a5cb200051571c9000001";
    	String parentSpanId = "26f045f879cdb3ad";
    	
    	// Using a Custom TextMap Getter to be the propagation method, with a HashMap as the vehicle of propagation.
    	Map <String, String> otelParentInfo = new HashMap<String, String>();
    	otelParentInfo.put("otel_parent_trace_id", parentTraceId);
    	otelParentInfo.put("otel_parent_span_id", parentSpanId);
    	    	
    	Context manualContext = Context.current();
        final CustomTextMapGetter getter = new CustomTextMapGetter();
        
        manualContext = openTelemetry.getPropagators()
        		.getTextMapPropagator()
        		.extract(Context.current(), otelParentInfo, getter);
        
    	final SolaceJCSMPTextMapSetter setter = new SolaceJCSMPTextMapSetter();
        final TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
        
        final Span sendSpan = tracer
            .spanBuilder("Product Update > Send")
            .setSpanKind(SpanKind.PRODUCER)

            // Optional: user defined Span attributes
            // dot separated, snake_case is the convention, keeping to a fixed 'something.*' name space too for custom ones.
            // See: https://opentelemetry.io/docs/specs/semconv/general/attribute-naming/

            // Some runtime attributes to include:
            .setAttribute("env", "Development")
            .setAttribute("user.name", System.getProperty("user.name"))
            .setAttribute("java.version", System.getProperty("java.version"))
            .setAttribute("os.name", System.getProperty("os.name"))
            // Try others such as local IP, Hostname, Process ID, etc.
            
            // Some transport attributes to include, in the SemanticAttributes name space:
            // See: https://opentelemetry.io/docs/specs/semconv/general/trace/

            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "solace")
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "send")
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, messageDestination.getName())
            .setAttribute(SemanticAttributes.NET_PROTOCOL_NAME, "smf")

            //.setParent(Context.current()) // set current context as parent	// empty in this case, same as .setNoParent()
            .setParent(manualContext)	// Changes things to PropagatedSpan vs SdkSpan, but doesn't show in Jaeger.
            //.setNoParent()
            .startSpan();
                
        try (Scope scope = sendSpan.makeCurrent()) {

            // Add some OTEL Baggage (key-value store) of contextual information 
            // that can 'propagate' across multiple systems and spans by being copied from one to another.
            // See: https://opentelemetry.io/docs/concepts/signals/baggage/

            // This is actually carried as span attributes in a specific attribute naming range, 
            // however it is transparent to the recipient when the baggage is extracted.
            // i.e. No need to worry about a name space, just key names as needed by the downstream application(s).
            // Using the W3C Propagater, see below for key name rules and restrictions.
            // https://www.w3.org/TR/baggage/#key

            String productCode = "ACME123";

            String telemetryBaggageStr = "product_operation=updated,product_code=" + productCode;
            Baggage telemetryBaggage = BaggageUtil.extractBaggage(telemetryBaggageStr);

            telemetryBaggage.storeInContext(Context.current()).makeCurrent();

            //and then inject "Transport Context with send span into the message
            
            propagator.inject(Context.current(), message, setter);

            // Insert the trace info as a message property to convey it to systems and protocols that do not support otel natively
            // i.e. Could be useful for internal logging for a receiver, or when creating onward spans manually and need the parent Trace ID.
            SDTMap messageProps = JCSMPFactory.onlyInstance().createMap();
            messageProps.putString("otel_parent_trace_id", Span.current().getSpanContext().getTraceId());
            messageProps.putString("otel_parent_span_id", Span.current().getSpanContext().getSpanId());
            messageProps.putString("otel_parent_baggage", telemetryBaggageStr ); 
            message.setProperties(messageProps);
            
            //message is being published to the given destination
            messagePublisher.send(message, messageDestination);

            System.out.println("Test message sent, search for Trace ID: " + Span.current().getSpanContext().getTraceId());

        } catch (Exception e) {
            sendSpan.recordException(e); //Span can record exception if any
            sendSpan.setStatus(StatusCode.ERROR, e.getMessage());
        } finally {
            sendSpan.end(); //Span data is exported when span.end() is called
        }
    }

    public static void main(String...args) throws JCSMPException {
        if (args.length < 3) { // Check command line arguments
            log("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        log(API + " " + SAMPLE_NAME + " initializing...");

        try {
            new DirectPublisherWithCustomPropagation().run(args);
        } catch (JCSMPException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        log("Main thread quitting.");
    }

    private static void log(String logMsg) {
        System.out.println(logMsg);
    }

    private static void log(String logMsg, Object...args) {
        System.out.println(String.format(logMsg, args));
    }
}