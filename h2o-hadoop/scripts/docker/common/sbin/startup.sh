#! /bin/bash

set -e

NC='\033[0m'
RED='\033[91m'

if [[ -d /startup && $(ls /startup) ]]; then
  cd /startup
  echo
  echo "###### Adding custom startup scripts ######"
  for x in $(ls); do
    echo -e "\tAdding startup script ${RED}${x}${NC}"
    cp ${x} /etc/startup
    chmod 700 /etc/startup/${x}
    sync
  done
  echo "###### Custom startup scripts added ######"
  echo
fi

cd /etc/startup/
for x in $(ls . | sort -n); do
  echo -e "###### Running startup script ${RED}${x}${NC} ######"
  chmod 700 ${x}
  sync
  ./${x}
done

if [[ ${ACTIVATE_SPARK} != '' ]]; then
  if [[ ! -e /usr/bin/activate_spark_${ACTIVATE_SPARK} ]]; then
    echo "Cannot find activation script for Spark ${ACTIVATE_SPARK}. Should be under /opt/activate_spark_${ACTIVATE_SPARK}"
    exit 1
  fi
  chmod 755 /usr/bin/activate_spark_${ACTIVATE_SPARK}
  sync
  /usr/bin/activate_spark_${ACTIVATE_SPARK}
fi

if [[ $(echo ${ENTER_BASH} | tr -s '[:upper:]' '[:lower:]') == 'true' ]]; then
  cd /home/h2o
  set +e
  /bin/bash
  set -e
fi
