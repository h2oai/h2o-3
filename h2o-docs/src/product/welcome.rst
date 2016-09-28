Welcome to H2O 3
==================

H2O is an open source, in-memory, distributed, fast, and scalable machine learning and predictive analytics platform that allows you to build machine learning models on big data and provides easy productionalization of those models in an enterprise environment.

H2O's core code is written in Java. Inside H2O, a Distributed Key/Value store is used to access and reference data, models, objects, etc., across all nodes and machines. The algorithms are implemented on top of H2O's distributed Map/Reduce framework and utilize the Java Fork/Join framework for multi-threading. The data is read in parallel and is distributed across the cluster and stored in memory in a columnar format in a compressed way. H2O’s data parser has built-in intelligence to guess the schema of the incoming dataset and supports data ingest from multiple sources in various formats.

H2O’s REST API allows access to all the capabilities of H2O from an external program or script via JSON over HTTP. The Rest API is used by H2O’s web interface (Flow UI), R binding (H2O-R), and Python binding (H2O-Python).

The speed, quality, ease-of-use, and model-deployment for the various cutting edge Supervised and Unsupervised algorithms like Deep Learning, Tree Ensembles, and GLRM make H2O a highly sought after API for big data data science.

Requirements
------------

The `Recommended Systems <http://www.h2o.ai/product/recommended-systems-for-h2o/>`_ PDF provides a basic overview of the operating systems, languages and APIs, Hadoop resource manager versions, cloud computing environments, browsers, and other resources recommended to run H2O. At a minimum, we recommend the following for compatibility with H2O:

-  **Operating Systems**:
 
   -  Windows 7 or later
   -  OS X 10.9 or later
   -  Ubuntu 12.04
   -  RHEL/CentOS 6 or later
   
-  **Languages**: Scala, R, and Python are not required to use H2O unless you want to use H2O in those environments, but Java is always required. Supported versions include:

   -  Java 7 or later. To build H2O or run H2O tests, the 64-bit JDK is required. To run the H2O binary using either the command line, R, or Python packages, only 64-bit JRE is required. Both of these are available on the `Java download page <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`__.
   -  Scala 2.10 or later
   -  R version 3 or later
   -  Python 2.7.x or 3.5.x 

-  **Browser**: An internet browser is required to use H2O's web UI, Flow. Supported versions include the latest version of Chrome, Firefox, Safari, or Internet Explorer. 
-  **Hadoop**: Hadoop is not required to run H2O unless you want to deploy H2O on a Hadoop cluster. Supported versions are listed on the `Download page <http://www.h2o.ai/download/h2o/hadoop>`_ for Hadoop and include:

   -  Cloudera CDH 5.2 or later (5.3 is recommended)
   -  MapR 3.1.1 or later
   -  Hortonworks HDP 2.1 or later 

-  **Spark**: Version 1.4 or later. Spark is only required if you want to run
   `Sparkling Water <https://github.com/h2oai/sparkling-water>`__.


Supported File Formats
----------------------

H2O currently supports the following file types:

- CSV (delimited) files
- ORC
- SVMLite
- ARFF
- XLS
- XLSX
- Avro (without multifile parsing or column type modification)
- Parquet

Note that ORC is available only if H2O is running as a Hadoop job. 


New Users
---------

If you're just getting started with H2O, here are some links to help you
learn more:

-  `Downloads page <http://www.h2o.ai/download/>`_: First things first - download a copy of H2O here by
   selecting a build under "Download H2O" (the "Bleeding Edge" build
   contains the latest changes, while the latest alpha release is a more
   stable build), then use the installation instruction tabs to install
   H2O on your `client of choice <http://www.h2o.ai/download/h2o/choose>`_
   (standalone, R, Python, Hadoop, or Maven).

   For first-time users, we recommend downloading the latest alpha
   release and the default standalone option (the first tab) as the
   installation method. Make sure to install Java if it is not already
   installed.

-  **Tutorials**: To see a step-by-step example of our algorithms in
   action, select a model type from the following list:

   -  `Deep Learning <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/dl/dl.md>`_
   -  `Gradient Boosting Machine (GBM) <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbm.md>`_
   -  `Generalized Linear Model (GLM) <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/glm/glm.md>`_
   -  `Kmeans <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/kmeans/kmeans.md>`_
   -  `Distributed Random Forest (DRF) <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/rf/rf.md>`_

-  :ref:`using-flow`: This document describes our new intuitive
   web interface, Flow. This interface is similar to IPython notebooks,
   and allows you to create a visual workflow to share with others.

