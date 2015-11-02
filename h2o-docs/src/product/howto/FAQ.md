#FAQ

##General Troubleshooting Tips


- Confirm your internet connection is active. 

- Test connectivity using curl: First, log in to the first node and enter `curl http://<Node2IP>:54321` (where `<Node2IP>` is the IP address of the second node. Then, log in to the second node and enter `curl http://<Node1IP>:54321` (where `<Node1IP>` is the IP address of the first node). Look for output from H2O.

- Try allocating more memory to H2O by modifying the `-Xmx` value when launching H2O from the command line (for example, `java -Xmx10g -jar h2o.jar` allocates 10g of memory for H2O). If you create a cluster with four 20g nodes (by specifying `-Xmx20g` four times), H2O will have a total of 80 gigs of memory available. For best performance, we recommend sizing your cluster to be about four times the size of your data. To avoid swapping, the `-Xmx` allocation must not exceed the physical memory on any node. Allocating the same amount of memory for all nodes is strongly recommended, as H2O works best with symmetric nodes.

- Confirm that no other sessions of H2O are running. To stop all running H2O sessions, enter `ps -efww | grep h2o` in your shell (OSX or Linux). 
- Confirm ports 54321 and 54322 are available for both TCP and UDP. Launch Telnet (for Windows users) or Terminal (for OS X users), then type `telnet localhost 54321`, `telnet localhost 54322`
- Confirm your firewall is not preventing the nodes from locating each other. If you can't launch H2O, we recommend temporarily disabling any firewalls until you can confirm they are not preventing H2O from launching. 
- Confirm the nodes are not using different versions of H2O. If the H2O initialization is not successful, look at the output in the shell - if you see `Attempting to join /localhost:54321 with an H2O version mismatch (md5 differs)`, update H2O on all the nodes to the same version.
- Confirm that there is space in the `/tmp` directory. 
	- Windows: In Command Prompt, enter `TEMP` and `%TEMP%` and delete files as needed, or use Disk Cleanup. 
	- OS X: In Terminal, enter `open $TMPDIR` and delete the folder with your username. 
- Confirm that the username is the same on all nodes; if not, define the cloud in the terminal when launching using `-name`:`java -jar h2o.jar -name myCloud`.
- Confirm that there are no spaces in the file path name used to launch H2O. 
- Confirm that the nodes are not on different networks by confirming that the IP addresses of the nodes are the same in the output: 
 ```
 INFO: Listening for HTTP and REST traffic on  IP_Address/
06-18 10:54:21.586 192.168.1.70:54323    25638  main      
INFO: H2O cloud name: 'H2O_User' on IP_Address, discovery address /Discovery_Address
INFO: Cloud of size 1 formed [IP_Address]
```
- Check if the nodes have different interfaces; if so, use the -network option to define the network (for example, `-network 127.0.0.1`). To use a network range, use a comma to separate the IP addresses (for example, `-network 123.45.67.0/22,123.45.68.0/24`).
- Force the bind address using `-ip`:`java -jar h2o.jar -ip <IP_Address> -port <PortNumber>`.
- (Hadoop only) Try launching H2O with a longer timeout: `hadoop jar h2odriver.jar -timeout 1800`
- (Hadoop only) Try to launch H2O using more memory: `hadoop jar h2odriver.jar -mapperXmx 10g`. The cluster’s memory capacity is the sum of all H2O nodes in the cluster. 
- (Linux only) Check if you have SELINUX or IPTABLES enabled; if so, disable them.
- (EC2 only) Check the configuration for the EC2 security group.

---

**The following error message displayed when I tried to launch H2O - what should I do?**

```
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
```
This error output indicates that your Java version is not supported. Upgrade to [Java 7 (JVM)](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html) or [later](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html) and H2O should launch successfully. 

---



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

**What are the "best practices" for preparing data for a K-Means model?**

There aren't specific "best practices," as it depends on your data and the column types. However, removing outliers and transforming any categorical columns to have the same weight as the numeric columns will help, especially if you're standardizing your data. 


---

**What is your implementation of Deep Learning based on?**

 Our Deep Learning algorithm is based on the feedforward neural net. For more information, refer to our Data Science documentation or [Wikipedia](https://en.wikipedia.org/wiki/Feedforward_neural_network). 

---

**How is deviance computed for a Deep Learning regression model?**

For a Deep Learning regression model, deviance is computed as follows: 

Loss = MeanSquare -> MSE==Deviance
For Absolute/Laplace or Huber -> MSE != Deviance. 

---

**For my 0-tree GBM multinomial model, I got a different score depending on whether or not validation was enabled, even though my dataset was the same - why is that?**

Different results may be generated because of the way H2O computes the initial MSE. 


---

**How does your Deep Learning Autoencoder work? Is it deep or shallow?**

H2O’s DL autoencoder is based on the standard deep (multi-layer) neural net architecture, where the entire network is learned together, instead of being stacked layer-by-layer.  The only difference is that no response is required in the input and that the output layer has as many neurons as the input layer. If you don’t achieve convergence, then try using the *Tanh* activation and fewer layers.  We have some example test scripts [here](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/deeplearning/), and even some that show [how stacked auto-encoders can be implemented in R](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/deeplearning/runit_deeplearning_stacked_autoencoder_large.R). 




---

**Are there any H2O examples using text for classification?**

Currently, the following examples are available for Sparkling Water: 

a) Use TF-IDF weighting scheme for classifying text messages 
https://github.com/h2oai/sparkling-water/blob/master/examples/scripts/mlconf_2015_hamSpam.script.scala 

b) Use Word2Vec Skip-gram model + GBM for classifying job titles 
https://github.com/h2oai/sparkling-water/blob/master/examples/scripts/craigslistJobTitles.scala 

