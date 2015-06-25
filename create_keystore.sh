#!/bin/sh

set -x

keytool -genkey -keyalg RSA -alias selfsigned -keystore mdsconfigurator.jks -storepass arm1234 -validity 360 -keysize 2048