-  `Launch from the command line <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/H2O-DevCmdLine.md>`_: This document describes some of the additional options that you can configure when launching H2O (for example, to specify a different directory for saved Flow data, to allocate more memory, or to use a flatfile for quick configuration of a cluster).

-  :ref:`Data_Science`: This document describes the science behind our algorithms and provides a detailed, per-algo view of each model type.

-  `GitHub Help <https://help.github.com/>`_: The GitHub Help system is a useful resource for becoming familiar with Git. 

New User Quick Start
~~~~~~~~~~~~~~~~~~~~

New users can follow the steps below to quickly get up and running with H2O directly from the **h2o-3** repository. These steps guide you through cloning the repository, starting H2O, and importing a dataset. Once you're up and running, you'll be better able to follow examples included within this user guide.

1. In a terminal window, create a folder for the H2O repository. The example below creates a folder called "repos" on the desktop.

::

   user$ mkdir ~/Desktop/repos
   
2. Change directories to that new folder, and then clone the repository. Notice that the prompt changes when you change directories.

::

    user$ cd ~/Desktop/repos
    repos user$ git clone https://github.com/h2oai/h2o-3.git

3. After the repo is cloned, change directories to the **h2o** folder.

::

    repos user$ cd h2o-3
    h2o-3 user$

4. Run the following command to retrieve sample datasets. These datasets are used throughout this User Guide as well as within the `Booklets <http://www.h2o.ai/resources/>`_. 

::

   h2o-3 user$ ./gradlew syncSmalldata

At this point, determine whether you want to complete this quick start in either R or Python, and run the corresponding commands below from either the R or Python tab.

.. example-code::
   .. code-block:: r

    # Download and install R:
    # 1. Go to http://cran.r-project.org/mirrors.html.
    # 2. Select your closest local mirror.
    # 3. Select your operating system (Linux, OS X, or Windows).
    # 4. Depending on your OS, download the appropriate file, along with any required packages.
    # 5. When the download is complete, unzip the file and install.

    # Start R
    h2o-3 user$ r
    ...
    Type 'demo()' for some demos, 'help()' for on-line help, or
    'help.start()' for an HTML browser interface to help.
    Type 'q()' to quit R.
    >
 
    # Copy and paste the following commands in R to download dependency packages.
    > pkgs <- c("methods","statmod","stats","graphics","RCurl","jsonlite","tools","utils")
    > for (pkg in pkgs) {if (! (pkg %in% rownames(installed.packages()))) { install.packages(pkg) }}
 
    # Run the following command to load the H2O:
    > library(h2o)

    # Run the following command to initialize H2O on your local machine (single-node cluster) using all available CPUs.
    > h2o.init(nthreads=-1)
 
    # Import the Iris (with headers) dataset.
    > path <- "smalldata/iris/iris_wheader.csv"
    > iris <- h2o.importFile(path)

    # View a summary of the imported dataset.
    > print(iris)

      sepal_len    sepal_wid    petal_len    petal_wid        class
    -----------  -----------  -----------  -----------  -----------
            5.1          3.5          1.4          0.2  Iris-setosa
            4.9          3            1.4          0.2  Iris-setosa
            4.7          3.2          1.3          0.2  Iris-setosa
            4.6          3.1          1.5          0.2  Iris-setosa
            5            3.6          1.4          0.2  Iris-setosa
            5.4          3.9          1.7          0.4  Iris-setosa
            4.6          3.4          1.4          0.3  Iris-setosa
            5            3.4          1.5          0.2  Iris-setosa
            4.4          2.9          1.4          0.2  Iris-setosa
            4.9          3.1          1.5          0.1  Iris-setosa
    [150 rows x 5 columns]
    >

   .. code-block:: python

    # Before starting Python, run the following commands to install dependencies.
    # Prepend these commands with `sudo` only if necessary.
    h2o-3 user$ [sudo] pip install -U requests
    h2o-3 user$ [sudo] pip install -U tabulate
    h2o-3 user$ [sudo] pip install -U future
    h2o-3 user$ [sudo] pip install -U six

    # Start python
    h2o-3 user$ python
    >>> 

    # Run the following command to import the H2O module:
    >>> import h2o

    # Run the following command to initialize H2O on your local machine (single-node cluster).
    >>> h2o.init()

    # Import the Iris (with headers) dataset.
    >>> path = "smalldata/iris/iris_wheader.csv"
    >>> iris = h2o.import_file(path=path)

    # View a summary of the imported dataset.
    >>> iris.summary
      sepal_len    sepal_wid    petal_len    petal_wid        class
    -----------  -----------  -----------  -----------  -----------
            5.1          3.5          1.4          0.2  Iris-setosa
            4.9          3            1.4          0.2  Iris-setosa
            4.7          3.2          1.3          0.2  Iris-setosa
            4.6          3.1          1.5          0.2  Iris-setosa
            5            3.6          1.4          0.2  Iris-setosa
            5.4          3.9          1.7          0.4  Iris-setosa
            4.6          3.4          1.4          0.3  Iris-setosa
            5            3.4          1.5          0.2  Iris-setosa
            4.4          2.9          1.4          0.2  Iris-setosa
            4.9          3.1          1.5          0.1  Iris-setosa

    [150 rows x 5 columns]
    <bound method H2OFrame.summary of >
    >>>

