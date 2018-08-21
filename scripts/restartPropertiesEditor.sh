#!/bin/sh

cd ${HOME}/properties-editor/scripts
./killPropertiesEditor.sh
cd ..
./runPropertiesEditor.sh &
