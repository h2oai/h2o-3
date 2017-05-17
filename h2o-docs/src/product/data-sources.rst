.. _data_sources:

Data Sources
============

H2O supports data ingest from various data sources. Natively, a local file system, HDFS and S3 are supported.
Additional data sources can be accessed through a generic HDFS API, such as Alluxio or OpenStack Swift.

Default Data Sources
--------------------
- local file system
- S3 
- HDFS

HDFS-like Data Sources
----------------------
Various data sources can be accessed through an HDFS API.
In this case, a library providing access to a data source needs to be passed on a command line when H2O is launched
(Reminder: Each node in the cluster must be launched in the same way.).
The library must be compatible with the HDFS API in order to be registered as a correct HDFS ``FileSystem``.

Alluxio FS
~~~~~~~~~~

**Required Library**

To access Alluxio data source, an Alluxio client library that is part of Alluxio distribution is required.
For example, ``alluxio-1.3.0/core/client/target/alluxio-core-client-1.3.0-jar-with-dependencies.jar``.

**H2O Command Line**

::

     java -cp alluxio-core-client-1.3.0-jar-with-dependencies.jar:build/h2o.jar water.H2OApp

**URI Scheme**

An Alluxio data source is referenced using ``alluxio://`` schema and location of Alluxio master.
For example,

::

    alluxio://localhost:19998/iris.csv

**core-site.xml Configuration**

Not supported.

IBM Swift Object Storage
~~~~~~~~~~~~~~~~~~~~~~~~

**Required Library**

To access IBM Object Store (which can be exposed via Bluemix or Softlayer), IBM's HDFS driver ``hadoop-openstack.jar`` is required.
The driver can be obtained, for example, by running BigInsight instances at location ``/usr/iop/4.2.0.0/hadoop-mapreduce/hadoop-openstack.jar``.

Note: The jar available at Maven central is not compatible with IBM Swift Object Storage.

**H2O Command Line**

::

    java -cp hadoop-openstack.jar:h2o.jar water.H2OApp

**URI Scheme**

Data source is available under the regular Swift URI structure: ``swift://<CONTAINER>.<SERVICE>/path/to/file``
For example,

::

    swift://smalldata.h2o/iris.csv

**core-site.xml Configuration**

The core-site.xml needs to be configured with Swift Object Store parameters.
These are available in the Bluemix/Softlayer management console.

.. code:: xml

    <configuration>
      <property>
        <name>fs.swift.service.SERVICE.auth.url</name>
        <value>https://identity.open.softlayer.com/v3/auth/tokens</value>
      </property>
      <property>
        <name>fs.swift.service.SERVICE.project.id</name>
        <value>...</value>
      </property>
      <property>
        <name>fs.swift.service.SERVICE.user.id</name>
        <value>...</value>
      </property>
      <property>
        <name>fs.swift.service.SERVICE.password</name>
        <value>...</value>
      </property>
      <property>
        <name>fs.swift.service.SERVICE.region</name>
        <value>dallas</value>
      </property>
      <property>
        <name>fs.swift.service.SERVICE.public</name>
        <value>false</value>
      </property>
    </configuration>

Google Cloud Storage Connector for Hadoop & Spark
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

**Required Library**

To access the Google Cloud Store Object Store, Google's cloud storage connector, ``gcs-connector-latest-hadoop2.jar`` is required. The official documentation and driver can be found `here <https://cloud.google.com/hadoop/google-cloud-storage-connector>`__.

**H2O Command Line**

::

    H2O on Hadoop:
    hadoop jar h2o-driver.jar -libjars /path/to/gcs-connector-latest-hadoop2.jar

    Sparkling Water
    export SPARK_CLASSPATH=/home/nick/spark-2.0.2-bin-hadoop2.6/lib_managed/jar/gcs-connector-latest-hadoop2.jar
    sparkling-water-2.0.5/bin/sparkling-shell --conf "spark.executor.memory=10g"

**URI Scheme**

Data source is available under the regular Google Storage URI structure: ``gs://<BUCKETNAME>/path/to/file``
For example,

::

    gs://mybucket/iris.csv

**core-site.xml Configuration**

core-site.xml must be configured for at least the following properties (class, project-id, bucketname) as shown in the example below. A full list of configuration options is found `here <https://github.com/GoogleCloudPlatform/bigdata-interop/blob/master/gcs/conf/gcs-core-default.xml>`__. 

.. code:: xml

    <configuration>
        <property>
                <name>fs.gs.impl</name>
                <value>com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem</value>
        </property>
        <property>
                <name>fs.gs.project.id</name>
                <value>my-google-project-id</value>
        </property>
        <property>
                <name>fs.gs.system.bucket</name>
                <value>mybucket</value>
        </property>
    </configuration>