Experienced Users
-----------------

If you've used previous versions of H2O, the following links will help
guide you through the process of upgrading to H2O-3.

-  `Recommended Systems <http://www.h2o.ai/product/recommended-systems-for-h2o/>`_: This one-page PDF provides a basic overview of
   the operating systems, languages and APIs, Hadoop resource manager
   versions, cloud computing environments, browsers, and other resources
   recommended to run H2O.

-  :ref:`migration`: This document provides a comprehensive guide to
   assist users in upgrading to H2O 3.0. It gives an overview of the
   changes to the algorithms and the web UI introduced in this version
   and describes the benefits of upgrading for users of R, APIs, and
   Java.

-  `Recent Changes <https://github.com/h2oai/h2o-3/blob/master/Changes.md>`_: This document describes the most recent changes in
   the latest build of H2O. It lists new features, enhancements
   (including changed parameter default values), and bug fixes for each
   release, organized by sub-categories such as Python, R, and Web UI.

-  `Contributing code <https://github.com/h2oai/h2o-3/blob/master/CONTRIBUTING.md>`_: If you're interested in contributing code to H2O,
   we appreciate your assistance! This document describes how to access
   our list of Jiras that are suggested tasks for contributors and how
   to contact us.


Sparkling Water Users
---------------------

Sparkling Water is a gradle project with the following submodules:

-  Core: Implementation of H2OContext, H2ORDD, and all technical
   integration code
-  Examples: Application, demos, examples
-  ML: Implementation of MLlib pipelines for H2O algorithms
-  Assembly: Creates "fatJar" composed of all other modules
-  py: Implementation of (h2o) Python binding to Sparkling Water

The best way to get started is to modify the core module or create a new
module, which extends a project.

Users of our Spark-compatible solution, Sparkling Water, should be aware
that Sparkling Water is only supported with the latest version of H2O.
For more information about Sparkling Water, refer to the following
links.

Sparkling Water is versioned according to the Spark versioning, so make
sure to use the Sparkling Water version that corresponds to the
installed version of Spark.


Getting Started with Sparkling Water
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


-  `Download Sparkling Water <http://www.h2o.ai/download/>`_: Go here to download Sparkling Water.

-  `Sparkling Water Development Documentation <https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md>`_: Read this document first
   to get started with Sparkling Water.

-  `Launch on Hadoop and Import from HDFS <https://github.com/h2oai/sparkling-water/tree/master/examples#sparkling-water-on-hadoop>`_: Go here to learn how to start
   Sparkling Water on Hadoop.

-  `Sparkling Water Tutorials <https://github.com/h2oai/sparkling-water/tree/master/examples>`_: Go here for demos and examples.

   -  `Sparkling Water K-means Tutorial <https://github.com/h2oai/sparkling-water/blob/master/examples/src/main/scala/org/apache/spark/examples/h2o/ProstateDemo.scala>`_: Go here to view a demo that uses
      Scala to create a K-means model.

   -  `Sparkling Water GBM Tutorial <https://github.com/h2oai/sparkling-water/blob/master/examples/src/main/scala/org/apache/spark/examples/h2o/CitiBikeSharingDemo.scala>`_: Go here to view a demo that uses
      Scala to create a GBM model.

   - `Sparkling Water on YARN <http://blog.h2o.ai/2014/11/sparkling-water-on-yarn-example/>`_: Follow these instructions to run Sparkling Water on a YARN cluster.

-  `Building Machine Learning Applications with Sparkling Water <http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/sparkling-water/index.html>`_: This short tutorial describes project building and demonstrates the capabilities of Sparkling Water using Spark Shell to build a Deep Learning model.

