# H2O K8S Clustering Tests

The goal of tests to be found in this directory is to ensure H2O is able to perform
clustering. The process of clustering involves:

1. Start in a Docker container and recognize H2O is ran in K8S environment,
1. Discover other related H2O nodes,
1. Form a cluster/cloud of H2O nodes of given size.

Oftentimes, the process of clustering is also referred to as "clouding".


## DNS Test Scenario

Tests clustering by means of DNS records of a [Kubernetes service](https://kubernetes.io/docs/concepts/services-networking/service/).
Currently, this is the only scenario tests, as there are no other scenarios supported yet.

![Test scenario](readme/h2o-k8s-clustering.png)

Exactly `n` H2O pods (for simplicity and speed `n = 2`) are deployed to a Kubernetes cluster using a Deployment.
A `StatefulSet` is a direct match for stateful applications like H2O, however, at the time this test suite was created,
the `kubectl wait` command did not support `StatefulSet`. Therefore, `Deployment` has been used.

### Test stage definition

A new **nightly** stage named `Kubernetes` has been created in `{h2o-home}/scripts/groovy/defineTestsStages.groovy`.
Every stages in H2O runes inside an H2O container. The `Kubernetes` stage has it's own container named `harbor.h2o.ai/opsh2oai/h2o-3-k8s`. Latest
revision is always used. The image of that docker container is represented by the `Dockerfile` file in this very folder.
Changes can be done by building the container with `docker build . -t harbor.h2o.ai/opsh2oai/h2o-3-k8s` and pushing to `harbor.h2o.ai`.
The build is defined in `{h2o-home}/docker/Jenkinsfile-build-k8s-test-docker`.

In that image, a `Docker` is installed together with [k3d](https://github.com/rancher/k3d) by Rancher.
K3D serves as a convenience tool to install [k3s](https://k3s.io/), a lightweight Kubernetes implementation.
After the cluster is started, H2O Deployment is applied, together with a headless service and an Ingress. The deployment of
`n` together with the headless service tests whether H2O is capable to form a cluster. The Ingress is set-up to make
the H2O cluster size testable from outside of the K8S cluster, using `h2o-cluster-check.sh`. Before the cluster-size check is
started, `kubectl wait` is used to wait for the pods to be deployed. The pod with H2O consists of a single Docker container,
with JDK and `h2o.jar` mounted from the build that is running. This docker container build is defined in
`{h2o-home}/docker/Jenkinsfile-build-k8s-test-h2o-docker`.

An automated build has been set-up in Jenkins to build both images: `Jenkins` -> `H2O-3` -> `docker-images` -> `h2o-3-k8s-test-docker-build`.

The deployment speed of H2O pods depends heavily on connection to `harbor.h2o.ai`, as there is a secondary Docker image to run H2O pods,
and this image is downloaded from `harbor.h2o.ai` every single time. As the whole Kubernetes docker runs inside a Docker and
is intended to be used only once, there is no cache. Usually, this stage is a matter of seconds. 

As soon as H2O pods are deployed, the `h2o-cluster-check.sh` is started.This queries H2O for cluster info by `curl http://localhost:8080/3/Cloud`.
The cloud size in the JSON returned must be equal to the expected value. If it is equal, then the test is considered to be a pass
and an exit value of `0` is returned, indicating a passed test to Jenkins. Otherwise, a value of `1` is returned, signaling a 
failed test to Jenkins. In both cases, before the script exists, a cleanup of the Kubernetes cluster is done using `k3d delete`
before the outer Docker is killed. This is an important step, as in case host Docker is used, the container with K3S Kubernetes
cluster could have lived on.


