Prerequisites
===============================
* Mule 3.2.0-SNAPSHOT (depends on changes in Mule concurrent libs, like dropping backport-util-concurrent)
* Netty development builds (e.g. 4.0.0.Alpha1-SNAPSHOT). Best built directly from source by forking it here on GitHub: http://github.com/netty/netty
* Java 6 - a dependency of Netty 4.x library and Mule 3.2.x in the future

Current Limitations
===============================
* At the moment only TCP stack is supported. In the future the plan is to support any codec, meaning tcp, http, websocket, etc.
* Threading profiles can't yet be configured (planned)
* Netty codec stack can't be configured yet (planned)
* No out-of-the-box support for Mule's length protocol (but still possible via standard Netty's codecs). As a result longer (e.g. 4KB)
  messages will get fragmented by tcp layer. This will be addressed once the above configuration options are implemented.

Sample Config
-------------------------------
```xml
<?xml version="1.0" encoding="UTF-8"?>
<mule xmlns="http://www.mulesoft.org/schema/mule/core"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:netty="http://www.mulesoft.org/schema/mule/netty"
      xmlns:tcp="http://www.mulesoft.org/schema/mule/tcp"
      xsi:schemaLocation="
       http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/3.2/mule.xsd
       http://www.mulesoft.org/schema/mule/netty http://www.mulesoft.org/schema/mule/netty/3.2/mule-netty.xsd
       http://www.mulesoft.org/schema/mule/tcp http://www.mulesoft.org/schema/mule/tcp/3.2/mule-tcp.xsd">

    <!--
        A convenience connector for use by MuleClient, optional
    -->
    <tcp:connector name="testClientConnector">
        <tcp:direct-protocol payloadOnly="true"/>
    </tcp:connector>

    <netty:connector name="nettyConnector"/>

    <flow name="netty-bridge">
        <!--
             Optimize by disabling transport transformer and skipping unnecessary type conversion
             to a 'generic' type like a stream. We're staying within Netty and can operate directly on ChannelBuffer.
        -->
        <netty:inbound-endpoint address="netty://localhost:5000" disableTransportTransformer="true"/>
        <netty:outbound-endpoint address="netty://localhost:5001"/>
    </flow>

    <flow name="netty-echo">
        <netty:inbound-endpoint address="netty://localhost:5001"/>
        <append-string-transformer message=" Received"/>
    </flow>

</mule>
```
