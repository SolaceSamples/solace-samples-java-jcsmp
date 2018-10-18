---
layout: features
title: Transactions
summary: Learn to use consumer active flow indication with exclusive queues.
links:
    - label: ActiveConsumerIndication
      link: /blob/master/src/features/ActiveConsumerIndication
---

This feature introduction shows how multiple consumers can bind to an exclusive queue, but only one client at a time can actively receive messages.

The example code builds on the Subscriber in the [publish/subscribe]({{ site.baseurl }}/publish-subscribe) messaging pattern.

## Feature Overview

If a queue has an exclusive access type, multiple clients can bind to the queue, but only one client at a time can actively receive messages from it. Therefore, when a client creates a Flow and binds to an exclusive queue, the flow might not be active for the client if other clients are bound to the queue.

If the Active Flow Indication Flow property is enabled, a Flow active event is returned to the client when its bound flow becomes the active flow. The client also receives a Flow inactive event whenever it loses an active flow (for example, if the flow disconnects).

Using the Active Flow Indication, a client application can learn if it is the primary or backup consumer of an exclusive queue. This can be useful in clustered applications to help establish roles and function properly in active / standby consumption models.

## Prerequisite

This sample requires that the queue "tutorial/queue" exists on the message router and is configured to be "exclusive".  Ensure the queue is enabled for both Incoming and Outgoing messages and set the Permission to at least 'Consume'.

## Code

The following code shows how message consumers can bind to an exclusive queue and handle the Consumer active/inactive event. The key aspect is to successfully set the `activeIndicationEnabled` field to true when creating the message consumer and to put appropriate event handling login in place for both the `solace.MessageConsumerEventName.ACTIVE` and `solace.MessageConsumerEventName.INACTIVE`. In this case, the sample simply logs the event.

```java
sample.createConsumer = function (session, messageConsumerName) {
        // Create a message consumer
        const messageConsumer = sample.session.createMessageConsumer({
            queueDescriptor: { name: sample.queueName, type: solace.QueueType.QUEUE },
            acknowledgeMode: solace.MessageConsumerAcknowledgeMode.CLIENT,
            activeIndicationEnabled: true,
        });
        ...
        messageConsumer.on(solace.MessageConsumerEventName.ACTIVE, function () {
            sample.log('=== ' + messageConsumerName + ': received ACTIVE event - Ready to receive messages');
        });
        messageConsumer.on(solace.MessageConsumerEventName.INACTIVE, function () {
            sample.log('=== ' + messageConsumerName + ': received INACTIVE event');
        });
        ...
        return messageConsumer;
    }
                    
```

When running the full sample, first start this sample and then run the [confirmed-delivery]({{ site.baseurl }}/confirmed-delivery) sample to send 10 messages.

## Learn More

<ul>
{% for item in page.links %}
<li>Related Source Code: <a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
<li><a href="{{ site.docs-active-flow-indication }}" target="_blank">Solace Feature Documentation</a></li>
</ul>


 