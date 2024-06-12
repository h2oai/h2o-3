Getting data into your H2O-3 cluster
====================================

The first step toward building and scoring your models is getting your data into the H2O-3 cluster/Java process that’s running on your local or remote machine. Whether you're importing data, uploading data, or retrieving data from HDFS or S3, be sure that your data is compatible with H2O-3.

.. _supported_file_formats:

Supported file formats
----------------------

H2O-3 supports the following file types:

- CSV (delimited, UTF-8 only) files (including GZipped CSV)
- ORC
- SVMLight
- ARFF
- XLS (BIFF 8 only)
- XLSX (BIFF 8 only)
- Avro version 1.8.0 (without multifile parsing or column type modification)
- Parquet
- Google Storage (gs://)

.. note::
 
    - H2O supports UTF-8 encodings for CSV files. Please convert UTF-16 encodings to UTF-8 encoding before parsing CSV files into H2O-3.
    - ORC is available only if H2O-3 is running as a Hadoop job. 
    - Users can also import Hive files that are saved in ORC format (experimental). 
    - When doing a parallel data import into a cluster: 

        - If the data is an unzipped CSV file, H2O-3 can do offset reads, so each node in your cluster can be directly reading its part of the CSV file in parallel. 
        - If the data is zipped, H2O-3 will have to read the whole file and unzip it before doing the parallel read.

    So, if you have very large data files reading from HDFS, it's best to use unzipped CSV. But, if the data is further away than the LAN, then it's best to use zipped CSV.

.. caution::
    
    - If you encounter issues importing XLS or XLSX files, you may be using an unsupported version. In this case, re-save the file in BIFF 8 format. Also note that XLS and XLSX support will eventually be deprecated.

.. _data_sources:

Data sources
------------

H2O-3 supports data ingest from various data sources. Natively, a local file system, remote file systems, HDFS, S3, and some relational databases are supported. Additional data sources can be accessed through a generic HDFS API, such as Alluxio or OpenStack Swift.

Default data sources
~~~~~~~~~~~~~~~~~~~~

- Local File System 
- Remote File
- S3 
- HDFS
- JDBC
- Hive

Local file system
'''''''''''''''''

Data from a local machine can be uploaded to H2O-3 through a push from the client. See more information on `uploading from your local file system <data-munging/uploading-data.html>`__.

Remote file
'''''''''''

Data that is hosted on the Internet can be imported into H2O-3 by specifying the URL. See more information on `importing data from the internet <data-munging/importing-data.html>`__.

HDFS-like data sources
''''''''''''''''''''''

Various data sources can be accessed through an HDFS API. In this case, a library providing access to a data source needs to be passed on a command line when H2O-3 is launched. The library must be compatible with the HDFS API in order to be registered as a correct HDFS ``FileSystem``.

.. tip::
    
    Each node in the cluster must be launched in the same way. 

Example HDFS-like data sources
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. tabs::
    .. tab:: Alluxio

        **Required library**
        
        To access the Alluxio data source, an Alluxio client library that is part of Alluxio distribution is required. For example, ``alluxio-1.3.0/core/client/target/alluxio-core-client-1.3.0-jar-with-dependencies.jar``.

        **H2O-3 command line**

        .. code-block:: bash

             java -cp alluxio-core-client-1.3.0-jar-with-dependencies.jar:build/h2o.jar water.H2OApp

        **URI scheme**

        An Alluxio data source is referenced using the ``alluxio://`` schema and the location of the Alluxio master. For example,

        .. code-block:: bash

            alluxio://localhost:19998/iris.csv

        ``core-site.xml`` **configuration**

        Not supported.

    .. tab:: IBM Swift Object Storage

        **Required library**

        To access IBM Object Store (which can be exposed via Bluemix or Softlayer), IBM's HDFS driver ``hadoop-openstack.jar`` is required. The driver can be obtained, for example, by running BigInsight instances at the following location: ``/usr/iop/4.2.0.0/hadoop-mapreduce/hadoop-openstack.jar``.

        .. caution:: 

            The JAR file available at Maven central is not compatible with IBM Swift Object Storage.

        **H2O-3 command line**
        
        .. code-block:: bash

            java -cp hadoop-openstack.jar:h2o.jar water.H2OApp

        **URI scheme**

        The data source is available under the regular Swift URI structure: ``swift://<CONTAINER>.<SERVICE>/path/to/file``. For example:

        .. code-block:: bash

            swift://smalldata.h2o/iris.csv

        ``core-site.xml`` **configuration**

        The ``core-site.xml`` needs to be configured with Swift Object Store parameters. These are available in the Bluemix/Softlayer management console.

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

    .. tab:: Google Cloud Storage Connector

        For Hadoop and Spark.

        **Required library**
        
        To access the Google Cloud Store Object Store, Google's cloud storage connector, ``gcs-connector-latest-hadoop2.jar`` is required. See `the official documentation and driver <https://cloud.google.com/dataproc/docs/concepts/connectors/cloud-storage>`__.

        **H2O-3 command line**

        .. code-block:: bash

            # H2O-3 on Hadoop:
            hadoop jar h2o-driver.jar -libjars /path/to/gcs-connector-latest-hadoop2.jar

            # Sparkling Water:
            export SPARK_CLASSPATH=/home/nick/spark-2.0.2-bin-hadoop2.6/lib_managed/jar/gcs-connector-latest-hadoop2.jar
            sparkling-water-2.0.5/bin/sparkling-shell --conf "spark.executor.memory=10g"

        **URI scheme**

        The data source is available under the regular Google Storage URI structure: ``gs://<BUCKETNAME>/path/to/file``. For example:

        .. code-block:: bash

            gs://mybucket/iris.csv

        ``core-site.xml`` **configuration**

        The ``core-site.xml`` must be configured for at least the following properties (as shown in the following example):

        - class
        - project-id
        - bucketname

        See the `full list of configuration options <https://github.com/GoogleCloudDataproc/hadoop-connectors/blob/master/gcs/CONFIGURATION.md>`__. 

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

.. _direct_hive_import:

Direct Hive import
~~~~~~~~~~~~~~~~~~

H2O-3 supports direct ingestion of data managed by Hive in Hadoop. This feature is available only when H2O-3 is running as a Hadoop job. Internally, H2O-3 uses metadata in the Hive Metastore database to determine the location and format of a given Hive table. H2O-3 then imports data directly from HDFS, so limitations of supported formats mentioned above apply. Data from Hive can be pulled into H2O-3 using the ``import_hive_table`` function. H2O-3 can read Hive table metadata two ways: 

- Direct Metastore access 
- JDBC

.. tip:: 
    
    When ingesting data from Hive in Hadoop, direct Hive import is preferred over :ref:`hive2`.

Requirements
''''''''''''

- You must have read access to Hive and the files it manages.
- For direct metastore access, the Hive JARs and configuration must be present on the H2O-3 job classpath. You can achieve this either by adding it to the ``yarn.application.classpath`` (or similar property for your resource manger of choice) or by adding Hive JARs and configuration to ``-libjars``. 
- For JDBC metadata access, the Hive JDBC Driver must be on the H2O-3 job classpath.

Limitations
'''''''''''

- The imported table must be stored in a :ref:`format supported by H2O-3<supported_file_formats>`. 
- (CSV) The Hive table property ``skip.header.line.count`` is not supported. CSV files with header rows will be imported with the header row as data.
- (Partitioned tables with different storage formats) H2O-3 supports importing partitioned tables that use different storage formats for different partitions; however, in some cases (for example, a large number of small partitions), H2O-3 may run out of memory while importing, even though the final data would easily fit into the memory allocated to the H2O-3 cluster.

Examples of importing
'''''''''''''''''''''

Example 1: Access metadata through metastore
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This example shows how to access metadata through the metastore. 

1. Start the H2O JAR in the terminal with your downloaded Hive JDBC driver in the classpath:

 .. code-block:: bash

      # start the h2o.jar:
      hadoop jar h2odriver.jar -libjars hive-jdbc-standalone.jar -nodes 3 -mapperXmx 6g

2. Import data in Python or R.

 .. tabs::
    .. code-tab:: python

        # basic import
        basic_import = h2o.import_hive_table("default", "table_name")

        # multi-format import
        multi_format_enabled = h2o.import_hive_table("default", 
                                                     "table_name", 
                                                     allow_multi_format=True)

        # import with partition filter
        with_partition_filter = h2o.import_hive_table("default", 
                                                      "table_name", 
                                                      [["2017", "02"]])
   
    .. code-tab:: r R

        # basic import
        basic_import <- h2o.import_hive_table("default", "table_name")

        # multi-format import
        multi_format_enabled <- h2o.import_hive_table("default", 
                                                      "table_name", 
                                                      allow_multi_format=True)

        # import with partition filter
        with_partition_filter <- h2o.import_hive_table("default", 
                                                       "table_name", 
                                                       [["2017", "02"]])


Example 2: Access metadata through JDBC
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This example shows how to access metadata through JDBC.  

1. Start the H2O JAR in the terminal with your downloaded Hive JDBC driver in the classpath:

 .. code-block:: bash

      # start the h2o.jar:
      hadoop jar h2odriver.jar -libjars hive-jdbc-standalone.jar -nodes 3 -mapperXmx 6g

2. Import data in Python or R.

 .. tabs::
   .. code-tab:: python

        # basic import of metadata via JDBC
        basic_import = h2o.import_hive_table("jdbc:hive2://hive-server:10000/default", "table_name")

   .. code-tab:: r R

        # basic import of metadata via JDBC
        basic_import <- h2o.import_hive_table("jdbc:hive2://hive-server:10000/default", "table_name")

JDBC databases
~~~~~~~~~~~~~~

Relational databases that include a JDBC (Java database connectivity) driver can be used as the source of data for machine learning in H2O-3. The supported SQL databases are MySQL, PostgreSQL, MariaDB, Netezza, Amazon Redshift, Teradata, and Hive. (See :ref:`hive2` for more information.) Data from these SQL databases can be pulled into H2O-3 using the ``import_sql_table`` and ``import_sql_select`` functions. 

See the following articles for examples about using JDBC data sources with H2O-3.

- `Setup postgresql database on OSX <https://aichamp.wordpress.com/2017/03/20/setup-postgresql-database-on-osx/>`__
- `Restoring DVD rental database into postgresql <https://aichamp.wordpress.com/2017/03/20/restoring-dvd-rental-database-into-postgresql/>`__
- `Building H2O-3 GLM model using Postgresql database and JDBC driver <https://aichamp.wordpress.com/2017/03/20/building-h2o-glm-model-using-postgresql-database-and-jdbc-driver/>`__

.. note:: 
    
    The handling of categorical values is different between file ingest and JDBC ingests. The JDBC treats categorical values as strings. Strings are not compressed in any way in H2O-3 memory, and using the JDBC interface might need more memory and additional data post-processing (converting to categoricals explicitly).


``import_sql_table`` function
'''''''''''''''''''''''''''''

This function imports a SQL table to H2OFrame in memory. This function assumes that the SQL table is not being updated and is stable. You can run multiple SELECT SQL queries concurrently for parallel ingestion.

.. tip::

    Be sure to start the ``h2o.jar`` in the terminal with your downloaded JDBC driver in the classpath:

    ::
      
          java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp

The ``import_sql_table`` function accepts the following parameters:

- ``connection_url``: The URL of the SQL database connection as specified by the Java Database Connectivity (JDBC) Driver. For example, ``jdbc:mysql://localhost:3306/menagerie?&useSSL=false``.
- ``table``: The name of the SQL table.
- ``columns``: A list of column names to import from SQL table. Defaults to importing all columns.
- ``username``: The username for the SQL server.
- ``password``: The password for the SQL server.
- ``optimize``: Specifies to optimize the import of the SQL table for faster imports. Note that this option is experimental.
- ``fetch_mode``: Set to ``DISTRIBUTED`` to enable distributed import. Set to ``SINGLE`` to force a sequential read by a single node from the database.
- ``num_chunks_hint``: Optionally specify the number of chunks for the target frame.

.. tabs::
   .. code-tab:: python

        connection_url = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
        table = "citibike20k"
        username = "root"
        password = "abc123"
        my_citibike_data = h2o.import_sql_table(connection_url, table, username, password)

   .. code-tab:: r R

        connection_url <- "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
        table <- "citibike20k"
        username <- "root"
        password <- "abc123"
        my_citibike_data <- h2o.import_sql_table(connection_url, table, username, password)

``import_sql_select`` function
''''''''''''''''''''''''''''''

This function imports the SQL table that is the result of the specified SQL query to the H2OFrame in memory. It creates a temporary SQL table from the specified ``sql_query``. You can run multiple SELECT SQL queries on the temporary table concurrently for parallel ingestion then drop the table.
    
.. tip:: 

    Be sure to start the ``h2o.jar`` in the terminal with your downloaded JDBC driver in the classpath:

    ::
      
          java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp

The ``import_sql_select`` function accepts the following parameters:

- ``connection_url``: URL of the SQL database connection as specified by the Java Database Connectivity (JDBC) Driver. For example, ``jdbc:mysql://localhost:3306/menagerie?&useSSL=false``.
- ``select_query``: SQL query starting with ``SELECT`` that returns rows from one or more database tables.
- ``username``: The username for the SQL server.
- ``password``: The password for the SQL server.
- ``optimize``: Specifies to optimize import of the SQL table for faster imports. Note that this option is experimental.
- ``use_temp_table``: Specifies whether a temporary table should be created by ``select_query``.
- ``temp_table_name``: The name of the temporary table to be created by ``select_query``.
- ``fetch_mode``: Set to ``DISTRIBUTED`` to enable distributed import. Set to ``SINGLE`` to force a sequential read by a single node from the database.

.. tabs::
   .. code-tab:: python

        connection_url = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
        select_query = "SELECT bikeid from citibike20k"
        username = "root"
        password = "abc123"
        my_citibike_data = h2o.import_sql_select(connection_url, select_query, username, password)

   .. code-tab:: r R

        connection_url <- "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
        select_query <-  "SELECT  bikeid  from  citibike20k"
        username <- "root"
        password <- "abc123"
        my_citibike_data <- h2o.import_sql_select(connection_url, select_query, username, password)

.. _hive2:

Hive JDBC driver
''''''''''''''''

H2O-3 can ingest data from Hive through the Hive JDBC driver (v2) by providing H2O-3 with the JDBC driver for your Hive version. Explore this `demo showing how to ingest data from Hive through the Hive v2 JDBC driver <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/hive_jdbc_driver/Hive.md>`__. The basic steps are described below. 

.. tip::

    - :ref:`direct_hive_import` is preferred over using the Hive JDBC driver.
    - H2O-3 can only load data from Hive version 2.2.0 or greater due to a limited implementation of the JDBC interface by Hive in earlier versions.


1. Set up a table with data. 

  a. Download `this AirlinesTest dataset from S3 <https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip>`__.

  b. Run the CLI client for Hive: 

   .. code-block:: bash

     beeline -u jdbc:hive2://hive-host:10000/db-name

  c. Create the DB table:

   .. code-block:: sql

     CREATE EXTERNAL TABLE IF IT DOES NOT EXIST AirlinesTest(
       fYear STRING ,
       fMonth STRING ,
       fDayofMonth STRING ,
       fDayOfWeek STRING ,
       DepTime INT ,
       ArrTime INT ,
       UniqueCarrier STRING ,
       Origin STRING ,
       Dest STRING ,
       Distance INT ,
       IsDepDelayed STRING ,
       IsDepDelayed_REC INT
     )
         COMMENT 'test table'
         ROW FORMAT DELIMITED
         FIELDS TERMINATED BY ','
         LOCATION '/tmp';

  d. Import the data from the dataset (note that the file must be present on HDFS in ``/tmp``):

   .. code-block:: sql

     LOAD DATA INPATH '/tmp/AirlinesTest.csv' OVERWRITE INTO TABLE AirlinesTest

2. Retrieve the Hive JDBC client JAR.

  - For Hortonworks, Hive JDBC client JARs can be found on one of the edge nodes after you have installed HDP: ``/usr/hdp/current/hive-client/lib/hive-jdbc-<version>-standalone.jar``. See more `information on Hortonworks and the Hive JDBC client <https://docs.hortonworks.com/HDPDocuments/HDP2/HDP-2.6.4/bk_data-access/content/hive-jdbc-odbc-drivers.html>`__.
  - For Cloudera, install the JDBC package for your operating system, and then add ``/usr/lib/hive/lib/hive-jdbc-<version>-standalone.jar`` to your classpath. See more `information on Cloudera and the Hive JDBC client <https://www.cloudera.com/documentation/enterprise/5-3-x/topics/cdh_ig_hive_jdbc_install.html>`__.
  - You can also retrieve this from Maven for your desired version using ``mvn dependency:get -Dartifact=groupId:artifactId:version``.

3. Add the Hive JDBC driver to H2O-3's classpath:


 .. code-block:: bash

        # Add the Hive JDBC driver to H2O-3's classpath:
        java -cp hive-jdbc.jar:<path_to_h2o_jar> water.H2OApp

4. Initialize H2O-3 in either Python or R and import your data:

 .. tabs::
   .. group-tab:: Python

     .. code-block:: python

        # initialize h2o in Python
        import h2o
        h2o.init(extra_classpath = ["hive-jdbc-standalone.jar"])

   .. group-tab:: R

     .. code-block:: r

        # initialize h2o in R
        library(h2o)
        h2o.init(extra_classpath=["hive-jdbc-standalone.jar"])

5. After the JAR file with the JDBC driver is added, the data from the Hive databases can be pulled into H2O-3 using the aforementioned ``import_sql_table`` and ``import_sql_select`` functions. 

 .. tabs::
  .. code-tab:: python

    connection_url = "jdbc:hive2://localhost:10000/default"
    select_query = "SELECT * FROM AirlinesTest;"
    username = "username"
    password = "changeit"

    airlines_dataset = h2o.import_sql_select(connection_url, 
                                             select_query, 
                                             username, 
                                             password)
  .. code-tab:: r R

    connection_url <- "jdbc:hive2://localhost:10000/default"
    select_query <- "SELECT * FROM AirlinesTest;"
    username <- "username"
    password <- "changeit"

    airlines_dataset <- h2o.import_sql_select(connection_url, 
                                              select_query, 
                                              username, 
                                              password)


Connect to Hive in a Kerberized Hadoop cluster
''''''''''''''''''''''''''''''''''''''''''''''

When importing data from Kerberized Hive on Hadoop, it's necessary to configure the h2odriver to authenticate with the Hive instance through a delegation token. Since Hadoop does not generate delegation tokens for Hive automatically, it's necessary to provide the h2odriver with additional configurations.

H2O-3 is able to generate Hive delegation tokens in three modes:

- `On the driver side <#generate-the-token-in-the-driver>`__, a token can be generated on H2O-3 cluster start.
- `On the mapper side <#generate-the-token-in-the-mapper-and-token-refresh>`__, a token refresh thread is started, periodically re-generating the token.
- `A combination of both of the above <#generate-the-token-in-the-driver-with-refresh-in-the-mapper>`__.

The following are the H2O-3 arguments used to configure the JDBC URL for Hive delegation token generation:

- ``hiveHost`` - The full address of HiveServer2 (for example, ``hostname:10000``).
- ``hivePrincipal`` -  The Hiveserver2 Kerberos principal (for example, ``hive/hostname@DOMAIN.COM``).
- ``hiveJdbcUrlPattern`` - (optional) Can be used to further customize the way the driver constructs the Hive JDBC URL. The default pattern used is ``jdbc:hive2://{{host}}/;{{auth}}`` where ``{{auth}}`` is replaced by ``principal={{hivePrincipal}}`` or ``auth=delegationToken`` based on the context.

.. attention::
    
    In the following examples, we omit the ``-libjars`` option of the ``hadoop.jar`` command because it is not necessary for token generation. You may need to add it to be able to import data from Hive via JDBC. 

Generate the token in the driver
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The advantage of this approach is that the keytab does not need to be distributed into the Hadoop cluster. 

**Requirements**

- The Hive JDBC driver is on h2odriver classpath through the ``HADOOP_CLASSPATH`` environment variable. (Only used to acquire the Hive delegation token.)
- The ``hiveHost``, ``hivePrincipal`` and optionally ``hiveJdbcUrlPattern`` arguments are present. See `Connect to Hive in a Kerberized Hadoop cluster <#connect-to-hive-in-a-kerberized-hadoop-cluster>`__ for more details.

**Example**

The following is an example of generating a token in the driver:

.. code-block:: bash

      export HADOOP_CLASSPATH=/path/to/hive-jdbc-standalone.jar
      hadoop jar h2odriver.jar \
          -nodes 1 -mapperXmx 4G \
          -hiveHost hostname:10000 -hivePrincipal hive/hostname@EXAMPLE.COM \
          -hiveJdbcUrlPattern "jdbc:hive2://{{host}}/;{{auth}};ssl=true;sslTrustStore=/path/to/keystore.jks"

Generate the token in the mapper and token refresh
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This approach generates a Hive delegation token after the H2O-3 cluster is fully started up and then periodically refreshes the token. Delegation tokens usually have a limited life span, and for long-running H2O-3 clusters, they need to be refreshed. For this to work, your keytab and principal need to be available to the H2O-3 cluster leader node.

**Requirements**

- The Hive JDBC driver is on the h2o mapper classpath (either through ``-libjars`` or YARN configuration).
- The ``hiveHost``, ``hivePrincipal`` and optionally ``hiveJdbcUrlPattern`` arguments are present. See `Connect to Hive in a Kerberized Hadoop cluster <#connect-to-hive-in-a-kerberized-hadoop-cluster>`__ for more details.
- The ``principal`` argument is set with the value of your Kerberos principal.
- The ``keytab`` argument set pointing to the file with your Kerberos keytab file.
- The ``refreshHiveTokens`` argument is present.

**Example**

The following is an example of how to set up a token refresh using the h2o mapper classpath:

.. code-block:: bash

      hadoop jar h2odriver.jar [-libjars /path/to/hive-jdbc-standalone.jar] \
          -nodes 1 -mapperXmx 4G \
          -hiveHost hostname:10000 -hivePrincipal hive/hostname@EXAMPLE.COM \
          -pricipal user/host@DOMAIN.COM -keytab path/to/user.keytab \
          -refreshHiveTokens

.. important::
    
    The provided keytab (``refreshHiveTokens``) will be copied over to the machine running the H2O-3 cluster leader node. For this reason, we strongly recommended that both YARN and HDFS be secured with encryption.

.. note:: 
    
    In case generation of the refreshing HDFS delegation tokens is required, the ``-refreshHdfsTokens`` argument has to be present. In specific deployments (e.g. on CDP with IDbroker security) you might need to enable S3A token refresh to acquire (and keep refreshing) delegation tokens to access S3 buckets. This option is enabled by the ``refreshS3ATokens`` argument.

Generate the token in the driver with refresh in the mapper
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This approach is a combination of the two previous scenarios. The Hive delegation token is first generated by the h2odriver and then periodically refreshed by the H2O-3 cluster leader node.

This is the best-of-both-worlds approach. The token is generated first in the driver and is available immediately on cluster start. It is then periodically refreshed and never expires.

**Requirements**

- The Hive JDBC driver is on the h2odriver and mapper classpaths.
- The ``hiveHost``, ``hivePrincipal`` and optionally ``hiveJdbcUrlPattern`` arguments are present. See `Connect to Hive in a Kerberized Hadoop cluster <#connect-to-hive-in-a-kerberized-hadoop-cluster>`__ for more details.
- The ``refreshHiveTokens`` argument is present.

**Example**

The following is an example of generating a token in the driver and setting up token refresh using the h2o mapper classpath:

.. code-block:: bash

      export HADOOP_CLASSPATH=/path/to/hive-jdbc-standalone.jar
      hadoop jar h2odriver.jar [-libjars /path/to/hive-jdbc-standalone.jar] \
          -nodes 1 -mapperXmx 4G \
          -hiveHost hostname:10000 -hivePrincipal hive/hostname@EXAMPLE.COM \
          -refreshHiveTokens


Use a delegation token when connecting to Hive through JDBC
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When running the actual data-load, specify the JDBC URL with the delegation token parameter:

.. tabs::
   .. code-tab:: python

        my_citibike_data = h2o.import_sql_table(
            "jdbc:hive2://hostname:10000/default;auth=delegationToken", 
            "citibike20k", "", ""
        )

   .. code-tab:: r R

        my_citibike_data <- h2o.import_sql_table(
            "jdbc:hive2://hostname:10000/default;auth=delegationToken", 
            "citibike20k", "", ""
        )