-  `Sparkling Water FAQ <http://www.h2o.ai/product/faq/#SparklingH2O>`_: This FAQ provides answers to many common
   questions about Sparkling Water.

-  `Connecting RStudio to Sparkling Water <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/Connecting_RStudio_to_Sparkling_Water.md>`_: This illustrated tutorial describes how to use RStudio to connect to Sparkling Water.

Sparkling Water Blog Posts
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `How Sparkling Water Brings H2O to Spark <http://blog.h2o.ai/2014/09/how-sparkling-water-brings-h2o-to-spark/>`_

-  `H2O - The Killer App on Spark <http://blog.h2o.ai/2014/06/h2o-killer-application-spark/>`_

-  `In-memory Big Data: Spark + H2O <http://blog.h2o.ai/2014/03/spark-h2o/>`_

Sparkling Water Meetup Slide Decks
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `Sparkling Water Meetups <http://www.slideshare.net/0xdata/spa-43755759>`_

-  `Interactive Session on Sparkling Water <http://www.slideshare.net/0xdata/2014-12-17meetup>`_

-  `Sparkling Water Hands-On <http://www.slideshare.net/0xdata/2014-09-30sparklingwaterhandson>`_

-  `Additional Sparkling Water Meetup meeting notes <https://github.com/h2oai/sparkling-water/tree/master/examples/meetups>`_


PySparkling
~~~~~~~~~~~~

**Note**: PySparkling requires Sparkling Water 1.5 or later.

H2O's PySparkling package is not available through ``pip``. (There is
`another <https://pypi.python.org/pypi/pysparkling/>`__ similarly-named
package.) H2O's PySparkling package requires
`EasyInstall <http://peak.telecommunity.com/DevCenter/EasyInstall>`__.

To install H2O's PySparkling package, use the egg file included in the
distribution.

1. Download `Spark 1.5.1 <https://spark.apache.org/downloads.html>`__.
2. Set the ``SPARK_HOME`` and ``MASTER`` variables as described on the
   `Downloads
   page <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.5/6/index.html>`__.
3. Download `Sparkling Water
   1.5 <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.5/6/index.html>`__
4. In the unpacked Sparkling Water directory, run the following command:
   ``easy_install --upgrade sparkling-water-1.5.6/py/dist/pySparkling-1.5.6-py2.7.egg``



Python Users
--------------

Pythonistas will be glad to know that H2O now provides support for this
popular programming language. Python users can also use H2O with IPython
notebooks. For more information, refer to the following links.

-  Click
   `here <http://www.h2o.ai/download/h2o/python>`__
   to view instructions on how to use H2O with Python.

-  `Python docs <../h2o-py/docs/index.html>`_: This document represents the definitive guide to using
   Python with H2O.

-   `Grid Search in Python <https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/H2O_tutorial_eeg_eyestate.ipynb>`_: This notebook demonstrates the use of grid search in Python.


R Users
-------

Currently, the only version of R that is known to be incompatible with
H2O is R version 3.1.0 (codename "Spring Dance"). If you are using that
version, we recommend upgrading the R version before using H2O.

To check which version of H2O is installed in R, use
``versions::installed.versions("h2o")``.

-  `R User Documentation <../h2o-r/h2o_package.pdf>`_: This document contains all commands in the H2O
   package for R, including examples and arguments. It represents the
   definitive guide to using H2O in R.

-  `Connecting RStudio to Sparkling Water <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/Connecting_RStudio_to_Sparkling_Water.md>`_: This illustrated tutorial
   describes how to use RStudio to connect to Sparkling Water.


**Note**: If you are running R on Linux, then you must install ``libcurl``, which allows H2O to communicate with R. We also recommend disabling SElinux and any firewalls, at least initially until you have confirmed H2O can initialize.

- On Ubuntu, run: ``apt-get install libcurl4-openssl-dev``
- On CentOs, run: ``yum install libcurl-devel``

Ensembles
---------

Ensemble machine learning methods use multiple learning algorithms to
obtain better predictive performance.

-  `H2O Ensemble GitHub repository <https://github.com/h2oai/h2o-3/tree/master/h2o-r/ensemble>`_: Location for the H2O Ensemble R
   package.

-  `Ensemble Documentation <http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/ensembles-stacking/index.html>`_: This documentation provides more details on
   the concepts behind ensembles and how to use them.



API Users
--------------

API users will be happy to know that the APIs have been more thoroughly
documented in the latest release of H2O and additional capabilities
(such as exporting weights and biases for Deep Learning models) have
been added.

