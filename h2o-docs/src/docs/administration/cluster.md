# Admin: Cluster Status
The status and location of a cluster can be verified by selecting
Cluster Status from the Admin drop down menu.

**Table Definitions:**
In the provided table each node in an H2O cloud has a
row of information.

**Name**
The name of the node. For example, if a user establishes three
nodes on different servers, then Name will display the IP address
and port in use for talking to each of those unique nodes.

**Num Keys**
The number of keys in the distributed value store.

**Value size bytes**
The aggregate size in bytes of all data on that
node (including the set or subset of a users parsed data, but also
the size in bytes of the information stored in the keys generated
from modeling or data manipulation.)

**Free men bytes**
The amount of free memory in the H2O node.

**Tot mem bytes**
The total amount of memory in the H2O node. This value may vary
over time depending on use.

**Max men bytes**
The maximum amount of memory that the H2O node will
attempt to use.

**Free disk bytes**
The amount of free memory in the ice root. When memory needs exceed
the capacity of the node, the overflow is handled by ice root, the
H2O corollary to disk memory.

**Max disk bytes**
The maximum amount of memory that can be used from ice root.

**Num cpus**
The number of cores being used by the node

**System Load**
The amount of computational work currently being carried out by the
cluster.

**Node Healthy**
Indiacates whether the node indicated by the row of the cluster
status table is healthy.

**PID**
Process ID number.

**Last contact**
The last time a specific node relayed communication about its
status. Last contact should read "now" or some number less than 30
seconds. If last contact is indicated to be more than 30 seconds
ago, the node may be experiencing a failure.

*Note* Definitions for Fj threads hi, Fj threads low, Fj queue hi, Fj queue
low, RCPS, and TCPS Active have been omitted. These fields are
primarily designed for by H2O programmers, and are in development. It
is likely that they will be removed in a future revision.

