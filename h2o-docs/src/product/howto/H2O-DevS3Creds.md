#How to Pass S3 Credentials to H2O

To make use of Amazon Web Services (AWS) storage solution S3 you will need to pass your S3 access credentials to H2O. This will allow you to access your data on S3 when importing data frames with path prefixes `s3n://...`.

##Standalone Instance

When running H2O on standalone mode aka using the simple java launch command, we can pass in the S3 credentials in two ways. 

- You can pass in credentials in standalone mode the same way we access data from hdfs on Hadoop mode. You'll need to create a `core-site.xml` file and pass it in with the flag `-hdfs_config`.

    To edit the properties in the core-site.xml file:

    <property>
      <name>fs.s3n.awsAccessKeyId</name>
      <value>[AWS SECRET KEY]</value>
    </property>

    <property>
      <name>fs.s3n.awsSecretAccessKey</name>
      <value>[AWS SECRET ACCESS KEY]</value>
    </property>

Launch with the configuration file `cote-site.xml` by running in the command line:

    java -jar h2o.jar -hdfs_config core-site.xml
or 
    java -cp h2o.jar water.H2OApp -hdfs_config core-site.xml

After which you can import the data with the S3 url path: `s3n://bucket/path/to/file.csv` with importFile.
  
- You can actually pass in the AWS Access Key and Secret Acess Key in S3N Url in Flow, R, or Python.
  
  To import the data from the Flow API:

        importFiles [ "s3n://<AWS_ACCESS_KEY>:<AWS_SECRET_KEY>@bucket/path/to/file.csv" ]

  To import the data from the R API:
  
        h2o.importFile(path = "s3n://<AWS_ACCESS_KEY>:<AWS_SECRET_KEY>@bucket/path/to/file.csv")

  To import the data from the Python API:
  
        h2o.import_frame(path = "s3n://<AWS_ACCESS_KEY>:<AWS_SECRET_KEY>@bucket/path/to/file.csv")
  
where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password.

##Accessing S3 Data from Hadoop Instance

H2O launched atop Hadoop servers can still access S3 Data in addition to having access to HDFS. To do this edit Hadoop's `core-site.xml` the same way. Set the `HADOOP_CONF_DIR` environment property to the directory containing the `core-site.xml` file. Typically the configuration directory for most hadoop distribution is `/etc/hadoop/conf`. 

- To launch H2O without use of any schedulers or with Yarn do the same as the standalone with the exception of the hdfs configuration directory path:

        java -jar h2o.jar -hdfs_config $HADOOP_CONF_DIR/core-site.xml
        java -cp h2o.jar water.H2OApp -hdfs_config $HADOOP_CONF_DIR/core-site.xml

- Pass the S3 credentials when launching H2O using the hadoop jar command use the `-D` flag to pass the credentials:

        hadoop jar h2odriver.jar -Dfs.s3.awsAccessKeyId="${AWS_ACCESS_KEY}" -Dfs.s3n.awsSecretAccessKey="${AWS_SECRET_KEY}" -n 3 -mapperXmx 10g  -output outputDirectory
    
where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password.

After which you can import the data with the S3 url path: 

  To import the data from the Flow API:

        importFiles [ "s3n://bucket/path/to/file.csv" ]

  To import the data from the R API:
  
        h2o.importFile(path = "s3n://bucket/path/to/file.csv")

  To import the data from the Python API:
  
        h2o.import_frame(path = "s3n://bucket/path/to/file.csv")

##Sparkling Water Instance

  To pass the s3 credentials to Sparkling Water, the credentials need to be passed via HADOOP_CONF_DIR that will point to a core-site.xml with the AWS_ACCESS_KEY AND AWS_SECRET_KEY. On Hadoop, typically the configuration directory is set to `/etc/hadoop/conf`:
  
        export HADOOP_CONF_DIR=/etc/hadoop/conf

  When running a local instance you can create a configuration directory locally with the core-site.xml and then export the path to the configuration directory:
  
        mkdir CONF
        cd CONF
        export HADOOP_CONF_DIR=`pwd`
  
