#FAQ

##General Troubleshooting Tips


- Confirm your internet connection is active. 

- Test connectivity using curl: First, log in to the first node and enter `curl http://<Node2IP>:54321` (where `<Node2IP>` is the IP address of the second node. Then, log in to the second node and enter `curl http://<Node1IP>:54321` (where `<Node1IP>` is the IP address of the first node). Look for output from H2O.

- Try allocating more memory to H2O by modifying the `-Xmx` value when launching H2O from the command line (for example, `java -Xmx10g -jar h2o.jar` allocates 10g of memory for H2O). If you create a cluster with four 20g nodes (by specifying `-Xmx20g` four times), H2O will have a total of 80 gigs of memory available. For best performance, we recommend sizing your cluster to be about four times the size of your data. To avoid swapping, the `-Xmx` allocation must not exceed the physical memory on any node. Allocating the same amount of memory for all nodes is strongly recommended, as H2O works best with symmetric nodes.

- Confirm that no other sessions of H2O are running. To stop all running H2O sessions, enter `ps -efww | grep h2o` in Terminal. 
- Confirm ports 54321 and 54322 are available for both TCP and UDP.
- Confirm your firewall is not preventing the nodes from locating each other.
- Confirm the nodes are not using different versions of H2O.
- Confirm that the username is the same on all nodes; if not, define the cloud in the terminal when launching using `-name`:`java -jar h2o.jar -name myCloud`.
- Confirm that the nodes are not on different networks.
- Check if the nodes have different interfaces; if so, use the -network option to define the network (for example, `-network 127.0.0.1`). To use a network range, use a comma to separate the IP addresses (for example, `-network 123.45.67.0/22,123.45.68.0/24`).
- Force the bind address using `-ip`:`java -jar h2o.jar -ip <IP_Address> -port <PortNumber>`.
- (Hadoop only) Try launching H2O with a longer timeout: `hadoop jar h2odriver.jar -timeout 1800`
- (Hadoop only) Try to launch H2O using more memory: `hadoop jar h2odriver.jar -mapperXmx 10g`. The cluster’s memory capacity is the sum of all H2O nodes in the cluster. 
- (Linux only) Check if you have SELINUX or IPTABLES enabled; if so, disable them.
- (EC2 only) Check the configuration for the EC2 security group.



##Algorithms

**What does it mean if the r2 value in my model is negative?**

The coefficient of determination (also known as r^2) can be negative if: 

- linear regression is used without an intercept (constant)
- non-linear functions are fitted to the data
- predictions compared to the corresponding outcomes are not based on the model-fitting procedure using those data
- it is early in the build process (may self-correct as more trees are added)

If your r2 value is negative after your model is complete, your model is likely incorrect. Make sure your data is suitable for the type of model, then try adding an intercept. 

---

**What's the process for implementing new algorithms in H2O?**

This [blog post](http://h2o.ai/blog/2014/16/Hacking/Algos/) by Cliff  walks you through building a new algorithm, using K-Means, Quantiles, and Grep as examples. 

To learn more about performance characteristics when implementing new algorithms, refer to Cliff's [KV Store Guide](http://0xdata.com/blog/2014/05/kv-store-memory-analytics-part-2-2/). 

---

**How do I find the standard errors of the parameter estimates (p-values)?**

P-values are currently not supported. They are on our road map and will be added, depending on the current customer demand/priorities. Generally, adding p-values involves significant engineering effort because p-values for regularized GLM are not straightforward and have been defined only recently (with no standard implementation available that we know of). P-values for a restricted set of GLM problems (no regularization, low number of predictors) are easier to do and may be added sooner, if there is a sufficient demand.

For now, we recommend using a non-zero l1 penalty (alpha  > 0) and considering all non-zero coefficients in the model as significant. The recommended use case is running GLM with lambda search enabled and alpha > 0 and picking the best lambda value based on cross-validation or hold-out set validation.

---

**How do I specify regression or classification for Distributed Random Forest in the web UI?**


If the response column is numeric, H2O generates a regression model. If the response column is enum, the model uses classification. To specify the column type, select it from the drop-down column heading list in the **Data Preview** section during parsing. 

---

**What's the largest number of classes that H2O supports for multinomial prediction?**

For tree-based algorithms, the maximum number of classes (or levels) for a response column is 1000. 

---

**How do I obtain a tree diagram of my DRF model?**

Output the SVG code for the edges and nodes. A simple tree visitor is available [here](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/tree/TreeVisitor.java) and the Java code generator is available [here](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/tree/TreeJCodeGen.java). 

---

**Is Word2Vec available? I can see the Java and R sources, but calling the API generates an error.**

Word2Vec, along with other natural language processing (NLP) algos, are currently in development in the current version of H2O. 

---

<!---
#commenting out as still in dev but wanted to save for later

**Are there any H2O examples using text for classification?**

Currently, the following examples are available for Sparkling Water: 

a) Use TF-IDF weighting scheme for classifying text messages 
https://github.com/h2oai/sparkling-water/blob/master/examples/scripts/mlconf_2015_hamSpam.script.scala 

b) Use Word2Vec Skip-gram model + GBM for classifying job titles 
https://github.com/h2oai/sparkling-water/blob/master/examples/scripts/craigslistJobTitles.scala 

