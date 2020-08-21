#!/bin/bash -ex

# This script initializes the kerberos database and stores necessary keys in keytabs

MAPRED_USER=$1

mkdir -p /var/log/kerberos
kdb5_util create -P h2o
kadmin.local -q 'addprinc -pw h2o hdfs/localhost@H2O.AI'
kadmin.local -q 'addprinc -pw h2o mapred/localhost@H2O.AI'
kadmin.local -q 'addprinc -pw h2o yarn/localhost@H2O.AI'
kadmin.local -q 'addprinc -pw h2o HTTP/localhost@H2O.AI'
kadmin.local -q 'addprinc -pw h2o hive/localhost@H2O.AI'
kadmin.local -q 'addprinc -pw h2o steam/localhost@H2O.AI'
kadmin.local -q 'addprinc -pw h2o jenkins@H2O.AI'
kadmin.local -q 'addprinc -pw h2o root@H2O.AI'
cd ${HADOOP_CONF_DIR}
kadmin.local -q 'xst -norandkey -k hdfs.keytab hdfs/localhost@H2O.AI HTTP/localhost@H2O.AI'
chown hdfs:hdfs hdfs.keytab
kadmin.local -q 'xst -norandkey -k yarn.keytab yarn/localhost@H2O.AI HTTP/localhost@H2O.AI'
chown yarn:hadoop yarn.keytab
kadmin.local -q 'xst -norandkey -k mapred.keytab mapred/localhost@H2O.AI HTTP/localhost@H2O.AI'
chown ${MAPRED_USER} mapred.keytab
kadmin.local -q 'xst -norandkey -k hive.keytab hive/localhost@H2O.AI HTTP/localhost@H2O.AI'
chown hive:hive hive.keytab
kadmin.local -q 'xst -norandkey -k steam.keytab steam/localhost@H2O.AI'
chown jenkins:jenkins steam.keytab
chmod 400 *.keytab

mkdir -p /srv/keys
cd /srv/keys
kadmin.local -q 'xst -norandkey -k h2o.keytab HTTP/localhost@H2O.AI'
chown -R jenkins:jenkins /srv/keys
