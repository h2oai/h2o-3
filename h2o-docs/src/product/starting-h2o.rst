Starting H2O
============

There are a variety of ways to start H2O, depending on which client you would like to use. The instructions below assume that you already downloaded and installed H2O. If you have not, then please refer to the `Downloading & Installing H2O <downloading.html>`__ section.

From R
------

Use the ``h2o.init()`` method to initialize H2O. This method accepts the following options. Note that in most cases, simply using ``h2o.init()`` is all that a user is required to do.

- ``nthreads``: This launches H2O using all available CPUs and is only applicable if you launch H2O locally using R. If you start H2O locally outside of R or start H2O on Hadoop, the nthreads parameter is not applicable.
- ``ip``: The IP address of the server where H2O is running.
- ``port``: The port number of the H2O server.
- ``startH2O``: (Optional) A logical value indicating whether to try to start H2O from R if no connection with H2O is detected. This is only possible if ``ip = "localhost"`` or ``ip = "127.0.0.1"``. If an existing connection is detected, R does not start H2O.
- ``forceDL``: (Optional) A logical value indicating whether to force download of the H2O executable. This defaults to FALSE, so the executable will only be downloaded if it does not already exist in the H2O R library resources directory at h2o/java/h2o.jar. 
- ``enable_assertions``:  (Optional) A logical value indicating whether H2O should be launched with assertions enabled. This is used mainly for error checking and debugging purposes. 
- ``license``: (Optional) A character string value specifying the full path of the license file. 
- ``max_mem_size``: (Optional) A character string specifying the maximum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter ``m`` or ``M`` to indicate megabytes, or ``g`` or ``G`` to indicate gigabytes.
- ``min_mem_size``: (Optional) A character string specifying the minimum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter ``m`` or ``M`` to indicate megabytes, or ``g`` or ``G`` to indicate gigabytes.
- ``ice_root``: (Optional) A directory to handle object spillage. The default varies by OS.
- ``strict_version_check``: (Optional) Setting this to FALSE is unsupported and should only be done when advised by technical support.
- ``ignore_config``: (Optional) This option allows you to specify whether to perform processing of a .h2oconfig file. When h2o.init() is specified, a call to a config reader method is invoked. This call can result in path issues when there is no "root" (for example, with a Windows network drive) because the config file reader searches up to "root." When there is no "root", the path to search will continue to expand, eventually result in an error. This value defaults to False.
- ``proxy``: (Optional) A character string specifying the proxy path.
- ``https``: (Optional) Set this to TRUE to use https instead of http.
- ``insecure``: (Optional) Set this to TRUE to disable SSL certificate checking.
- ``username``: (Optional) The username to log in with.
- ``password``: (Optional) The password to log in with.
- ``cookies``: (Optional) Vector (or list) of cookies to add to request.
- ``context_path``: (Optional) The last part of connection URL. For example, **http://<ip>:<port>/<context_path>**

By default, ``h2o.init()`` first checks if an H2O instance is connectible. If it cannot connect and ``start = TRUE`` with ``ip = "localhost"``, it will attempt to start an instance of H2O at localhost:54321. If an open ip and port of your choice are passed in, then this method will attempt to start an H2O instance at that specified ip and port.

When initializing H2O locally, this method searches for the h2o.jar file in the R library resources (system.file("java", "h2o.jar", package = "h2o")), and if the file does not exist, it will automatically attempt to download the correct version from Amazon S3. The user must have Internet access for this process to be successful.

Once connected, the ``h2o.init()`` method checks to see if the local H2O R package version matches the version of H2O running on the server. If there is a mismatch and the user indicates he/she wants to upgrade, it will remove the local H2O R package and download/install the H2O R package from the server.

**Note**: You may want to manually upgrade your package rather than waiting until being prompted. This requires that you fully uninstall and reinstall the H2O package and the H2O client package. You must unload packages running in the environment before upgrading. We also recommended that you restart R or R studio after upgrading.

Example
~~~~~~~

