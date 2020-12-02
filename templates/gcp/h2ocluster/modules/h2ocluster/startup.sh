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

# Get list of instances in the instance group
#instances=$(gcloud compute instance-groups unmanaged list-instances "${IG_NAME}" --zone="${ZONE}" | tail -n +2 | cut -d " " -f 1)
#echo "${instances}" > /tmp/instances

# Declare and array to capture ip addresses
IP_ADDRESSES=()
for i in $(seq 0 $((NODES_COUNT-1)))
do
    IP_ADDRESS=$(getent hosts "${NODES_PREFIX}${i}.${FQDN_SUFFIX}" | awk '{print $1}' | head -1)
    IP_ADDRESSES+=("${IP_ADDRESS}")
done
#for instance in ${instances}
#do
#    IP_ADDRESS=$(getent hosts "${instance}.${FQDN_SUFFIX}" | awk '{print $1}' | head -1)
#    IP_ADDRESSES+=("${IP_ADDRESS}")
#done
echo "${IP_ADDRESSES[*]}" | tr ' ' '\n' > /tmp/instance_ips
echo "${IP_ADDRESSES[*]}" | tr ' ' '\n' | sed 's/$/:54321/' > /tmp/flatfile
 
# Signal Startup script completion
gcloud compute instances add-metadata ${INSTANCE} --metadata startup-complete=TRUE --zone=${ZONE}

