Clusters
--------

**When trying to launch H2O, I received the following error message:
``ERROR: Too many retries starting cloud.`` What should I do?**

If you are trying to start a multi-node cluster where the nodes use
multiple network interfaces, by default H2O will resort to using the
default host (127.0.0.1).

To specify an IP address, launch H2O using the following command:

``java -jar h2o.jar -ip <IP_Address> -port <PortNumber>``

If this does not resolve the issue, try the following additional
troubleshooting tips:

-  Confirm your internet connection is active.
-  Test connectivity using curl: First, log in to the first node and
   enter curl http://:54321 (where is the IP address of the second node.
   Then, log in to the second node and enter curl http://:54321 (where
   is the IP address of the first node). Look for output from H2O.
-  Confirm ports 54321 and 54322 are available for both TCP and UDP.
-  Confirm your firewall is not preventing the nodes from locating each
   other.
-  Confirm the nodes are not using different versions of H2O.
-  Confirm that the username is the same on all nodes; if not, define
   the cloud in the terminal when launching using
   ``-name``:``java -jar h2o.jar -name myCloud``.
-  Confirm that the nodes are not on different networks.
-  Check if the nodes have different interfaces; if so, use the -network
   option to define the network (for example, ``-network 127.0.0.1``).
-  Force the bind address using
   ``-ip``:``java -jar h2o.jar -ip <IP_Address> -port <PortNumber>``.
-  (Linux only) Check if you have SELINUX or IPTABLES enabled; if so,
   disable them.
-  (EC2 only) Check the configuration for the EC2 security group.

--------------

**What should I do if I tried to start a cluster but the nodes started
independent clouds that are not connected?**

Because the default cloud name is the user name of the node, if the
nodes are on different operating systems (for example, one node is using
Windows and the other uses OS X), the different user names on each
machine will prevent the nodes from recognizing that they belong to the
same cloud. To resolve this issue, use ``-name`` to configure the same
name for all nodes.

--------------

**One of the nodes in my cluster is unavailable — what do I do?**

H2O does not support high availability (HA). If a node in the cluster is
unavailable, bring the cluster down and create a new healthy cluster.

--------------

**How do I add new nodes to an existing cluster?**

New nodes can only be added if H2O has not started any jobs. Once H2O
starts a task, it locks the cluster to prevent new nodes from joining.
If H2O has started a job, you must create a new cluster to include
additional nodes.

--------------

**How do I check if all the nodes in the cluster are healthy and
communicating?**

In the Flow web UI, click the **Admin** menu and select **Cluster
Status**.

--------------

**How do I create a cluster behind a firewall?**

H2O uses two ports:

-  The ``REST_API`` port (54321): Specify when launching H2O using
   ``-port``; uses TCP only.
-  The ``INTERNAL_COMMUNICATION`` port (54322): Implied based on the
   port specified as the ``REST_API`` port, +1; requires TCP and UDP.

You can start the cluster behind the firewall, but to reach it, you must
make a tunnel to reach the ``REST_API`` port. To use the cluster, the
``REST_API`` port of at least one node must be reachable.

--------------

**How can I create a multi-node H2O cluster on a SLURM system?**

The syntax below comes from `https://github.com/ck37/savio-notes/blob/master/h2o-slurm-multinode.Rmd <https://github.com/ck37/savio-notes/blob/master/h2o-slurm-multinode.Rmd>`__ and describes how to create a multi-node H2O cluster on a Simple Linux Utility for Resource Management (SLURM) system using R. 

