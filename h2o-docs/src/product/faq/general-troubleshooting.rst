General Troubleshooting Tips
----------------------------

-  Confirm your internet connection is active.

-  Test connectivity using curl: First, log in to the first node and
   enter ``curl http://<Node2IP>:54321`` (where ``<Node2IP>`` is the IP
   address of the second node. Then, log in to the second node and enter
   ``curl http://<Node1IP>:54321`` (where ``<Node1IP>`` is the IP
   address of the first node). Look for output from H2O.

-  Try allocating more memory to H2O by modifying the ``-Xmx`` value
   when launching H2O from the command line (for example,
   ``java -Xmx10g -jar h2o.jar`` allocates 10g of memory for H2O). If
   you create a cluster with four 20g nodes (by specifying ``-Xmx20g``
   four times), H2O will have a total of 80 gigs of memory available.
   For best performance, we recommend sizing your cluster to be about
   four times the size of your data. To avoid swapping, the ``-Xmx``
   allocation must not exceed the physical memory on any node.
   Allocating the same amount of memory for all nodes is strongly
   recommended, as H2O works best with symmetric nodes.

-  Confirm that no other sessions of H2O are running. To stop all
   running H2O sessions, enter ``ps -efww | grep h2o`` in your shell
   (OSX or Linux).
-  Confirm ports 54321 and 54322 are available for TCP.
   Launch Telnet (for Windows users) or Terminal (for OS X users), then
   type ``telnet localhost 54321``, ``telnet localhost 54322``
-  Confirm your firewall is not preventing the nodes from locating each
   other. If you can't launch H2O, we recommend temporarily disabling
   any firewalls until you can confirm they are not preventing H2O from
   launching.
-  Confirm the nodes are not using different versions of H2O. If the H2O
   initialization is not successful, look at the output in the shell -
   if you see
   ``Attempting to join /localhost:54321 with an H2O version mismatch (md5 differs)``,
   update H2O on all the nodes to the same version.
-  Confirm that there is space in the ``/tmp`` directory.

   -  Windows: In Command Prompt, enter ``TEMP`` and ``%TEMP%`` and
      delete files as needed, or use Disk Cleanup.
   -  OS X: In Terminal, enter ``open $TMPDIR`` and delete the folder
      with your username.

-  Confirm that the username is the same on all nodes; if not, define
   the cloud in the terminal when launching using
   ``-name``:``java -jar h2o.jar -name myCloud``.
-  Confirm that there are no spaces in the file path name used to launch
   H2O.
-  Confirm that the nodes are not on different networks by confirming
   that the IP addresses of the nodes are the same in the output:

      ::

         INFO: Listening for HTTP and REST traffic on  IP_Address/ 06-18 10:54:21.586 192.168.1.70:54323    25638  main       INFO: H2O cloud name: 'H2O_User' on IP_Address, discovery address /Discovery_Address INFO: Cloud of size 1 formed [IP_Address]

-  Check if the nodes have different interfaces; if so, use the -network
   option to define the network (for example, ``-network 127.0.0.1``).
   To use a network range, use a comma to separate the IP addresses (for
   example, ``-network 123.45.67.0/22,123.45.68.0/24``).
-  Force the bind address using
   ``-ip``:``java -jar h2o.jar -ip <IP_Address> -port <PortNumber>``.
-  (Hadoop only) Try launching H2O with a longer timeout:
   ``hadoop jar h2odriver.jar -timeout 1800``
-  (Hadoop only) Try to launch H2O using more memory:
   ``hadoop jar h2odriver.jar -mapperXmx 10g``. The cluster’s memory
   capacity is the sum of all H2O nodes in the cluster.
-  (Linux only) Check if you have SELINUX or IPTABLES enabled; if so,
   disable them.
-  (EC2 only) Check the configuration for the EC2 security group.

--------------

**The following error message displayed when I tried to launch H2O -
what should I do?**

::

    Exception in thread "main" java.lang.UnsupportedClassVersionError: water/H2OApp
    : Unsupported major.minor version 51.0
            at java.lang.ClassLoader.defineClass1(Native Method)
            at java.lang.ClassLoader.defineClassCond(Unknown Source)
            at java.lang.ClassLoader.defineClass(Unknown Source)
            at java.security.SecureClassLoader.defineClass(Unknown Source)
            at java.net.URLClassLoader.defineClass(Unknown Source)
            at java.net.URLClassLoader.access$000(Unknown Source)
            at java.net.URLClassLoader$1.run(Unknown Source)
            at java.security.AccessController.doPrivileged(Native Method)
            at java.net.URLClassLoader.findClass(Unknown Source)
            at java.lang.ClassLoader.loadClass(Unknown Source)
            at sun.misc.Launcher$AppClassLoader.loadClass(Unknown Source)
            at java.lang.ClassLoader.loadClass(Unknown Source)
    Could not find the main class: water.H2OApp. Program will exit.