REST APIs are generated immediately out of the code, allowing users to
implement machine learning in many ways. For example, REST APIs could be
used to call a model created by sensor data and to set up auto-alerts if
the sensor data falls below a specified threshold.

-  `H2O 3 REST API Overview <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/api/REST/h2o_3_rest_api_overview.md>`_: This document describes how the REST API
   commands are used in H2O, versioning, experimental APIs, verbs,
   status codes, formats, schemas, payloads, metadata, and examples.

-  `REST API Reference <rest-api-reference.html>`_: This document represents the definitive guide to the H2O REST API.

-  `REST API Schema Reference <rest-api-reference.html#schema-reference>`_: This document represents the definitive guide to the H2O REST API schemas.


Java Users
--------------

For Java developers, the following resources will help you create your
own custom app that uses H2O.

-  `H2O Core Java Developer Documentation <../h2o-core/javadoc/index.html>`_: The definitive Java API guide
   for the core components of H2O.

-  `H2O Algos Java Developer Documentation <../h2o-algos/javadoc/index.html>`_: The definitive Java API guide
   for the algorithms used by H2O.

-  `h2o-genmodel (POJO) Javadoc <../h2o-genmodel/javadoc/index.html>`_: Provides a step-by-step guide to creating and implementing POJOs in a Java application.


Developers
--------------

If you're looking to use H2O to help you develop your own apps, the
following links will provide helpful references.

For the latest version of IDEA IntelliJ, run ``./gradlew idea``, then
click **File > Open** within IDEA. Select the ``.ipr`` file in the
repository and click the **Choose** button.

For older versions of IDEA IntelliJ, run ``./gradlew idea``, then
**Import Project** within IDEA and point it to the `h2o-3 directory <https://github.com/h2oai/h2o-3>`_.

**Note**: This process will take longer, so we recommend using the
first method if possible.

For JUnit tests to pass, you may need multiple H2O nodes. Create a
"Run/Debug" configuration with the following parameters:

::

    Type: Application
    Main class: H2OApp
    Use class path of module: h2o-app

After starting multiple "worker" node processes in addition to the JUnit
test process, they will cloud up and run the multi-node JUnit tests.

-  `Recommended Systems <http://www.h2o.ai/product/recommended-systems-for-h2o/>`_: This one-page PDF provides a basic overview of
   the operating systems, languages and APIs, Hadoop resource manager
   versions, cloud computing environments, browsers, and other resources
   recommended to run H2O.

-  `Developer Documentation <https://github.com/h2oai/h2o-3#4-building-h2o-3>`_: Detailed instructions on how to build and
   launch H2O, including how to clone the repository, how to pull from
   the repository, and how to install required dependencies.

-  Click
   `here <http://www.h2o.ai/download/h2o/maven>`__
   to view instructions on how to use H2O with Maven.

-  `Maven install <https://github.com/h2oai/h2o-3/blob/master/build.gradle>`_: This page provides information on how to build a
   version of H2O that generates the correct IDE files.

-  `apps.h2o.ai <http://apps.h2o.ai/>`_: Apps.h2o.ai is designed to support application
   developers via events, networking opportunities, and a new, dedicated
   website comprising developer kits and technical specs, news, and
   product spotlights.

-  `H2O Droplet Project Templates <https://github.com/h2oai/h2o-droplets>`_: This page provides template info for projects
   created in Java, Scala, or Sparkling Water.

-  `H2O Scala API Developer Documentation <../h2o-scala/scaladoc/index.html>`_: The definitive Scala API guide
   for H2O.
   
-  `Hacking Algos <http://blog.h2o.ai/2014/11/hacking-algorithms-in-h2o-with-cliff/>`_: This blog post by Cliff walks you through building a
   new algorithm, using K-Means, Quantiles, and Grep as examples.

-  `KV Store Guide <http://blog.h2o.ai/2014/05/kv-store-memory-analytics-part-2-2/>`_: Learn more about performance characteristics when
   implementing new algorithms.

-  `Contributing code <https://github.com/h2oai/h2o-3/blob/master/CONTRIBUTING.md>`_: If you're interested in contributing code to H2O,
   we appreciate your assistance! This document describes how to access
   our list of Jiras that contributors can work on and how to contact
   us. **Note**: To access this link, you must have an `Atlassian
   account <https://id.atlassian.com/signup?application=mac&tenant=&continue=https%3A%2F%2Fmy.atlassian.com>`__.
