#!/usr/bin/env bash

set -ex

while (( "$#" )); do
  case "$1" in
    --notify-file)
      notifyFile=$2
      shift 2
      ;;
    --driver-log-file)
      driverLogFile=$2
      shift 2
      ;;
    --yarn-logs-file)
      yarnLogsFile=$2
      shift 2
      ;;
    -*|--*=) # unsupported flags
      echo "Error: Unsupported flag $1" >&2
      exit 1
      ;;
  esac
done


if [ -f ${notifyFile} ]; then
    YARN_APPLICATION_ID=$(cat ${notifyFile} | grep job | sed 's/job/application/g')
elif [ -f ${driverLogFile} ]; then
    YARN_APPLICATION_ID=$(cat ${driverLogFile} | grep 'yarn logs -applicationId' | sed -r 's/.*(application_[0-9]+_[0-9]+).*/\1/' | head -n 1)
fi
if [ "$YARN_APPLICATION_ID" != "" ]; then
    echo "YARN Application ID is ${YARN_APPLICATION_ID}"
    yarn application -kill ${YARN_APPLICATION_ID}
    if [ "$yarnLogsFile" != "" ]; then
      yarn logs -applicationId ${YARN_APPLICATION_ID} > ${yarnLogsFile}
    fi
else
    echo "No cleanup, did not find yarn application id."
fi        
