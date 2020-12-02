#!/usr/bin/env bash
set -o xtrace

ZONE=$(basename $(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone))
INSTANCE=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/name)
INTERNAL_FQDN=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/hostname)
FQDN_SUFFIX=$(echo "${INTERNAL_FQDN}" | cut -d "." -f 2-)


NODES_COUNT=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/nodes-count)
NODES_PREFIX=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/nodes-prefix)

PROJECT_ID=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/project-id)
IG_NAME=$(curl --silent -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/instance-group-name)

# sleep to let all instances to be created 
sleep 30


# Get list of instances in the instance group
# https://cloud.google.com/compute/docs/instance-groups/creating-groups-of-unmanaged-instances#listing_vms
list_instance_url="https://compute.googleapis.com/compute/v1/projects/${PROJECT_ID}/zones/${ZONE}/instanceGroups/${IG_NAME}/listInstances"
response=$(curl --silent -X POST ${list_instance_url})

echo "${ZONE}" > /tmp/zone
echo "${INSTANCE}" > /tmp/instance
echo "${NODES_COUNT}" > /tmp/nodes_count
echo "${NODES_PREFIX}" > /tmp/nodes_prefix
echo "${PROJECT_ID}" > /tmp/project_id
echo "${IG_NAME}" > /tmp/ig_name
echo "${INTERNAL_FQDN}" > /tmp/hostname
echo ".${FQDN_SUFFIX}" > /tmp/hostname_suffix

echo "${response}" > /tmp/instance_list_response

# Declare and array to capture ip addresses
IP_ADDRESSES=()
for i in $(seq 0 $((NODES_COUNT-1)))
do
    IP_ADDRESS=$(getent hosts "${NODES_PREFIX}${i}.${FQDN_SUFFIX}" | awk '{print $1}' | head -1)
    IP_ADDRESSES+=(IP_ADDRESS)
done

echo "${IP_ADDRESSES[*]}" > /tmp/instance_ips
 
# Signal Startup script completion
gcloud compute instances add-metadata ${INSTANCE} --metadata startup-complete=TRUE --zone=${ZONE}

