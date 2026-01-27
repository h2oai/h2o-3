Use H2O with Kubernetes
=======================

H2O software stack
------------------

In order to understand the behavior and limitations of a H2O distributed cluster, it is mandatory to understand the basics of H2O's design. The H2O cluster is stateful. If one H2O node is terminated, the cluster is immediately recognized as unhealthy and has to be restarted as a whole.

This implies that H2O nodes must be treated as stateful by Kubernetes. In Kubernetes, a set of pods sharing a common state is handled by a `StatefulSet <https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/>`__. Kubernetes tooling for stateless applications in not applicable to H2O. The proper way to deploy H2O is to set ``kind: StatefulSet``. A StatefulSet ensures:

- H2O Nodes are treated as a single unit, being brought up and down gracefully and together.
- No attempts will be made by K8S healthcheck (if defined) to restart individual H2O nodes in case of an error.
- Persistent storages and volumes associated with the StatefulSet of H2O Nodes will not be deleted once the cluster is brought down.


H2O Kubernetes deployment
-------------------------

In order to spawn a H2O cluster inside a Kubernetes cluster, the following list of requirements must be met:

1. A Kubernetes cluster. For local development, `k3s <https://k3s.io/>`__ is a great choice. For easy start, `OpenShift by RedHat <https://www.redhat.com/en/technologies/cloud-computing/openshift>`__ is a great choice with their 30 day free trial.
2. Docker image with H2O inside.
3. A Kubernetes deployment definition with a StatefulSet of H2O pods and a headless service.

After H2O is started, there must be a way for H2O nodes to "find" themselves and form a cluster. This is the role of the `headless service <https://kubernetes.io/docs/concepts/services-networking/service/#headless-services>`__. This approach works on all major Kubernetes clusters without any additional permissions required.

For reproducibility, resource limits and requests should always be set to equal values.

.. note::
  
  Official K8s images don’t apply any `security settings <../security.html>`__. The default user is ``root``. If you require a secure interface with H2O running the image, you need to change the user and execution command to add security parameters before running the image. The following is the default command used in Docker images:

::
  
  java -Djava.library.path=/opt/h2oai/h2o-3/xgb_lib_dir -XX:+UseContainerSupport -XX:MaxRAMPercentage=50 -jar /opt/h2oai/h2o-3/h2o.jar


Headless service
~~~~~~~~~~~~~~~~

.. code:: yaml

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

- ``clusterIP: None``: Defines the service as headless, and ``port: 54321`` is the default H2O port. Users and client libraries use this port to talk to the H2O cluster.
- ``app: h2o-k8s``: Set to the name of the application with H2O pods inside. Be sure this setting corresponds to the name of the chosen H2O deployment name.

StatefulSet
~~~~~~~~~~~

.. code:: yaml

  apiVersion: apps/v1
  kind: StatefulSet
  metadata:
    name: h2o-stateful-set
    namespace: ds-h2o
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
          image: h2oai/h2o-open-source-k8s:latest
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


Besides the standardized Kubernetes settings (e.g. replicas: 3 defining the number of pods with H2O instantiated), there are several settings to pay attention to:

- The **application name** (``app: h2o-k8s``) must correspond to the name expected by the above-defined headless service in order for the H2O node discovery to work. 
- H2O communicates on port 54321, therefore ``containerPort: 54321`` must be exposed to make it possible for the clients to connect.
- The **pod management policy** must be set to parallel: ``podManagementPolicy: "Parallel"``. This makes Kubernetes spawn all H2O nodes at once. If not specified, Kubernetes will spawn the pods with H2O nodes sequentially, one after another, significantly prolonging the startup process.

Native Kubernetes resources
~~~~~~~~~~~~~~~~~~~~~~~~~~~

H2O is able to discover other pods with H2O under the same service automatically by using the resources native to Kubernetes: services and environment variables.

Services
''''''''

In order to ensure reproducibility, all requests should be directed towards the H2O Leader node. Leader node election is done after the node discovery process is completed. Therefore, after the clustering is formed and the leader node is known, only the pod with the H2O leader node should be made available (ready). This makes the service(s) on top of the deployment route all requests only to the leader node. 