::

  library h2o
  h2o.init()

  H2O is not running yet, starting it now...

  Note:  In case of errors look at the following log files:
      /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T//RtmpKtZXsy/h2o_techwriter_started_from_r.out
      /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T//RtmpKtZXsy/h2o_techwriter_started_from_r.err

  java version "1.8.0_25"
  Java(TM) SE Runtime Environment (build 1.8.0_25-b17)
  Java HotSpot(TM) 64-Bit Server VM (build 25.25-b02, mixed mode)

  Starting H2O JVM and connecting: .. Connection successful!

  R is connected to the H2O cluster: 
      H2O cluster uptime:         2 seconds 812 milliseconds 
      H2O cluster version:        3.20.0.1 
      H2O cluster version age:    9 days  
      H2O cluster name:           H2O_started_from_R_techwriter_awt197 
      H2O cluster total nodes:    1 
      H2O cluster total memory:   3.56 GB 
      H2O cluster total cores:    8 
      H2O cluster allowed cores:  8 
      H2O cluster healthy:        TRUE 
      H2O Connection ip:          localhost 
      H2O Connection port:        54321 
      H2O Connection proxy:       NA 
      H2O Internal Security:      FALSE 
      R Version:                  R version 3.2.2 (2015-08-14) 

From Python
-----------

Use the ``h2o.init()`` function to initialize H2O. This function accepts the following options. Note that in most cases, simply using ``h2o.init()`` is all that a user is required to do.


- ``url``: Full URL of the server to connect to. (This can be used instead of ``ip`` + ``port`` + ``https``.)
- ``ip``: The ip address (or host name) of the server where H2O is running.
- ``port``: Port number that H2O service is listening to.
- ``https``: Set to True to connect via https:// instead of http://.
- ``insecure``: When using https, setting this to True will disable SSL certificates verification.
- ``username``: The username to log in with when using basic authentication.
- ``password``: The password to log in with when using basic authentication.
- ``cookies``: Cookie (or list of) to add to each request.
- ``proxy``: The proxy server address.
- ``start_h2o``: If False, do not attempt to start an H2O server when a connection to an existing one failed.
- ``nthreads``: "Number of threads" option when launching a new H2O server.
- ``ice_root``: The directory for temporary files for the new H2O server.
- ``enable_assertions``: Enable assertions in Java for the new H2O server.
- ``max_mem_size``: Maximum memory to use for the new H2O server. Integer input will be evaluated as gigabytes.  Other units can be specified by passing in a string (e.g. "160M" for 160 megabytes).
- ``min_mem_size``: Minimum memory to use for the new H2O server. Integer input will be evaluated as gigabytes.  Other units can be specified by passing in a string (e.g. "160M" for 160 megabytes).
- ``strict_version_check``: If True, an error will be raised if the client and server versions don't match.

Example
~~~~~~~

::

  python
  import h2o
  h2o.init(ip="localhost", port=54323)

  Checking whether there is an H2O instance running at http://localhost:54323..... not found.
  Attempting to start a local H2O server...
    Java Version: java version "1.8.0_25"; Java(TM) SE Runtime Environment (build 1.8.0_25-b17); Java HotSpot(TM) 64-Bit Server VM (build 25.25-b02, mixed mode)
    Starting server from /Users/techwriter/anaconda/lib/python2.7/site-packages/h2o/backend/bin/h2o.jar
    Ice root: /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T/tmpN2xfkW
    JVM stdout: /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T/tmpN2xfkW/h2o_techwriter_started_from_python.out
    JVM stderr: /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T/tmpN2xfkW/h2o_techwriter_started_from_python.err
    Server is running at http://127.0.0.1:54323
  Connecting to H2O server at http://127.0.0.1:54323... successful.
  --------------------------  ---------------------------------
  H2O cluster uptime:         02 secs
  H2O cluster version:        3.20.0.1
  H2O cluster version age:    9 days
  H2O cluster name:           H2O_from_python_techwriter_pu6lbs
  H2O cluster total nodes:    1
  H2O cluster free memory:    3.556 Gb
  H2O cluster total cores:    8
  H2O cluster allowed cores:  8
  H2O cluster status:         accepting new members, healthy
  H2O connection url:         http://127.0.0.1:54323
  H2O connection proxy:
  H2O internal security:      False
  Python version:             2.7.12 final
  --------------------------  ---------------------------------

From Anaconda
~~~~~~~~~~~~~

This section describes how run H2O in an Anaconda Cloud environment. This section assumes that you have installed H2O on Anaconda using the instructions in the `Install on Anaconda Cloud <downloading.html#install-on-anaconda-cloud>`__ section. 

