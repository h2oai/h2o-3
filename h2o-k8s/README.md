# H2O Kubernetes integration

The integration of Kubernetes and H2O is possible via `water.k8s.KubernetesEmbeddedConfigProvider` - to be found
in this module. This implementation of `EmbeddedConfigProvider` is dynamically loaded on H2O start and remains inactive
unless H2O is running in a Docker container managed by Kubernetes is detected.  

## Running H2O in K8s - user's guide

H2O Pods deployed on Kubernetes cluster require a 
[headless service](https://kubernetes.io/docs/concepts/services-networking/service/#headless-services) 
for H2O Node discovery. The headless service, instead of load-balancing incoming requests to the underlying
H2O pods, returns a set of adresses of all the underlying pods. It is therefore the responsibility of the K8S
cluster administrator to set-up the service correctly to cover H2O nodes only.

### Creating the headless service
First, a headless service must be created on Kubernetes.  

```yaml
apiVersion: v1
kind: Service
metadata:
  name: h2o-service
  namespace: <namespace-name>
spec:
  type: ClusterIP
  clusterIP: None
  selector:
    app: h2o-k8s
  ports:
  - protocol: TCP
    port: 54321
```

The `clusterIP: None` defines the service as headless. The `port: 54321` is the default H2O port. Users and client libraries
use this port to talk to the H2O cluster.

The `app: h2o-k8s` setting is of **great importance**, as this is the name of the application with H2O pods inside. 
Please make sure this setting corresponds to the name of H2O deployment name chosen.

### Creating the H2O Stateful Set

It is **strongly recommended** to run H2O as a [Stateful set](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
on a Kubernetes cluster. Kubernetes assumes all the pods inside the cluster are stateful and does not attempt to restart
the individual pods on failure. Once a job is triggered on an H2O cluster, the cluster is locked and no additional nodes
can be added. Therefore, the cluster has to be restarted as a whole if required - which is a perfect fit for a StatefulSet.

In order to ensure reproducibility, all requests should be directed towards H2O Leader node. Leader node election is done
after the set of nodes discovered is complete. Therefore, after the clustering is complete and the leader node is known,
only the pod with H2O leader node should be made available. This also makes the service(s) on top of the deployment route
all requests only to the leader node. To achieve that, readiness probe residing on `/kubernetes/isLeaderNode` address is used.
Once the clustering is done, all nodes but the leader node mark themselves as not ready, leaving only the leader node exposed.

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: h2o-stateful-set
  namespace: <namespace-name>
spec:
  serviceName: h2o-service
  podManagementPolicy: "Parallel"
  replicas: 3
  selector:
    matchLabels:
      app: h2o-k8s
  template:
    metadata:
      labels:
        app: h2o-k8s
    spec:
      containers:
        - name: h2o-k8s
          image: 'h2oai/h2o-open-source-k8s:<tagname>'
          resources:
            requests:
              memory: "4Gi"
          ports:
            - containerPort: 54321
              protocol: TCP
          readinessProbe:
            httpGet:
              path: /kubernetes/isLeaderNode
              port: 8081
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 1
          env:
          - name: H2O_KUBERNETES_SERVICE_DNS
            value: h2o-service.<namespace-name>.svc.cluster.local
          - name: H2O_NODE_LOOKUP_TIMEOUT
            value: '180'
          - name: H2O_NODE_EXPECTED_COUNT
            value: '3'
          - name: H2O_KUBERNETES_API_PORT
            value: '8081'
```
Besides standardized Kubernetes settings, like `replicas: 3` defining the number of pods with H2O instantiated, there are
several settings to pay attention to.

The name of the application `app: h2o-k8s` must correspond to the name expected by the above-defined headless service in order
for the H2O node discovery to work. H2O communicates on port 54321, therefore `containerPort: 54321`must be exposed to
make it possible for the clients to connect.

The documentation of the official H2O Docker images is available at the official [H2O Docker Hub page](https://hub.docker.com/r/h2oai/h2o-open-source-k8s). Use the `nightly` tag to
get the bleeding-edge Docker image with H2O inside. 

Environment variables:

1. `H2O_KUBERNETES_SERVICE_DNS` - **[MANDATORY]** Crucial for the clustering to work. The format usually follows the
 `<service-name>.<project-name>.svc.cluster.local` pattern. This setting enables H2O node discovery via DNS.
  It must be modified to match the name of the headless service created. Also, pay attention to the rest of the address
  to match the specifics of your Kubernetes implementation.
1. `H2O_NODE_LOOKUP_TIMEOUT` - **[OPTIONAL]** Node lookup constraint. Time before the node lookup is ended. 
1. `H2O_NODE_EXPECTED_COUNT` - **[OPTIONAL]** Node lookup constraint. Expected number of H2O pods to be discovered.
1. `H2O_KUBERNETES_API_PORT` - **[OPTIONAL]** Port for Kubernetes API checks to listen on. Defaults to 8080.

If none of the optional lookup constraints is specified, a sensible default node lookup timeout will be set - currently
defaults to 3 minutes. If any of the lookup constraints are defined, the H2O node lookup is terminated on whichever 
condition is met first.

### Exposing H2O cluster

Exposing the H2O cluster is a responsibility of the Kubernetes administrator. By default, an
 [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/) can be created. Different platforms offer
 different capabilities, e.g. OpenShift offers [Routes](https://docs.openshift.com/container-platform/4.3/networking/routes/route-configuration.html).
