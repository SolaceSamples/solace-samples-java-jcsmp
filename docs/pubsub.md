This tutorial will introduce you to the fundamentals of the Solace API by connecting a client, adding a topic subscription and sending a message matching this topic subscription. This forms the basis for any publish / subscribe message exchange illustrated here:

![](http://2vs7bv4aq50r1hyri14a8xkf.wpengine.netdna-cdn.com/wp-content/uploads/2015/08/publish-subscribe.png)

---

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts](http://dev.solacesystems.com/docs/core-concepts/).
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN
    *   Enabled client username

One simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here](http://dev.solacesystems.com/docs/get-started/setting-up-solace-vmr_vmware/). By default the Solace VMR will run with the “default” message VPN configured and ready for messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration, adapt the instructions to match your configuration.

---

## Goals

The goal of this tutorial is to demonstrate the most basic messaging interaction using Solace. This tutorial will show you:

1.  How to build and send a message on a topic
2.  How to subscribe to a topic and receive a message

---

## Solace message router properties

In order to send or receive messages to a Solace message router, you need to know a few details of how to connect to the Solace message router. Specifically you need to know the following:


|**Resource**|**Value**|**Description**|
|------------|---------|---------------|
|Host        |String of the form `DNS name` or `IP:Port`|This is the address clients use when connecting to the Solace message router to send and receive messages. For a Solace VMR this there is only a single interface so the IP is the same as the management IP address. For Solace message router appliances this is the host address of the message-backbone.|
|Message VPN|String|The Solace message router Message VPN that this client should connect to. The simplest option is to use the “default” message-vpn which is present on all Solace message routers and fully enabled for message traffic on Solace VMRs.|
|Client Username|String|The client username. For the Solace VMR default message VPN, authentication is disabled by default, so this can be any value.|
|Client Password|String|The optional client password. For the Solace VMR default message VPN, authentication is disabled by default, so this can be any value or omitted.|

For the purposes of this tutorial, you will connect to the default message VPN of a Solace VMR so the only required information to proceed is the Solace VMR host string which this tutorial accepts as an argument.

---

## Obtaining the Solace API

This tutorial depends on you having the Java API downloaded and available. The Java API library can be [downloaded here](http://dev.solacesystems.com/downloads/). The Java API is distributed as a zip file containing the required jars, API documentation, and examples. The instructions in this tutorial assume you have downloaded the Java API library and unpacked it to a known location. If your environment differs then adjust the build instructions appropriately.

---

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

---

## Receiving a message

This tutorial uses “Direct” messages which are at most once delivery messages. So first, let’s express interest in the messages by subscribing to a Solace topic. Then you can look at publishing a matching message and see it received.

With a session connected in the previous step, the next step is to create a message consumer. Message consumers enable the asynchronous receipt of messages through callbacks. These callbacks are defined in JCSMP by the XMLMessageListener interface.

![](http://2vs7bv4aq50r1hyri14a8xkf.wpengine.netdna-cdn.com/wp-content/uploads/2015/08/pub-sub-receiving-message-300x134.png)

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

---

## Sending a message

Now it is time to send a message to the waiting consumer.

![](http://2vs7bv4aq50r1hyri14a8xkf.wpengine.netdna-cdn.com/wp-content/uploads/2015/08/pub-sub-sending-message-300x134.png)

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

---

## Summarizing

Combining the example source code shown above results in the following source code files:

*   [TopicPublisher.java](http://dev.solacesystems.com/wp-content/uploads/TopicPublisher.java)
*   [TopicSubscriber.java](http://dev.solacesystems.com/wp-content/uploads/TopicSubscriber.java)

### Building

Building these examples is simple. The following provides an example using Linux. These instructions assume you have unpacked the Solace Java API into a directory next to the getting started samples that you just downloaded. There are many suitable ways to build and execute these samples in Java. Adapt these instructions to suit your needs depending on your environment.

In the following examples replace VERSION with the Solace API version you downloaded.

```
javac -cp sol-jcsmp-VERSION/lib/*:. TopicPublisher.java
javac -cp sol-jcsmp-VERSION/lib/*:. TopicSubscriber.java
```

### Sample Output

If you start the TopicSubscriber with a single argument for the Solace message router host address it will connect and wait for a message.

```
$ java -cp sol-jcsmp-VERSION/lib/*:. TopicSubscriber HOST
TopicSubscriber initializing...
Connected. Awaiting message...
```

Then you can send a message using the TopicPublisher again using a single argument to specify the Solace message router host address. If successful, the output for the producer will look like the following:

```
$ java -cp sol-jcsmp-VERSION/lib/*:. TopicPublisher HOST
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

If you have any issues sending and receiving a message, check the [Solace community](http://dev.solacesystems.com/community/) for answers to common issues.
