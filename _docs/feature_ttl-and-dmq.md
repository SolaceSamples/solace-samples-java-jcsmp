---
layout: features
title: TTL and Dead Message Queue
summary: Learn how to send messsages with an expiration and manage expired messages.
links:
    - label: MessageTTLAndDeadMessageQueue.java
      link: /blob/master/src/main/java/com/solace/samples/features/MessageTTLAndDeadMessageQueue.java
---

This sample demonstrates how to send a message with Time to Live (TTL) enabled and how to allow an expired message to be collected by the Dead Message Queue (DMQ).

## Feature Overview

To ensure that stale messages are not consumed, you can set a Time‑To-Live (TTL) value (in milliseconds) for each Guaranteed message published by a producer.

If destination queues or topic endpoints are configured to respect message TTLs, when received messages’ TTLs have passed, they are either discarded by the endpoint, or, if the messages are eligible for a Dead Message Queue (DMQ), they are moved to a DMQ provisioned on the message broker.

This feature is very useful in real-time applications where receiving a stale message could cause harm or waste valuable real-time processing resources.

## Prerequisite

The [Client Profile]({{ site.docs-client-profile }}) must be configured to [allow receiving guaranteed messages]({{ site.docs-client-profile-allow-g-receives }}) and [allow creating guaranteed messages]({{ site.docs-client-profile-allow-g-creates }}).

NOTE:  This is the default configuration in PubSub+ Cloud messaging services.

## Code

This code example creates a Dead Message Queue (DMQ), sends some messages with a Time to Live (TTL) and shows how messages with the TTL will expire and then get sent the DMQ.

First, create the Dead Message Queue (DMQ).

NOTE:  The Message Broker uses this Queue as the DMQ by default because it has the name #DEAD_MSG_QUEUE. 

```java
private static String DMQ_NAME = "#DEAD_MSG_QUEUE";
private Queue deadMsgQ = null;
//...
EndpointProperties dmq_provision = new EndpointProperties();
dmq_provision.setPermission(EndpointProperties.PERMISSION_DELETE);
dmq_provision.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
deadMsgQ = JCSMPFactory.onlyInstance().createQueue(DMQ_NAME);
session.provision(deadMsgQ,dmq_provision,JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
```

Then create five messages on a regular Queue where two messages have a three second TTL and one of those messages is eligible for the DMQ, which means that it will be sent to the DMQ when the TTL expires.  Wait for five seconds.

```java
BytesXMLMessage m = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
m.setDeliveryMode(DeliveryMode.PERSISTENT);
for (int i = 1; i <= 5; i++) {
    m.setUserData(String.valueOf(i).getBytes());
    m.writeAttachment(getBinaryData(i * 20));
    // Set message TTL to 3000 milliseconds for two messages and set
    // one of the two messages to be Dead Message Queue eligible.
    if (i%2 == 0) {
        m.setTimeToLive(3000);  // Set TTL to 3 seconds for two messages
        m.setDMQEligible(i%4 == 0);  // Set one message as DMQ eligible
    }
    else {
        m.setTimeToLive(0);
        m.setDMQEligible(false);
    }
    prod.send(m, ep_queue);  // Send each of the five messages to the Queue
}
 
Thread.sleep(5000);  // Wait five seconds for the TTL to expire
```

Now consume messages on the regular Queue.  Only the messages without a TTL will still be on the Queue.  In this case there are three three of them after waiting five seconds.

```java
rx_msg_count = 0;
flow_prop = new ConsumerFlowProperties();
flow_prop.setEndpoint(ep_queue);
cons = session.createFlow(new MessageDumpListener(), flow_prop);
cons.start();
Thread.sleep(2000);  // Wait to receive the messages
cons.close();
```

Lastly, consume messages on the Dead Message Queue (DMQ).

In this case, one message is on the DMQ which is the message with a TTL of three seconds that was eligible for the DMQ.  The other message that expired that was not eligible for the DMQ is no longer spooled on the message broker.

```java
ConsumerFlowProperties dmq_props = new ConsumerFlowProperties();
dmq_props.setEndpoint(deadMsgQ);
cons = session.createFlow(new MessageDumpListener(), dmq_props);
cons.start();
Thread.sleep(1000);
```

## Learn More

<ul>
{% for item in page.links %}
<li>Related Source Code: <a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
<li><a href="https://docs.solace.com/Solace-JMS-API/Setting-Message-Properties.htm?Highlight=Time%20to%20live" target="_blank">Solace Feature Documentation</a></li>
</ul>


 