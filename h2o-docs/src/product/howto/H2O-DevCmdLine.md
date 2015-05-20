# ... From the Cmd Line

You can use Terminal (OS X) or the Command Prompt (Windows) to launch H2O 3.0. When you launch from the command line, you can include additional instructions to H2O 3.0, such as how many nodes to launch, how much memory to allocate for each node, assign names to the nodes in the cloud, and more. 

There are two different argument types:

- JVM arguments
- H2O arguments

The arguments use the following format: java `<JVM Options>` -jar h2o.jar `<H2O Options>`.  

##JVM Options

- `-version`: Display Java version info. 
- `-Xmx<Heap Size>`: To set the total heap size for an H2O node, configure the memory allocation option `-Xmx`. By default, this option is set to 1 Gb (`-Xmx1g`). When launching nodes, we recommend allocating a total of four times the memory of your data. 

> **Note**: Do not try to launch H2O with more memory than you have available. 


##H2O Options

- `h` or `-help`: Display this information in the command line output. 
- `-name <H2OCloudName>`: Assign a name to the H2O instance in the cloud (where `<H2OCloudName>` is the name of the cloud. Nodes with the same cloud name will form an H2O cloud (also known as an H2O cluster). 
- `-flatfile <FileName>`: Specify a flatfile of IP address for faster cloud formation (where `<FileName>` is the name of the flatfile. 
- `-ip <IPnodeAddress>`: Specify an IP address other than the default `localhost` for the node to use (where `<IPnodeAddress>` is the IP address). 
- `-port <#>`: Specify a port number other than the default `54321` for the node to use (where `<#>` is the port number). 
- `-network <IPv4NetworkSpecification1>[,<IPv4NetworkSpecification2> ...]`: Specify a range (where applicable) of IP addresses (where `<IPv4NetworkSpecification1>` represents the first interface, `<IPv4NetworkSpecification2>` represents the second, and so on). The IP address discovery code binds to the first interface that matches one of the networks in the comma-separated list. For example, `10.1.2.0/24` supports 256 possibilities. 
- `-ice_root <fileSystemPath>`: Specify a directory for H2O to spill temporary data to disk (where `<fileSystemPath>` is the file path). 
- `-flow_dir <server-side or HDFS directory>`: Specify a directory for saved flows. The default is `/Users/h2o-<H2OUserName>/h2oflows` (where `<H2OUserName>` is your user name). 
- `nthreads <#ofThreads>`: Specify the maximum number of threads in the low-priority batch work queue (where `<#ofThreads>` is the number of threads). The default is 99. 
- `-client`: Launch H2O node in client mode. This is used mostly for running Sparkling Water. 


##Cloud Formation Behavior

New H2O nodes join to form a cloud during launch. After a job has started on the cloud, it  prevents new members from joining. 

- To start an H2O node with 4GB of memory and a default cloud name: 
  `java -Xmx4g -jar h2o.jar`

- To start an H2O node with 6GB of memory and a specific cloud name: 
  `java -Xmx6g -jar h2o.jar -name MyCloud`

- To start an H2O cloud with three 2GB nodes using the default cloud names: 
  `java -Xmx2g -jar h2o.jar &`
  `java -Xmx2g -jar h2o.jar &`
  `java -Xmx2g -jar h2o.jar &`

Wait for the `INFO: Registered: # schemas in: #mS` output before entering the above command again to add another node (the number for # will vary).

##Flatfile Configuration

If you are configuring many nodes, it is faster and easier to use the `-flatfile` option, rather than `-ip` and `-port`. 

To configure H2O on a multi-node cluster:

0. Locate a set of hosts. 
0. [Download](http://h2o.ai/download) the appropriate version of H2O for your environment. 
0. Verify that the same h2o.jar file is available on all hosts. 
0. Create a flatfile (a plain text file with the IP and port numbers of the hosts). Use one entry per line. For example:
   
   ```
   192.168.1.163:54321
   192.168.1.164:54321   
   ```
0. Copy the flatfile.txt to each node in the cluster. 
0. Use the `-Xmx` option to specify the amount of memory for each node. The cluster's memory capacity is the sum of all H2O nodes in the cluster. 

 For example, if you create a cluster with four 20g nodes (by specifying `-Xmx20g` four times), H2O will have a total of 80 gigs of memory available. 

 For best performance, we recommend sizing your cluster to be about four times the size of your data. To avoid swapping, the `-Xmx` allocation must not exceed the physical memory on any node. Allocating the same amount of memory for all nodes is strongly recommended, as H2O works best with symmetric nodes. 

 Note the optional `-ip` and `-port` options specify the IP address and ports to use. The `-ip` option is especially helpful for hosts with multiple network interfaces. 

 `java -Xmx20g -jar h2o.jar -flatfile flatfile.txt -port 54321`

 The output will resemble the following: 

	```
	04-20 16:14:00.253 192.168.1.70:54321    2754   main      INFO:   1. Open a terminal and run 'ssh -L 55555:localhost:54321 H2O-DevUser@###.###.#.##'
	04-20 16:14:00.253 192.168.1.70:54321    2754   main      INFO:   2. Point your browser to http://localhost:55555
	04-20 16:14:00.437 192.168.1.70:54321    2754   main      INFO: Log dir: '/tmp/h2o-H2O-DevUser/h2ologs'
	04-20 16:14:00.437 192.168.1.70:54321    2754   main      INFO: Cur dir: '/Users/H2O-DevUser/h2o-dev'
	04-20 16:14:00.459 192.168.1.70:54321    2754   main      INFO: HDFS subsystem successfully initialized
	04-20 16:14:00.460 192.168.1.70:54321    2754   main      INFO: S3 subsystem successfully initialized
	04-20 16:14:00.460 192.168.1.70:54321    2754   main      INFO: Flow dir: '/Users/H2O-DevUser/h2oflows'
	04-20 16:14:00.475 192.168.1.70:54321    2754   main      INFO: Cloud of size 1 formed [/192.168.1.70:54321]
	```

 As you add more nodes to your cluster, the output is updated:
`INFO WATER: Cloud of size 2 formed [/...]...`

0. Access the H2O 3.0 web UI (Flow) with your browser. Point your browser to the HTTP address specified in the output `Listening for HTTP and REST traffic on ...`. 

  To check if the cloud is available, point to the url `http://<ip>:<port>/Cloud.json` (an example of the JSON response is provided below). Wait for `cloud_size` to be the expected value and the `consensus` field to be true: 
  
  ```
  {
  ...

  "cloud_size": 2,
  "consensus": true,

  ...
  }
  ```



## Manual Multi-node

Running H2O on a multi-node cluster allows you to use more memory for large-scale tasks (for example, creating models from huge datasets) than would be possible on a single node. 

1. Locate a set of hosts that will be used to create your cluster. A host can be a server, an EC2 instance, or your laptop.

2. Download H2O, including the .jar file, by going to the [H2O downloads page](http://h2o.ai/download/) and choosing the appropriate version for your environment.

3. Verify the same h2o.jar file is available on each host in the multi-node cluster.

4. Create a flatfile.txt that contains an IP address and port number for each H2O instance. Use one entry per line.  For example:

  ``` 
  192.168.1.163:54321
  192.168.1.164:54321
  ```

   A flat file listing the nodes is the easiest way to get multiple H2O nodes to find each other and form a cluster. Note that the `-flatfile` option tells one H2O node where to find the others.  It is not a substitute for the `-ip` and `-port` specification.

5. Copy the flatfile.txt to each node in your cluster.

6. Use the `-Xmx` option in the Java command line to specify the amount of memory allocated to each H2O node.  The cluster's memory capacity is the sum of the memory available across all H2O nodes in the cluster.

   For example, if you create a cluster with four 20g nodes (by specifying `-Xmx20g`), H2O will have a total of 80 gigs of memory available.

   For best performance, we recommend creating a cluster about four times the size of your data. However, to avoid memory swapping, the Xmx value must not be larger than the physical memory on any given node.  We strongly recommend allocating the same amount of memory for all nodes, since H2O works best with symmetric nodes.

   The optional `-ip` (not shown in the example below) and `-port` options tell this H2O node what IP address and ports (port and port+1 are used) to use.  The `-ip` option is especially helpful for hosts that have multiple network interfaces.

   `$ java -Xmx20g -jar h2o.jar -flatfile flatfile.txt -port 54321`

   You will see output similar to the following:

  	```
  	05-11 16:40:46.268 172.16.2.39:54322     34242  main      INFO: ----- H2O started  -----
	05-11 16:40:46.337 172.16.2.39:54322     34242  main      INFO: Build git branch: master
	05-11 16:40:46.337 172.16.2.39:54322     34242  main      INFO: Build git hash: 	6c96387f893f3454912e20638dcb2f23a2786723
	05-11 16:40:46.337 172.16.2.39:54322     34242  main      INFO: Build git describe: jenkins-master-1192-10-g6c96387-dirty
	05-11 16:40:46.337 172.16.2.39:54322     34242  main      INFO: Build project version: 0.3.0.99999
	05-11 16:40:46.337 172.16.2.39:54322     34242  main      INFO: Built by: 'H2OUser'
	05-11 16:40:46.337 172.16.2.39:54322     34242  main      INFO: Built on: '2015-05-08 11:19:26'
	05-11 16:40:46.337 172.16.2.39:54322     34242  main      INFO: Java availableProcessors: 8
	05-11 16:40:46.338 172.16.2.39:54322     34242  main      INFO: Java heap totalMemory: 245.5 MB
	05-11 16:40:46.338 172.16.2.39:54322     34242  main      INFO: Java heap maxMemory: 17.78 GB
	05-11 16:40:46.338 172.16.2.39:54322     34242  main      INFO: Java version: Java 1.7.0_67 (from Oracle Corporation)
	05-11 16:40:46.338 172.16.2.39:54322     34242  main      INFO: OS   version: Mac OS X 10.10.3 (x86_64)
	05-11 16:40:46.338 172.16.2.39:54322     34242  main      INFO: Machine physical memory: 16.00 GB
	05-11 16:40:46.339 172.16.2.39:54322     34242  main      INFO: X-h2o-cluster-id: 1431387646125
	05-11 16:40:46.339 172.16.2.39:54322     34242  main      INFO: Opted out of sending usage metrics.
	05-11 16:40:46.339 172.16.2.39:54322     34242  main      INFO: Possible IP Address: en5 (en5), fe80:0:0:0:daeb:97ff:feb3:6d4b%4
	05-11 16:40:46.339 172.16.2.39:54322     34242  main      INFO: Possible IP Address: en5 (en5), 172.16.2.39
	05-11 16:40:46.339 172.16.2.39:54322     34242  main      INFO: Possible IP Address: lo0 (lo0), fe80:0:0:0:0:0:0:1%1
	05-11 16:40:46.339 172.16.2.39:54322     34242  main      INFO: Possible IP Address: lo0 (lo0), 0:0:0:0:0:0:0:1
	05-11 16:40:46.339 172.16.2.39:54322     34242  main      INFO: Possible IP Address: lo0 (lo0), 127.0.0.1
	05-11 16:40:46.340 172.16.2.39:54322     34242  main      INFO: Internal communication uses port: 54323
	05-11 16:40:46.340 172.16.2.39:54322     34242  main      INFO: Listening for HTTP and REST traffic on  http://172.16.2.39:54322/
	05-11 16:40:46.342 172.16.2.39:54322     34242  main      INFO: H2O cloud name: 'H2OUser' on /172.16.2.39:54322, static configuration based on -flatfile flatfile.txt
	05-11 16:40:46.342 172.16.2.39:54322     34242  main      INFO: If you have trouble connecting, try SSH tunneling from your local machine (e.g., via port 55555):
	05-11 16:40:46.342 172.16.2.39:54322     34242  main      INFO:   1. Open a terminal and run 'ssh -L 55555:localhost:54322 H2OUser@172.16.2.39'
	05-11 16:40:46.342 172.16.2.39:54322     34242  main      INFO:   2. Point your browser to http://localhost:55555
	05-11 16:40:46.542 172.16.2.39:54322     34242  main      INFO: Log dir: '/tmp/h2o-H2OUser/h2ologs'
	05-11 16:40:46.543 172.16.2.39:54322     34242  main      INFO: Cur dir: '/Users/H2OUser/h2o-dev'
	05-11 16:40:46.564 172.16.2.39:54322     34242  main      INFO: HDFS subsystem successfully initialized
	05-11 16:40:46.565 172.16.2.39:54322     34242  main      INFO: S3 subsystem successfully initialized
	05-11 16:40:46.565 172.16.2.39:54322     34242  main      INFO: Flow dir: '/Users/H2OUser/h2oflows'
	05-11 16:40:46.578 172.16.2.39:54322     34242  main      INFO: Cloud of size 3 formed [/172.16.2.39:54322, 172.16.2.40:54322, 172.16.2.41:54322]
	```

   As you add more nodes to your cluster, the H2O output will inform you:

     `INFO: Cloud of size 3 formed [/...]`

7. Access the H2O Web UI with your browser.  Point your browser to the IP address listed under **"Listening for HTTP and REST traffic on..."** in the H2O output.

8. If you are programmatically creating the cloud, give the cloud some time to establish itself (typically one minute is sufficient) and then check to see if the cloud is up.

   To check the cloud's status, point to the url http://<ip>:<port>/Cloud.json (see a piece of the JSON response below).  Wait for `cloud_size` to be the expected value and the `consensus` field to be true.

```

  {
    ...
  
    "cloud_size": 2,
    "consensus": true,
  
    ...
  }
```
