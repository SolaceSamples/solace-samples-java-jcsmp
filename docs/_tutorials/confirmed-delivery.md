---
layout: tutorials
title: Confirmed Delivery
summary: Learn how to confirm your messages are delivered to Solace messaging.
icon: confirmed-delivery.png
links:
    - label: ConfirmedPublish.java
      link: /blob/master/src/main/java/com/solace/samples/ConfirmedPublish.java
---


This tutorial builds on the basic concepts introduced in [Persistence with Queues]({{ site.baseurl }}/persistence-with-queues) tutorial and will show you how to properly process publisher acknowledgements. Once an acknowledgement for a message has been received and processed, you have confirmed your persistent messages have been properly accepted by the Solace message router and therefore can be guaranteed of no message loss.

![confirmed-delivery]({{ site.baseurl }}/images/confirmed-delivery.png)

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to Solace messaging with the following configuration details:
    *   Connectivity information for a Solace message-VPN configured for guaranteed messaging support
    *   Enabled client username and password
    *   Client-profile enabled with guaranteed messaging permissions.

One simple way to get access to Solace messaging quickly is to create a messaging service in DataGo [as outlined here]({{ site.links-datago-setup}}){:target="_top"}. You can find other ways to get access to Solace messaging on the [home page]({{ site.baseurl }}/) of these tutorials.

## Goals

The goal of this tutorial is to understand the following:

*  How to properly handle persistent message acknowledgements on message send.

## Get Solace Messaging

This tutorial requires access Solace messaging and requires that you know several connectivity properties about your Solace messaging. Specifically you need to know the following:

<table>
  <tr>
    <th>Resource</th>
    <th>Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>Host</td>
    <td>String</td>
    <td>This is the address clients use when connecting to the Solace messaging to send and receive messages. (Format: <code>DNS_NAME:Port</code> or <code>IP:Port</code>)</td>
  </tr>
  <tr>
    <td>Message VPN</td>
    <td>String</td>
    <td>The Solace message router Message VPN that this client should connect to. </td>
  </tr>
  <tr>
    <td>Client Username</td>
    <td>String</td>
    <td>The client username. (See Notes below)</td>
  </tr>
  <tr>
    <td>Client Password</td>
    <td>String</td>
    <td>The client password. (See Notes below)</td>
  </tr>
</table>

There are several ways you can get access to Solace Messaging and find these required properties.

### Option 1: Use DataGo

* Follow [these instructions]({{ site.links-datago-setup }}){:target="_top"} to quickly spin up a cloud-based Solace messaging service for your applications.
* The messaging connectivity information is found in the service details in the connectivity tab. You will need the SMF URI as host string in this tutorial.  
    ![]({{ site.baseurl }}/images/connectivity-info.png)

### Option 2: Start a Solace VMR

For instructions on how to start the Solace VMR in leading Clouds, Container Platforms or Hypervisors see the "[Set up a VMR]({{ site.docs-vmr-setup }}){:target="_top"}" tutorials which outline where to download and and how to install the software.

Note: By default, the Solace VMR "default" message VPN has authentication disabled. In this scenario, the client-username and client-password fields are still required by the samples but can be any value.

### Option 3: Get access to a Solace appliance

* Contact your Solace appliance administrators and obtain the following:
    * A Solace Message-VPN where you can produce and consume direct and persistent messages
    * The host name or IP address of the Solace appliance hosting your Message-VPN
    * A username and password to access the Solace appliance

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
  <version>[10,)</version>
</dependency>
```

### Get the API: Using the Solace Developer Portal

The Java API library can be [downloaded here]({{ site.links-downloads }}){:target="_top"}. The Java API is distributed as a zip file containing the required jars, API documentation, and examples. 

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

{% for item in page.links %}
* [{{ item.label }}]({{ site.repository }}{{ item.link }}){:target="_blank"}
{% endfor %}

### Getting the Source

This tutorial is available in GitHub.  To get started, clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.baseurl | remove: '/'}}
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
$ ./build/staged/bin/confirmedPublish <host:port> <message-vpn> <client-username> <client-password>
```

You have now successfully sent persistent messages to a Solace router and confirmed its receipt by correlating the acknowledgement.

If you have any issues sending and receiving a message, check the [Solace community]({{ site.links-community }}){:target="_top"} for answers to common issues.
