---
layout: features
title: Browsing Queues
summary: Learn how to look at the contents of a Queue without consuming any messages.
links:
    - label: QueueProvisionAndBrowse.java
      link: /blob/master/src/main/java/com/solace/samples/features/QueueProvisionAndBrowse.java
---

This sample shows how to use a Browser to look at the Queue's contents.

## Feature Overview

Client applications using the Java and .NET APIs can use the Browser interface to look at Guaranteed messages spooled for a Queue in the order of oldest to newest without consuming them.  The Browser can also be used to remove messages from the Queue.

A Browser could be used by an application to pre-screen messages by detecting message content that could cause fatal conditions and removing the messages before they are processed.

## Prerequisite

The [Client Profile]({{ site.docs-client-profile }}) must be configured to [allow receiving guaranteed messages]({{ site.docs-client-profile-allow-g-receives }})

NOTE:  This is the default configuration in PubSub+ Cloud messaging services.

## Code

The following code creates a Browser and gets messages from a Queue without removing them.  The Browser is also able to remove messages from a Queue and this is shown in the code below by removing only the 3rd message.

```java
BrowserProperties br_prop = new BrowserProperties();
br_prop.setEndpoint(ep_queue);
br_prop.setTransportWindowSize(1);
br_prop.setWaitTimeout(1000);
Browser myBrowser = session.createBrowser(br_prop);
BytesXMLMessage rx_msg = null;
int rx_msg_count = 0;
do {
    rx_msg = myBrowser.getNext();
    if (rx_msg != null) {
        rx_msg_count++;
        System.out.println("Browser got message... dumping:");
        System.out.println(rx_msg.dump());
        if (rx_msg_count == 3) {
            System.out.print("Removing message from queue...");
            myBrowser.remove(rx_msg);
            System.out.println("OK");
        }
    }
} while (rx_msg != null);                    
```

## Learn More

<ul>
{% for item in page.links %}
<li>Related Source Code: <a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
<li><a href="https://docs.solace.com/Solace-PubSub-Messaging-APIs/Developer-Guide/Browsing-Guaranteed-Mess.htm?Highlight=Browsing" target="_blank">Solace Feature Documentation</a></li>
</ul>


 