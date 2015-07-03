# ... From the Cmd Line

You can use Terminal (OS X) or the Command Prompt (Windows) to launch H2O 3.0. When you launch from the command line, you can include additional instructions to H2O 3.0, such as how many nodes to launch, how much memory to allocate for each node, assign names to the nodes in the cloud, and more. 

>**Note**: H2O requires some space in the `/tmp` directory to launch. If you cannot launch H2O, try freeing up some space in the `/tmp` directory, then try launching H2O again. 

For more detailed instructions on how to build and launch H2O, including how to clone the repository, how to pull from the repository, and how to install required dependencies, refer to the [developer documentation](https://github.com/h2oai/h2o-3#41-building-from-the-command-line-quick-start). 

There are two different argument types:

- JVM arguments
- H2O arguments

The arguments use the following format: java `<JVM Options>` -jar h2o.jar `<H2O Options>`.  

##JVM Options

- `-version`: Display Java version info. 
- `-Xmx<Heap Size>`: To set the total heap size for an H2O node, configure the memory allocation option `-Xmx`. By default, this option is set to 1 Gb (`-Xmx1g`). When launching nodes, we recommend allocating a total of four times the memory of your data. 

> **Note**: Do not try to launch H2O with more memory than you have available. 


##H2O Options

- `-h` or `-help`: Display this information in the command line output. 
- `-name <H2OCloudName>`: Assign a name to the H2O instance in the cloud (where `<H2OCloudName>` is the name of the cloud. Nodes with the same cloud name will form an H2O cloud (also known as an H2O cluster). 
- `-flatfile <FileName>`: Specify a flatfile of IP address for faster cloud formation (where `<FileName>` is the name of the flatfile. 
- `-ip <IPnodeAddress>`: Specify an IP address other than the default `localhost` for the node to use (where `<IPnodeAddress>` is the IP address). 
- `-port <#>`: Specify a port number other than the default `54321` for the node to use (where `<#>` is the port number). 
- `-network ###.##.##.#/##`: Specify an IP addresses (where `###.##.##.#/##` represents the IP address and subnet mask). The IP address discovery code binds to the first interface that matches one of the networks in the comma-separated list; to specify an IP address, use `-network`. To specify a range, use a comma to separate the IP addresses: `-network 123.45.67.0/22,123.45.68.0/24`. For example, `10.1.2.0/24` supports 256 possibilities.
- `-ice_root <fileSystemPath>`: Specify a directory for H2O to spill temporary data to disk (where `<fileSystemPath>` is the file path). 
- `-flow_dir <server-side or HDFS directory>`: Specify a directory for saved flows. The default is `/Users/h2o-<H2OUserName>/h2oflows` (where `<H2OUserName>` is your user name). 
- `-nthreads <#ofThreads>`: Specify the maximum number of threads in the low-priority batch work queue (where `<#ofThreads>` is the number of threads). The default is 99. 
- `-client`: Launch H2O node in client mode. This is used mostly for running Sparkling Water. 


##Cloud Formation Behavior

New H2O nodes join to form a cloud during launch. After a job has started on the cloud, it  prevents new members from joining. 

- To start an H2O node with 4GB of memory and a default cloud name: 
  `java -Xmx4g -jar h2o.jar`

- To start an H2O node with 6GB of memory and a specific cloud name: 
  `java -Xmx6g -jar h2o.jar -name MyCloud`

- To start an H2O cloud with three 2GB nodes using the default cloud names: 

  ```
  java -Xmx2g -jar h2o.jar &
  java -Xmx2g -jar h2o.jar &
  java -Xmx2g -jar h2o.jar &
  ```

Wait for the `INFO: Registered: # schemas in: #mS` output before entering the above command again to add another node (the number for # will vary).

##Flatfile Configuration for Multi-Node Clusters

Running H2O on a multi-node cluster allows you to use more memory for large-scale tasks (for example, creating models from huge datasets) than would be possible on a single node. 

If you are configuring many nodes, using the `-flatfile` option is fast and easy. The `-flatfile` option is used to define a list of potential cloud peers. However, it is not an alternative to `-ip` and `-port`, which should be used to bind the IP and port address of the node you are using to launch H2O.   

To configure H2O on a multi-node cluster:

0. Locate a set of hosts that will be used to create your cluster. A host can be a server, an EC2 instance, or your laptop.
0. [Download](http://h2o.ai/download) the appropriate version of H2O for your environment. 
0. Verify the same h2o.jar file is available on each host in the multi-node cluster. 
0. Create a flatfile.txt that contains an IP address and port number for each H2O instance. Use one entry per line.  For example:
   
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
	04-20 16:14:00.253 192.168.1.70:54321    2754   main      INFO:   1. Open a terminal and run 'ssh -L 55555:localhost:54321 H2O-3User@###.###.#.##'
	04-20 16:14:00.253 192.168.1.70:54321    2754   main      INFO:   2. Point your browser to http://localhost:55555
	04-20 16:14:00.437 192.168.1.70:54321    2754   main      INFO: Log dir: '/tmp/h2o-H2O-3User/h2ologs'
	04-20 16:14:00.437 192.168.1.70:54321    2754   main      INFO: Cur dir: '/Users/H2O-3User/h2o-3'
	04-20 16:14:00.459 192.168.1.70:54321    2754   main      INFO: HDFS subsystem successfully initialized
	04-20 16:14:00.460 192.168.1.70:54321    2754   main      INFO: S3 subsystem successfully initialized
	04-20 16:14:00.460 192.168.1.70:54321    2754   main      INFO: Flow dir: '/Users/H2O-3User/h2oflows'
	04-20 16:14:00.475 192.168.1.70:54321    2754   main      INFO: Cloud of size 1 formed [/192.168.1.70:54321]
	```

 As you add more nodes to your cluster, the output is updated:
`INFO WATER: Cloud of size 2 formed [/...]...`

0. Access the H2O 3.0 web UI (Flow) with your browser. Point your browser to the HTTP address specified in the output `Listening for HTTP and REST traffic on ...`. 


