#!/bin/sh

cd ${HOME}/properties-editor/scripts
./killPropertiesEditor.sh
cd ${HOME}/properties-editor
./runPropertiesEditor.sh &