-->

##Building H2O


**Using `./gradlew build` doesn't generate a build successfully - is there anything I can do to troubleshoot?**

Use `./gradlew clean` before running `./gradlew build`. 

---

**I tried using `./gradlew build` after using `git pull` to update my local H2O repo, but now I can't get H2O to build successfully - what should I do?**

Try using `./gradlew build -x test` - the build may be failing tests if data is not synced correctly. 

---

##Clusters


**When trying to launch H2O, I received the following error message: `ERROR: Too many retries starting cloud.` What should I do?**

If you are trying to start a multi-node cluster where the nodes use multiple network interfaces, by default H2O will resort to using the default host (127.0.0.1). 

To specify an IP address, launch H2O using the following command: 

`java -jar h2o.jar -ip <IP_Address> -port <PortNumber>`

If this does not resolve the issue, try the following additional troubleshooting tips: 

- Confirm your internet connection is active. 

- Test connectivity using curl: First, log in to the first node and enter curl http://<Node2IP>:54321 (where <Node2IP> is the IP address of the second node. Then, log in to the second node and enter curl http://<Node1IP>:54321 (where <Node1IP> is the IP address of the first node). Look for output from H2O.

- Confirm ports 54321 and 54322 are available for both TCP and UDP.
- Confirm your firewall is not preventing the nodes from locating each other.
- Confirm the nodes are not using different versions of H2O.
- Confirm that the username is the same on all nodes; if not, define the cloud in the terminal when launching using `-name`:`java -jar h2o.jar -name myCloud`.
- Confirm that the nodes are not on different networks.
- Check if the nodes have different interfaces; if so, use the -network option to define the network (for example, `-network 127.0.0.1`).
- Force the bind address using `-ip`:`java -jar h2o.jar -ip <IP_Address> -port <PortNumber>`.
- (Linux only) Check if you have SELINUX or IPTABLES enabled; if so, disable them.
- (EC2 only) Check the configuration for the EC2 security group.

---

**What should I do if I tried to start a cluster but the nodes started independent clouds that are not connected?**

Because the default cloud name is the user name of the node, if the nodes are on different operating systems (for example, one node is using Windows and the other uses OS X), the different user names on each machine will prevent the nodes from recognizing that they belong to the same cloud. To resolve this issue, use `-name` to configure the same name for all nodes. 

---

**One of the nodes in my cluster is unavailable — what do I do?**

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


**I launched H2O instances on my nodes - why won't they form a cloud?**

If you launch without specifying the IP address by adding argument -ip:

`$ java -Xmx20g -jar h2o.jar -flatfile flatfile.txt -port 54321`

and multiple local IP addresses are detected, H2O uses the default localhost (127.0.0.1) as shown below:

  ```
  10:26:32.266 main      WARN WATER: Multiple local IPs detected:
  +                                    /198.168.1.161  /198.168.58.102
  +                                  Attempting to determine correct address...
  10:26:32.284 main      WARN WATER: Failed to determine IP, falling back to localhost.
  10:26:32.325 main      INFO WATER: Internal communication uses port: 54322
  +                                  Listening for HTTP and REST traffic
  +                                  on http://127.0.0.1:54321/
  10:26:32.378 main      WARN WATER: Flatfile configuration does not include self:
  /127.0.0.1:54321 but contains [/192.168.1.161:54321, /192.168.1.162:54321]
  ```