::

    # Check on the nodes we have access to.
    node_list = Sys.getenv("SLURM_NODELIST")
    cat("SLURM nodes:", node_list, "\n")

    # Loop up IPs of the allocated nodes.
    if (node_list != "") {
      nodes = strsplit(node_list, ",")[[1]]
      ips = rep(NA, length(nodes))
      for (i in 1:length(nodes)) {
        args = c("hosts", nodes[i])
        result = system2("getent", args = args, stdout = T)
        # Extract the IP from the result output.
        ips[i] = sub("^([^ ]+) +.*$", "\\1", result, perl = T)
      }
      cat("SLURM IPs:", paste(ips, collapse=", "), "\n")
      # Combine into a network string for h2o.
      network = paste0(paste0(ips, "/32"), collapse=",")
      cat("Network:", network, "\n")
    }

    # Specify how many nodes we want h2o to use.
    h2o_num_nodes = length(ips)

    # Options to pass to java call:
    args = c(
      # -Xmx30g allocate 30GB of RAM per node. Needs to come before "-jar"
      "-Xmx30g",
      # Specify path to downloaded h2o jar.
      "-jar ~/software/h2o-latest/h2o.jar",
      # Specify a cloud name for the cluster.
      "-name h2o_r",
      # Specify IPs of other nodes.
      paste("-network", network)
    )
    cat(paste0("Args:\n", paste(args, collapse="\n"), "\n"))

    # Run once for each node we want to start.
    for (node_i in 1:h2o_num_nodes) {
      cat("\nLaunching h2o worker on", ips[node_i], "\n")
      new_args = c(ips[node_i], "java", args)
      # Ssh into the target IP and launch an h2o worker with its own
      # output and error files. These could go in a subdirectory.
      cmd_result = system2("ssh", args = new_args,
                           stdout = paste0("h2o_out_", node_i, ".txt"),
                           stderr = paste0("h2o_err_", node_i, ".txt"),
                           # Need to specify wait=F so that it runs in the background.
                           wait = F)
      # This should be 0.
      cat("Cmd result:", cmd_result, "\n")
      # Wait one second between inits.
      Sys.sleep(1L)
    }

    # Wait 3 more seconds to find all the nodes, otherwise we may only
    # find the node on localhost.
    Sys.sleep(3L)

    # Check if h2o is running. We will see ssh processes and one java process.
    system2("ps", c("-ef", "| grep h2o.jar"), stdout = T)

    suppressMessages(library(h2oEnsemble))

    # Connect to our existing h2o cluster.
    # Do not try to start a new server from R.
    h2o.init(startH2O = F)

    #################################

    # Run H2O commands here.

    #################################
    h2o.shutdown(prompt = F)

--------------

**I launched H2O instances on my nodes - why won't they form a cloud?**

If you launch without specifying the IP address by adding argument -ip:

``$ java -Xmx20g -jar h2o.jar -flatfile flatfile.txt -port 54321``

and multiple local IP addresses are detected, H2O uses the default
localhost (127.0.0.1) as shown below:

::

  10:26:32.266 main      WARN WATER: Multiple local IPs detected:
  +                                    /198.168.1.161  /198.168.58.102
  +                                  Attempting to determine correct address...
  10:26:32.284 main      WARN WATER: Failed to determine IP, falling back to localhost.
  10:26:32.325 main      INFO WATER: Internal communication uses port: 54322
  +                                  Listening for HTTP and REST traffic
  +                                  on http://127.0.0.1:54321/
  10:26:32.378 main      WARN WATER: Flatfile configuration does not include self:
  /127.0.0.1:54321 but contains [/192.168.1.161:54321, /192.168.1.162:54321]

To avoid using 127.0.0.1 on servers with multiple local IP addresses,
run the command with the -ip argument to force H2O to launch at the
specified IP:

``$ java -Xmx20g -jar h2o.jar -flatfile flatfile.txt -ip 192.168.1.161 -port 54321``

--------------

**How does the timeline tool work?**

The timeline is a debugging tool that provides information on the
current communication between H2O nodes. It shows a snapshot of the most
recent messages passed between the nodes. Each node retains its own
history of messages sent to or received from other nodes.

H2O collects these messages from all the nodes and orders them by
whether they were sent or received. Each node has an implicit internal
order where sent messages must precede received messages on the other
node.

The following information displays for each message:

-  ``HH:MM:SS:MS`` and ``nanosec``: The local time of the event
-  ``Who``: The endpoint of the message; can be either a source/receiver
   node or source node and multicast for broadcasted messages
-  ``I/O Type``: The type of communication (either UDP for small
   messages or TCP for large messages) >\ **Note**: UDP messages are
   only sent if the UDP option was enabled when launching H2O or for
   multicast when a flatfile is not used for configuration.
-  ``Event``: The type of H2O message. The most common type is a
   distributed task, which displays as ``exec`` (the requested task) ->
   ``ack`` (results of the processed task) -> ``ackck`` (sender
   acknowledges receiving the response, task is completed and removed)
-  ``rebooted``: Sent during node startup
-  ``heartbeat``: Provides small message tracking information about node
   health, exchanged periodically between nodes
-  ``fetchack``: Aknowledgement of the ``Fetch`` type task, which
   retrieves the ID of a previously unseen type
-  ``bytes``: Information extracted from the message, including the type
   of the task and the unique task number
