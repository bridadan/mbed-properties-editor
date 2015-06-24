#!/bin/sh

cd conf

/bin/rm -f deviceserver.properties.new 2>&1 1>/dev/null

cat deviceserver.properties.DEFAULT > deviceserver.properties.new
cat deviceserver.properties.updated >> deviceserver.properties.new

/bin/rm -f deviceserver.properties.updated

cd ..

exit 0
