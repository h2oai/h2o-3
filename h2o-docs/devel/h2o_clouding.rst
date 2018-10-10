H2O Clouding Behaviour
----------------------

The purpose of this document is to document the behaviour of H2O clouding process. In H2O, there are two ways how a
cluster can be created:

- **multicast discovery**

  In this case we use multicast communication to form a cluster.

- **flatfile discovery**

  In this case we simulate multicast communication by manually specifying the IP addresses and ports
  of the nodes which can be part of the cloud. The flatfile is a simple text file with lines in a form of:

    - ip_A2:port_A2
    - ip_B1:port_B2

  In this mode, each node in the cluster needs to be started with the ``-flatfile`` option pointing to this file. This file does not need
  to have the same content on all the nodes, however, it needs to be ensured that the nodes can transitivelly discover each other. The same
  holds for the client node. The client node can contain flaffile with at least one node in the cluster (not self).

  The flatfile is automatically used when running H2O on Hadoop or in any Sparkling Water mode.

  Also it is important to say that the nodes in the flatfile does not automatically form a cluster. They just specify the nodes
  we can communicate with, but the cluster is created independently.

Initialization of local node
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Each cluster is represented by a name specified by ``-name`` configuration option. If this option is not specified, then
``System.getProperty("user.name")`` is used as default. It is also set to unique name when running H2O on hadoop using
``hadoop jar h2odriver.jar``

The communication is allowed only between the nodes with the same cluster name. If any node hears from a node from a different
cluster(cluster with different name) it discards the data and does not perform any further actions. This way we ensure the
cluster isolation.

This also applies to stopping the H2O cluster, which means that it is not possible for a node from different cluster to kill
different cluster. The kill request needs to come from the same cluster.

The H2O cloud consist of H2O worker nodes, which needs to remain available during the whole lifetime of the cluster. The cluster
can also have H2O clients connected to it. They are not part of the cluster and are not used for the distributed computations.
The clients can be used to drive the computation on the worker nodes. For example, the major usage of H2O client is in Sparkling Water.


Each node performs its initialization process. First, we decide whether we should be running on IPV4 stack or IPV6 stack.
This is configured by ``IS_IPV6`` static property of ``H2O`` class as
``IS_IPV6 = NetworkUtils.isIPv6Preferred() && !NetworkUtils.isIPv4Preferred()``. Furthemore, the IP address of this node
is determined in the ``H2O.main`` method and is specified as ``SELF_ADDRESS``. The ``InetAddress`` is searched for based
on the passed options to the H2O program - mainly ``-ip`` and ``-network``. These two options are exclusive and can't be
used both at the same time. See ``water.init.HostnameGuesser`` for the full details on how the address is determined. This
has, for example, effect when H2O node is connected to multiple networks and we try, using the heuristic mentioned in the class
above, find the best public network.

After IP stack and IP address have been determined, H2O initializes the networking.
The network initialization is specified in the ``H2O.startLocalNode`` static method. This method first calls
``NetworkInit.initializeNetworkSockets()`` method which:

- creates the network sockets and opens two ports, one for public API and one for internal communication
- in case ``-flatfile`` option was passed to the node, the flatfile is parsed. If the flatfile is not empty, we add all nodes
  from the flatfile to the ``STATIC_H2OS`` property and mark the communication mode as flatfile. If the flatfile is empty or the option is not specified,
  we keep ``STATIC_H2OS`` set to ``null`` and continue operating in multicast mode

The ``startLocalNode`` method further:

- initializes the heartbeat with cloud name and bunch of other required properties so we can be sure we communicate only with the nodes in the same cluster
- in case of the node is client (``-client``), we report ourselves as the client to ourselves. This is important for the consistency in cases
  we need perform some operations on all the clients, including me.