---

**Most machine learning tools cannot predict with a new categorical level that was not included in the training set. How does H2O make predictions in this scenario?**

Here is an example of how the prediction process works in H2O: 

0. Train a model using data that has a categorical predictor column with levels B,C, and D (no other levels); this level will be the "training set domain": {B,C,D}
0. During scoring, the test set has only rows with levels A,C, and E for that column; this is the "test set domain": {A,C,E}
0. For scoring, a combined "scoring domain" is created, which is the training domain appended with the extra test set domain entries: {B,C,D,A,E} 
0. Each model can handle these extra levels {A,E} separately during scoring. 

The behavior for unseen categorical levels depends on the algorithm and how it handles missing levels (NA values): 

- DRF and GBM treat missing or NA factor levels as the smallest value present (left-most in the bins), which can go left or right for any split. Unseen factor levels always go left in any split. 
- Deep Learning creates an extra input neuron for missing and unseen categorical levels, which can remain untrained if there were no missing or unseen categorical levels in the training data, resulting in a random contribution to the next layer during testing. 
- GLM skips unseen levels in the beta*x dot product. 

---

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

**How does the timeline tool work?**

The timeline is a debugging tool that provides information on the current communication between H2O nodes. It shows a snapshot of the most recent messages passed between the nodes. Each node retains its own history of messages sent to or received from other nodes. 

H2O collects these messages from all the nodes and orders them by whether they were sent or received. Each node has an implicit internal order where sent messages must precede received messages on the other node. 

The following information displays for each message: 

- `HH:MM:SS:MS` and `nanosec`: The local time of the event
- `Who`: The endpoint of the message; can be either a source/receiver node or source node and multicast for broadcasted messages
- `I/O Type`: The type of communication (either UDP for small messages or TCP for large messages)
   >**Note**: UDP messages are only sent if the UDP option was enabled when launching H2O or for multicast when a flatfile is not used for configuration. 
- `Event`: The type of H2O message. The most common type is a distributed task, which displays as `exec` (the requested task) -> `ack` (results of the processed task) -> `ackck` (sender acknowledges receiving the response, task is completed and removed)
- `rebooted`: Sent during node startup 
- `heartbeat`: Provides small message tracking information about node health, exchanged periodically between nodes
- `fetchack`: Aknowledgement of the `Fetch` type task, which retrieves the ID of a previously unseen type
- `bytes`: Information extracted from the message, including the type of the task and the unique task number 



---


##Data

**How should I format my SVMLight data before importing?**

The data must be formatted as a sorted list of unique integers, the column indices must be >= 1, and the columns must be in ascending order. 

---


**What date and time formats does H2O support?**

H2O is set to auto-detect two major date/time formats. Because many date time formats are ambiguous (e.g. 01/02/03), general date time detection is not used.  

