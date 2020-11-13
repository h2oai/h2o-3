#!/bin/bash

# Splits yarn logs into per-container files

set -e

LOG_FILE=$1

if [ ! -f "$LOG_FILE" ]; then
  echo "ERRR: File '$LOG_FILE' doesn't exist."
  exit 1
fi

CONT_DIR=$(echo $LOG_FILE | sed 's/.log$//')

mkdir $CONT_DIR

CONT_NUM=$(grep '^Container: container' application_1596880493584_110418.log | wc -l | tr -d ' ')

echo "INFO: Found $CONT_NUM containers"
cd $CONT_DIR

let SPLIT_NUM=CONT_NUM-1
csplit -s -f container_ "../$LOG_FILE" '/^Container:/' "{$SPLIT_NUM}"


for cont in container_*; do
   cont_name=$(head -1 $cont | cut -d' ' -f2)
   if [ -z "$cont_name" -a "container_00" = "$cont" ]; then
      rm "$cont"
   else
      mv "$cont" "$cont_name"
   fi
done