The network communication is initialized in the ``H2O.startNetworkServices`` method. This method starts the sockets and starts listening on
the defined ports. However we still don't send any heartbeats so the different nodes can't still hear from us. The next step is to announce ourselves
 in the ``Paxos.doHeartbeat`` method which, at this point, creates a cluster of size 1. The final step
is to start the heartbeat thread so the rest of the nodes can hear from us and the cloud can be created.

Clouding
~~~~~~~~

Clouding is handled inside the ``Paxos.doHeartbeat`` method. This method always first checks whether we are communication with
the node in the same cluster and exits early if we are not -> therefore we can't cloud up with the nodes from different clusters.

In case of multicast, the discovery of the nodes is automatic via the multicast network protocol, however, in case
of flatfile, we still need to handle some cases to ensure all nodes have consistent list of ``STATIC_H2OS``. Also in both, multicast
and flatfile, we need to ensure that in case the client is connected to the cluster, all nodes in the cluster
have the same information about the client before they report back to the client. This can be summarized in the invariant:

    A node which never heard about the client, can't never contact the client about it's existence.

Therefore, when a client hears from a node, we can be sure that this client is known on that node.

In case of multicast, we just report the client and make it part of the list of available clients to this node. In case of flatfile need to do two operations:

- In case we received message from a client, we are not a client and the information about client is still missing in the list of ``STATIC_H2OS``,
  we broadcast the information about the client to the rest of the nodes (and current node as well) via the ``ClientEvent``. During the initialization,
  this broadcast can be triggered multiple times in the network because the client can have more entries in the flatfile and therefore more initial nodes
  to start communication with. When a node receives the client event, it adds the client to it's list of ``STATIC_H2OS``, reports the client to its list of clients
  and also correctly sets up the client heartbeat so we have correct information about the client.

- In case we received message from a regular node and we are a client, we just add the node to the list of ``STATIC_H2OS``.

Once we correctly reported the clients and the nodes, we need to continue with the clouding process as at this point, we just
reported the nodes, we didn't create a new version of a cloud.

It is also important to mention that the first distribution write operation always trigger lock clouding. That means that cluster shape
can change, but after the first distributed write task, the new nodes are ignored. To following invariant describes the effect of client joining
the cloud on the locking.

    The process of discovering the client when a new client connects to the cluster does not trigger cloud locking.



The ``Paxos`` class is also using ``PROPOSED`` hash map to help with the clouding process. This map is empty at the start of the local node.


If the node, from which we have received a heartbeat message inside the ``doHeartBeat`` method, is not client and is not in the ``PROPOSED`` map,
we do several operations.

- if the cloud is already locked, we ignore the request and send kill message to the incoming H2O node
- if the cloud is not locked, but we already have the common cloud knowledge (represented as ``boolean commonKnowledge`` field in ``Paxos`` class), we reset the common knowledge to false as we
  have a new node joining (this node knows about the node, but the rest of the nodes might not, we don't have common knowledge anymore). We also start announcing the false common knowledge as
  part of the heartbeat so the rest of the nodes can acct accordingly. Then, we store the node into the ``PROPOSED`` field and update the cloud hash field in our heartbeat (we take the cloud hash from the newly
  incoming node)

It is valid that we don't do the same handling for the incoming client node, as the client nodes are not part of the client adn thus can't never be location in the ``PROPOSED`` map. If the node is not client,
is already in the ``PROPOSED`` map and we report positive common knowledge we can exit with positive result as there is no more work for us to do.

Therefore the next handling only continues if there is a new incoming node and we don't have common knowledge. We create a new cluster based on nodes stored in the ``PROPOSED`` map
If we are client and the resulting cloud is empty, we exit positively and try it with the next request as we need to discover some nodes, otherwise it's not relevant for the client to continue.
After these checks, we set new cloud - new nodes and new hash.

The final operations just checks that all nodes in this new cloud report the same size and common knowledge is set to positive value. If we are running H2O in some embedded software (such as, Sparkling Water) we report the new cluster size there as well.
