#!/bin/sh

cd ${HOME}/properties-editor/scripts
./killPropertiesEditor.sh
sleep 5
cd ${HOME}/properties-editor
./runPropertiesEditor.sh &
