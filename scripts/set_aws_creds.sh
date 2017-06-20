#!/bin/sh

set_config_file() {
   echo "[default]" > config
   echo "region =" $1 >> config
}

set_credential_file() {
   echo "[default]" > credentials
   echo "aws_access_key_id =" $1 >> credentials
   echo "aws_secret_access_key =" $2 >> credentials
}

set_creds() {
   mkdir /home/arm/.aws
   cd /home/arm/.aws
   set_config_file $1
   set_credential_file $2 $3
   chmod 644 config credentials
}


remove_creds() {
   cd /home/arm
   mv .aws .aws-BAK 2>&1 1> /dev/null
}


main() {
   remove_creds $*
   set_creds $*
}

REGION="$1"
KEY_ID="$2"
ACCESS_KEY="$3"

if [ "${REGION}X" = "X" ]; then
  echo "Usage $0 <region> <key_id> <access_key>"
  exit 1
fi

if [ "${KEY_ID}X" = "X" ]; then
  echo "Usage $0 <region> <key_id> <access_key>"
  exit 1
fi

if [ "${ACCESS_KEY}X" = "X" ]; then
  echo "Usage $0 <region> <key_id> <access_key>"
  exit 1
fi 

main $*
