---
layout: tutorials
title: Publish/Subscribe
summary: Learn how to set up pub/sub messaging on a Solace VMR.
icon: publish-subscribe.png
---


This tutorial will introduce you to the fundamentals of the Solace API by connecting a client, adding a topic subscription and sending a message matching this topic subscription. This forms the basis for any publish / subscribe message exchange illustrated here:

![]({{ site.baseurl }}/images/publish-subscribe.png)

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN
    *   Enabled client username

One simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here]({{ site.docs-vmr-setup }}){:target="_top"}. By default the Solace VMR will run with the “default” message VPN configured and ready for messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration, adapt the instructions to match your configuration.

The build instructions in this tutorial assume you are using a Linux shell. If your environment differs, adapt the instructions.

## Goals

The goal of this tutorial is to demonstrate the most basic messaging interaction using Solace. This tutorial will show you:

1.  How to build and send a message on a topic
2.  How to subscribe to a topic and receive a message

## Solace message router properties

In order to send or receive messages to a Solace message router, you need to know a few details of how to connect to the Solace message router. Specifically you need to know the following:


<table>
  <tr>
    <th>Resource</th>
    <th>Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>Host</td>
    <td>String of the form <code>DNS name</code> or <code>IP:Port</code></td>
    <td>This is the address clients use when connecting to the Solace message router to send and receive messages. For a Solace VMR this there is only a single interface so the IP is the same as the management IP address. For Solace message router appliances this is the host address of the message-backbone.</td>
  </tr>
  <tr>
    <td>Message VPN</td>
    <td>String</td>
    <td>The Solace message router Message VPN that this client should connect to. The simplest option is to use the “default” message-vpn which is present on all Solace message routers and fully enabled for message traffic on Solace VMRs.</td>
  </tr>
  <tr>
    <td>Client Username</td>
    <td>String</td>
    <td>The client username. For the Solace VMR default message VPN, authentication is disabled by default, so this can be any value.</td>
  </tr>
  <tr>
    <td>Client Password</td>
    <td>String</td>
    <td>The optional client password. For the Solace VMR default message VPN, authentication is disabled by default, so this can be any value or omitted.</td>
  </tr>
</table>

For the purposes of this tutorial, you will connect to the default message VPN of a Solace VMR so the only required information to proceed is the Solace VMR host string which this tutorial accepts as an argument.

## Obtaining the Solace API

