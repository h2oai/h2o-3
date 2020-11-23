#!/usr/bin/env bash

curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/name -o /tmp/instance.name
curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/zone -o /tmp/instance.zone
curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/id -o /tmp/instance.id
curl -H "Metadata-Flavor: Google" http://metadata.google.internal/computeMetadata/v1/instance/attributes/servers -o /tmp/instance.servers
