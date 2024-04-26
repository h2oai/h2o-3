Welcome to H2O-3
================

H2O is an open source, in-memory, distributed, fast, and scalable machine learning and predictive analytics platform. It lets you build machine learning models on big data and provides easy productionalization of those models in an enterprise environment.

Basic framework
---------------

H2O's core code is written in Java. A distributed key-value store is used to access and reference data, models, objects, etc. across all nodes and machines. The algorithms are implemented on top of H2O's distributed map-reduce framework and utilize the Java fork/join framework for multi-threading. The data is read in parallel and is distributed across the cluster. It is stored in-memory in a columnar format in a compressed way. H2O's data parser has built-in intelligence to guess the schema of the incoming dataset and supports data ingest from multiple sources in various formats.

REST API
~~~~~~~~

H2O's REST API allow access to all the capabilities of H2O frm an external program or script through JSON over HTTP. The REST API is used by H2O's web interface (Flow UI), R binding (H2O-R), and Python binding (H2O-Python).

The speed, quality, ease-of-use, and model-deployment for our various supervised and unsupervised algorithms (such as Deep Learning, GLRM, or our tree ensembles) make H2O a highly sought after API for big data data science.

H2O is licensed under the `Apache License, Version 2.0 <http://www.apache.org/licenses/LICENSE-2.0>`__.

Requirements
------------

We recommend the following at minimum for compatibility with H2O:

- **Operating systems**:
   
   - Windows 7+
   - Mac OS 10.9+
   - Ubuntu 12.04
   - RHEL/CentOS 6+

- **Languages**: R and Python are not required to use H2O (unless you want to use H2O in those environments), but Java is always required (see `Java requirements <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/welcome.html#java-requirements>`__).
   
   - R version 3+
   - Python 3.6.x, 3.7.x, 3.8.x, 3.9.x, 3.10.x, 3.11.x

- **Browser**: An internet browser is required to use H2O's web UI, Flow.
   
   - Google Chrome
   - Firefox
   - Safari
   - Microsoft Edge

Java requirements
~~~~~~~~~~~~~~~~~

H2O runs on Java. The 64-bit JDK is required to build H2O or run H2O tests. Only the 64-bit JRE is required to run the H2O binary using either the command line, R, or Python packages.

Java support
''''''''''''

H2O supports the following versions of Java:

- Java SE 17
- Java SE 16
- Java SE 15
- Java SE 14
- Java SE 13
- Java SE 12
- Java SE 11
- Java SE 10
- Java SE 9
- Java SE 8

`Download the latest supported version of Java <https://jdk.java.net/archive/>`__.

Unsupported Java versions
'''''''''''''''''''''''''

We recommend that only power users force an unsupported Java version. Unsupported Java versions can only be used for experiments. For production versions, we only guarantee the Java versions from the supported list.

How to force an unsupported Java version
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The following code forces an unsupported Java version:

.. code-block:: bash

   java -jar -Dsys.ai.h2o.debug.allowJavaVersions=19 h2o.jar

Java support with H2O and Hadoop
''''''''''''''''''''''''''''''''

Java support is different between H2O and Hadoop. Hadoop only supports `Java 8 and Java 11 <https://cwiki.apache.org/confluence/display/HADOOP/Hadoop+Java+Versions>`__. Therefore, when running H2O on Hadoop, we recommend only running H2O on Java 8 or Java 11.

Optional requirements
~~~~~~~~~~~~~~~~~~~~~

This section outlines requirements for optional ways you can run H2O.

Optional Hadoop requirements
''''''''''''''''''''''''''''

Hadoop is only required if you want to deploy H2O on a Hadoop cluster. Supported versions are listed on the `Downloads <http://www.h2o.ai/download/>`__ page (when you select the Install on Hadoop tab) and include:

- Cloudera CDH 5.4+
- Hortonworks HDP 2.2+
- MapR 4.0+
- IBM Open Platform 4.2

See the `Hadoop users <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/welcome.html#on-hadoop>`__ section for more details.

Optional Conda requirements
'''''''''''''''''''''''''''

Conda is only required if you want to run H2O on the Anaconda cloud:

- Conda 3.6+ repository

Optional Spark requirements
'''''''''''''''''''''''''''

Spark is only required if you want to run Sparkling Water. Supported spark versions:

- Spark 3.4
- Spark 3.3
- Spark 3.2
- Spark 3.1
- Spark 3.0
- Spark 2.4
- Spark 2.3


User support
------------

H2O supports many different types of users. 

.. toctree::
    :maxdepth: 1

    getting-started/getting-started
    getting-started/flow-users
    getting-started/python-users
    getting-started/r-users
    getting-started/sparkling-users
    getting-started/api-users
    getting-started/java-users
    getting-started/hadoop-users
    getting-started/docker-users
    getting-started/kubernetes-users
    getting-started/experienced-users


