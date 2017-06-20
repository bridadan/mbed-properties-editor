#!/bin/sh

run_properties_editor() {
    cd ${HOME}/properties-editor
    java -jar ./target/mbed-properties-editor-1.0.0.jar
}

main() {
   run_properties_editor $*
}

main $*
