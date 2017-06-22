#!/bin/sh

restart_service() 
{
    cd ${HOME}/service
    ./killService.sh
    ./runService.sh &
}

main()
{
    restart_service $*
}

main $*
