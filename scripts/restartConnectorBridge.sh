#!/bin/sh

restart_connector_bridge() 
{
    cd ${HOME}/mds
    ./killConnectorBridge.sh
    ./runConnectorBridge.sh
}

main()
{
    restart_connector_bridge $*
}

main $*
