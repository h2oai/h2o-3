#!/bin/bash -ex

source /usr/sbin/get_hive_home.sh

if [ "$HIVE_HOME" = "" ]
then
    HIVE=hive
else
    HIVE="$HIVE_HOME/bin/hive"
fi
HIVE_VERSION_MAJOR=$($HIVE --version | grep -E '^Hive' | sed -E 's/Hive ([0-9])\..*/\1/')
if [ "$HIVE_VERSION_MAJOR" = "2" ] || [ "$HIVE_VERSION_MAJOR" = "3" ]
then
    echo "Detected Hive version 2 or greater"
    exit 0
else
    echo "No hive or Hive 1.x or 0.x"
    exit 1
fi
