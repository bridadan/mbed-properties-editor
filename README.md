# mbed-properties-editor

This is the source repo for the mbed properties editor used within the bridge containers.

The mbed-properties-editor allows for customized java "properties" file editing from a simple website

The default website, typically within a bridge container is accessed as follows:

https://container_ip_address:8234

Of note: within the java properties file, a property called "config_fields" can be used to limit, order, customize what subset of properties (of all the properties in the properties file) is to be displayed in the web page. 

