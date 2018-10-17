SHELL := /bin/bash
THIS_FILE := $(lastword $(MAKEFILE_LIST))
.PHONY : all

VERSION ?= dev
PUSH ?= 0
GPU ?= false
H2O_BRANCH ?= $(shell git rev-parse --abbrev-ref HEAD)
GRADLE_VERSION ?= $(shell cat ../gradle/wrapper/gradle-wrapper.properties | grep distributionUrl | egrep -o '([0-9]+\.+)+[0-9]+')

ifeq ($(shell echo $(GPU) | tr [:upper:] [:lower:] ),true)
	GPU_SUFFIX := -gpu
	BASE_FROM_IMAGE := nvidia/cuda:9.0-cudnn7-devel-ubuntu16.04
else
	BASE_FROM_IMAGE := ubuntu:16.04
endif

dev-base:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-base$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-base \
		--build-arg FROM_IMAGE=$(BASE_FROM_IMAGE) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-base$(GPU_SUFFIX):$(VERSION)
endif


ifneq ($(CI), 1)
dev-jdk-8-base: dev-base
endif
dev-jdk-8-base:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-jdk-8-base$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-8-base \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-jdk-8-base$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-jdk-others-base: dev-base
endif
dev-jdk-others-base:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-jdk-others-base$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-others-base \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-jdk-others-base$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-base: dev-jdk-8-base
endif
dev-python-base:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-python-base$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-python-base \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-python-base$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-base: dev-jdk-8-base
endif
dev-r-base:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-r-base$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-r-base \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-r-base$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-2.7-jdk-%: dev-python-2.7
endif
dev-python-2.7-jdk-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-python-2.7-jdk-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=docker.h2o.ai/opsh2oai/h2o-3/dev-python-2.7 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-python-2.7-jdk-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.5-jdk-%: dev-python-3.5
endif
dev-python-3.5-jdk-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-python-3.5-jdk-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=docker.h2o.ai/opsh2oai/h2o-3/dev-python-3.5 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-python-3.5-jdk-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.6-jdk-%: dev-python-3.6
endif
dev-python-3.6-jdk-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-python-3.6-jdk-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=docker.h2o.ai/opsh2oai/h2o-3/dev-python-3.6 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-python-3.6-jdk-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-%: dev-python-base
endif
dev-python-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-python-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-python \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg PYTHON_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-python-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-3.3.3-jdk-%: dev-r-3.3.3
endif
dev-r-3.3.3-jdk-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-r-3.3.3-jdk-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=docker.h2o.ai/opsh2oai/h2o-3/dev-r-3.3.3 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-r-3.3.3-jdk-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-3.4.1-jdk-%: dev-r-3.4.1
endif
dev-r-3.4.1-jdk-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1-jdk-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=docker.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1-jdk-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-%: dev-r-base
endif
dev-r-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-r-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-r \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg R_VERSION=$* \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-r-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-jdk-%: dev-jdk-others-base
endif
dev-jdk-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-jdk-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg INSTALL_JAVA_VERSION=$* \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-jdk-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-build-base: dev-r-3.4.1
endif
dev-build-base:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-build-base$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-build-base \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-build-base$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-build-gradle-%: dev-build-base
endif
dev-build-gradle-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-build-gradle-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-build \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-build-gradle-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-benchmark-gradle-%: dev-build-gradle-$(GRADLE_VERSION)
endif
dev-benchmark-gradle-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-benchmark-gradle-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-benchmark \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg GRADLE_VERSION=$(GRADLE_VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-benchmark-gradle-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-build-hadoop-gradle-%: dev-build-gradle-$(GRADLE_VERSION)
endif
dev-build-hadoop-gradle-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-build-hadoop-gradle-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-build-hadoop \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		--build-arg GRADLE_VERSION=$(GRADLE_VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-build-hadoop-gradle-$*$(GPU_SUFFIX):$(VERSION)
endif

ifneq ($(CI), 1)
dev-release-gradle-%: dev-build-hadoop-gradle-$(GRADLE_VERSION)
endif
dev-release-gradle-%:
	docker build -t docker.h2o.ai/opsh2oai/h2o-3/dev-release-gradle-$*$(GPU_SUFFIX):$(VERSION) \
		-f jenkins-images/Dockerfile-release \
		--build-arg GPU_SUFFIX=$(GPU_SUFFIX) \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		--build-arg GRADLE_VERSION=$(GRADLE_VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push docker.h2o.ai/opsh2oai/h2o-3/dev-release-gradle-$*$(GPU_SUFFIX):$(VERSION)
endif
