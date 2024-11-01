.NOTPARALLEL: all
SHELL:=/bin/bash
THIS_FILE := $(lastword $(MAKEFILE_LIST))

test-image-prostate:
	./train_local.sh test_gbm_prostate h2o-gbm-local

test-image-titanic:
	./train_local.sh test_gbm_titanic h2o-gbm-local

test-image-checkpoint:
	./train_local.sh test_gbm_prostate_checkpoint h2o-gbm-local

test-image-checkpoint-produced:
	./train_local.sh test_gbm_prostate_checkpoints_correctly_produced h2o-gbm-local
