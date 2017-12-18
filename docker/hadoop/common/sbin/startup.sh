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
  done
  sync
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

if [[ $(echo ${ENTER_BASH} | tr -s '[:upper:]' '[:lower:]') == 'true' ]]; then
  cd /home/h2o
  set +e
  /bin/bash
  set -e
fi
