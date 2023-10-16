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

import com.solace.messaging.trace.propagation.SolaceJCSMPTextMapGetter;
import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessageConsumer;
import com.solacesystems.jcsmp.XMLMessageListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingOperationValues;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * A sample that shows how to generate a Subscriber/process span with Solace OpenTelemetry
 * Integration for JCSMP.
 * <p>
 * Setup a Solace PubSub+ Broker and OpenTelemetry Collector as per tutorial  >
 * https://codelabs.solace.dev/codelabs/dt-otel/index.html
 * <p>
 * This is the Subscriber in the Publish-Subscribe messaging pattern.
 */
public class DirectSubscriberWithManualInstrumentation {

  private static final String SERVICE_NAME = "SolaceJCSMPTopicSubscriberManualInstrument";
  private static final String SAMPLE_NAME = DirectSubscriberWithManualInstrumentation.class.getSimpleName();
  private static final String TOPIC_NAME = "solace/samples/jcsmp/direct/pub/tracing";
  private static final String API = "JCSMP";

  //Latch used for synchronizing between threads
  private final CountDownLatch latch = new CountDownLatch(1);

  static {
    //Setup OpenTelemetry
    TracingUtil.initManualTracing(SERVICE_NAME);
  }

  public void run(String... args) throws JCSMPException, InterruptedException {
    final JCSMPProperties properties = new JCSMPProperties();
    properties.setProperty(JCSMPProperties.HOST, args[0]);         // host:port
    properties.setProperty(JCSMPProperties.VPN_NAME, args[1]);     // message-vpn
    properties.setProperty(JCSMPProperties.USERNAME, args[2]);     // client-username
    if (args.length > 3) {
      properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
    }

    final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
    session.connect();  // connect to the broker
    final Topic topic = JCSMPFactory.onlyInstance().createTopic(TOPIC_NAME);
    final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);

    //Sample messageProcessor callback
    final Consumer<BytesXMLMessage> consoleLogger = (message) -> {
      log("New message received:%n%s%n", message.dump());
    };

    //Anonymous inner-class for MessageListener, this demonstrates the async threaded message callback
    final XMLMessageConsumer consumer = session.getMessageConsumer(new XMLMessageListener() {
      @Override
      public void onReceive(BytesXMLMessage message) {
        messageReceiverTracer(message, consoleLogger, topic, openTelemetry, tracer);
        latch.countDown();
      }

      @Override
      public void onException(JCSMPException e) {  // uh oh!
        log("### MessageListener's onException(): %s%n", e);
      }
    });

    session.addSubscription(JCSMPFactory.onlyInstance().createTopic(TOPIC_NAME));
    consumer.start();
    log(API + " " + SAMPLE_NAME + " connected, and running.");
    log("Awaiting message...");

    //the main thread blocks at the next statement until a message received
    latch.await();
    session.closeSession();  //will also close consumer object
  }

  private void messageReceiverTracer(BytesXMLMessage receivedMessage,
      Consumer<BytesXMLMessage> messageProcessor, Topic messageDestination,
      OpenTelemetry openTelemetry, Tracer tracer) {
    if (receivedMessage == null) {
      return;
    }

    //Extract tracing context from message, if any using the SolaceJmsW3CTextMapGetter
    final SolaceJCSMPTextMapGetter getter = new SolaceJCSMPTextMapGetter();
    final Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
        .extract(Context.current(), receivedMessage, getter);

    //Set the extracted context as current context
    try (final Scope scope = extractedContext.makeCurrent()) {
      //Create a child span and set extracted/current context as parent of this span
      final Span span = tracer
          .spanBuilder("mySolaceReceiverApp" + " " + MessagingOperationValues.PROCESS)
          .setSpanKind(SpanKind.CONSUMER)
          //Optional: user defined Span attributes
          .setAttribute("SERVICE_NAME", SERVICE_NAME)
          .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "solace")
          .setAttribute(SemanticAttributes.MESSAGING_OPERATION, MessagingOperationValues.PROCESS)
          .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, messageDestination.getName())
          .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_TEMPORARY, false)
          // creates a parent child relationship to a message publisher's application span if any
          .setParent(extractedContext)
          .startSpan();

      try {
        messageProcessor.accept(receivedMessage);
      } catch (Exception e) {
        span.recordException(e); //Span can record exception if any
        span.setStatus(StatusCode.ERROR, e.getMessage());
      } finally {
        span.end(); //Span data is exported when span.end() is called
      }
    }
  }

  public static void main(String... args) throws JCSMPException, InterruptedException {
    if (args.length < 3) {  // Check command line arguments
      log("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
      System.exit(-1);
    }
    log(API + " " + SAMPLE_NAME + " initializing...");

    new DirectSubscriberWithManualInstrumentation().run(args);

    log("Main thread quitting.");
  }

  private static void log(String logMsg) {
    System.out.println(logMsg);
  }

  private static void log(String logMsg, Object... args) {
    System.out.println(String.format(logMsg, args));
  }
}