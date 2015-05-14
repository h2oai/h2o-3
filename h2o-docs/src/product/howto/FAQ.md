#FAQ

**How do I score using an exported JSON model?**

Since JSON is just a representation format, it cannot be directly executed, so a JSON export can't be used for scoring. However, you can score by: 

- including the POJO in your execution stream and handing it observations one at a time 

or

- handing your data in bulk to an H2O cluster, which will score using high throughput parallel and distributed bulk scoring. 


---

**How do I predict using multiple response variables?**

Currently, H2O does not support multiple response variables. To predict different response variables, build multiple modes. 

---

**How do I kill any running instances of H2O?**

In Terminal, enter `ps -efww | grep h2o`, then kill any running PIDs. 

---


##Clusters


**When trying to launch H2O, I received the following error message: `ERROR: Too many retries starting cloud.` What should I do?**

If you are trying to start a multi-node cluster where the nodes use multiple network interfaces, by default H2O will resort to using the default host (127.0.0.1). 

To specify an IP address, launch H2O using the following command: 

`java -jar h2o.jar -ip <IP_Address> -port <PortNumber>`

If this does not resolve the issue, try the following additional troubleshooting tips: 

- Test connectivity using `curl`: First, log in to the first node and enter `curl http://<Node2IP>:54321` (where `<Node2IP>` is the IP address of the second node. Then, log in to the second node and enter `curl http://<Node1IP>:54321` (where `<Node1IP>` is the IP address of the first node). Look for output from H2O. 
- Confirm ports 54321 and 54322 are available for both TCP and UDP. 
- Confirm your firewall is not preventing the nodes from locating each other. 
- Check if you have SELINUX or IPTABLES enabled; if so, disable them.  
- Check the configuration for the EC2 security group.
- Confirm that the username is the same on all nodes; if not, define the cloud using `-name`. 
- Check if the nodes are on different networks. 
- Check if the nodes have different interfaces; if so, use the `-network` option to define the network (for example, `-network 127.0.0.1`). 
- Force the bind address using `-ip`. 
- Confirm the nodes are not using different versions of H2O. 

---

**What should I do if I tried to start a cluster but the nodes started independent clouds that are not connected?**

Because the default cloud name is the user name of the node, if the nodes are on different operating systems (for example, one node is using Windows and the other uses OS X), the different user names on each machine will prevent the nodes from recognizing that they belong to the same cloud. To resolve this issue, use `-name` to configure the same name for all nodes. 

---

**One of the nodes in my cluster is unavailable - what do I do?**

H2O does not support high availability (HA). If a node in the cluster is unavailable, bring the cluster down and create a new healthy cluster. 

---

**How do I add new nodes to an existing cluster?**

New nodes can only be added if H2O has not started any jobs. Once H2O starts a task, it locks the cluster to prevent new nodes from joining. If H2O has started a job, you must create a new cluster to include additional nodes. 

---

**How do I check if all the nodes in the cluster are healthy and communicating?**

In the Flow web UI, click the **Admin** menu and select **Cluster Status**. 

---

**How do I create a cluster behind a firewall?**

H2O uses two ports: 

- The `REST_API` port (54321): Specify when launching H2O using `-port`; uses TCP only. 
- The `INTERNAL_COMMUNICATION` port (54322): Implied based on the port specified as the `REST_API` port, +1; requires TCP and UDP. 

You can start the cluster behind the firewall, but to reach it, you must make a tunnel to reach the `REST_API` port. To use the cluster, the `REST_API` port of at least one node must be reachable. 

---

**How do I specify which nodes should run H2O in a Hadoop cluster?**

Currently, this is not yet supported. To provide resource isolation (for example, to isolate H2O to the worker nodes, rather than the master nodes), use YARN Nodemanagers to specify the nodes to use. 

---

##Sparkling Water

**How do I inspect H2O using Flow while a droplet is running?**

If your droplet execution time is very short, add a simple sleep statement to your code: 

`Thread.sleep(...)`

---

**How do I change the memory size of the executors in a droplet?**

There are two ways to do this: 

- Change your default Spark setup in `$SPARK_HOME/conf/spark-defaults.conf`

  or 

- Pass `--conf` via spark-submit when you launch your droplet (e.g., `$SPARK_HOME/bin/spark-submit --conf spark.executor.memory=4g --master $MASTER --class org.my.Droplet $TOPDIR/assembly/build/libs/droplet.jar`

---

  