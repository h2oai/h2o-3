SHELL := /bin/bash
THIS_FILE := $(lastword $(MAKEFILE_LIST))
.PHONY : all

VERSION ?= dev
PUSH ?= 0
H2O_BRANCH ?= $(shell git rev-parse --abbrev-ref HEAD)
NO_CACHE ?= --no-cache
BASE_FROM_IMAGE := nvidia/cuda:12.1.0-devel-ubuntu22.04

dev-base:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-base:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-base \
		--build-arg FROM_IMAGE=$(BASE_FROM_IMAGE) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-base:$(VERSION)
endif

ifneq ($(CI), 1)
dev-jdk-8-base: dev-base
endif
dev-jdk-8-base:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-8-base:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-8-base \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-8-base:$(VERSION)
endif

ifneq ($(CI), 1)
dev-jdk-others-base: dev-base
endif
dev-jdk-others-base:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-others-base:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others-base \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-others-base:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-base: dev-jdk-8-base
endif
dev-python-base:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-base:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-python-base \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-base:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-base: dev-python-3.7
endif
dev-r-base:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-base:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-r-base \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-base:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.6-jdk-%: dev-python-3.6
endif
dev-python-3.6-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.6-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.6 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.6-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.7-jdk-%: dev-python-3.7
endif
dev-python-3.7-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.7-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.7 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.7-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.8-jdk-%: dev-python-3.8
endif
dev-python-3.8-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.8-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.8 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.8-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.9-jdk-%: dev-python-3.9
endif
dev-python-3.9-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.9-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.9 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.9-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.10-jdk-%: dev-python-3.10
endif
dev-python-3.10-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.10-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.10 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.10-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-3.11-jdk-%: dev-python-3.11
endif
dev-python-3.11-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.11-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.11 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-3.11-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-mojocompat: dev-python-3.7

endif
dev-mojocompat:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-mojocompat:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-mojocompat \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-mojocompat:$(VERSION)
endif

ifneq ($(CI), 1)
dev-python-%: dev-python-base
endif
dev-python-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-python \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg PYTHON_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-3.3.3-jdk-%: dev-r-3.3.3
endif
dev-r-3.3.3-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.3.3-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.3.3 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.3.3-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-3.4.1-jdk-%: dev-r-3.4.1
endif
dev-r-3.4.1-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.4.1-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-3.5.3-jdk-%: dev-r-3.5.3
endif
dev-r-3.5.3-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.5.3-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.5.3 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.5.3-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-3.6.2-jdk-%: dev-r-3.6.2
endif
dev-r-3.6.2-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.6.2-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.6.2 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-3.6.2-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-4.0.2-jdk-%: dev-r-4.0.2
endif
dev-r-4.0.2-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-4.0.2-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-r-4.0.2 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-4.0.2-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-4.4.0-jdk-%: dev-r-4.4.0
endif
dev-r-4.4.0-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-4.4.0-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-r-4.4.0 \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-4.4.0-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-r-%: dev-r-base
endif
dev-r-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-r \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg R_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-jdk-%: dev-jdk-others-base
endif
dev-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg INSTALL_JAVA_VERSION=$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-$*:$(VERSION)
endif

ifneq ($(CI), 1)
dev-build-base: dev-r-3.4.1
endif
dev-build-base:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-build-base:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-build-base \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-build-base:$(VERSION)
endif

ifneq ($(CI), 1)
dev-build-hadoop: dev-build-base
endif
dev-build-hadoop:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-build-hadoop:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-build-hadoop \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-build-hadoop:$(VERSION)
endif

ifneq ($(CI), 1)
dev-release: dev-build-hadoop
endif
dev-release:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-release:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-release \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-release:$(VERSION)
endif
