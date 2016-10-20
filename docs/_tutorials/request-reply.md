---
layout: tutorials
title: Request/Reply
summary: Learn how to set up request/reply messaging.
icon: request-reply.png
---


This tutorial outlines both roles in the request-response message exchange pattern. It will show you how to act as the client by creating a request, sending it and waiting for the response. It will also show you how to act as the server by receiving incoming requests, creating a reply and sending it back to the client. It builds on the basic concepts introduced in [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe).

![]({{ site.baseurl }}/images/request-reply.png)

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN
    *   Enabled client username

One simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here]({{ site.docs-vmr-setup }}){:target="_top"}. By default the Solace VMR will run with the “default” message VPN configured and ready for messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration, adapt the instructions to match your configuration.

The build instructions in this tutorial assume you are using a Linux shell. If your environment differs, adapt the instructions.

## Goals

The goal of this tutorial is to understand the following:

*   On the requestor side:
    1.  How to create a request
    2.  How to receive a response
    3.  How to use the Solace API to correlate the request and response
*   On the replier side:
    1.  How to detect a request expecting a reply
    2.  How to generate a reply message

## Overview

Request-reply messaging is supported by the Solace message router for all delivery modes. For direct messaging, the Solace APIs provide the Requestor object for convenience. This object makes it easy to send a request and wait for the reply message. It is a convenience object that makes use of the API provided “inbox” topic that is automatically created for each Solace client and automatically correlates requests with replies using the message correlation ID. (See Message Correlation below for more details). On the reply side another convenience method enables applications to easily send replies for specific requests. Direct messaging request reply is the delivery mode that is illustrated in this sample.

It is also possible to use guaranteed messaging for request reply scenarios. In this case the replier can listen on a queue for incoming requests and the requestor can use a temporary endpoint to attract replies. The requestor and replier must manually correlate the messages. This is explained further in the [Solace documentation]({{ site.docs-gm-rr }}){:target="_top"} and shown in the API samples named `RRGuaranteedRequestor` and `RRGuaranteedReplier`.

### Message Correlation

For request-reply messaging to be successful it must be possible for the requestor to correlate the request with the subsequent reply. Solace messages support two fields that are needed to enable request-reply correlation. The reply-to field can be used by the requestor to indicate a Solace Topic or Queue where the reply should be sent. A natural choice for this is often the unique `P2PINBOX_IN_USE` topic which is an auto-generated unique topic per client which is accessible as a session property. The second requirement is to be able to detect the reply message from the stream of incoming messages. This is accomplished using the correlation-id field. This field will transit the Solace messaging system unmodified. Repliers can include the same correlation-id in a reply message to allow the requestor to detect the corresponding reply. The figure below outlines this exchange.

![]({{ site.baseurl }}/images/Request-Reply_diagram-1.png)

For direct messages however, this is simplified through the use of the `Requestor` object as shown in this sample.

## Trying it yourself

This tutorial is available in [GitHub]({{ site.repository }}){:target="_blank"} along with the other [Solace Developer Getting Started Examples]({{ site.links-get-started }}){:target="_top"}.

To successfully build the samples you must have the Java API downloaded and available. The Java API library can be [downloaded here]({{ site.links-downloads }}){:target="_top"}. The Java API is distributed as a zip file containing the required jars, API documentation, and examples.

At the end, this tutorial walks through downloading and running the sample from source.

## Connecting a session to the message router

As with other tutorials, this tutorial requires a JCSMPSession connected to the default message VPN of a Solace VMR which has authentication disabled. So the only required information to proceed is the Solace VMR host string which this tutorial accepts as an argument. Connect the session as outlined in the [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe).

## Making a request

First let’s look at the requestor. This is the application that will send the initial request message and wait for the reply.

![]({{ site.baseurl }}/images/Request-Reply_diagram-2.png)

For convenience, we will use the `Requestor` object that is created from the `Session` object. The Requestor object makes use of the Session’s `Producer` and `Consumer` objects to send messages and receive replies. So in order for the `Requestor` to function correctly, there must be a `Producer` and `Consumer` created within the session. Normally this will already be done by other parts of the application. However, for demonstration purposes, the simplest was to accomplish this is show below.

```java
XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
    public void responseReceived(String messageID) {
        System.out.println("Producer received response for msg: " + messageID);
    } public void handleError(String messageID, JCSMPException e, long timestamp) {
        System.out.printf("Producer received error for msg: %s@%s - %s%n",
            messageID,timestamp,e);
    }
});
XMLMessageConsumer consumer = session.getMessageConsumer((XMLMessageListener)null); consumer.start();
```

