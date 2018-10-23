---
layout: features
title: Active Consumer Indication
summary: Learn to use consumer active flow indication with exclusive queues.
links:
---

This sample shows how to request active flow indication for an endpoint (like a Queue) when creating a flow and how to handle active flow indication events.

## Feature Overview

If a queue has an exclusive access type (refer to Defining Endpoint Properties), multiple clients can bind to the queue, but only one client at a time can actively receive messages from it. Therefore, when a client creates a Flow and binds to an exclusive queue, the flow might not be active for the client if other clients are bound to the queue.

If the Active Flow Indication Flow property is enabled, a Flow active event is returned to the client when its bound flow becomes the active flow. The client also receives a Flow inactive event whenever it loses an active flow (for example, if the flow disconnects).

Consider using Active Flow Indication as a way to manage failover from a primary to a backup consumer application when the primary consumer fails.  In this case the backup consumer can use the Active Flow Indication event to run any setup required in order for it to act as the primary consumer.  Active Flow Indication could also be used as a way to detect and respond to a Primary consumer failure which could auto-recover the consumer when it stops receiving messages.

## Prerequisite

These [Client Profile Configuration](https://docs.solace.com/Configuring-and-Managing/Configuring-Client-Profiles.htm){:target="_blank"} properties must be configured as follows:

[ENDPOINT_MANAGEMENT](https://docs.solace.com/API-Developer-Online-Ref-Documentation/java/com/solacesystems/jcsmp/CapabilityType.html#ENDPOINT_MANAGEMENT){:target="_blank"} property must be set to "true".

[ACTIVE_FLOW_INDICATION](https://docs.solace.com/API-Developer-Online-Ref-Documentation/java/com/solacesystems/jcsmp/CapabilityType.html#ACTIVE_FLOW_INDICATION){:target="_blank"} property must be set to "true".

NOTE:  This is the default configuration in PubSub+ Cloud messaging services.

## Code

Implement the FlowEventHandler and XMLMessageListener interfaces. 

In this sample we simply output the flow event as text to show that the event is occurring.  The XMLMessageListener interface is implemented so that we can use it to create the flow (see code below), but it is otherwise unused in this sample.

```java
public class QueueProvisionAndRequestActiveFlowIndication extends SampleApp implements XMLMessageListener, FlowEventHandler {
...
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

Create two flows and listen to Active Flow Indication events. 

When the first flow is started the active flow event is triggered because it is the only flow that is bound to the Queue.  When the second flow is started it doesn't receive the active flow event until the first flow is closed.

```java
flowOne = session.createFlow(
    this, //xmlMessageListener
    new ConsumerFlowProperties().setEndpoint(ep_queue).setActiveFlowIndication(true), //consumerFlowProperties
    null, //endpointProperties
    this //flowEventHandler
);
 
flowOne.start();
 
System.out.println("Create flow two");
flowTwo = session.createFlow(
    this,
    new ConsumerFlowProperties().setEndpoint(ep_queue).setActiveFlowIndication(true),
    null,
    this
);
 
flowTwo.start();
 
Thread.sleep(5000);
 
flowOne.close(); // Active flow indication event for flowTwo fires now that flowOne is closed
```

## Learn More

<ul>
{% for item in page.links %}
<li>Related Source Code: <a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
<li><a href="https://docs.solace.com/Solace-PubSub-Messaging-APIs/Developer-Guide/Creating-Flows.htm#Active-Flow-Indication" target="_blank">Solace Feature Documentation</a></li>
</ul>


 