The first format is for dates formatted as yyyy-MM-dd. Year is a four-digit number, the month is a two-digit number ranging from 1 to 12, and the day is a two-digit value ranging from 1 to 31. This format can also be followed by a space and then a time (specified below). 

The second date format is for dates formatted as dd-MMM-yy. Here the day must be one or two digits with a value ranging from 1 to 31. The month must be either a three-letter abbreviation or the full month name but is not case sensitive. The year must be either two or four digits. In agreement with [POSIX](https://en.wikipedia.org/wiki/POSIX) standards, two-digit dates >= 69 are assumed to be in the 20th century (e.g. 1969) and the rest are part of the 21st century. This date format can be followed by either a space or colon character and then a time. The '-' between the values is optional. 

Times are specified as HH:mm:ss. HH is a two-digit hour and must be a value between 0-23 (for 24-hour time) or 1-12 (for a twelve-hour clock). mm is a two-digit minute value and must be a value between 0-59. ss is a two-digit second value and must be a value between 0-59. This format can be followed with either milliseconds, nanoseconds, and/or the cycle (i.e. AM/PM). If milliseconds are included, the format is HH:mm:ss:SSS. If nanoseconds are included, the format is HH:mm:ss:SSSnnnnnn. H2O only stores fractions of a second up to the millisecond, so accuracy may be lost. Nanosecond parsing is only included for convenience. Finally, a valid time can end with a space character and then either "AM" or "PM". For this format, the hours must range from 1 to 12. Within the time, the ':' character can be replaced with a '.' character.

---

**How does H2O handle name collisions/conflicts in the dataset?**

If there is a name conflict (for example, column 48 isn't named, but C48 already exists), then the column name in concatenated to itself until a unique name is created. So for the previously cited example, H2O will try renaming the column to C48C48, then C48C48C48, and so on until an unused name is generated. 

---

**What types of data columns does H2O support?**

Currently, H2O supports: 

- float (any IEEE double)
- integer (up to 64bit, but compressed according to actual range)
- factor (same as integer, but with a String mapping, often handled differently in the algorithms)
- time (same as 64bit integer, but with a time-since-Unix-epoch interpretation)
- UUID (128bit integer, no math allowed)
- String

---

**I am trying to parse a Gzip data file containing multiple files, but it does not parse as quickly as the uncompressed files. Why is this?**

Parsing Gzip files is not done in parallel, so it is sequential and uses only one core. Other parallel parse compression schemes are on the roadmap. 



---


##General

**How do I score using an exported JSON model?**

Since JSON is just a representation format, it cannot be directly executed, so a JSON export can't be used for scoring. However, you can score by: 

- including the POJO in your execution stream and handing it observations one at a time 

  or

- handing your data in bulk to an H2O cluster, which will score using high throughput parallel and distributed bulk scoring. 


---

**How do I score using an exported POJO?**

The generated POJO can be used indepedently of a H2O cluster. First use `curl` to send the h2o-genmodel.jar file and the java code for model to the server. The following is an example; the ip address and model names will need to be changed. 

```
mkdir tmpdir
cd tmpdir
curl http://127.0.0.1:54321/3/h2o-genmodel.jar > h2o-genmodel.jar
curl http://127.0.0.1:54321/3/Models.java/gbm_model > gbm_model.java
```

To score a simple .CSV file, download the [PredictCSV.java](https://raw.githubusercontent.com/h2oai/h2o-3/master/h2o-r/tests/testdir_javapredict/PredictCSV.java) file and compile it with the POJO. Make a subdirectory for the compilation (this is useful if you have multiple models to score on).

```
wget https://raw.githubusercontent.com/h2oai/h2o-3/master/h2o-r/tests/testdir_javapredict/PredictCSV.java
mkdir gbm_model_dir
javac -cp h2o-genmodel.jar -J-Xmx2g -J-XX:MaxPermSize=128m PredictCSV.java gbm_model.java -d gbm_model_dir
``` 

Specify the following:
- the classpath using `-cp` 
- the model name (or class) using `--model` 
- the csv file you want to score using `--input` 
- the location for the predictions using `--output`. 
 
You must match the table column names to the order specified in the POJO. The output file will be in a .hex format, which is a lossless text representation of floating point numbers. Both R and Java will be able to read the hex strings as numerics.

```
java -ea -cp h2o-genmodel.jar:gbm_model_dir -Xmx4g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m PredictCSV --header --model gbm_model --input input.csv --output output.csv
```

---

**How do I predict using multiple response variables?**

Currently, H2O does not support multiple response variables. To predict different response variables, build multiple models. 

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

**I received the following error message when launching H2O - how do I resolve the error?**

```
Invalid flow_dir illegal character at index 12...
```

This error message means that there is a space (or other unsupported character) in your H2O directory. To resolve this error: 

- Create a new folder without unsupported characters to use as the H2O directory (for example, `C:\h2o`). 

  or 
  
- Specify a different save directory using the `-flow_dir` parameter when launching H2O: `java -jar h2o.jar -flow_dir test`

---

**How does `importFiles()` work in H2O?**

`importFiles()` gets the basic information for the file and then returns a key representing that file. This key is used during parsing to read in the file and to save space so that the file isn't loaded every time; instead, it is loaded into H2O then referenced using the key. For files hosted online, H2O verifies the destination is valid, creates a vec that loads the file when necessary, and returns a key.


---

**Does H2O support GPUs?**

Currently, we do not support this capability. If you are interested in contributing your efforts to support this feature to our open-source code database, please contact us at [h2ostream@googlegroups.com](mailto:h2ostream@googlegroups.com). 


---

**How can I continue working on a model in H2O after restarting?**

There are a number of ways you can save your model in H2O: 

- In the web UI, click the **Flow** menu then click **Save Flow**. Your flow is saved to the *Flows* tab in the **Help** sidebar on the right. 
- In the web UI, click the **Flow** menu then click **Download this Flow...**. Your flow is saved to the location you specify in the pop-up **Save As** window that appears. 
- (For DRF, GBM, and DL models only): Use model checkpointing to resume training a model. Copy the `model_id` number from a built model and paste it into the *checkpoint* field in the `buildModel` cell. 


---

**How can I find out more about H2O's real-time, nano-fast scoring engine?** 

H2O's scoring engine uses a Plain Old Java Object (POJO). The POJO code runs quickly but is single-threaded.  It is intended for embedding into lightweight real-time environments.

All the work is done by the call to the appropriate predict method.  There is no involvement from H2O in this case.

To compare multiple models simultaneously, use the POJO to call the models using multiple threads. For more information on using POJOs, refer to the [POJO Quick Start Guide](http://h2o-release.s3.amazonaws.com/h2o/master/3167/docs-website/h2o-docs/index.html#POJO%20Quick%20Start) and [POJO Java Documentation](http://h2o-release.s3.amazonaws.com/h2o/master/3167/docs-website/h2o-genmodel/javadoc/index.html)

In-H2O scoring is triggered on an existing H2O cluster, typically using a REST API call. H2O evaluates the predictions in a parallel and distributed fashion for this case.  The predictions are stored into a new Frame and can be written out using `h2o.exportFile()`, for example.

---

**I am using an older version of H2O (2.8 or prior) - where can I find documentation for this version?**

If you are using H2O 2.8 or prior, we strongly recommend <a href="http://h2o.ai/download/" target="_blank">upgrading to the latest version of H2O</a> if possible. 

If you do not wish to upgrade to the latest version, documentation for H2O Classic is available [here](http://docs.h2o.ai/h2oclassic/index.html). 

---

##Hadoop


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

**How do I specify which nodes should run H2O in a Hadoop cluster?**

After creating and applying the desired node labels and associating them with specific queues as described in the [Hadoop documentation](http://docs.hortonworks.com/HDPDocuments/HDP2/HDP-2.2.0/YARN_RM_v22/node_labels/index.html#Item1.1), launch H2O using the following command: 

`hadoop jar h2odriver.jar -Dmapreduce.job.queuename=<my-h2o-queue> -nodes <num-nodes> -mapperXmx 6g -output hdfsOutputDirName`


- `-Dmapreduce.job.queuename=<my-h2o-queue>` represents the queue name
- `-nodes <num-nodes>` represents the number of nodes
- `-mapperXmx 6g` launches H2O with 6g of memory
- `-output hdfsOutputDirName` specifies the HDFS output directory as `hdfsOutputDirName`


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

**I keep getting a message that I need to install Java. I have the latest version of Java installed, but I am still getting this message. What should I do?**

This error message displays if the `JAVA_HOME` environment variable is not set correctly. The `JAVA_HOME` variable is likely points to Apple Java version 6 instead of Oracle Java version 8. 

If you are running OS X 10.7 or earlier, enter the following in Terminal: 
`export JAVA_HOME=/Library/Internet\ Plug-Ins/JavaAppletPlugin.plugin/Contents/Home`

If you are running OS X 10.8 or later, modify the launchd.plist by entering the following in Terminal: 

```
cat << EOF | sudo tee /Library/LaunchDaemons/setenv.JAVA_HOME.plist
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
  <plist version="1.0">
  <dict>
  <key>Label</key>
  <string>setenv.JAVA_HOME</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/launchctl</string>
    <string>setenv</string>
    <string>JAVA_HOME</string>
    <string>/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>ServiceIPC</key>
  <false/>
</dict>
</plist>
EOF
```


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

**How should I specify the datatype during import in Python?**

Refer to the following example: 

```
#Let's say you want to change the second column "CAPSULE" of prostate.csv
#to categorical. You have 3 options.

#Option 1. Use a dictionary of column names to types. 
fr = h2o.import_file("smalldata/logreg/prostate.csv", col_types = {"CAPSULE":"Enum"})
fr.describe()

#Option 2. Use a list of column types.
c_types = [None]*9
c_types[1] = "Enum"
fr = h2o.import_file("smalldata/logreg/prostate.csv", col_types = c_types)
fr.describe()

#Option 3. Use parse_setup().
fraw = h2o.import_file("smalldata/logreg/prostate.csv", parse = False)
fsetup = h2o.parse_setup(fraw) 
fsetup["column_types"][1] = '"Enum"'
fr = h2o.parse_raw(fsetup) 
fr.describe()
```

---

**How do I view a list of variable importances in Python?**

Use `model.varimp(return_list=True)` as shown in the following example:

```
model = h2o.gbm(y = "IsDepDelayed", x = ["Month"], training_frame = df)
vi = model.varimp(return_list=True)
Out[26]:
[(u'Month', 69.27436828613281, 1.0, 1.0)]
```



---

##R

**Which versions of R are compatible with H2O?**

Currently, the only version of R that is known to not work well with H2O is R version 3.1.0 (codename "Spring Dance"). If you are using this version, we recommend upgrading the R version before using H2O. 



---

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
Error in h2o.init() : 
Version mismatch! H2O is running version 3.2.0.9 but R package is version 3.2.0.3
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
if (! ("jsonlite" %in% rownames(installed.packages()))) { install.packages("jsonlite") }
if (! ("tools" %in% rownames(installed.packages()))) { install.packages("tools") }
if (! ("utils" %in% rownames(installed.packages()))) { install.packages("utils") }
```


Finally, install the latest version of the H2O package for R: 

```
install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/master/{{build_number}}/R")))
library(h2o)
localH2O = h2o.init(nthreads=-1)
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

**How do I extract the model weights from a model I've creating using H2O in R? I've enabled `extract_model_weights_and_biases`, but the output refers to a file I can't open in R.**

For an example of how to extract weights and biases from a model, refer to the following repo location on [GitHub](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/deeplearning/runit_deeplearning_weights_and_biases.R). 

---

**I'm using CentOS and I want to run H2O in R - are there any dependencies I need to install?**

Yes, make sure to install `libcurl`, which allows H2O to communicate with R. We also recommend disabling SElinux and any firewalls, at least initially until you have confirmed H2O can initialize. 

---

**How do I change variable/header names on an H2O frame in R?**

There are two ways to change header names. To specify the headers during parsing, import the headers in R and then specify the header as the column name when the actual data frame is imported: 

```
header <- h2o.importFile(path = pathToHeader)
data   <- h2o.importFile(path = pathToData, col.names = header)
data
```

You can also use the `names()` function: 
```
header <- c("user", "specified", "column", "names")
data   <- h2o.importFile(path = pathToData)
names(data) <- header
```

To replace specific column names, you can also use a `sub/gsub` in R: 

```
header <- c("user", "specified", "column", "names")
## I want to replace "user" column with "computer"
data   <- h2o.importFile(path = pathToData)
names(data) <- sub(pattern = "user", replacement = "computer", x = names(header))
```
---

**My R terminal crashed - how can I re-access my H2O frame?**

Launch H2O and use your web browser to access the web UI, Flow, at `localhost:54321`. Click the **Data** menu, then click **List All Frames**. Copy the frame ID, then run `h2o.ls()` in R to list all the frames, or use the frame ID in the following code (replacing `YOUR_FRAME_ID` with the frame ID): 

```
library(h2o)
localH2O = h2o.init(ip="sri.h2o.ai", port=54321, startH2O = F, strict_version_check=T)
data_frame <- h2o.getFrame(frame_id = "YOUR_FRAME_ID",conn = localH2O)
``` 
---

**How do I remove rows containing NAs in an H2OFrame?**

To remove NAs from rows:

```
  a   b    c    d    e
1 0   NA   NA   NA   NA
2 0   2    2    2    2
3 0   NA   NA   NA   NA
4 0   NA   NA   1    2
5 0   NA   NA   NA   NA
6 0   1    2    3    2
```

Removing rows 1, 3, 4, 5 to get:

```
  a   b    c    d    e
2 0   2    2    2    2
6 0   1    2    3    2
```

Use `na.omit(myFrame)`, where `myFrame` represents the name of the frame you are editing. 

---

**I installed H2O in R using OS X and updated all the dependencies, but the following error message displayed: `Error in .h2o.doSafeREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, Unexpected CURL error: Empty reply from server` - what should I do?**


This error message displays if the `JAVA_HOME` environment variable is not set correctly. The `JAVA_HOME` variable is likely points to Apple Java version 6 instead of Oracle Java version 8. 

If you are running OS X 10.7 or earlier, enter the following in Terminal: 
`export JAVA_HOME=/Library/Internet\ Plug-Ins/JavaAppletPlugin.plugin/Contents/Home`

If you are running OS X 10.8 or later, modify the launchd.plist by entering the following in Terminal: 

```
cat << EOF | sudo tee /Library/LaunchDaemons/setenv.JAVA_HOME.plist
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
  <plist version="1.0">
  <dict>
  <key>Label</key>
  <string>setenv.JAVA_HOME</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/launchctl</string>
    <string>setenv</string>
    <string>JAVA_HOME</string>
    <string>/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>ServiceIPC</key>
  <false/>
</dict>
</plist>
EOF
```

---

<!---

in progress - commenting out until complete

**How do I extract the variable importance from the output in R?**

Launch R, then enter the following: 

```
library(h2o)
h <- h2o.init()
as.h2o(iris)
as.h2o(testing)
m <- h2o.gbm(x=1:4, y=5, data=hex, importance=T)

m@model$varimp
             Relative importance Scaled.Values Percent.Influence
Petal.Width          7.216290000  1.0000000000       51.22833426
Petal.Length         6.851120500  0.9493965043       48.63600147
Sepal.Length         0.013625654  0.0018881799        0.09672831
Sepal.Width          0.005484723  0.0007600474        0.03893596
```

The variable importances are returned as an R data frame and you can extract the names and values of the data frame as follows:

```
is.data.frame(m@model$varimp)
# [1] TRUE

names(m@model$varimp)
# [1] "Relative importance" "Scaled.Values"       "Percent.Influence"  

rownames(m@model$varimp)
# [1] "Petal.Width"  "Petal.Length" "Sepal.Length" "Sepal.Width"

m@model$varimp$"Relative importance"
# [1] 7.216290000 6.851120500 0.013625654 0.005484723
```

-->


---

**How does the `col.names` argument work in `group_by`?**

You need to add the `col.names` inside the `gb.control` list. Refer to the following example:

```
newframe <- h2o.group_by(dd, by="footwear_category", nrow("email_event_click_ct"), sum("email_event_click_ct"), mean("email_event_click_ct"),
    sd("email_event_click_ct"), gb.control = list( col.names=c("count", "total_email_event_click_ct", "avg_email_event_click_ct", "std_email_event_click_ct") ) )
newframe$avg_email_event_click_ct2 = newframe$total_email_event_click_ct / newframe$count
```

---

**How are the results of `h2o.predict` displayed?**


The order of the rows in the results for `h2o.predict` is the same as the order in which the data was loaded, even if some rows fail (for example, due to missing values or unseen factor levels). To bind a per-row identifier, use `cbind`. 

---

**How do I view all the variable importances for a model?**

By default, H2O returns the top five and lowest five variable importances. 
To view all the variable importances, use the following: 

```
model <- h2o.getModel(model_id = "my_H2O_modelID",conn=localH2O)

varimp<-as.data.frame(h2o.varimp(model))
```


---

**How do I add random noise to a column in an H2O frame?**

To add random noise to a column in an H2O frame, refer to the following example: 

```
h2o.init()

fr <- as.h2o(iris)

  |======================================================================| 100%

random_column <- h2o.runif(fr)

new_fr <- h2o.cbind(fr,random_column)

new_fr
```



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

**How do I use Sparkling Water from the Spark shell?**

There are two methods: 

- Use `$SPARK_HOME/bin/spark-shell --packages ai.h2o:sparkling-water-core_2.10:1.3.3`

  or 
  
- `bin/sparkling-shell`

The software distribution provides example scripts in the `examples/scripts` directory: 

`bin/sparkling-shell -i examples/scripts/chicagoCrimeSmallShell.script.scala`

For either method, initialize H2O as shown in the following example: 

```
import org.apache.spark.h2o._
val h2oContext = new H2OContext(sc).start()
```

After successfully launching H2O, the following output displays: 

```
Sparkling Water Context:
 * number of executors: 3
 * list of used executors:
  (executorId, host, port)
  ------------------------
  (1,Michals-MBP.0xdata.loc,54325)
  (0,Michals-MBP.0xdata.loc,54321)
  (2,Michals-MBP.0xdata.loc,54323)
  ------------------------

  Open H2O Flow in browser: http://172.16.2.223:54327 (CMD + click in Mac OSX)
  
```

---

**How do I use H2O with Spark Submit?**

Spark Submit is for submitting self-contained applications. For more information, refer to the [Spark documentation](https://spark.apache.org/docs/latest/quick-start.html#self-contained-applications). 

First, initialize H2O: 

```
import org.apache.spark.h2o._
val h2oContext = new H2OContext(sc).start()
```

The Sparkling Water distribution provides several examples of self-contained applications built with Sparkling Water. To run the examples: 

`bin/run-example.sh ChicagoCrimeAppSmall`

The "magic" behind `run-example.sh` is a regular Spark Submit: 

`$SPARK_HOME/bin/spark-submit ChicagoCrimeAppSmall --packages ai.h2o:sparkling-water-core_2.10:1.3.3 --packages ai.h2o:sparkling-water-examples_2.10:1.3.3`

---

**How do I use Sparkling Water with Databricks cloud?**

Sparkling Water compatibility with Databricks cloud is still in development. 



---

**How do I develop applications with Sparkling Water?**

For a regular Spark application (a self-contained application as described in the [Spark documentation](https://spark.apache.org/docs/latest/quick-start.html#self-contained-applications)), the app needs to initialize `H2OServices` via `H2OContext`: 

```
import org.apache.spark.h2o._
val h2oContext = new H2OContext(sc).start()
```

For more information, refer to the [Sparkling Water development documentation](https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md). 

---

**How do I connect to Sparkling Water from R or Python?**

After starting `H2OServices` by starting `H2OContext`, point your client to the IP address and port number specified in `H2OContext`. 

---
**I'm getting a `java.lang.ArrayIndexOutOfBoundsException` when I try to run Sparkling Water - what do I need to do to resolve this error?**

This error message displays if you have not set up the `H2OContext` before running Sparkling Water. To set up the `H2OContext`: 

```
import org.apache.spark.h2o._
val h2oContext = new H2OContext(sc)
```
After setting up `H2OContext`, try to run Sparkling Water again. 

---





---

##Tableau

**Where can I learn more about running H2O with Tableau?**

For more information about using H2O with Tableau, refer to [this link](http://learn.h2o.ai/content/demos/integration_with_tableau_and_excel.html) and our [demo](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_demos/runit_demo_tableau.R) in our GitHub repository. Other demos are available [here](https://s3-us-west-1.amazonaws.com/testing-amy/Demo_Template_9.0Windows.twb) and [here](https://github.com/h2oai/h2o/blob/master/tableau/meta_data/airlines_meta.csv). 

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


