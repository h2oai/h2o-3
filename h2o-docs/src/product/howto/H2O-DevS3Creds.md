#How to Pass S3 Credentials to H2O

To make use of Amazon Web Services (AWS) storage solution S3, you will need to pass your S3 access credentials to H2O. This will allow you to access your data on S3 when importing data frames with path prefixes `s3n://...`.

##Standalone Instance

When running H2O in standalone mode using the simple Java launch command (`java -jar h2o.jar`), we can pass the S3 credentials in three ways: 

- You can pass credentials in standalone mode the same way as accesssing data from HDFS in Hadoop mode. You'll need to create a `core-site.xml` file and pass it in with the flag `-hdfs_config`.

    `java -jar h2o.jar -hdfs_config core-site.xml`
    or 
    `java -cp h2o.jar water.H2OApp -hdfs_config core-site.xml`

   Then import the data with the S3 url path: `importFile ["s3n://bucket/path/to/file.csv"]`.
  
- You can pass the AWS Access Key and Secret Access Key in the S3N Url in Flow, R, or Python (where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password):
  
    - To import the data from the Flow API:
    
      `importFiles [ "s3n://<AWS_ACCESS_KEY>:<AWS_SECRET_KEY>@bucket/path/to/file.csv" ]`

    - To import the data from the R API:
    
      `h2o.importFile(path = "s3n://<AWS_ACCESS_KEY>:<AWS_SECRET_KEY>@bucket/path/to/file.csv")`
      
    - To import the data from the Python API:
    
      `h2o.import_frame(path = "s3n://<AWS_ACCESS_KEY>:<AWS_SECRET_KEY>@bucket/path/to/file.csv")`
  
  
- Pass the S3 credentials using the `-D` parameters when launching H2O:

  `java -Dfs.s3.awsAccessKeyId="${AWS_ACCESS_KEY}" -Dfs.s3.awsSecretAccessKey="${AWS_SECRET_KEY}" -jar h2o.jar`      
  (where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password)
  

##Hadoop Instance

There are two ways to pass your S3 credentials to H2O: 

- Specify the credentials by creating a `core-site.xml` file:

  `java -jar h2o.jar -hdfs_config core-site.xml`
  
  `java -cp h2o.jar water.H2OApp -hdfs_config core-site.xml` 
  
  **Note**: Redirect the `HADOOP_CONF_DIR` environment property to the directory containing the `core-site.xml` file. 


- Specify the credentials in the Hadoop configuration (`HADOOP_CONF_DIR`) as part of the S3N URL using `importFile`:

  `s3n://&lt;AWS_ACCESS_KEY&gt;:&lt;AWS_SECRET_KEY&gt;@bucket/path/file.csv` 
  
  (where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password)



##Sparkling Water Instance

  For Sparkling Water, the S3 credentials need to be passed via the `HADOOP_CONF_DIR` that will point to a `core-site.xml` with the `AWS_ACCESS_KEY` AND `AWS_SECRET_KEY`. On Hadoop, typically the configuration directory is set to `/etc/hadoop/conf`:
  
    export HADOOP_CONF_DIR=/etc/hadoop/conf

Edit the properties in the core-site.xml file:

    <property>
      <name>fs.s3n.awsAccessKeyId</name>
      <value>[AWS SECRET KEY]</value>
    </property>

    <property>
      <name>fs.s3n.awsSecretAccessKey</name>
      <value>[AWS SECRET ACCESS KEY]</value>
    </property>
  
  If you are running a local instance, create a configuration directory locally with the `core-site.xml` and then export the path to the configuration directory:
  
    mkdir CONF
    cd CONF
    export HADOOP_CONF_DIR=`pwd`
  
