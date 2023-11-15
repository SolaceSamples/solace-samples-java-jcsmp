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

import com.solace.messaging.trace.propagation.SolaceJCSMPTextMapSetter;
import com.solacesystems.jcsmp.Destination;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
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
public class DirectPublisherWithManualInstrumentation {

  private static final String SERVICE_NAME = "SolaceJCSMPTopicPublisherManualInstrument";
  private static final String SAMPLE_NAME = DirectPublisherWithManualInstrumentation.class.getSimpleName();
  private static final String TOPIC_NAME = "solace/samples/jcsmp/direct/pub/tracing";
  private static final String API = "JCSMP";

  static {
    //Setup OpenTelemetry
    TracingUtil.initManualTracing(SERVICE_NAME);
  }

  public void run(String... args) throws JCSMPException {
    final JCSMPProperties properties = new JCSMPProperties();
    properties.setProperty(JCSMPProperties.HOST, args[0]);         // host:port
    properties.setProperty(JCSMPProperties.VPN_NAME, args[1]);     // message-vpn
    properties.setProperty(JCSMPProperties.USERNAME, args[2]);     // client-username
    if (args.length > 3) {
      properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
    }

    final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
    session.connect();  // connect to the broker

    // Simple anonymous inner-class for handling publishing events
    final XMLMessageProducer producer = session.getMessageProducer(
        new JCSMPStreamingPublishCorrelatingEventHandler() {
          // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
          @Override
          public void responseReceivedEx(Object key) {
          }

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

    final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);

    //TRACE MESSAGE PUBLISH
    messagePublisherTracer(message, producer, topic, openTelemetry, tracer);

    session.closeSession();  // will also close producer object
  }

  private void messagePublisherTracer(XMLMessage message, XMLMessageProducer messagePublisher,
      Destination messageDestination, OpenTelemetry openTelemetry, Tracer tracer) {

    final Span sendSpan = tracer
        .spanBuilder("mySolacePublisherApp > send")
        .setSpanKind(SpanKind.PRODUCER)
        //Optional: user defined Span attributes
        .setAttribute("SERVICE_NAME", SERVICE_NAME)
        .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "solace")
        .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "send")
        .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, messageDestination.getName())
        .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_TEMPORARY, false)
        .setParent(Context.current()) // set current context as parent
        .startSpan();

    try (Scope scope = sendSpan.makeCurrent()) {
      final SolaceJCSMPTextMapSetter setter = new SolaceJCSMPTextMapSetter();
      final TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
      //and then inject current context with send span into the message
      propagator.inject(Context.current(), message, setter);
      //message is being published to the given destination
      messagePublisher.send(message, messageDestination);
    } catch (Exception e) {
      sendSpan.recordException(e); //Span can record exception if any
      sendSpan.setStatus(StatusCode.ERROR, e.getMessage());
    } finally {
      sendSpan.end(); //Span data is exported when span.end() is called
    }
  }

  public static void main(String... args) throws JCSMPException {
    if (args.length < 3) {  // Check command line arguments
      log("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
      System.exit(-1);
    }
    log(API + " " + SAMPLE_NAME + " initializing...");

    new DirectPublisherWithManualInstrumentation().run(args);

    log("Main thread quitting.");
  }

  private static void log(String logMsg) {
    System.out.println(logMsg);
  }

  private static void log(String logMsg, Object... args) {
    System.out.println(String.format(logMsg, args));
  }
}