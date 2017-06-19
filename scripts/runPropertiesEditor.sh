#!/bin/sh

run_properties_editor() {
    cd ${HOME}/properties-editor
    java -jar ./target/mbedPropertiesEditor-1.0.0.jar
}

main() {
   run_properties_editor $*
}

main $*
