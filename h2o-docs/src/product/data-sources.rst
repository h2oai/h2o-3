.. _data_sources:

Data Sources
============

H2O supports data ingest from various data sources. Natively, a local file system, HDFS and S3 are supported.
Additional data sources can be accessed via generic HDFS API - for example, Alluxio or OpenStack SWIFT.

Default data sources
--------------------
- local file system
- S3 
- HDFS

HDFS-like data sources
----------------------
Various data sources can be accessed via HDFS API. In this case, a library providing access to a data source, needs
to be passed on a command line during H2O launch (reminder: each node in the cluster needs to be launched in the same way).
The library needs to be compatible with HDFS API to be registered as a correct HDFS ``FileSystem``.

Alluxio FS
~~~~~~~~~~

**Required library**

To access Alluxio data source, an Alluxio client library which is part of Alluxio distribution is required. 
For example, ``alluxio-1.3.0/core/client/target/alluxio-core-client-1.3.0-jar-with-dependencies.jar``.

**H2O Command Line**

::

     java -cp alluxio-core-client-1.3.0-jar-with-dependencies.jar:build/h2o.jar water.H2OApp

**URI Scheme**

Alluxio data source is referenced by ``alluxio://`` schema and location of Alluxio master. 
For example,

::

    alluxio://localhost:19998/iris.csv

**core-site.xml configuration**

Not supported.

IBM Swift Object Storage
~~~~~~~~~~~~~~~~~~~~~~~~

**Required library**

To access IBM Object Store (exposed via Bluemix or via Softlayer), IBM's HDFS driver ``hadoop-openstack.jar`` is required. 
It can be obtained, for example, from running BigInsight instances at location ``/usr/iop/4.2.0.0/hadoop-mapreduce/hadoop-openstack.jar``.

Note: The jar available at Maven central is not compatible with IBM Swift Object Storage.

**H2O Command Line**

::

    java -cp hadoop-openstack.jar:h2o.jar water.H2OApp

**URI Scheme**

Data source is available under regular Swift URI structure: ``swift://<CONTAINER>.<SERVICE>/path/to/file``
For example,

::

    swift://smalldata.h2o/iris.csv

**core-site.xml configuration**

The core-site.xml needs to be configured with parameters of Swift object store available in Bluemix/Softlayer management console.

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


