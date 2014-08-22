# H2O on a Multi-Node Cluster

The purpose of this walk-through is to show users how to set up
an H2O multi-node cluster. Begin by locating a set of hosts to make up our cluster (a host could be a server, an EC2 instance, or your laptop.)

**STEP 1**

To download H2O, including the .jar, go to
the H2O [downloads page](http://0xdata.com/downloadtable/) and choose the version that is right for your environment.


**STEP 2**

Make sure the same h2o.jar file is available on every host.


**STEP 3**

The best way to get multiple H2O nodes to find each other is to
provide a flat file which lists the set of nodes.

Create a flatfile.txt with the IP and port for each H2O instance.
Put one entry per line.  For example:

    192.168.1.163:54321
    192.168.1.164:54321

(Note that the -flatfile option tells one H2O node where to find the
others.  It is not a substitute for the -ip and -port specification.)


**STEP 4**

Copy the flatfile.txt to each node in your cluster.

**STEP 5**


The Xmx option in the java command line specifies the amount of memory
allocated to one H2O node.  The cluster's memory capacity is the sum
across all H2O nodes in the cluster.

For example, if a user creates a cluster with four 20g nodes (by
specifying Xmx20g), H2O will have available a total of 80 gigs of
memory.

For best performance, we recommend you size your cluster to be about
four times the size of your data (but to avoid swapping, Xmx must not
be larger than physical memory on any given node).  Giving all nodes
the same amount of memory is strongly recommended (H2O
works best with symmetric nodes).

Note the optional -ip (not shown in the example below) and -port
options tell this H2O node what IP address and ports (port and port+1
are used) to bind to.  The -ip option is especially helpful for hosts
that have multiple network interfaces.

    $ java -Xmx20g -jar h2o.jar -flatfile flatfile.txt -port 54321

You will see output similar to the following:

    08:35:33.553 main      INFO WATER: ----- H2O started -----
    08:35:33.555 main      INFO WATER: Build git branch: master
    08:35:33.555 main      INFO WATER: Build git hash: f253798433c109b19acd14cb973b45f255c59f3f
    08:35:33.555 main      INFO WATER: Build git describe: f253798
    08:35:33.555 main      INFO WATER: Build project version: 1.7.0.520
    08:35:33.555 main      INFO WATER: Built by: 'jenkins'
    08:35:33.555 main      INFO WATER: Built on: 'Thu Sep 12 00:01:52 PDT 2013'
    08:35:33.556 main      INFO WATER: Java availableProcessors: 32
    08:35:33.558 main      INFO WATER: Java heap totalMemory: 1.92 gb
    08:35:33.559 main      INFO WATER: Java heap maxMemory: 17.78 gb
    08:35:33.559 main      INFO WATER: ICE root: '/tmp/h2o-tomk'
    08:35:33.580 main      INFO WATER: Internal communication uses port: 54322
    +                                  Listening for HTTP and REST
               traffic
                                       on  http://192.168.1.163:54321/
    08:35:33.613 main      INFO WATER: H2O cloud name: 'MyClusterName'
    08:35:33.613 main      INFO WATER: (v1.7.0.520) 'MyClusterName' on /192.168.1.163:54321, static configuration based on -flatfile flatfile.txt
    08:35:33.615 main      INFO WATER: Cloud of size 1 formed [/192.168.1.163:54321]
    08:35:33.747 main      INFO WATER: Log dir: '/tmp/h2o-tomk/h2ologs'


As you add more nodes to your cluster, the H2O output will inform you:

    INFO WATER: Cloud of size 2 formed [/...]...


**STEP 6**

Access the H2O Web UI with your browser.  Point your browser to the HTTP link given by **"Listening for HTTP and REST traffic on..."** in the H2O output.


**STEP 7**

If you are programmatically creating the cloud, you should give the
cloud some time to establish itself (typically one minute is
sufficient) and then check to see if the cloud is up.

To do this, point to the url `http://[ip]:[port]/Cloud.json` (see a
piece of the JSON response below).  Wait for the "cloud_size" to be
the expected value and the "consensus" field to be true.

    {
      ...

      "cloud_size": 2,
      "consensus": true,

      ...
    }

