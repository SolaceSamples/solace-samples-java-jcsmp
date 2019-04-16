---
layout: features
title: Transactions
summary: Learn how to group messaging operations into an atomic transaction.
links:
    - label: Transactions.java
      link: /blob/master/src/main/java/com/solace/samples/features/Transactions.java
---

This sample demonstrates how to acknowledge the receipt of a message and send a new message in a single atomic transaction.

## Feature Overview

Transacted Sessions enable client applications to group multiple message send and/or receive operations together in single, atomic units known as local transactions. Each transacted Session can support a single series of transactions.

Messages that are published and received in a transaction are staged on the message broker. To complete all of the message send and receive operations in a transaction, the transaction must be committed. Alternatively, a transaction can also be rolled back so that all of its publish and receive operations are canceled. Once a transaction is completed, another transaction automatically begins.

This feature can be used to guarantee that a message is not removed from the message broker until a reply message has been sent.

## Prerequisite

The [Client Profile]({{ site.docs-client-profile }}) must be configured to [allow transacted sessions]({{ site.docs-client-profile-allow-transacted }}).

NOTE:  This is the default configuration in PubSub+ Cloud messaging services.

## Code

The following code shows how to acknowledge the receipt of a message and send a reply message in a single atomic transaction, guaranteeing that the original message is not acknowledged until the reply message is received by the message broker.

First, create a transacted session.

```java
protected JCSMPSession session = null;
//...
public TransactedSession txSession;
//...
txSession = session.createTransactedSession();
```

Next, create a producer and consumer from the transacted session.

```java
public XMLMessageProducer producer;
public FlowReceiver receiver;
//...
ProducerFlowProperties prodFlowProps = new ProducerFlowProperties();
prodFlowProps.setWindowSize(100);
producer = txSession.createProducer(prodFlowProps, this);

ConsumerFlowProperties consFlowProps = new ConsumerFlowProperties();
consFlowProps.setEndpoint(queue);
consFlowProps.setStartState(true);
EndpointProperties endpointProps = new EndpointProperties();
endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
receiver = txSession.createFlow(this, consFlowProps, endpointProps);
```

Now when receiving a message, send a message with the transacted session and commit the transaction.

Committing the transaction automatically acknowledges receipt of the message that was received on the transacted session.

```java
public void onReceive(BytesXMLMessage message) {
    //...
    BytesXMLMessage reply = JCSMPFactory.onlyInstance().createMessage(BytesXMLMessage.class);
    reply.setDeliveryMode(DeliveryMode.PERSISTENT);
    reply.setSenderId("Replier");
    producer.send(reply, message.getReplyTo());

    // this commit will acknowledge the received message and
    // deliver the sent message.
    txSession.commit();
    //...
}
```

## Learn More

<ul>
{% for item in page.links %}
<li>Related Source Code: <a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
<li><a href="https://docs.solace.com/Solace-JMS-API/Using-Transacted-Sessions.htm?Highlight=Transactions" target="_blank">Solace Feature Documentation</a></li>
</ul>


 