---
layout: features
title: TTL and Dead Message Queue
summary: Learn how to send messsages with an expiration and manage expired messages.
links:
---

This sample demonstrates how to send a message with Time to Live (TTL) enabled and how to allow an expired message to be collected by the Dead Message Queue (DMQ).

## Feature Overview

To ensure that stale messages are not consumed, you can set a Time‑To-Live (TTL) value (in milliseconds) for each Guaranteed message published by a producer.
If destination queues or topic endpoints are configured to respect message TTLs, when received messages’ TTLs have passed, they are either discarded by the endpoint, or, if the messages are eligible for a Dead Message Queue (DMQ), they are moved to a DMQ provisioned on the message broker.

## Prerequisite

These [Client Profile Configuration](https://docs.solace.com/Configuring-and-Managing/Configuring-Client-Profiles.htm){:target="_blank"} properties must be configured as follows:

[ENDPOINT_MANAGEMENT](https://docs.solace.com/API-Developer-Online-Ref-Documentation/java/com/solacesystems/jcsmp/CapabilityType.html#ENDPOINT_MANAGEMENT){:target="_blank"} property must be set to "true".

[ENDPOINT_MESSAGE_TTL](https://docs.solace.com/API-Developer-Online-Ref-Documentation/java/com/solacesystems/jcsmp/CapabilityType.html#ENDPOINT_MESSAGE_TTL){:target="_blank"} property must be set to "true".

NOTE:  This is the default configuration in PubSub+ Cloud messaging services.

## Code

Set Time to Live (TTL) on a message and make it eligible for the Dead Message Queue (DMQ).

In this case we are creating five messages on a Queue where two messages have a three second TTL and one of those messages is eligible for the DMQ.  Then we wait for five seconds.

NOTE:  There is no code required to define which Queue is the DMQ.  A Queue with the name #DEAD_MSG_QUEUE was created earlier in the sample and the Message Broker uses this Queue by default as the DMQ because of the name #DEAD_MSG_QUEUE. 

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

Consume messages on the Queue.

Only the messages without a TTL will still be on the Queue.  In this case three messages are still on the Queue after waiting five seconds.

```java
rx_msg_count = 0;
flow_prop = new ConsumerFlowProperties();
flow_prop.setEndpoint(ep_queue);
cons = session.createFlow(new MessageDumpListener(), flow_prop);
cons.start();
Thread.sleep(2000);  // Wait to receive the messages
cons.close();
```

Consume messages on the Dead Message Queue (DMQ).

In this case, one message is on the DMQ which is the message with a TTL of three seconds which was eligible for the DMQ.  The other message that expired which was not eligible for the DMQ is no longer spooled on the message broker.

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


 