Next you must create a message and the topic to send the message to. This is done in the same way as illustrated in the [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe).

```java
final Topic topic = JCSMPFactory.onlyInstance().createTopic("tutorial/requests");
TextMessage request = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
final String text = "Sample Request";
request.setText(text);
```

Finally create a Requestor object and send the request. This example demonstrates a blocking call where the method will wait for the response message to be received. Asynchronous is also possible. For this see the [online API documentation]({{ site.docs-api-ref }}){:target="_top"} for more details.

```java
final int timeoutMs = 10000;
try {
    Requestor requestor = session.createRequestor();
    BytesXMLMessage reply = requestor.request(request, timeoutMs, topic);
} catch (JCSMPRequestTimeoutException e) {
    System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
}
```

If no response is received within the timeout specified (10 seconds in this example), then the API will throw a `JCSMPRequestTimeoutException`.

## Replying to a request

Now it is time to receive the request and generate an appropriate reply.

![Request-Reply_diagram-3]({{ site.baseurl }}/images/Request-Reply_diagram-3.png)

Just as with previous tutorials, you still need to connect a session and subscribe to the topics that requests are sent on. However, in order to send replies back to the requestor, you will also need a `Producer`. The following is an example of the most basic producer.

```java
/* Anonymous inner-class for handling publishing events */
final XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
    public void responseReceived(String messageID) {
        System.out.println("Producer received response for msg: " + messageID);
    }

    public void handleError(String messageID, JCSMPException e, long timestamp) {
        System.out.printf("Producer received error for msg: %s@%s - %s%n", messageID, timestamp, e);
    }
});
```

Then you simply have to modify the `onReceive()` method of the `XMLMessageConsumer` to inspect incoming messages and generate appropriate replies. For example, the following code will send a response to all messages that have a reply-to field. This makes use of the `XMLMessageProducer` convenience method `sendReply()`. This method will properly copy the correlation-ID from the request to the reply and send the reply message to the reply-to destination found in the request message.

```java
public void onReceive(BytesXMLMessage request) {

    if (request.getReplyTo() != null) {
        TextMessage reply =
            JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        final String text = "Sample response";
        reply.setText(text);

        try {
            producer.sendReply(request, reply);
        } catch (JCSMPException e) {
            System.out.println("Error sending reply.");
        }
    } else {
        System.out.println("Received message without reply-to field");
    }
}
```

## Receiving the Reply Message

All that’s left is to receive and process the reply message as it is received at the requestor. If you now update your requestor code to match the following you will see each reply printed to the console.

```java
try {
    Requestor requestor = session.createRequestor();
    BytesXMLMessage reply = requestor.request(request, timeoutMs, topic);

    // Process the reply
    if (reply instanceof TextMessage) {
        System.out.printf("TextMessage response received: '%s'%n",
            ((TextMessage)reply).getText());
    }
    System.out.printf("Response Message Dump:%n%s%n",reply.dump());
} catch (JCSMPRequestTimeoutException e) {
    System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
}
```

## Summarizing

The full source code for this example is available in [GitHub]({{ site.repository }}){:target="_blank"}. If you combine the example source code shown above results in the following source:

*   [BasicRequestor.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/BasicRequestor.java){:target="_blank"}
*   [BasicReplier.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/BasicReplier.java){:target="_blank"}

### Getting the Source

Clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.baseurl | remove: '/'}}
```

### Building

Building these examples is simple.  Download and unpacked the Java API library to a known location. Then copy the contents of the `sol-jcsmp-VERSION/lib` directory to a `libs` sub-directory in your `{{ site.baseurl | remove: '/'}}`.

In the following command line replace VERSION with the Solace API version you downloaded.

```
mkdir libs
cp  ../sol-jcsmp-VERSION/lib/* libs
```

Now you can simply build the project using Gradle.

```
./gradlew assemble
```

This build all of the Java Getting Started Samples with OS specific launch scripts. The files are staged in the `build/staged` directory.

### Running the Sample

First start the `BasicReplier` so that it is up and listening for requests. Then you can use the `BasicRequestor` sample to send requests and receive replies.

```
$ ./build/staged/bin/basicReplier <HOST>
$ ./build/staged/bin/basicRequestor <HOST>
```

With that you now know how to successfully implement the request-reply message exchange pattern using Direct messages.

If you have any issues sending and receiving a message, check the [Solace community]({{ site.links-community }}){:target="_top"} for answers to common issues.
