#!/bin/sh

do_merge() 
{
    cd conf
    /bin/rm -f deviceserver.properties.new 2>&1 1>/dev/null
    cat deviceserver.properties.DEFAULT > deviceserver.properties.new
    cat deviceserver.properties.updated >> deviceserver.properties.new
    /bin/rm -f deviceserver.properties.updated
    cd ..
}

main()
{
    do_merge $*
}

main $*
