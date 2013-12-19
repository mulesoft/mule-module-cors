CORS Support for Mule Apps
==========================

Cross Origin Resource Sharing enables javascript clients that run on a web browser to safely consume remote API's that are not hosted in the same server where the code in execution came from. This is escencially the case not only for web applications but for embedded webapps running in mobile devices.

The CORS mule module makes easy for mule applications to share resources for clients of other origins.

Here is an example configuration:

```xml

<cors:config name="Cors" doc:name="Cors">
    <cors:origins>
        <cors:origin url="http://localhost:8383" accessControlMaxAge="30">
            <cors:methods>
                <cors:method>GET</cors:method>
            </cors:methods>
            <cors:headers>
                <cors:header>X-Requested-With</cors:header>
            </cors:headers>
        </cors:origin>
    </cors:origins>
</cors:config>

```
And applied in a flow:

```xml

<flow name="mule-configFlow1" doc:name="mule-configFlow1">
    <http:inbound-endpoint exchange-pattern="request-response"
        host="localhost" port="8081" path="resources" doc:name="HTTP" />

		<cors:validate config-ref="Cors" doc:name="Cors"/>

		<flow-ref name="sysprops" doc:name="Flow Reference" />
</flow>
```

This allows easy verification of origins and access constraints for non public resources.