This error output indicates that your Java version is not supported.
Upgrade to `Java 7
(JVM) <http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html>`__
or
`later <http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html>`__
and H2O should launch successfully.

--------------

**I am not launching on Hadoop. How can I increase the amount of time that H2O allows for expected nodes to connect?**

For cluster startup, if you are not launching on Hadoop, then you will not need to specify a timeout. You can add additional nodes to the cloud as long as you haven't submitted any jobs to the cluster. When you do submit a job to the cluster, the cluster will lock and will print a message similar to `"Locking cloud to new members, because <reason>..."`.

--------------

**Occasionally I receive an "out of memory" error. Is there a method based on the trees/depth/cross-validations that is used to determine how much memory is needed to store the model?**

We normally suggest 3-4 times the size of the dataset for the amount of memory required. It's difficult to calculate the exact memory footprint for each case because running with a different solver or with different parameters can change the memory footprint drastically. In GLM for example, there's an internal heuristic for checking the estimated memory needed:

`https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/glm/GLM.java#L182 <https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/glm/GLM.java#L182>`__

In this particular case, if you use IRLSM or Coordinate Descent, the memory footprint is roughly in bytes:

  (number of predictors expanded)^2 * (# of cores on one machine) * 4 

And if you run cross validation with, for example, two-fold xval, then the memory footprint would be double this number; three-fold would be triple, etc.

For GBM and Random Forest, another heuristic is run by checking how much memory is needed to hold the trees. Additionally, the number of histograms that are calculated per predictor also matters. 

Depending on the user-specified predictors, max_depth per tree, the number of trees, nbins per histogram, and the number of classes (binomial vs multinomial), memory consumption will vary:

- Holding trees: 2^max_depth * nclasses * ntrees * 10 (or the avg bytes/element)
- Computing histograms: (num_predictors) * nbins * 3 (histogram/leaf) * (2^max_depth) * nclasses * 8

--------------

**What's the best approach to help diagnose a possible memory problem on a cluster?**

We've found that the best way to understand JVM memory consumption is to turn on specific flags. These flags differ depending on your Java version.

For Java version < 10, the following flags are available:

::

   -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps

You can then use the following tool to analyze the output: http://www.tagtraum.com/gcviewer-download.html

Since Java 9, the previously metioned flags have been marked as deprecated and are completely removed in Java version 10 and newer. The following flag may be used instead.

::

   -Xlog:gc=info

--------------

**How can I debug memory issues?**

We recommend the following approach using R to debug memory issues:

::

   my for loop {
    # perform loop

    rm(R object that isn’t needed anymore)
    rm(R object of h2o thing that isn’t needed anymore)

    # trigger removal of h2o back-end objects that got rm’d above, since the rm can be lazy.
    gc()
    # optional extra one to be paranoid.  this is usually very fast.
    gc()

    # optionally sanity check that you see only what you expect to see here, and not more.
    h2o.ls()

    # tell back-end cluster nodes to do three back-to-back JVM full GCs.
    h2o:::.h2o.garbageCollect()
    h2o:::.h2o.garbageCollect()
    h2o:::.h2o.garbageCollect()
   }

Note that the ``h2o.garbageCollct()`` function works as follows:

::

   # Trigger an explicit garbage collection across all nodes in the H2O cluster.
   .h2o.garbageCollect <- function() {
     res <- .h2o.__remoteSend("GarbageCollect", method = "POST")
   }


This tells the backend to do a forcible full-GC on each node in the H2O cluster. Doing three of them back-to-back makes it stand out clearly in the gcviewer chart where the bottom-of-inner loop is. You can then correlate what you expect to see with the X (time) axis of the memory utilization graph. 

At this point you want to see if the bottom trough of the usage is growing from iteration to iteration after the triple full-GC bars in the graph. If the trough is not growing from iteration to iteration, then there is no leak; your usage is just really too much, and you need a bigger heap. If the trough is growing, then there is likely some kind of leak. You can try to use ``h2o.ls()`` to learn where the leak is. If ``h2o.ls()`` doesn't help, then you will have to drill much deeper using, for example, YourKit and reviewing the JVM-level heap profiles. 

--------------

**Is there a way to clear everything from H2O (including H2OFrames/Models)?**

You can open Flow and select individual items to delete from H2O, or you can run the following to remove everything from H2O:

::

    import water.api.RemoveAllHandler
    new RemoveAllHandler().remove(3,new RemoveAllV3())

    
