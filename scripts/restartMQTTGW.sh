#!/bin/sh

restart_mqtt_gw() 
{
    cd ${HOME}/mds
    ./killGW.sh
    ./runGW.sh
}

main()
{
    restart_mqtt_gw $*
}

main $*
