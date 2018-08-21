#!/bin/sh

cd ${HOME}/properties-editor/scripts
./killPropertiesEditor.sh
sleep 8
cd ${HOME}/properties-editor
./runPropertiesEditor.sh &