Launching Jupyter Notebook
^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Open a Terminal window and launch jupyter notebook. 

   ::

     user$ jupyter notebook

2. Create a new Python notebook by selecting the **New** button in the upper left corner. At this point, you can begin using Jupyter Notebook to run H2O Python commands. An example notebook follows.

GBM Example
^^^^^^^^^^^

After you successfully launch Jupyter notebook, enter the following commands to run a GBM example. 

1. Import the H2O and GBM modules.

  .. figure:: images/anaconda_import_module.png
     :alt: Import H2O

2. Initialize H2O using ``h2o.init()``.

  .. figure:: images/anaconda_init.png
     :alt: Initialize H2O

3. Import the Airlines dataset. This dataset will be used to classify whether a flight will be delayed.

  .. figure:: images/anaconda_import_airlines.png
     :alt: Import dataset

4. Convert columns to factors.

  .. figure:: images/anaconda_convert_columns.png
     :alt: Convert columns to factors

5. Set the predictor names and the response column name.

  .. figure:: images/anaconda_predictor_response.png
     :alt: Set predictor names and response column

6. Split the dataset into training and validation sets.

  .. figure:: images/anaconda_split_data.png
     :alt: Split the dataset

7. Specify the number of bins that will be included in the historgram and then split. 

  .. figure:: images/anaconda_nbins_cats.png
     :alt: Try a range of nbins_cats

8. Train the models.

  .. figure:: images/anaconda_train_model.png
     :alt: Train the models

9. Print the AUC scores for the training data and the validation data. 

  .. figure:: images/anaconda_print_auc.png
     :alt: Print the AUC score

Troubleshooting
^^^^^^^^^^^^^^^

If your system includes two versions of Anaconda (a global installation and a user-specific installation), be sure to use the User Anaconda. Using the Global Anaconda will result in an error when you attempt to run commands in Jupyter Notebook. You can verify the version that you are using by running ``which pip`` (Mac) or ``where pip`` (Windows). If your system shows that your environment is set up to use Global Anaconda by default, then change the PATH environment variable to use the User Anaconda. 

From the Command Line
---------------------

.. todo:: create a table of command line options (should you say expression or primary?) 
.. todo:: provide examples for most common clusters

You can use Terminal (OS X) or the Command Prompt (Windows) to launch
H2O. 

When you launch from the command line, you can include
additional instructions to H2O 3.0, such as how many nodes to launch,
how much memory to allocate for each node, assign names to the nodes in
the cloud, and more.

    **Note**: H2O requires some space in the ``/tmp`` directory to
    launch. If you cannot launch H2O, try freeing up some space in the
    ``/tmp`` directory, then try launching H2O again.

For more detailed instructions on how to build and launch H2O, including
how to clone the repository, how to pull from the repository, and how to
install required dependencies, refer to the `developer
documentation <https://github.com/h2oai/h2o-3#41-building-from-the-command-line-quick-start>`_.

There are three different argument types:

-  JVM options
-  H2O options
-  Authentication options

The arguments use the following format: java ``<JVM Options>`` -jar h2o.jar ``<H2O Options>``.

JVM Options
~~~~~~~~~~~

-  ``-version``: Display Java version info.
-  ``-Xmx<Heap Size>``: To set the total heap size for an H2O node, configure the memory allocation option ``-Xmx``. By default, this option is set to 1 Gb (``-Xmx1g``). When launching nodes, we recommend allocating a total of four times the memory of your data.

    **Note**: Do not try to launch H2O with more memory than you have available.

H2O Options
~~~~~~~~~~~

-	``-h`` or ``-help``: Display this information in the command line output.
- ``-version``: Specify to print version information and exit.
-	``-name <H2OCloudName>``: Assign a name to the H2O instance in the cloud (where ``<H2OCloudName>`` is the name of the cloud). Nodes with the same cloud name will form an H2O cloud (also known as an H2O cluster).
-	``-flatfile <FileName>``: Specify a flatfile of IP address for faster cloud formation (where ``<FileName>`` is the name of the flatfile).
-	``-ip <IPnodeAddress>``: Specify an IP for the machine other than the default ``localhost``, for example:
    
    - IPv4: ``-ip 178.16.2.223`` 
    - IPv6: ``-ip 2001:db8:1234:0:0:0:0:1`` (Short version of IPv6 with ``::`` is not supported.) **Note**: If you are selecting a link-local address ``fe80::/96``, it is necessary to specify the *zone index* (e.g., ``%en0`` for ``fe80::2acf:e9ff:fe15:e0f3%en0``) in order to select the right interface.

