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

package com.solace.samples.jcsmp.snippets;

import com.solace.messaging.trace.propagation.SolaceJCSMPTextMapGetter;
import com.solace.messaging.trace.propagation.SolaceJCSMPTextMapSetter;
import com.solacesystems.jcsmp.Topic;
import com.solacesystems.jcsmp.XMLMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.semconv.SemanticAttributes.MessagingDestinationKindValues;
import io.opentelemetry.semconv.SemanticAttributes.MessagingOperationValues;
import java.util.function.Consumer;

public class HowToImplementTracingManualInstrumentation {

  /**
   * Example how to inject a tracing context into Solace Message before it is published to a queue
   * or topic.
   *
   * @param messageToPublish A Solace JCSMP message to be used for publishing.
   * @param openTelemetry    The entry point to telemetry functionality for tracing, metrics and
   *                         baggage.
   */
  void howToInjectTraceContextInSolaceMessage(XMLMessage messageToPublish,
      OpenTelemetry openTelemetry) {
    final SolaceJCSMPTextMapSetter setter = new SolaceJCSMPTextMapSetter();
    // Injects current context into the message to transport it across message boundaries.
    // Transported context will be used to create parent - child relationship
    // between spans from different services and broker spans
    final Context contextToInject = Context.current();
    openTelemetry.getPropagators().getTextMapPropagator()
        .inject(contextToInject, messageToPublish, setter);
  }

  /**
   * Example how to extract a tracing context from the JCSMP Solace Message upon message receipt.
   *
   * @param receivedMessage Received Solace message.
   * @param openTelemetry   The entry point to telemetry functionality for tracing, metrics and
   *                        baggage.
   */
  void howToExtractTraceContextIfAnyFromSolaceMessage(XMLMessage receivedMessage,
      OpenTelemetry openTelemetry) {
    //Extracts tracing context from a message, if any using the SolaceJCSMPTextMapGetter
    final SolaceJCSMPTextMapGetter getter = new SolaceJCSMPTextMapGetter();
    final Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
        .extract(Context.current(), receivedMessage, getter);
    //and then set the extractedContext as current context
    try (Scope scope = extractedContext.makeCurrent()) {
      scope.equals(null);  // to not get the compile warning due to not using scope.  stupid javac
      //...
    }
  }

  /**
   * Example how to inject a tracing context in the Solace Message and generate a SEND span for the
   * published message
   *
   * @param message            A Solace message that support tracing context propagation.
   * @param messageProducer    JCSMP Message producer that can publish messages
   * @param messageDestination message will be published to this topic
   * @param openTelemetry      The entry-point to telemetry functionality for tracing, metrics and
   *                           baggage.
   * @param tracer             Tracer is the interface for Span creation and interaction with the
   *                           in-process context.
   */
  void howToCreateSpanOnMessagePublish(XMLMessage message, XMLMessageProducer messageProducer,
      Topic messageDestination, OpenTelemetry openTelemetry, Tracer tracer) {

    //Create a new span with a current context as parent of this span
    final Span sendSpan = tracer
        .spanBuilder("mySolacePublisherApp" + " " + MessagingOperationValues.PROCESS)
        .setSpanKind(SpanKind.CLIENT)
        // published to a topic endpoint (non temporary)
        .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND,
            MessagingDestinationKindValues.TOPIC)
        .setAttribute(SemanticAttributes.MESSAGING_TEMP_DESTINATION, false)
        //Set more attributes as needed
        //.setAttribute(...)
        //.setAttribute(...)
        .setParent(Context.current()) // set current context as parent
        .startSpan();

    //set sendSpan as new current context
    try (Scope scope = sendSpan.makeCurrent()) {
      scope.equals(null);  // to not get the compile warning due to not using scope.  stupid javac
      final SolaceJCSMPTextMapSetter setter = new SolaceJCSMPTextMapSetter();
      final TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
      //and then inject current context with send span into the message
      propagator.inject(Context.current(), message, setter);
      // message is being published to the given topic
      messageProducer.send(message, messageDestination);
    } catch (Exception e) {
      sendSpan.recordException(e); //Span can record exception if any
      sendSpan.setStatus(StatusCode.ERROR, e.getMessage()); //Set span status as ERROR/FAILED
    } finally {
      sendSpan.end(); //End sendSpan. Span data is exported when span.end() is called.
    }
  }

  /**
   * Example how to extract a tracing context from the Solace Message and generate a RECEIVE span
   * for the received message
   *
   * @param receivedMessage  A Solace message.
   * @param messageProcessor A callback function that user could use to process a message
   * @param openTelemetry    The OpenTelemetry class is the entry point to telemetry functionality
   *                         for tracing, metrics and baggage from OpenTelemetry Java SDK.
   * @param tracer           OpenTelemetry Tracer is the interface from OpenTelemetry Java SDK for
   *                         span creation and interaction with the in-process context.
   */
  void howToCreateNewSpanOnMessageReceive(XMLMessage receivedMessage,
      Consumer<XMLMessage> messageProcessor,
      OpenTelemetry openTelemetry, Tracer tracer) {

    //Extract tracing context from message, if any using the SolaceJCSMPTextMapGetter
    final SolaceJCSMPTextMapGetter getter = new SolaceJCSMPTextMapGetter();
    final Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
        .extract(Context.current(), receivedMessage, getter);

    //Set the extracted context as current context
    try (Scope scope = extractedContext.makeCurrent()) {
      scope.equals(null);  // to not get the compile warning due to not using scope.  stupid javac
      //Create a child span and set extracted/current context as parent of this span
      final Span receiveSpan = tracer
          .spanBuilder("mySolaceReceiverApp" + " " + MessagingOperationValues.RECEIVE)
          .setSpanKind(SpanKind.CLIENT)
          // for the case the message was received on a non temporary queue endpoint
          .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND,
              MessagingDestinationKindValues.QUEUE)
          .setAttribute(SemanticAttributes.MESSAGING_TEMP_DESTINATION, false)
          //Set more attributes as needed
          //.setAttribute(...)
          //.setAttribute(...)
          // creates a parent child relationship to a message publisher's application span is any
          .setParent(extractedContext)
          // starts span
          .startSpan();

      try {
        // do something with a message in a callback
        messageProcessor.accept(receivedMessage);
      } catch (Exception e) {
        receiveSpan.recordException(e); //Span can record exception if any
        receiveSpan.setStatus(StatusCode.ERROR,
            e.getMessage()); //and set span status as ERROR/FAILED
      } finally {
        receiveSpan.end(); //End receiveSpan. Span data is exported when span.end() is called.
      }
    }
  }
}