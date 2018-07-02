#!/bin/bash

H2OAI_USER=ubuntu
NAME=$(curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/name)
LOCALITY=$(curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone)
INSTANCE_ID=$(curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/id)
IP=$(dig +short myip.opendns.com @resolver1.opendns.com)

# Create nginx certs
if [ ! -f /opt/h2oai/h2o3-cert.crt ]
then
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /opt/h2oai/h2o3-cert.key \
  -out /opt/h2oai/h2o3-cert.crt << EOF
US
CA
$LOCALITY
$NAME
$INSTANCE_ID
$IP

EOF
  echo "Created Self-Signed Certificates"
else
  echo "Self-Signed Certificates Already Exist"
fi

# Create user password
if [ ! -f /opt/h2oai/htpasswd ]
then
  htpasswd -bc /opt/h2oai/htpasswd h2oai "${INSTANCE_ID}"
  echo "Created Password for User h2oai"
else
  echo "Password and Username Already Exist"
fi

# Create Flatfile
if [ ! -f /opt/h2oai/flatfile.txt ]
then
  touch /opt/h2oai/flatfile.txt
  flatfile="/opt/flatfile.txt"

  hosts=$(curl -H "Metadata-Flavor: Google" "http://metadata.google.internal/computeMetadata/v1/instance/attributes/servers")
  hosts=`echo $hosts | sed -e 's/|/,/g'`
  hosts=${hosts::-1}
  IFS=',' read -r -a array <<< "$hosts"

  for i in "${array[@]}"
  do
    gcloud compute instances list | awk -v pat="$i" '$1 ~ pat { print $4":54321" }' >> /opt/h2oai/flatfile.txt
  done
else
  echo "flatfile.txt already exists"
fi

sleep 15
systemctl restart nginx