-	``-port <#>``: Specify a PORT used for REST API. The communication port will be the port with value +1 higher.
-	``-baseport``: Specifies the starting port to find a free port for REST API, the internal communication port will be port with value +1 higher.
-	``-network <ip_address/subnet_mask>``: Specify an IP addresses with a subnet mask. The IP address discovery code binds to the first interface that matches one of the networks in the comma-separated list; to specify an IP address, use ``-network``. To specify a range, use a comma to separate the IP addresses: ``-network 123.45.67.0/22,123.45.68.0/24``. For example, ``10.1.2.0/24`` supports 256 possibilities. IPv4 and IPv6 addresses are supported. 

    - IPv4: ``-network 178.0.0.0/8``
    - IPv6: ``-network 2001:db8:1234:0:0:0:0:0/48`` (short version of IPv6 with ``::`` is not supported.)

-	``-ice_root <fileSystemPath>``: Specify a directory for H2O to spill temporary data to disk (where ``<fileSystemPath>`` is the file path).
- ``-log_dir <fileSystemPath>\``: Specify the directory where H2O writes logs to disk. (This usually has a good default that you need not change.
- ``-log_level <TRACE,DEBUG,INFO,WARN,ERRR,FATAL>``: Specify to write messages at this logging level, or above. The default is INFO.
- ``-flow_dir <server-side or HDFS directory>``: Specify a directory for saved flows. The default is ``/Users/h2o-<H2OUserName>/h2oflows`` (where ``<H2OUserName>`` is your user name).
- ``-nthreads <#ofThreads>``: Specify the maximum number of threads in the low-priority batch work queue (where ``<#ofThreads>`` is the number of threads). 
- ``-client``: Launch H2O node in client mode. This is used mostly for running Sparkling Water.
- ``-notify_local <fileSystemPath>``: Specifies a file to write to when the node is up. The file system path contains a single line with the IP and port of the embedded web server. For example, 192.168.1.100:54321. 
-  ``-context_path <context_path>``: The context path for Jetty.
- ``features``: Disable availability of features considered to be experimental or beta. Currently, this only works with algorithms. Options include:

   -  ``stable``: Only stable algorithms will be enabled; beta and experimental will not.
   -  ``beta``: Only beta and stable algorithms will be enabled; experimental will not.
   -  ``experimental``: Enables all algorithms (default).   

Authentication Options
~~~~~~~~~~~~~~~~~~~~~~

-  ``-jks <filename>``: Specify a Java keystore file.
-  ``-jks_pass <password>``: Specify the Java keystore password.
-  ``-hash_login``: Specify to use Jetty HashLoginService. This defaults to False.
-  ``-ldap_login``: Specify to use Jetty LdapLoginService. This defaults to False.
-  ``-kerberos_login``: Specify to use Kerberos LoginService. This defaults to False.
-  ``-pam_login``: Specify to use the Pluggable Authentication Module (PAM) LoginService. This defaults to False. 
-  ``-login_conf <filename>``: Specify the LoginService configuration file.
-  ``-form_auth``: Enables Form-based authentication for Flow. This defaults to Basic authentication.
-  ``-session_timeout <minutes>``: Specifies the number of minutes that a session can remain idle before the server invalidates the session and requests a new login. Requires ``-form_auth``. This defaults to no timeout.
-  ``-internal_security_conf <filename>``: Specify the path (absolute or relative) to a file containing all internal security related configurations.

H2O Networking
~~~~~~~~~~~~~~

H2O Internal Communication
^^^^^^^^^^^^^^^^^^^^^^^^^^

By default, H2O selects the IP and PORT for internal communication automatically using the following this process (if not specified):

1. Retrieve a list of available interfaces (which are up).
2. Sort them with "bond" interfaces put on the top.
3. For each interface, extract associated IPs.
4. Pick only reachable IPs (that filter IPs provided by interfaces, such as awdl):

  - If there is a site IP, use it.
  - Otherwise, if there is a link local IP, use it. (For IPv6, the link IP 0xfe80/96 is associated with each interface.)
  - Or finally, try to find a local IP. (Use loopback or try to use Google DNS to find IP for this machine.)

**Notes**: The port is selected by looking for a free port starting with port 54322. The IP, PORT and network selection can be changed by the following options:

  - ``-ip`` 
  - ``network``
  - ``-port``
  - ``-baseport`` 


Cloud Formation Behavior
^^^^^^^^^^^^^^^^^^^^^^^^

New H2O nodes join to form a cloud during launch. After a job has
started on the cloud, it prevents new members from joining.

-  To start an H2O node with 4GB of memory and a default cloud name:
   ``java -Xmx4g -jar h2o.jar``

-  To start an H2O node with 6GB of memory and a specific cloud name:
   ``java -Xmx6g -jar h2o.jar -name MyCloud``

-  To start an H2O cloud with three 2GB nodes using the default cloud
   names: ``java -Xmx2g -jar h2o.jar &   java -Xmx2g -jar h2o.jar &   java -Xmx2g -jar h2o.jar &``

Wait for the ``INFO: Registered: # schemas in: #mS`` output before
entering the above command again to add another node (the number for #
will vary).

Clouding Up: Cluster Creation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

H2O provides two modes for cluster creation:

-  Multicast based
-  Flatfile based

Multicast
'''''''''

In this mode, H2O is using IP multicast to announce existence of H2O nodes. Each node selects the same multicast group and port based on specified shared cloud name (see ``-name`` option). For example, for IPv4/PORT a generated multicast group is ``228.246.114.236:58614`` (for cloud name ``michal``), 
for IPv6/PORT a generated multicast group is ``ff05:0:3ff6:72ec:0:0:3ff6:72ec:58614`` (for cloud name ``michal`` and link-local address which enforce link-local scope).

For IPv6 the scope of multicast address is enforced by a selected node IP. For example, if IP the selection process selects link-local address, then the scope of multicast will be link-local. This can be modified by specifying JVM variable ``sys.ai.h2o.network.ipv6.scope`` which enforces addressing scope use in multicast group address (for example, ``-Dsys.ai.h2o.network.ipv6.scope=0x0005000000000000`` enforces the site local scope. For more details please consult the
class ``water.util.NetworkUtils``).

For more information about scopes, see the following `image <http://www.tcpipguide.com/free/diagrams/ipv6scope.png>`_. 

Flatfile
''''''''

The flatfile describes a topology of a H2O cluster. The flatfile definition is passed via the ``-flatfile`` option. It needs to be passed at each node in the cluster, but definition does not be the same at each node. However, transitive closure of all definitions should contains all nodes. For example, for the following definition

+---------+-------+-------+-------+
| Nodes   | nodeA | nodeB | nodeC |
+---------+-------+-------+-------+
|Flatfile | A,B   | A, B  | B, C  |
+---------+-------+-------+-------+

The resulting cluster will be formed by nodes A, B, C. The node A transitively sees node C via node B flatfile definition, and vice versa.

The flatfile contains a list of nodes in the form ``IP:PORT`` that are going to compose a resulting cluster (each node on a separated line, everything prefixed by ``#`` is ignored). Running H2O on a multi-node cluster allows you to use more memory for large-scale tasks (for example, creating models from huge datasets) than would be possible on a single node.

**IPv4**:

::

	# run two nodes on 108
	10.10.65.108:54322
	10.10.65.108:54325

**IPv6**:

::

	0:0:0:0:0:0:0:1:54321
	0:0:0:0:0:0:0:1:54323

Web Server
^^^^^^^^^^

The web server IP is auto-configured in the same way as internal communication IP, nevertheless the created socket listens on all available interfaces. A specific API can be specified with the ``-web_ip`` option.

Options
'''''''

- ``-web_ip``: specifies IP for web server to expose REST API

Dual Stacks
^^^^^^^^^^^

Dual stack machines support IPv4 and IPv6 network stacks.
Right now, H2O always prefer IPV4, however the preference can be changed via JVM system options ``java.net.preferIPv4Addresses`` and ``java.net.preferIPv6Addresses``. For example:

- ``-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Addresses=true`` - H2O will try to select IPv4
- ``-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Addresses=false`` - H2O will try to select IPv6

On Spark
--------

Refer to the `Getting Started with Sparkling Water <welcome.html#getting-started-with-sparkling-water>`__ section for information on how to launch H2O on Spark. 
