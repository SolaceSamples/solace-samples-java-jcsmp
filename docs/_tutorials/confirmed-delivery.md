---
layout: tutorials
title: Confirmed Delivery
summary: Learn how to confirm that your messages are received by a Solace message router.
icon: confirmed-delivery.png
---

This tutorial builds on the basic concepts introduced in [Persistence with Queues]({{ site.baseurl }}/persistence-with-queues) tutorial and will show you how to properly process publisher acknowledgements. Once an acknowledgement for a message has been received and processed, you have confirmed your persistent messages have been properly accepted by the Solace message router and therefore can be guaranteed of no message loss.

![confirmed-delivery]({{ site.baseurl }}/images/confirmed-delivery.png)

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts](http://dev.solacesystems.com//docs/core-concepts/){:target="_top"}.
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN configured for guaranteed messaging support.
    *   Enabled client username.
    *   Client-profile enabled with guaranteed messaging permissions.

Note that one simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here](http://dev.solacesystems.com/docs/get-started/setting-up-solace-vmr_vmware/){:target="_top"}. By default the Solace VMR will with the “default” message VPN configured and ready for guaranteed messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration adapt the tutorial appropriately to match your configuration.

The build instructions in this tutorial assume you are using a Linux shell. If your environment differs, adapt the instructions.

## Goals

The goal of this tutorial is to understand the following:

*  How to properly handle persistent message acknowledgements on message send.

## Trying it yourself

This tutorial is available in [GitHub]({{ site.repository }}){:target="_blank"} along with the other [Solace Developer Getting Started Examples](http://dev.solacesystems.com/get-started/java-tutorials/){:target="_top"}.

To successfully build the samples you must have the Java API downloaded and available. The Java API library can be [downloaded here](http://dev.solacesystems.com/downloads/){:target="_top"}. The Java API is distributed as a zip file containing the required jars, API documentation, and examples.

At the end, this tutorial walks through downloading and running the sample from source.

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

## Adding Message Correlation on Send

The [Persistence with Queues]({{ site.baseurl }}/persistence-with-queues) tutorial demonstrated how to send persistent messages using code very similar to the following. The only difference below is the message text and the loop.

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

## Summarizing

The full source code for this example is available in [GitHub]({{ site.repository }}){:target="_blank"}. If you combine the example source code shown above results in the following source:

*   [ConfirmedPublish.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/ConfirmedPublish.java){:target="_blank"}

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

Run the example from the command line as follows.

```
$ ./build/staged/bin/confirmedPublish <HOST>
```

You have now successfully sent persistent messages to a Solace router and confirmed its receipt by correlating the acknowledgement.

If you have any issues sending and receiving a message, check the [Solace community](http://dev.solacesystems.com/community/){:target="_top"} for answers to common issues.
