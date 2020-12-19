#!/usr/bin/env bash
set -o xtrace

PROJECT_ID=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/project-id)
echo "${PROJECT_ID}" > /tmp/project_id

ZONE=$(basename $(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone))
echo "${ZONE}" > /tmp/zone

INSTANCE=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/name)
echo "${INSTANCE}" > /tmp/instance

INTERNAL_FQDN=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/hostname)
echo "${INTERNAL_FQDN}" > /tmp/hostname
FQDN_SUFFIX=$(echo "${INTERNAL_FQDN}" | cut -d "." -f 2-)
echo ".${FQDN_SUFFIX}" > /tmp/hostname_suffix


NODES_COUNT=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/nodes-count)
echo "${NODES_COUNT}" > /tmp/nodes_count
NODES_PREFIX=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/nodes-prefix)
echo "${NODES_PREFIX}" > /tmp/nodes_prefix

IG_NAME=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/instance-group-name)
echo "${IG_NAME}" > /tmp/ig_name

# sleep to let all instances to be created 
sleep 10


# Declare and array to capture ip addresses
IP_ADDRESSES=()
for i in $(seq 0 $((NODES_COUNT-1)))
do
    IP_ADDRESS=$(getent hosts "${NODES_PREFIX}${i}.${FQDN_SUFFIX}" | awk '{print $1}' | head -1)
    IP_ADDRESSES+=("${IP_ADDRESS}")
done

echo "${IP_ADDRESSES[*]}" | tr ' ' '\n' > /tmp/instance_ips
echo "${IP_ADDRESSES[*]}" | tr ' ' '\n' | sed 's/$/:54321/' > /tmp/flatfile
# Flatfile generated- but do it at the proper place later

H2O_URL=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/h2o-url)
echo "${H2O_URL}" > /tmp/h2o_url

H2O_ZIP_FILE=$(basename ${H2O_URL})
echo "${H2O_ZIP_FILE}" > /tmp/h2o_filename
H2O_VERSION=${H2O_ZIP_FILE%.zip}
echo "${H2O_VERSION}" > /tmp/h2o_version
H2O_HOME_DIR="/opt/h2oai/${H2O_VERSION}"
echo "${H2O_HOME_DIR}" > /tmp/h2o_home_dir


# Run H2O
memTotalKb=$(cat /proc/meminfo | grep MemTotal | sed 's/MemTotal:[ \t]*//' | sed 's/ kB//')
memTotalMb=$[ $memTotalKb / 1024 ]
tmp=$[ $memTotalMb * 90 ]
xmxMb=$[ $tmp / 100 ]

# run H2O in flatfile approach. 
cp /tmp/flatfile "${H2O_HOME_DIR}/flatfile.txt" 
java -Xmx${xmxMb}m -jar "${H2O_HOME_DIR}/h2o.jar" -flatfile "${H2O_HOME_DIR}/flatfile.txt" -name "${IG_NAME}"