To avoid using 127.0.0.1 on servers with multiple local IP addresses, run the command with the -ip argument to force H2O to launch at the specified IP:

`$ java -Xmx20g -jar h2o.jar -flatfile flatfile.txt -ip 192.168.1.161 -port 54321`

---

##Data

**How should I format my SVMLight data before importing?**

The data must be formatted as a sorted list of unique integers, the column indices must be >= 1, and the columns must be in ascending order. 

---

##General

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

In Terminal, enter `ps -efww | grep h2o`, then kill any running PIDs. You can also find the running instance in Terminal and press **Ctrl + C** on your keyboard. To confirm no H2O sessions are still running, go to `http://localhost:54321` and verify that the H2O web UI does not display. 

---


**Why is H2O not launching from the command line?**

	$ java -jar h2o.jar &
	% Exception in thread "main" java.lang.ExceptionInInitializerError
	at java.lang.Class.initializeClass(libgcj.so.10)
	at water.Boot.getMD5(Boot.java:73)
	at water.Boot.<init>(Boot.java:114)
	at water.Boot.<clinit>(Boot.java:57)
	at java.lang.Class.initializeClass(libgcj.so.10)
    Caused by: java.lang.IllegalArgumentException
    at java.util.regex.Pattern.compile(libgcj.so.10)
    at water.util.Utils.<clinit>(Utils.java:1286)
    at java.lang.Class.initializeClass(libgcj.so.10)
    ...4 more

The only prerequisite for running H2O is a compatible version of Java. We recommend Oracle's [Java 1.7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html).

  
---

**Why did I receive the following error when I tried to launch H2O?**

```
[root@sandbox h2o-dev-0.3.0.1188-hdp2.2]hadoop jar h2odriver.jar -nodes 2 -mapperXmx 1g -output hdfsOutputDirName
Determining driver host interface for mapper->driver callback...
   [Possible callback IP address: 10.0.2.15]
   [Possible callback IP address: 127.0.0.1]
Using mapper->driver callback IP address and port: 10.0.2.15:41188
(You can override these with -driverif and -driverport.)
Memory Settings:
   mapreduce.map.java.opts:     -Xms1g -Xmx1g -Dlog4j.defaultInitOverride=true
   Extra memory percent:        10
   mapreduce.map.memory.mb:     1126
15/05/08 02:33:40 INFO impl.TimelineClientImpl: Timeline service address: http://sandbox.hortonworks.com:8188/ws/v1/timeline/
15/05/08 02:33:41 INFO client.RMProxy: Connecting to ResourceManager at sandbox.hortonworks.com/10.0.2.15:8050
15/05/08 02:33:47 INFO mapreduce.JobSubmitter: number of splits:2
15/05/08 02:33:48 INFO mapreduce.JobSubmitter: Submitting tokens for job: job_1431052132967_0001
15/05/08 02:33:51 INFO impl.YarnClientImpl: Submitted application application_1431052132967_0001
15/05/08 02:33:51 INFO mapreduce.Job: The url to track the job: http://sandbox.hortonworks.com:8088/proxy/application_1431052132967_0001/
Job name 'H2O_3889' submitted
JobTracker job ID is 'job_1431052132967_0001'
For YARN users, logs command is 'yarn logs -applicationId application_1431052132967_0001'
Waiting for H2O cluster to come up...
H2O node 10.0.2.15:54321 requested flatfile
ERROR: Timed out waiting for H2O cluster to come up (120 seconds)
ERROR: (Try specifying the -timeout option to increase the waiting time limit)
15/05/08 02:35:59 INFO impl.TimelineClientImpl: Timeline service address: http://sandbox.hortonworks.com:8188/ws/v1/timeline/
15/05/08 02:35:59 INFO client.RMProxy: Connecting to ResourceManager at sandbox.hortonworks.com/10.0.2.15:8050

----- YARN cluster metrics -----
Number of YARN worker nodes: 1

----- Nodes -----
Node: http://sandbox.hortonworks.com:8042 Rack: /default-rack, RUNNING, 1 containers used, 0.2 / 2.2 GB used, 1 / 8 vcores used

----- Queues -----
Queue name:            default
   Queue state:       RUNNING
   Current capacity:  0.11
   Capacity:          1.00
   Maximum capacity:  1.00
   Application count: 1
   ----- Applications in this queue -----
   Application ID:                  application_1431052132967_0001 (H2O_3889)
       Started:                     root (Fri May 08 02:33:50 UTC 2015)
       Application state:           FINISHED
       Tracking URL:                http://sandbox.hortonworks.com:8088/proxy/application_1431052132967_0001/jobhistory/job/job_1431052132967_0001
       Queue name:                  default
       Used/Reserved containers:    1 / 0
       Needed/Used/Reserved memory: 0.2 GB / 0.2 GB / 0.0 GB
       Needed/Used/Reserved vcores: 1 / 1 / 0

Queue 'default' approximate utilization: 0.2 / 2.2 GB used, 1 / 8 vcores used

----------------------------------------------------------------------

ERROR:   Job memory request (2.2 GB) exceeds available YARN cluster memory (2.2 GB)
WARNING: Job memory request (2.2 GB) exceeds queue available memory capacity (2.0 GB)
ERROR:   Only 1 out of the requested 2 worker containers were started due to YARN cluster resource limitations

----------------------------------------------------------------------
Attempting to clean up hadoop job...
15/05/08 02:35:59 INFO impl.YarnClientImpl: Killed application application_1431052132967_0001
Killed.
[root@sandbox h2o-dev-0.3.0.1188-hdp2.2]#
```

