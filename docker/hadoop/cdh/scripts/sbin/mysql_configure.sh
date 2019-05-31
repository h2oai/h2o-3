#!/bin/bash -ex

# Kill unclosed connections to counter a bug in CDH 6.2
echo "wait_timeout        = 5" >> /etc/mysql/mysql.conf.d/mysqld.cnf
