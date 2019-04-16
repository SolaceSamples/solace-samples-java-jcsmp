---
layout: features
title: Active Consumer Indication
summary: Learn to use consumer active flow indication with exclusive queues.
links:
    - label: QueueProvisionAndRequestActiveFlowIndication.java
      link: /blob/master/src/main/java/com/solace/samples/features/QueueProvisionAndRequestActiveFlowIndication.java
---

This sample shows how to request active flow indication for an endpoint (like a Queue) when creating a flow and how to handle active flow indication events.

## Feature Overview

If a queue has an exclusive access type, multiple clients can bind to the queue, but only one client at a time can actively receive messages from it. Therefore, when a client creates a Flow and binds to an exclusive queue, the flow might not be active for the client if other clients are bound to the queue.

If the Active Flow Indication Flow property is enabled, a Flow active event is returned to the client when its bound flow becomes the active flow. The client also receives a Flow inactive event whenever it loses an active flow (for example, if the flow disconnects).

Using the Active Flow Indication, a client application can learn if it is the primary or backup consumer of an exclusive queue. This can be useful in clustered applications to help establish roles and function properly in active / standby consumption models.

## Prerequisite

The [Client Profile]({{ site.docs-client-profile }}) must be configured to [allow receiving guaranteed messages]({{ site.docs-client-profile-allow-g-receives }}).

NOTE:  This is the default configuration in PubSub+ Cloud messaging services.

## Code

This sample code will create two flows and show how the second flow will receive the active flow indication event when the first flow is closed.

First, implement the FlowEventHandler and XMLMessageListener interfaces. 

In this sample we simply output the flow event as text to show that the event is occurring.  The XMLMessageListener interface is implemented so that we can use it to create the flow (see code below), but it is otherwise unused in this sample.

```java
public class QueueProvisionAndRequestActiveFlowIndication extends SampleApp implements XMLMessageListener, FlowEventHandler {
//...
// FlowEventHandler
public void handleEvent(Object source, FlowEventArgs event) {
    System.out.println("Flow Event - " + event);
}
// XMLMessageListener
public void onException(JCSMPException exception) {
    exception.printStackTrace();
}
// XMLMessageListener
public void onReceive(BytesXMLMessage message) {
    System.out.println("Received Message:\n" + message.dump());
}                    
```

Then, create two flows and listen to Active Flow Indication events. 

When the first flow is started the active flow event is triggered because it is the only flow that is bound to the Queue.  When the second flow is started it doesn't receive the active flow event.

```java
flowOne = session.createFlow(
    this, //xmlMessageListener
    new ConsumerFlowProperties().setEndpoint(ep_queue).setActiveFlowIndication(true), //consumerFlowProperties
    null, //endpointProperties
    this //flowEventHandler
);
 
flowOne.start();  // flowOne receives the active flow event

//... flowTwo is created and started as well in the same way, but it doesn't receive the active flow event
```

When the first flow is closed, the second flow receives the active flow event.

```java
flowOne.close(); // Active flow indication event for flowTwo fires now that flowOne is closed
```

## Learn More

<ul>
{% for item in page.links %}
<li>Related Source Code: <a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
<li><a href="https://docs.solace.com/Solace-PubSub-Messaging-APIs/Developer-Guide/Creating-Flows.htm#Active-Flow-Indication" target="_blank">Solace Feature Documentation</a></li>
</ul>


 