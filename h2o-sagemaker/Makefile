.NOTPARALLEL: all
SHELL:=/bin/bash
THIS_FILE := $(lastword $(MAKEFILE_LIST))

build-sagemaker-image:
	docker build -t h2o-gbm-sagemaker -f gbm/Dockerfile gbm/

build-local-image:
	cp -r gbm/gbm_scripts ../build
	cp -r ../h2o-py/build/main/dist/ ../build/dist
	docker build -t h2o-gbm-local -f gbm/Dockerfile.localtest ../build
	rm -rf ../build/gbm_scripts
	rm -rf ../build/dist

build-official-sagemaker-from-local: build-local-image
	docker tag h2o-gbm-local:latest $(ACCOUNTID).dkr.ecr.$(REGION).amazonaws.com/$(ECR):$(VERSION)
	aws ecr get-login-password --region $(REGION) | docker login --username AWS --password-stdin $(ACCOUNTID).dkr.ecr.$(REGION).amazonaws.com
	docker push $(ACCOUNTID).dkr.ecr.us-east-1.amazonaws.com/$(ECR):$(VERSION)

build-official-sagemaker-from-latest: build-sagemaker-image
	docker tag h2o-gbm-local:latest $(ACCOUNTID).dkr.ecr.$(REGION).amazonaws.com/$(ECR):$(VERSION)
	aws ecr get-login-password --region $(REGION) | docker login --username AWS --password-stdin $(ACCOUNTID).dkr.ecr.$(REGION).amazonaws.com
	docker push $(ACCOUNTID).dkr.ecr.us-east-1.amazonaws.com/$(ECR):$(VERSION)
