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
