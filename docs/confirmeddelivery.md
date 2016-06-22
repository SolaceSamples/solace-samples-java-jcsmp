This tutorial builds on the basic concepts introduced in [Persistence with Queues](http://dev.solacesystems.com/docs/get-started/persistence-with-queues_java/) tutorial and will show you how to properly process publisher acknowledgements. Once an acknowledgement for a message has been received and processed, you have confirmed your persistent messages have been properly accepted by the Solace message router and therefore can be guaranteed of no message loss.

![confirmed-delivery](http://2vs7bv4aq50r1hyri14a8xkf.wpengine.netdna-cdn.com/wp-content/uploads/2015/07/confirmed-delivery.png)

---

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts](/docs/core-concepts/).
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN configured for guaranteed messaging support.
    *   Enabled client username.
    *   Client-profile enabled with guaranteed messaging permissions.

Note that one simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here](http://dev.solacesystems.com/docs/get-started/setting-up-solace-vmr_vmware/). By default the Solace VMR will with the “default” message VPN configured and ready for guaranteed messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration adapt the tutorial appropriately to match your configuration.

---

## Goals

The goal of this tutorial is to understand the following:

*  How to properly handle persistent message acknowledgements on message send.

## Obtaining the Solace API

This tutorial depends on you having the Java API downloaded and available. The Java API library can be [downloaded here](http://dev.solacesystems.com/downloads/). The Java API is distributed as a zip file containing the required jars, API documentation, and examples. The instructions in this tutorial assume you have downloaded the Java API library and unpacked it to a known location. If your environment differs then adjust the build instructions appropriately.

---

## Message Acknowledgement Correlation

In order to send fully persistent messages to a Solace message router with no chance of message loss, it is absolutely necessary to properly process the acknowledgements that come back from the Solace message router. These acknowledgements will let you know if the message was accepted by the Solace message router or if it was rejected. If it is rejected, the acknowledgement will also contain exact details of why it was rejected. For example, you may not have permission to send persistent messages or queue destination may not exist etc.

In order to properly handle message acknowledgements it is also important to know which application event or message is being acknowledged. In other words, applications often need some application context along with the acknowledgement from the Solace message router to properly process the business logic on their end. The Solace Java API enables this through a more advanced publisher callback interface called `JCSMPStreamingPublishCorrelatingEventHandler`. This callback allows applications to attach a correlation object on message send and this correlation object is also returned in the acknowledgement. This allows applications to easily pass the application context to the acknowledgement, handling enabling proper correlation of messages sent and acknowledgements received.

For the purposes of this tutorial, we will track message context using the following simple class. It will keep track of the application message ID, the message that was sent, and the result of the acknowledgements.

```java
class MsgInfo {
    public volatile boolean acked = false;
    public volatile boolean publishedSuccessfully = false;
    public BytesXMLMessage sessionIndependentMessage = null;
    public final long id;

    public MsgInfo(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("Message ID: %d, PubConf: %b, PubSuccessful:
                                %b",
                                id,
                                acked,
                                publishedSuccessfully);
    }
}
```

---

## Connection setup

First, connect to the Solace message router in exactly the same way as other tutorials.

```java
final JCSMPProperties properties = new JCSMPProperties();
properties.setProperty(JCSMPProperties.HOST, args[0]);
properties.setProperty(JCSMPProperties.VPN_NAME, "default");
properties.setProperty(JCSMPProperties.USERNAME, "tutorialUser");
final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
session.connect();
```

---

## Adding Message Correlation on Send

The [Persistence with Queues](http://dev.solacesystems.com/docs/get-started/persistence-with-queues_java/) tutorial demonstrated how to send persistent messages using code very similar to the following. The only difference below is the message text and the loop.

```java
for (int i = 1; i &amp;lt;= count; i++) {
    TextMessage msg =   JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
    msg.setDeliveryMode(DeliveryMode.PERSISTENT);
    String text = "Confirmed Publish Tutorial! Message ID: "+ i;
    msg.setText(text);
    prod.send(msg, queue);
}
```

Adding a message correlation object to allow an application to easily correlate acknowledgements is accomplished using the `TextMessage.setCorrelationKey()` method where you pass in the object you want returned to your application in the acknowledgement callback. So after augmenting the publish code from above, you’re left with the following:

```java
for (int i = 1; i &amp;lt;= count; i++) {
    TextMessage msg =     JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
    msg.setDeliveryMode(DeliveryMode.PERSISTENT);
    String text = "Confirmed Publish Tutorial! Message ID: "+ i;
    msg.setText(text);

    final MsgInfo msgCorrelationInfo = new MsgInfo(i);
    msgCorrelationInfo.sessionIndependentMessage = msg;
    msgList.add(msgCorrelationInfo);
    msg.setCorrelationKey(msgCorrelationInfo);

    prod.send(msg, queue);
}
```

This will create a correlation object, add it to a global list for later processing and then add it to the Solace message prior to sending.

---

## Processing the Solace Acknowledgement

To process the acknowledgements with correlation, you must implement the `JCSMPStreamingPublishCorrelatingEventHandler` interface. This interface extends the basic `JCSMPStreamingPublishEventHandler`. It adds two new methods for handling acknowledgements and deprecates those found in the original `JCSMPStreamingPublishEventHandler`. The following code shows you a basic acknowledgement processing class that will store the result from the Solace message router. When it is done, it will count down a latch to notify the main thread of ack processing.

```java
class PubCallback implements JCSMPStreamingPublishCorrelatingEventHandler {

    public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
        if (key instanceof MsgInfo) {
            MsgInfo i = (MsgInfo) key;
            i.acked = true;
            System.out.printf("Message response (rejected) received for %s, error was %s \n", i, cause);
        }
        latch.countDown();
    }

    public void responseReceivedEx(Object key) {
        if (key instanceof MsgInfo) {
            MsgInfo i = (MsgInfo) key;
            i.acked = true;
            i.publishedSuccessfully = true;
            System.out.printf("Message response (accepted) received for %s \n", i);
        }
        latch.countDown();
    }

    public void handleError(String messageID, JCSMPException cause, long timestamp) {
        // Never called
    }

    public void responseReceived(String messageID) {
        // Never called
    }
}
```

---

## Summarizing

When you put this all together you end up with a program that will send messages to the Solace message router, then wait for their acknowledgements and finally once all messages are acknowledged, it will print the status of each message to the screen.

See the following source code for the full application:

*   [ConfirmedPublish.java](http://dev.solacesystems.com/wp-content/uploads/ConfirmedPublish.java)

### Building

Building this example is simple. The following provides an example using Linux. These instructions assume you have unpacked the Solace Java API into a directory next to the getting started samples that you just downloaded. There are many suitable ways to build and execute these samples in Java. Adapt these instructions to suit your needs depending on your environment.

In the following example replace VERSION with the Solace API version you downloaded.

```
javac -cp sol-jcsmp-VERSION/lib/*:. ConfirmedPublish.java
```

### Sample Output

Run the example from the command line as follows.

```
$ java -cp sol-jcsmp-VERSION/lib/*:. ConfirmedPublish HOST</pre>
```

You have now successfully sent persistent messages to a Solace router and confirmed its receipt by correlating the acknowledgement.

If you have any issues check the Solace community Q&A for answers to common issues seen.

---

## Up Next:

[Learn how to map topics to a queue](http://dev.solacesystems.com/docs/get-started/topic-queue-mapping/).
