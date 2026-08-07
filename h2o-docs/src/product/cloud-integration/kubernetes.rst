Using H2O-3 with Kubernetes
===========================

H2O-3 software stack
--------------------

To understand the behavior and limitations of an H2O-3 distributed cluster, you need to understand the basics of H2O-3's design. The H2O-3 cluster is stateful: if one H2O-3 node is terminated, the cluster is immediately recognized as unhealthy and must be restarted as a whole.

This means H2O-3 nodes must be treated as stateful by Kubernetes. In Kubernetes, a set of pods sharing a common state is handled by a `StatefulSet <https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/>`__. Kubernetes tooling for stateless applications is not applicable to H2O-3. The proper way to deploy H2O-3 is to set ``kind: StatefulSet``. A StatefulSet ensures that:

- H2O-3 nodes are treated as a single unit, being brought up and down gracefully and together.
- The Kubernetes healthcheck (if defined) does not attempt to restart individual H2O-3 nodes in case of an error.
- Persistent storage and volumes associated with the StatefulSet of H2O-3 nodes are not deleted once the cluster is brought down.


H2O-3 Kubernetes deployment
---------------------------

To spawn an H2O-3 cluster inside a Kubernetes cluster, the following requirements must be met:

1. A Kubernetes cluster. For local development, k3s works well. OpenShift by Red Hat offers a 30-day free trial.
2. A Docker image with H2O-3 inside.
3. A Kubernetes deployment definition with a StatefulSet of H2O-3 pods and a headless service.

After H2O-3 is started, there must be a way for H2O-3 nodes to "find" themselves and form a cluster. This is the role of the `headless service <https://kubernetes.io/docs/concepts/services-networking/service/#headless-services>`__. This approach works on all major Kubernetes clusters without any additional permissions required.

For reproducibility, resource limits and requests should always be set to equal values.

.. note::

    Official Kubernetes images don't apply any `security settings <../security.html>`__. The default user is ``root``. If you require a secure interface with the H2O-3 image, you must change the user and execution command to add security parameters before running the image. The following is the default command used in Docker images:

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

The ``clusterIP: None`` defines the service as headless, and ``port: 54321`` is the default H2O-3 port. You and your client libraries use this port to talk to the H2O-3 cluster.

The ``app: h2o-k8s`` setting is the name of the application with H2O-3 pods inside. Make sure this setting matches the name of your chosen H2O-3 deployment.

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


Besides the standardized Kubernetes settings (for example, ``replicas: 3``, which defines the number of pods with H2O-3 instantiated), pay attention to the following settings:

- The **application name** (``app: h2o-k8s``) must match the name expected by the headless service defined above for H2O-3 node discovery to work.
- H2O-3 communicates on port ``54321``, so ``containerPort: 54321`` must be exposed to make it possible for clients to connect.
- The **pod management policy** must be set to parallel: ``podManagementPolicy: "Parallel"``. This makes Kubernetes spawn all H2O-3 nodes at once. If not specified, Kubernetes spawns the pods with H2O-3 nodes sequentially, one after another, significantly prolonging the startup process.

Native Kubernetes resources
~~~~~~~~~~~~~~~~~~~~~~~~~~~

H2O-3 can discover other pods with H2O-3 under the same service automatically by using resources native to Kubernetes: services and environment variables.

Services
''''''''

To ensure reproducibility, all requests should be directed toward the H2O-3 leader node. Leader node election happens after the node discovery process completes. Therefore, after the cluster is formed and the leader node is known, only the pod with the H2O-3 leader node should be made available (ready). This makes the service(s) on top of the deployment route all requests only to the leader node.

Once the cluster is formed, all nodes except the leader node mark themselves as "not ready", leaving only the leader node exposed. The ``readinessProbe`` residing on ``/kubernetes/isLeaderNode`` ensures that only the leader node is exposed once the cluster is formed by making all nodes except the leader node "not available".

The default port for the H2O-3 Kubernetes API is 8080. However, in the example, an optional environment variable changes the port to 8081 to demonstrate the functionality.

Environment variables
'''''''''''''''''''''

If none of the optional lookup constraints are specified, a sensible default node lookup timeout is set (defaults to 3 minutes). If any of the lookup constraints are defined, the H2O-3 node lookup terminates on whichever condition is met first.

1. ``H2O_KUBERNETES_SERVICE_DNS`` — **[MANDATORY]** Crucial for clustering to work. The format usually follows the ``<service-name>.<project-name>.svc.cluster.local`` pattern. This setting enables H2O-3 node discovery via DNS. It must be modified to match the name of the headless service created. Also, pay attention to the rest of the address to match the specifics of your Kubernetes implementation.
2. ``H2O_NODE_LOOKUP_TIMEOUT`` — **[OPTIONAL]** Node lookup constraint. Specifies the time before the node lookup times out.
3. ``H2O_NODE_EXPECTED_COUNT`` — **[OPTIONAL]** Node lookup constraint. The expected number of H2O-3 pods to be discovered (should be equal to the number of replicas).
4. ``H2O_KUBERNETES_API_PORT`` — **[OPTIONAL]** Port for Kubernetes API checks and probes to listen on. Defaults to 8080.

Exposing H2O-3
~~~~~~~~~~~~~~

To expose H2O-3 and make it available from outside the Kubernetes cluster, an Ingress is required. Some vendors provide custom resources to achieve the same goal (for example, `OpenShift and Routes <https://docs.openshift.com/container-platform/4.5/networking/ingress-operator.html#nw-ingress-sharding_configuring-ingress>`__). The following is an example Ingress definition. Path configuration, namespace, and other Ingress attributes are always specific to your cluster specification.

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

There are three key requirements for ensuring that actions invoked on H2O-3 are reproducible:

1. The same amount of memory.
2. The same number of CPUs.
3. The client sends requests only to the H2O-3 leader node.

In a Kubernetes environment, one common mistake is to set different resource quotas for ``requests`` and ``limits`` for a pod. If the underlying JVM running inside the Docker image inside a pod uses a certain percentage of available memory, that amount of memory might be different each time H2O-3 starts, because Kubernetes might allocate a different amount of memory every time. The same rules apply to CPU ``limits`` and ``requests``.

The ``readinessProbe`` residing on ``/kubernetes/isLeaderNode`` ensures that only the leader node is exposed once the cluster is formed by making all nodes except the leader node "not available". Without the readiness probe, reproducibility is not guaranteed.


Installing H2O-3 with Helm
~~~~~~~~~~~~~~~~~~~~~~~~~~

`Helm <https://helm.sh/>`__ can be used to deploy H2O-3 into a Kubernetes cluster. Helm requires that you set up the ``KUBECONFIG`` environment variable properly or that you state the ``KUBECONFIG`` destination explicitly. There are three steps required to use the official H2O-3 Helm chart:

1. Add the H2O-3 Helm chart repository.
2. Use ``helm install`` to install H2O-3 open source to Kubernetes.
3. (Optional) Test the installation.

.. code:: bash

  helm repo add h2o https://charts.h2o.ai --version |version|
  helm install basic-h2o h2o/h2o
  helm test basic-h2o


The basic command ``helm install basic-h2o h2o/h2o`` only installs a minimal H2O-3 cluster with few resources. There are various settings and modifications available. To inspect a complete list of the configuration options available, use the ``helm inspect values h2o/h2o --version |version|`` command.

Among the most common settings are the number of H2O-3 nodes (there is one pod per H2O-3 node) spawned, memory and CPU resources for each H2O-3 node, and an ingress. The following example configures node count, memory, CPU, and ingress.

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