This tutorial depends on you having the Solace Messaging API for Java (JCSMP). Here are a few easy ways to get the Java API. The instructions in the [Building](#building) section assume you're using Gradle and pulling the jars from maven central. If your environment differs then adjust the build instructions appropriately.

### Get the API: Using Gradle

```
compile("com.solacesystems:sol-jcsmp:10.+")
```

### Get the API: Using Maven

```
<dependency>
  <groupId>com.solacesystems</groupId>
  <artifactId>sol-jcsmp</artifactId>
  <version>10.+</version>
</dependency>
```

### Get the API: Using the Solace Developer Portal

The Java API library can be [downloaded here]({{ site.links-downloads }}){:target="_top"}. The Java API is distributed as a zip file containing the required jars, API documentation, and examples. 

## Trying it yourself

This tutorial is available in [GitHub]({{ site.repository }}){:target="_blank"} along with the other [Solace Developer Getting Started Examples]({{ site.links-get-started }}){:target="_top"}.

At the end, this tutorial walks through downloading and running the sample from source.

## Connecting to the Solace message router

In order to send or receive messages, an application must connect a Solace session. The Solace session is the basis for all client communication with the Solace message router.

In the Solace messaging API for Java (JCSMP), Solace sessions are created from the JCSMP factory from a set of properties.

```java
final JCSMPProperties properties = new JCSMPProperties();
properties.setProperty(JCSMPProperties.HOST, args[0]);
properties.setProperty(JCSMPProperties.VPN_NAME, "default");
properties.setProperty(JCSMPProperties.USERNAME, "helloWorldTutorial");
final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
session.connect();
```

At this point your client is connected to the Solace message router. You can use SolAdmin to view the client connection and related details.

## Receiving a message

This tutorial uses “Direct” messages which are at most once delivery messages. So first, let’s express interest in the messages by subscribing to a Solace topic. Then you can look at publishing a matching message and see it received.

With a session connected in the previous step, the next step is to create a message consumer. Message consumers enable the asynchronous receipt of messages through callbacks. These callbacks are defined in JCSMP by the XMLMessageListener interface.

![]({{ site.baseurl }}/images/pub-sub-receiving-message-300x134.png)

```java
final CountDownLatch latch = new CountDownLatch(1);

final XMLMessageConsumer cons = session.getMessageConsumer(new XMLMessageListener() {
    public void onReceive(BytesXMLMessage msg) {
        if (msg instanceof TextMessage) {
            System.out.printf("TextMessage received: '%s'%n",
                              ((TextMessage)msg).getText());
        } else {
            System.out.println("Message received.");
        }
        System.out.printf("Message Dump:%n%s%n",msg.dump());
        latch.countDown();  // unblock main thread
    }
    public void onException(JCSMPException e) {
        System.out.printf("Consumer received exception: %s%n",e);
        latch.countDown();  // unblock main thread
    }
});
```

The message consumer code uses a countdown latch in this hello world example to block the consumer thread until a single message has been received.

Then you must subscribe to a topic in order to express interest in receiving messages. This tutorial uses the topic `tutorial/topic`.

```java
final Topic topic = JCSMPFactory.onlyInstance().createTopic("tutorial/topic");
session.addSubscription(topic);
cons.start();
```

Then after the subscription is added, the consumer is started. At this point the consumer is ready to receive messages.

```java
try {
    latch.await(); // block here until message received, and latch will flip
} catch (InterruptedException e) {
    System.out.println("I was awoken while waiting");
}
```

## Sending a message

Now it is time to send a message to the waiting consumer.

![]({{ site.baseurl }}/images/pub-sub-sending-message-300x134.png)

### Establishing the publisher flow

In JCSMP, a message producer is required for sending messages to a Solace message router. Message Producers implement either the `JCSMPStreamingPublishEventHandler` or the `JCSMPStreamingPublishCorrelatingEventHandler`. The `JCSMPStreamingPublishEventHandler` is the simplest and is sufficient for sending direct messages to a Solace message router.

```java
XMLMessageProducer prod = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
    public void responseReceived(String messageID) {
        System.out.println("Producer received response for msg: " + messageID);
    }
    public void handleError(String messageID, JCSMPException e, long timestamp) {
        System.out.printf("Producer received error for msg: %s@%s - %s%n",
                           messageID,timestamp,e);
     }
});
```

The above code will print out any callbacks received from the Solace API by the application.

### Creating and sending the message

To send a message, you must create a message and a topic. Both of these are created from the JCSMPFactory. This tutorial will send a Solace Text message with contents “Hello world!”.

```java
final Topic topic = JCSMPFactory.onlyInstance().createTopic("tutorial/topic");
TextMessage msg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
final String text = "Hello world!";
msg.setText(text);
prod.send(msg,topic);
```

At this point the producer has sent a message to the Solace message router and your waiting consumer will have received the message and printed its contents to the screen.

## Summarizing

The full source code for this example is available in [GitHub]({{ site.repository }}){:target="_blank"}. If you combine the example source code shown above results in the following source:

*   [TopicPublisher.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/TopicPublisher.java){:target="_blank"}
*   [TopicSubscriber.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/TopicSubscriber.java){:target="_blank"}

### Getting the Source

Clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.baseurl | remove: '/'}}
```

### Building

Building these examples is simple.  You can simply build the project using Gradle.

```
./gradlew assemble
```

This builds all of the Java Getting Started Samples with OS specific launch scripts. The files are staged in the `build/staged` directory.

### Running the Sample

If you start the `TopicSubscriber` with a single argument for the Solace message router host address it will connect and wait for a message.

```
$ ./build/staged/bin/topicSubscriber <HOST>
TopicSubscriber initializing...
Connected. Awaiting message...
```

Then you can send a message using the `TopicPublisher` again using a single argument to specify the Solace message router host address. If successful, the output for the producer will look like the following:

```
$ ./build/staged/bin/topicPublisher <HOST>
Topic Publisher initializing...
Connected. About to send message 'Hello world!' to topic 'tutorial/topic'...
Message sent. Exiting.
```

With the message delivered the subscriber output will look like the following:

```
Received message:
Destination:         Topic 'tutorial/topic'
Class Of Service:    COS_1
DeliveryMode:        DIRECT
Binary Attachment:   len=12
48 65 6c 6c 6f 20 77 6f 72 6c 64 21           Hello world!
Exiting.
```

The received message is printed to the screen. The TextMessage contents was “Hello world!” as expected and the message dump contains extra information about the Solace message that was received.

You have now successfully connected a client, subscribed to a topic and exchanged messages using this topic.

If you have any issues sending and receiving a message, check the [Solace community]({{ site.links-community }}){:target="_top"} for answers to common issues.
