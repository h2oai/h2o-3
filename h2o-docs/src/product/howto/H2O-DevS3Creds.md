#How to Pass S3 Credentials to H2O

To use Amazon Web Services (AWS) with H2O, you must pass your S3 credentials to H2O. If you do not pass your credentials to H2O, H2O will not be compatible with AWS. 

##Standalone Instance

There are three ways to pass your S3 credentials to H2O: 

- Specify the credentials by creating a `core-site.xml` file:

  `java -jar h2o.jar -hdfs_config core-site.xml` 
  `java -cp h2o.jar water.H2OApp -hdfs_config core-site.xml`
  
- Specify the credentials as part of the S3N URL using `importFile`:

  `s3n://&lt;AWS_ACCESS_KEY&gt;:&lt;AWS_SECRET_KEY&gt;@bucket/path/file.csv` (where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password)
  
- Pass the S3 credentials using the `-D` parameters when launching H2O:

  `java -Dfs.s3.awsAccessKeyId="${AWS_ACCESS_KEY}" -Dfs.s3.awsSecretAccessKey="${AWS_SECRET_KEY}" -jar h2o.jar` (where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password)
  

##Hadoop Instance

There are two ways to pass your S3 credentials to H2O: 

- Specify the credentials by creating a `core-site.xml` file:

  `java -jar h2o.jar -hdfs_config core-site.xml` 
  `java -cp h2o.jar water.H2OApp -hdfs_config core-site.xml` 
  
  **Note**: Redirect the `HADOOP_CONF_DIR` environment property to the directory containing the `core-site.xml` file. 


- Specify the credentials in the Hadoop configuration (`HADOOP_CONF_DIR`) as part of the S3N URL using `importFile`:

  `s3n://&lt;AWS_ACCESS_KEY&gt;:&lt;AWS_SECRET_KEY&gt;@bucket/path/file.csv` (where `AWS_ACCESS_KEY` represents your user name and `AWS_SECRET_KEY` represents your password)



##Sparkling Water Instance

  To pass the s3 credentials to Sparkling Water, the credentials need to be passed via HADOOP_CONF_DIR that will point to a core-site.xml with the AWS_ACCESS_KEY AND AWS_SECRET_KEY. On Hadoop, typically the configuration directory is set to `/etc/hadoop/conf`:
  
  `export HADOOP_CONF_DIR=/etc/hadoop/conf`

Edit the properties in the core-site.xml file:

  <property>
    <name>fs.s3n.awsAccessKeyId</name>
    <value>[AWS SECRET KEY]</value>
  </property>

  <property>
    <name>fs.s3n.awsSecretAccessKey</name>
    <value>[AWS SECRET ACCESS KEY]</value>
  </property>
  
  When running a local instance you can create a configuration directory locally with the core-site.xml and then export the path to the configuration directory:
  
    mkdir CONF
    cd CONF
    export HADOOP_CONF_DIR=`pwd`
  