Once the clustering is done, all nodes but the leader node mark themselves as "not ready", leaving only the leader node exposed. The ``readinessProbe`` residing on ``/kubernetes/isLeaderNode`` makes sure only the leader node is exposed once the cluster is formed by making all nodes but the leader node "not available". 

The default port for H2O Kubernetes API is ``8080``. However, in the previous example, an optional environment variable changes the port to ``8081`` to demonstrate the functionality.

Environment variables
'''''''''''''''''''''

If none of the optional lookup constraints are specified, a sensible default node lookup timeout will be set (defaults to 3 minutes). If any of the lookup constraints are defined, the H2O node lookup is terminated on whichever condition is met first.

- ``H2O_KUBERNETES_SERVICE_DNS``: *Required* Crucial for clustering to work. This format usually follows the ``<service-name>.<project-namespace>.svc.cluster.local`` pattern. This setting enables H2O node discovery through DNS. It must be modified to match the name of the headless service you created. Be sure you also pay attention to the rest of the address: it needs to match the specifics of your Kubernetes implementation.
- ``H2O_NODE_LOOKUP_TIMEOUT``: Node lookup constraint. Specify the time before the node lookup times out.
- ``H2O_NODE_EXPECTED_COUNT``: Node lookup constraint. Specofu the expected number of H2O pods to be discovered.
- ``H2O_KUBERNETES_API_PORT``: Port for Kubernetes API checks to listen on (defaults to ``8080``). 

Expose H2O
~~~~~~~~~~

In order to expose H2O and make it available from the outside of the Kubernetes cluster, an Ingress is required. Some vendors provide custom resources to achieve the same goal (e.g.
`OpenShift and Routes <https://docs.openshift.com/container-platform/4.5/networking/ingress-operator.html#nw-ingress-sharding_configuring-ingress>`__). Path configuration, namespace and other Ingress attributes are always specific to your cluster specification.

The following example is for an Ingress:

.. code:: yaml

  apiVersion: networking.k8s.io/v1beta1
  kind: Ingress
  metadata:
    name: h2o-ingress
    namespace: <namespace-name>
  spec:
    rules:
    - http:
        paths:
        - path: /
          backend:
            serviceName: h2o-service
            servicePort: 80

Reproducibility notes
~~~~~~~~~~~~~~~~~~~~~

There are three key requirements to make sure actions invoked on H2O are reproducible:

1. Same amount of memory,
2. Same number of CPUs,
3. Client sends requests only to the H2O leader node.

In a Kubernetes environment, one common mistake is to set different resource quotas for ``requests`` and ``limits`` for a pod. If the underlying JVM running inside the docker image inside a pod uses certain percentage of memory available, that amount of memory might be different each time H2O starts, as Kubernetes might actually allocate different amount of memory every time. These same rules apply to CPU ``limits`` and ``requests``.

The ``readinessProbe`` residing on ``/kubernetes/isLeaderNode`` makes sure only the leader node is exposed once the cluster is formed by making all nodes but the leader node "not available". Without the readiness probe, reproducibility is not guaranteed.


Install H2O with Helm
---------------------

`Helm <https://helm.sh/>`__ can be used to deploy H2O into a kubernetes cluster. Helm requires setting up the KUBECONFIG environment variable properly or stating the KUBECONFIG destination explicitly. There are three steps required in order to use the official H2O Helm chart:

1. Add H2O Helm chart repository,
2. Use ``helm install`` to install H2O Open source to Kubernetes,
3. (Optional) test the installation.

.. code:: bash

  helm repo add h2o https://charts.h2o.ai --version |version|
  helm install basic-h2o h2o/h2o
  helm test basic-h2o


The basic command ``helm install basic-h2o h2o/h2o`` only installs a minimal H2O cluster with few resources. There are various settings and modifications available. To inspect a complete list of the configuration options available, use the  ``helm inspect values h2o/h2o --version |version|`` command.

Among the most common settings are number of H2O nodes (there is one pod per each H2O node) spawned, memory and CPU resources for each H2O node, and an ingress. The following is an example on how to configure these basic options.

.. code:: yaml

  h2o:
    nodeCount: 3
  resources:
    cpu: 12
    memory: 32Gi
  ingress:
    enabled: true
    annotations: {}
    hosts:
      - host: ""
        paths: ["/"]
    tls: []

