---
layout: tutorials
title: Publish/Subscribe
summary: Learn to publish and subscribe to messages.
icon: I_dev_P+S.svg
links:
    - label: TopicPublisher.java
      link: /blob/master/src/main/java/com/solace/samples/TopicPublisher.java
    - label: TopicSubscriber.java
      link: /blob/master/src/main/java/com/solace/samples/TopicSubscriber.java
---

This tutorial will introduce you to the fundamentals of the Solace API by connecting a client, adding a topic subscription and sending a message matching this topic subscription. This forms the basis for any publish / subscribe message exchange.

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to Solace messaging with the following configuration details:
    *   Connectivity information for a Solace message-VPN
    *   Enabled client username and password

One simple way to get access to Solace messaging quickly is to create a messaging service in Solace Cloud [as outlined here]({{ site.links-solaceCloud-setup}}){:target="_top"}. You can find other ways to get access to Solace messaging below.


## Goals

The goal of this tutorial is to demonstrate the most basic messaging interaction using Solace. This tutorial will show you:

1.  How to build and send a message on a topic
2.  How to subscribe to a topic and receive a message


{% include_relative assets/solaceMessaging.md %}
{% include_relative assets/solaceApi.md %}


## Connecting to the Solace message router

In order to send or receive messages, an application must connect a Solace session. The Solace session is the basis for all client communication with the Solace message router.

In the Solace messaging API for Java (JCSMP), Solace sessions are created from the JCSMP factory using a set of properties.

```java
final JCSMPProperties properties = new JCSMPProperties();
properties.setProperty(JCSMPProperties.HOST, args[0]);
properties.setProperty(JCSMPProperties.USERNAME, args[1].split("@")[0]);
properties.setProperty(JCSMPProperties.VPN_NAME,  args[1].split("@")[1]);
if (args.length > 2) {
    properties.setProperty(JCSMPProperties.PASSWORD, args[2]);
}
final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);

session.connect();
```

At this point your client is connected to the Solace message router. You can use SolAdmin to view the client connection and related details.

## Receiving a message

This tutorial uses “Direct” messages which are at most once delivery messages. So first, let’s express interest in the messages by subscribing to a Solace topic. Then you can look at publishing a matching message and see it received.

With a session connected in the previous step, the next step is to create a message consumer. Message consumers enable the asynchronous receipt of messages through callbacks. These callbacks are defined in JCSMP by the XMLMessageListener interface.

![]({{ site.baseurl }}/assets/images/pub-sub-receiving-message-300x134.png)

```java
final CountDownLatch latch = new CountDownLatch(1);

final XMLMessageConsumer cons = session.getMessageConsumer(new XMLMessageListener() {

    @Override
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

    @Override
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

![]({{ site.baseurl }}/assets/images/pub-sub-sending-message-300x134.png)

### Establishing the publisher flow

In JCSMP, a message producer is required for sending messages to a Solace message router. Message Producers implement either the `JCSMPStreamingPublishEventHandler` or the `JCSMPStreamingPublishCorrelatingEventHandler`. The `JCSMPStreamingPublishEventHandler` is the simplest and is sufficient for sending direct messages to a Solace message router.

```java
XMLMessageProducer prod = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {

    @Override
    public void responseReceived(String messageID) {
        System.out.println("Producer received response for msg: " + messageID);
    }

    @Override
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

<ul>
{% for item in page.links %}
<li><a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
</ul>

### Getting the Source

This tutorial is available in GitHub.  To get started, clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.repository | split: '/' | last}}
```

### Building

The build instructions in this tutorial assume you are using a Linux shell. If your environment differs, adapt the instructions.

Building these examples is simple.  You can simply build the project using Gradle.

```
./gradlew assemble
```

This builds all of the Java Getting Started Samples with OS specific launch scripts. The files are staged in the `build/staged` directory.

### Running the Sample

If you start the `TopicSubscriber`, with the required arguments of your Solace messaging, it will connect and wait for a message.

```
$ ./build/staged/bin/topicSubscriber <host:port> <client-username>@<message-vpn> [client-password]
TopicSubscriber initializing...
Connected. Awaiting message...
```

Then you can send a message using the `TopicPublisher` with the same arguments. If successful, the output for the producer will look like the following:

```
$ ./build/staged/bin/topicPublisher <host:port> <client-username>@<message-vpn> [client-password]
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
