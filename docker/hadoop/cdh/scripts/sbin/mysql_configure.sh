#!/bin/bash -ex

if [ "${VERSION}" = "6.2" ] || [ "${VERSION}" = "6.3" ]
then 
  echo "Decrease mysql connection timeout." 
  # Kill unclosed connections to counter a bug in CDH 6.2
  echo "wait_timeout        = 5" >> /etc/mysql/mysql.conf.d/mysqld.cnf
fi
