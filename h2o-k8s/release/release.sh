#! /bin/bash -x

echo ${H2O_RED_HAT_REGISTRY_KEY} | docker login -u unused scan.connect.redhat.com --password-stdin
docker tag $DOCKER_IMAGE_NAME:$DOCKER_IMAGE_TAG scan.connect.redhat.com/$H2O_RED_HAT_PID/h2o:$DOCKER_IMAGE_TAG
DIGEST=$(docker push scan.connect.redhat.com/$H2O_RED_HAT_PID/h2o:$DOCKER_IMAGE_TAG | sed -n 's/.*\(sha256:[a-zA-Z0-9]*\).*$/\1/p')
echo $DIGEST
python scripts/jenkins/red_hat/red_hat_docker_certification.py --api_key $H2O_RED_HAT_APIKEY --pid $H2O_RED_HAT_PID --tag $DOCKER_IMAGE_TAG $DIGEST
