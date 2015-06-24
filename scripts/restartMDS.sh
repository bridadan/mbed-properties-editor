#!/bin/sh

restart_mds()
{
    cd ${HOME}
    ./restart.sh
}

backup_config_files()
{ 
    NOW=`date -d "today" +"%Y%m%d%H%M"`
    cd ${HOME}/mds/device-server-enterprise/conf
    cp deviceserver.properties deviceserver.properties-${NOW}
}

install_config_files()
{
    cd ${HOME}/configurator/conf
    mv deviceserver.properties.new ${HOME}/mds/device-server-enterprise/conf/deviceserver.properties
}

update_config_files()
{
    if [ -f ${HOME}/configurator/conf/deviceserver.properties.new ]; then
        backup_config_files $*
        install_config_files $*
    fi
}

main() 
{
    update_config_files $*
    restart_mds $*
}

main $*
