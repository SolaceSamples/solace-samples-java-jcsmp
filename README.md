# Getting Started Examples
## Solace Messaging API for Java (JCSMP)

These tutorials will get you up to speed and sending messages with Solace technology as quickly as possible. There are two ways you can get started:

- If your company has Solace message routers deployed, contact your middleware team to obtain the host name or IP address of a Solace message router to test against, a username and password to access it, and a VPN in which you can produce and consume messages.
- If you do not have access to a Solace message router, you will need to go through the “[Set up a VMR](http://dev.solacesystems.com/get-started/vmr-setup-tutorials/setting-up-solace-vmr/)” tutorial to download and install the software.

## Contents

This repository contains code and matching tutorial walk throughs for five different basic Solace messaging patterns. For a nice introduction to the Solace API and associated tutorials, check out the [tutorials landing page](https://mdspielman.github.io/solace-getting-started-java).

See the individual tutorials for details:

- [Publish/Subscribe](https://mdspielman.github.io/solace-getting-started-java/docs/publish-subscribe): Learn how to set up pub/sub messaging on a Solace VMR.
- [Persistence](https://mdspielman.github.io/solace-getting-started-java/docs/persistence-with-queues): Learn how to set up persistence for guaranteed delivery.
- [Request/Reply](https://mdspielman.github.io/solace-getting-started-java/docs/request-reply): Learn how to set up request/reply messaging.
- [Confirmed Delivery](https://mdspielman.github.io/solace-getting-started-java/docs/confirmed-delivery): Learn how to confirm that your messages are received by a Solace message router.
- [Topic to Queue Mapping](https://mdspielman.github.io/solace-getting-started-java/docs/topic-to-queue-mapping): Learn how to map existing topics to Solace queues.

## Checking out and Building

To check out the project and build from source, do the following:

    git clone git://github.com/mdspielman/solace-getting-started-java
    cd solace-getting-started-java
    ./gradlew assemble

## Running the Samples

To try individual samples, build the project from source and then run samples like the following:

    ./build/staged/bin/topicPublisher <msg_backbone_ip:port>

The individual tutorials linked above provide full details which can walk you through the samples, what they do, and how to correctly run them to explore Solace messaging.

## Using Eclipse

To generate Eclipse metadata (.classpath and .project files), do the following:

    ./gradlew eclipse

Once complete, you may then import the projects into Eclipse as usual:

 *File -> Import -> Existing projects into workspace*

Browse to the *'solace-getting-started-java'* root directory. All projects should import
free of errors.

## Using IntelliJ IDEA

To generate IDEA metadata (.iml and .ipr files), do the following:

    ./gradlew idea

## Resources

For more information try these resources:

- The Solace Developer Portal website at:
[http://dev.solacesystems.com](http://dev.solacesystems.com/)
- Get a better understanding of [Solace technology.](http://dev.solacesystems.com/tech/)
- Check out the [Solace blog](http://dev.solacesystems.com/blog/) for other interesting discussions around Solace technology
- Ask the [Solace community.](http://dev.solacesystems.com/community/)


