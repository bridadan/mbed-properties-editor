# mbed-properties-editor

This is the source repo for the mbed properties editor used within the bridge containers.

The mbed-properties-editor allows for customized java "properties" file editing from a simple website

The default website, typically within a bridge container is accessed as follows:

https://container_ip_address:8234

Of note: within the java properties file, a property called "config_fields" can be used to limit, order, customize what subset of properties (of all the properties in the properties file) is to be displayed in the web page. 

This is a Java Netbeans-based maven project.

Copyright 2015. ARM Ltd. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
