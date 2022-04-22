.NOTPARALLEL: all
SHELL:=/bin/bash
THIS_FILE := $(lastword $(MAKEFILE_LIST))

build-sagemaker-image:
	docker build -t h2o-gbm-sagemaker -f gbm/Dockerfile gbm/

#build-local-image:
#	docker build -t h2o-gbm-local -f gbm/Dockerfile.localtest ../

#test-image:
#	./tests/train_local.sh "test/test_gbm_prostate" h2o-gbm-sagemaker
