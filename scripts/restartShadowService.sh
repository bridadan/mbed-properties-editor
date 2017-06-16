#!/bin/sh

restart_shadow_service() 
{
    cd ${HOME}/shadow-service
    ./killShadowService.sh
    ./runShadowService.sh
}

main()
{
    restart_shadow_service $*
}

main $*
