#! /bin/bash

set -ex

cat << EOF | debconf-set-selections
slapd slapd/internal/generated_adminpw password admin
slapd slapd/internal/adminpw password admin
slapd slapd/password2 password admin
slapd slapd/password1 password admin
slapd slapd/dump_database_destdir string /var/backups/slapd-VERSION
slapd slapd/domain string 0xdata.loc
slapd shared/organization string 0xdata.loc
slapd slapd/backend string HDB
slapd slapd/purge_database boolean true
slapd slapd/move_old_database boolean true
slapd slapd/allow_ldap_v2 boolean false
slapd slapd/no_configuration boolean false
slapd slapd/dump_database select when needed
EOF

dpkg-reconfigure -f noninteractive slapd

service slapd start

ldapwhoami -H ldap:// -x

ldapmodify -a -x -D "cn=admin,dc=0xdata,dc=loc" -w admin -H ldap:// -f opt/ldap-scripts/users.ldif
ldapmodify -a -x -D "cn=admin,dc=0xdata,dc=loc" -w admin -H ldap:// -f opt/ldap-scripts/jenkins.ldif
service slapd stop