The H2O launch failed because more memory was requested than was available. Make sure you are not trying to specify more memory in the launch parameters than you have available. 

---

**How does the architecture of H2O work?**

This [PDF](https://github.com/h2oai/h2o-meetups/blob/master/2014_11_18_H2O_in_Big_Data_Environments/H2OinBigDataEnvironments.pdf) includes diagrams and slides depicting how H2O works in big data environments. 

---
**How does H2O work with Excel?**

For more information on how H2O works with Excel, refer to this [page](http://learn.h2o.ai/content/demos/excel.html). 


---

##Hadoop

<!---
>commenting out as in progress per Michal
**Why did I get an error in R when I tried to save my model to my home directory in Hadoop?**

To save the model in HDFS, prepend the save directory with `hdfs://`:

```
# build model
model = h2o.glm(model params)

# save model
hdfs_name_node <- "mr-0x6"
hdfs_tmp_dir <- "/tmp/runit”
model_path <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
h2o.saveModel(model, dir = model_path, name = “mymodel")
```

---
-->

**How do I specify which nodes should run H2O in a Hadoop cluster?**

Currently, this is not yet supported. To provide resource isolation (for example, to isolate H2O to the worker nodes, rather than the master nodes), use YARN Nodemanagers to specify the nodes to use. 

---

**How do I import data from HDFS in R and in Flow?**

To import from HDFS in R: 

```
h2o.importHDFS(path, conn = h2o.getConnection(), pattern = "",
destination_frame = "", parse = TRUE, header = NA, sep = "",
col.names = NULL, na.strings = NULL)
```

Here is another example: 

```
# pathToAirlines <- "hdfs://mr-0xd6.0xdata.loc/datasets/airlines_all.csv"
# airlines.hex <- h2o.importFile(conn = h, path = pathToAirlines, destination_frame = "airlines.hex")
```


In Flow, the easiest way is to let the auto-suggestion feature in the *Search:* field complete the path for you. Just start typing the path to the file, starting with the top-level directory, and H2O provides a list of matching files. 

  ![Flow - Import Auto-Suggest](images/Flow_Import_AutoSuggest.png)
  
Click the file to add it to the *Search:* field.   

---

**Why do I receive the following error when I try to save my notebook in Flow?**

```
Error saving notebook: Error calling POST /3/NodePersistentStorage/notebook/Test%201 with opts
```

When you are running H2O on Hadoop, H2O tries to determine the home HDFS directory so it can use that as the download location. If the default home HDFS directory is not found, manually set the download location from the command line using the `-flow_dir` parameter (for example, `hadoop jar h2odriver.jar <...> -flow_dir hdfs:///user/yourname/yourflowdir`). You can view the default download directory in the logs by clicking **Admin > View logs...** and looking for the line that begins `Flow dir:`.


---

##Java

**How do I use H2O with Java?**

There are two ways to use H2O with Java. The simplest way is to call the REST API from your Java program to a remote cluster and should meet the needs of most users. 

You can access the REST API documentation within Flow, or on our [documentation site](http://h2o-release.s3.amazonaws.com/h2o/{{branch_name}}/{{build_number}}/docs-website/h2o-docs/index.html#route-reference). 

Flow, Python, and R all rely on the REST API to run H2O. For example, each action in Flow translates into one or more REST API calls. The script fragments in the cells in Flow are essentially the payloads for the REST API calls. Most R and Python API calls translate into a single REST API call. 

To see how the REST API is used with H2O: 

- Using Chrome as your internet browser, open the developer tab while viewing the web UI. As you perform tasks, review the network calls made by Flow. 

- Write an R program for H2O using the H2O R package that uses `h2o.startLogging()` at the beginning. All REST API calls used are logged. 

The second way to use H2O with Java is to embed H2O within your Java application, similar to [Sparkling Water](https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md). 

---

**How do I communicate with a remote cluster using the REST API?**

To create a set of bare POJOs for the REST API payloads that can be used by JVM REST API clients: 

0. Clone the sources from GitHub. 
0. Start an H2O instance. 
0. Enter `% cd py`.
0. Enter `% python generate_java_binding.py`. 

This script connects to the server, gets all the metadata for the REST API schemas, and writes the Java POJOs to `{sourcehome}/build/bindings/Java`. 


---

##Python

**How do I specify a value as an enum in Python? Is there a Python equivalent of `as.factor()` in R?**

Use `.asfactor()` to specify a value as an enum. 

---

**I received the following error when I tried to install H2O using the Python instructions on the downloads page - what should I do to resolve it?**

```
Downloading/unpacking http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/12/Python/h2o-3.0.0.12-py2.py3-none-any.whl 
  Downloading h2o-3.0.0.12-py2.py3-none-any.whl (43.1Mb): 43.1Mb downloaded 
  Running setup.py egg_info for package from http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/12/Python/h2o-3.0.0.12-py2.py3-none-any.whl 
    Traceback (most recent call last): 
      File "<string>", line 14, in <module> 
    IOError: [Errno 2] No such file or directory: '/tmp/pip-nTu3HK-build/setup.py' 
    Complete output from command python setup.py egg_info: 
    Traceback (most recent call last): 

  File "<string>", line 14, in <module> 

IOError: [Errno 2] No such file or directory: '/tmp/pip-nTu3HK-build/setup.py' 

---------------------------------------- 
Command python setup.py egg_info failed with error code 1 in /tmp/pip-nTu3HK-build
```

With Python, there is no automatic update of installed packages, so you must upgrade manually. Additionally, the package distribution method recently changed from `distutils` to `wheel`. The following procedure should be tried first if you are having trouble installing the H2O package, particularly if error messages related to `bdist_wheel` or `eggs` display. 

```
# this gets the latest setuptools 
# see https://pip.pypa.io/en/latest/installing.html 
wget https://bootstrap.pypa.io/ez_setup.py -O - | sudo python 

# platform dependent ways of installing pip are at 
# https://pip.pypa.io/en/latest/installing.html 
# but the above should work on most linux platforms? 

# on ubuntu 
# if you already have some version of pip, you can skip this. 
sudo apt-get install python-pip 

# the package manager doesn't install the latest. upgrade to latest 
# we're not using easy_install any more, so don't care about checking that 
pip install pip --upgrade 

# I've seen pip not install to the final version ..i.e. it goes to an almost 
# final version first, then another upgrade gets it to the final version. 
# We'll cover that, and also double check the install. 

# after upgrading pip, the path name may change from /usr/bin to /usr/local/bin 
# start a new shell, just to make sure you see any path changes 

bash 

# Also: I like double checking that the install is bulletproof by reinstalling. 
# Sometimes it seems like things say they are installed, but have errors during the install. Check for no errors or stack traces. 

pip install pip --upgrade --force-reinstall 

# distribute should be at the most recent now. Just in case 
# don't do --force-reinstall here, it causes an issue. 

pip install distribute --upgrade 


# Now check the versions 
pip list | egrep '(distribute|pip|setuptools)' 
distribute (0.7.3) 
pip (7.0.3) 
setuptools (17.0) 


# Re-install wheel 
pip install wheel --upgrade --force-reinstall 

```

After completing this procedure, go to Python and use `h2o.init()` to start H2O in Python. 

>**Note**: 
>
>If you use gradlew to build the jar yourself, you have to start the jar >yourself before you do `h2o.init()`.
>
>If you download the jar and the H2O package, `h2o.init()` will work like R >and you don't have to start the jar yourself.




---

##R

**How can I install the H2O R package if I am having permissions problems?**

This issue typically occurs for Linux users when the R software was installed by a root user. For more information, refer to the following [link](https://stat.ethz.ch/R-manual/R-devel/library/base/html/libPaths.html). 

To specify the installation location for the R packages, create a file that contains the `R_LIBS_USER` environment variable:

`echo R_LIBS_USER=\"~/.Rlibrary\" > ~/.Renviron`

Confirm the file was created successfully using `cat`: 

`$ cat ~/.Renviron`

You should see the following output:
 
`R_LIBS_USER="~/.Rlibrary"`

Create a new directory for the environment variable:

`$ mkdir ~/.Rlibrary`

Start R and enter the following: 

`.libPaths()`

Look for the following output to confirm the changes: 

```
[1] "<Your home directory>/.Rlibrary"                                         
[2] "/Library/Frameworks/R.framework/Versions/3.1/Resources/library"
```

---

**I received the following error message after launching H2O in RStudio and using `h2o.init` - what should I do to resolve this error?**

```
> localH2O = h2o.init()
Successfully connected to http://127.0.0.1:54321/
 
ERROR: Unexpected HTTP Status code: 301 Moved Permanently (url = http://127.0.0.
1:54321/3/Cloud?skip_ticks=true)
 
Error in fromJSON(rv$payload) : unexpected character '<'
Calls: h2o.init ... gsub -> .h2o.doSafeGET -> .h2o.doSafeREST -> fromJSON
Execution halted 
```

This error is due to a version mismatch between the H2O package and the running H2O instance. Make sure you are using the latest version of both files by downloading H2O from the [downloads page](http://h2o.ai/download/) and installing the latest version and that you have removed any previous H2O R package versions by running: 

```
if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }
```

Make sure to install the dependencies for the H2O R package as well: 

```
if (! ("methods" %in% rownames(installed.packages()))) { install.packages("methods") }
if (! ("statmod" %in% rownames(installed.packages()))) { install.packages("statmod") }
if (! ("stats" %in% rownames(installed.packages()))) { install.packages("stats") }
if (! ("graphics" %in% rownames(installed.packages()))) { install.packages("graphics") }
if (! ("RCurl" %in% rownames(installed.packages()))) { install.packages("RCurl") }
if (! ("rjson" %in% rownames(installed.packages()))) { install.packages("rjson") }
if (! ("tools" %in% rownames(installed.packages()))) { install.packages("tools") }
if (! ("utils" %in% rownames(installed.packages()))) { install.packages("utils") }
```


Finally, install the latest version of the H2O package for R: 

```
install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/master/{{build_number}}/R")))
library(h2o)
localH2O = h2o.init()
```

---

**I received the following error message after trying to run some code - what should I do?** 

```
> fit <- h2o.deeplearning(x=2:4, y=1, training_frame=train_hex)
  |=========================================================================================================| 100%
Error in model$training_metrics$MSE :
  $ operator not defined for this S4 class
In addition: Warning message:
Not all shim outputs are fully supported, please see ?h2o.shim for more information
```

Remove the `h2o.shim(enable=TRUE)` line and try running the code again. Note that the `h2o.shim` is only a way to notify users of previous versions of H2O about changes to the H2O R package - it will not revise your code, but provides suggested replacements for deprecated commands and parameters. 


---


##Sparkling Water

**How do I filter an H2OFrame using Sparkling Water?**

Filtering columns is easy: just remove the unnecessary columns or create a new H2OFrame from the columns you want to include (`Frame(String[] names, Vec[] vec)`), then make the H2OFrame wrapper around it (`new H2OFrame(frame)`). 

Filtering rows is a little bit harder. There are two ways: 

- Create an additional binary vector holding `1/0` for the `in/out` sample (make sure to take this additional vector into account in your computations). This solution is quite cheap, since you do not duplicate data - just create a simple vector in a data walk. 

  or 
  
- Create a new frame with the filtered rows. This is a harder task, since you have to copy data. For reference, look at the #deepSlice call on Frame (`H2OFrame`)


---

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

**I received the following error while running Sparkling Water using multiple nodes, but not when using a single node - what should I do?**

```
onExCompletion for water.parser.ParseDataset$MultiFileParseTask@31cd4150
water.DException$DistributedException: from /10.23.36.177:54321; by class water.parser.ParseDataset$MultiFileParseTask; class water.DException$DistributedException: from /10.23.36.177:54325; by class water.parser.ParseDataset$MultiFileParseTask; class water.DException$DistributedException: from /10.23.36.178:54325; by class water.parser.ParseDataset$MultiFileParseTask$DistributedParse; class java.lang.NullPointerException: null
	at water.persist.PersistManager.load(PersistManager.java:141)
	at water.Value.loadPersist(Value.java:226)
	at water.Value.memOrLoad(Value.java:123)
	at water.Value.get(Value.java:137)
	at water.fvec.Vec.chunkForChunkIdx(Vec.java:794)
	at water.fvec.ByteVec.chunkForChunkIdx(ByteVec.java:18)
	at water.fvec.ByteVec.chunkForChunkIdx(ByteVec.java:14)
	at water.MRTask.compute2(MRTask.java:426)
	at water.MRTask.compute2(MRTask.java:398)
```

This error output displays if the input file is not present on all nodes. Because of the way that Sparkling Water distributes data, the input file is required on all nodes (including remote), not just the primary node. Make sure there is a copy of the input file on all the nodes, then try again. 

---

**Are there any drawbacks to using Sparkling Water compared to standalone H2O?**

The version of H2O embedded in Sparkling Water is the same as the standalone version. 

---

##Tunneling between servers with H2O

To tunnel between servers (for example, due to firewalls): 

1. Use ssh to log in to the machine where H2O will run.
2. Start an instance of H2O by locating the working directory and calling a java command similar to the following example. 

 The port number chosen here is arbitrary; yours may be different.

 `$ java -jar h2o.jar -port  55599`

 This returns output similar to the following:

	```
	irene@mr-0x3:~/target$ java -jar h2o.jar -port 55599
	04:48:58.053 main      INFO WATER: ----- H2O started -----
	04:48:58.055 main      INFO WATER: Build git branch: master
	04:48:58.055 main      INFO WATER: Build git hash: 64fe68c59ced5875ac6bac26a784ce210ef9f7a0
	04:48:58.055 main      INFO WATER: Build git describe: 64fe68c
	04:48:58.055 main      INFO WATER: Build project version: 1.7.0.99999
	04:48:58.055 main      INFO WATER: Built by: 'Irene'
	04:48:58.055 main      INFO WATER: Built on: 'Wed Sep  4 07:30:45 PDT 2013'
	04:48:58.055 main      INFO WATER: Java availableProcessors: 4
	04:48:58.059 main      INFO WATER: Java heap totalMemory: 0.47 gb
	04:48:58.059 main      INFO WATER: Java heap maxMemory: 6.96 gb
	04:48:58.060 main      INFO WATER: ICE root: '/tmp'
	04:48:58.081 main      INFO WATER: Internal communication uses port: 55600
	+                                  Listening for HTTP and REST traffic on
	+                                  http://192.168.1.173:55599/
	04:48:58.109 main      INFO WATER: H2O cloud name: 'irene'
	04:48:58.109 main      INFO WATER: (v1.7.0.99999) 'irene' on
	/192.168.1.173:55599, discovery address /230 .252.255.19:59132
	04:48:58.111 main      INFO WATER: Cloud of size 1 formed [/192.168.1.173:55599]
	04:48:58.247 main      INFO WATER: Log dir: '/tmp/h2ologs'
	```

3. Log into the remote machine where the running instance of H2O will be forwarded using a command similar to the following (your specified port numbers and IP address will be different)

 	`ssh -L 55577:localhost:55599 irene@192.168.1.173`

4. Check the cluster status.

You are now using H2O from localhost:55577, but the
instance of H2O is running on the remote server (in this
case the server with the ip address 192.168.1.xxx) at port number 55599.

To see this in action note that the web UI is pointed at
localhost:55577, but that the cluster status shows the cluster running
on 192.168.1.173:55599

    
---

