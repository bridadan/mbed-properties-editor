#!/bin/sh

restart_service() 
{
    cd ${HOME}
    ./restart.sh
}

main()
{
    restart_service $*
}

main $*
