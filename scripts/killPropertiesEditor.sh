#!/bin/sh
PID=`ps -ef | grep properties-editor | grep java | awk '{print $2}'`
if [ "${PID}X" != "X" ]; then
    echo "Killing properties editor..."
    kill ${PID}
else 
    echo "Properties editor not running (OK)."
    exit 0
fi
