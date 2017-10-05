---
layout: tutorials
title: Example Tutorial 1
summary: This is a short description for the landing page.
icon: tutorial-1-icon.png
---

You would describe your tutorial in markdown like these. See other samples for full examples.

Here is an example image


## A Section

Some bullets:

*   Bullet 1
*   Bullet 2 with nesting:
    *   Nest 1

## Another section

With a sample code snippet for creating a `Queue`.

~~~java
// create the queue object locally
String queueName = "Q/tutorial";
final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);

// set queue permissions to "consume" and access-type to "exclusive"
final EndpointProperties endpointProps = new EndpointProperties();
endpointProps.setPermission(EndpointProperties.PERMISSION_CONSUME);
endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);

// Actually provision it, and do not fail if it already exists
session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
~~~
