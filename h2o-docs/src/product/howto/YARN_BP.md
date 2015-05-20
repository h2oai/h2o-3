#YARN Best Practices

YARN (Yet Another Resource Manager) is a resource management tool that can be used to allocate memory for H2O. 


##Using YARN with H2O 

When you launch H2O on Hadoop using the `hadoop jar` command, YARN allocates the necessary resources to launch the requested number of nodes. H2O launches as a MapReduce (V2) task, where each mapper is an H2O node of the specified size. 

Occasionally, YARN may reject a job request. This usually occurs because either there is not enough memory to launch the job or because of an incorrect configuration. 

If YARN rejects the job request, try launching the job with less memory to see if that is the cause of the failure. Specify smaller values (we recommend `1`) for `-mapperXmx` and `-nodes` to confirm that H2O can launch successfully:
` hadoop jar h2odriver_hdp2.1.jar water.hadoop.h2odriver
-libjars ../h2o.jar -mapperXmx 1g -extramempercent 20 -nodes 1 -output hdfsOutputDir`  

To resolve configuration issues, adjust the maximum memory that YARN will allow when launching each mapper. If the cluster manager settings are configured for the default maximum memory size but the memory required for the request exceeds that amount, YARN will not launch and H2O will time out. If you are using the default configuration, change the configuration settings in your cluster manager to specify memory allocation when launching mapper tasks. To calculate the amount of memory required for a successful launch, use the following formula: 

>YARN container size (`mapreduce.map.memory.mb`) = `-mapperXmx` value + (`-mapperXmx` * `-extramempercent` [default is 10%])

The `mapreduce.map.memory.mb` value must be less than the YARN memory configuration values for the launch to succeed. 

To learn how to change the `mapreduce.map.memory.mb` values in Cloudera, Ambari, and MapR, refer to the documentation [here](http://docs.h2o.ai/h2oclassic/deployment/hadoop_yarn.html). 


##Specifying Queues

If you do not specify a queue when launching H2O, H2O jobs are submitted to the default queue. Jobs submitted to the default queue have a lower priority than jobs submitted to a specific queue. 

To specify a queue with Hadoop, enter `-Dmapreduce.job.queuename=<queue name>` (where `<queue name>` is the name of the queue) when launching Hadoop. For example, `hadoop jar h2odriver.jar -nodes 1 -mapperXmx 1g -output hdfsOutputDirName  -Dmapreduce.job.queuename=H2O`. 


##Specifying Output Directories

To prevent overwriting multiple users' files, each mapper task must have a unique output directory name. Change the `-output hdfsOutputDir` argument (where `hdfsOutputDir` is the name of the directory. 

##Customizing YARN

Most of the configurable YARN variables are stored in `yarn-site.xml`. To prevent settings from being overridden, you can mark a config as "final."If you change any values in `yarn-site.xml`, you must restart YARN to confirm the changes. 


>Checkpointing? 
>Logs?