---
layout: tutorials
title: Confirmed Delivery
summary: Learn how to confirm your messages are delivered to Solace messaging.
icon: I_dev_confirm.svg
links:
    - label: ConfirmedPublish.java
      link: /blob/master/src/main/java/com/solace/samples/ConfirmedPublish.java
---


This tutorial builds on the basic concepts introduced in [Persistence with Queues]({{ site.baseurl }}/persistence-with-queues) tutorial and will show you how to properly process publisher acknowledgements. Once an acknowledgement for a message has been received and processed, you have confirmed your persistent messages have been properly accepted by the Solace message router and therefore can be guaranteed of no message loss.

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to Solace messaging with the following configuration details:
    *   Connectivity information for a Solace message-VPN configured for guaranteed messaging support
    *   Enabled client username and password
    *   Client-profile enabled with guaranteed messaging permissions.

One simple way to get access to Solace messaging quickly is to create a messaging service in Solace Cloud [as outlined here]({{ site.links-solaceCloud-setup}}){:target="_top"}. You can find other ways to get access to Solace messaging below.

## Goals

The goal of this tutorial is to understand the following:

*  How to properly handle persistent message acknowledgements on message send.


{% include_relative assets/solaceMessaging.md %}
{% include_relative assets/solaceApi.md %}


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
properties.setProperty(JCSMPProperties.USERNAME, args[1].split("@")[0]);
properties.setProperty(JCSMPProperties.VPN_NAME,  args[1].split("@")[1]);
if (args.length > 2) {
    properties.setProperty(JCSMPProperties.PASSWORD, args[2]);
}
final JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
session.connect();
```

## Adding Message Correlation on Send

The [Persistence with Queues]({{ site.baseurl }}/persistence-with-queues) tutorial demonstrated how to send persistent messages using code very similar to the following. The only difference below is the message text and the loop.

```java
for (int i = 1; i <= count; i++) {
    TextMessage msg =   JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
    msg.setDeliveryMode(DeliveryMode.PERSISTENT);
    String text = "Confirmed Publish Tutorial! Message ID: "+ i;
    msg.setText(text);
    prod.send(msg, queue);
}
```

Adding a message correlation object to allow an application to easily correlate acknowledgements is accomplished using the `TextMessage.setCorrelationKey()` method where you pass in the object you want returned to your application in the acknowledgement callback. So after augmenting the publish code from above, you’re left with the following:

```java
for (int i = 1; i <= count; i++) {
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

Run the example from the command line as follows.

```
$ ./build/staged/bin/confirmedPublish <host:port> <client-username>@<message-vpn> [client-password]
```

You have now successfully sent persistent messages to a Solace router and confirmed its receipt by correlating the acknowledgement.

If you have any issues sending and receiving a message, check the [Solace community]({{ site.links-community }}){:target="_top"} for answers to common issues.
