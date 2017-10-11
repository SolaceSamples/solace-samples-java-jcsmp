
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

### Option 1: Use Solace Cloud

* Follow [these instructions]({{ site.links-solaceCloud-setup }}){:target="_top"} to quickly spin up a cloud-based Solace messaging service for your applications.
* The messaging connectivity information is found in the service details in the connectivity tab. You will use the SMF URI as host string in this tutorial.

![]({{ site.baseurl }}/images/connectivity-info.png)

### Option 2: Start a Solace VMR

For instructions on how to start the Solace VMR in leading Clouds, Container Platforms or Hypervisors see the "[Set up a VMR]({{ site.docs-vmr-setup }}){:target="_top"}" tutorials which outline where to download and and how to install the software.

Note: By default, the Solace VMR "default" message VPN has authentication disabled. In this scenario, the client-username and client-password fields are still required by the samples but can be any value.

### Option 3: Get access to a Solace appliance

* Contact your Solace appliance administrators and obtain the following:
    * A Solace Message-VPN where you can produce and consume direct and persistent messages
    * The host name or IP address of the Solace appliance hosting your Message-VPN
    * A username and password to access the Solace appliance
