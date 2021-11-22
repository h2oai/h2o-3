SHELL := /bin/bash
THIS_FILE := $(lastword $(MAKEFILE_LIST))
.PHONY : all

VERSION ?= dev
PUSH ?= 0
H2O_BRANCH ?= $(shell git rev-parse --abbrev-ref HEAD)
NO_CACHE ?= --no-cache
BASE_FROM_IMAGE := nvidia/cuda:9.0-cudnn7-devel-ubuntu16.04

# same as Jenkinsfile-build-docker
PY_VERSIONS = 2.7  3.5  3.7  3.8
R_VERSIONS = 3.3.3  3.4.4  3.5.3  3.6.3  4.0.5  4.1.2 
# default versions
JAVA_DEF = 8
PY_DEF = 3.7
R_DEF = 3.4.4

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
dev-jdk-$(JAVA_DEF)-base: dev-base
endif
dev-jdk-$(JAVA_DEF)-base:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-$(JAVA_DEF)-base:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-$(JAVA_DEF)-base \
		--build-arg FROM_VERSION=$(VERSION) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-jdk-$(JAVA_DEF)-base:$(VERSION)
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
dev-python-base: dev-jdk-$(JAVA_DEF)-base
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
dev-r-base: dev-python-$(PY_DEF)
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
dev-mojocompat: dev-python-$(PY_DEF)
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

define make-dev-python-jdk
ifneq ($(CI), 1)
dev-python-$1-jdk-%: dev-python-$1
endif
dev-python-$1-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-python-$1-jdk-$$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-python-$1 \
		--build-arg INSTALL_JAVA_VERSION=$$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-python-$1-jdk-$$*:$(VERSION)
endif
all:: dev-python-$1-jdk-%
endef
$(foreach ver, $(PY_VERSIONS), $(eval $(call make-dev-python-jdk,$(ver))))

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

define make-dev-r-jdk
ifneq ($(CI), 1)
dev-r-$1-jdk-%: dev-r-$1
endif
dev-r-$1-jdk-%:
	docker build -t harbor.h2o.ai/opsh2oai/h2o-3/dev-r-$1-jdk-$$*:$(VERSION) \
		$(NO_CACHE) \
		-f jenkins-images/Dockerfile-jdk-others \
		--build-arg FROM_VERSION=$(VERSION) \
		--build-arg FROM_IMAGE=harbor.h2o.ai/opsh2oai/h2o-3/dev-r-$1 \
		--build-arg INSTALL_JAVA_VERSION=$$* \
		--build-arg H2O_BRANCH=$(H2O_BRANCH) \
		.
ifeq ($(shell echo $(PUSH) | tr [:upper:] [:lower:] ),true)
	docker push harbor.h2o.ai/opsh2oai/h2o-3/dev-r-$1-jdk-$$*:$(VERSION)
endif
all:: dev-r-$1-jdk-%
endef
$(foreach ver, $(R_VERSIONS), $(eval $(call make-dev-r-jdk,$(ver))))

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
dev-build-base: dev-r-$(R_DEF)
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
