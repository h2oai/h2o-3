Welcome to H2O 3.0
==================

Welcome to the H2O documentation site! Depending on your area of
interest, select a learning path from the links above.

We're glad you're interested in learning more about H2O - if you have
any questions or need general support, please email them to our Google
Group, `h2ostream <mailto:h2ostream@googlegroups.com>`__ or post them on
our Google groups forum, h2ostream. This is a public forum, so your
question will be visible to other users.

**Note**: To join our Google group on h2ostream, you need a Google
account (such as Gmail or Google+). On the h2ostream page, click the
**Join group** button, then click the **New Topic** button to post a new
message. You don't need to request or leave a message to join - you
should be added to the group automatically.

We welcome your feedback! Please let us know if you have any questions
or comments about H2O by clicking the chat balloon button in the
lower-right corner in Flow (H2O's web UI).

.. figure:: images/ChatButton.png
   :alt: Chat Button

   Chat Button

Type your question in the entry field that appears at the bottom of the
sidebar and you will be connected with an H2O expert who will respond to
your query in real time.

.. figure:: images/ChatSidebar.png
   :alt: Chat Sidebar

   Chat Sidebar

--------------

 ##New Users

If you're just getting started with H2O, here are some links to help you
learn more:

-  Recommended Systems: This one-page PDF provides a basic overview of
   the operating systems, languages and APIs, Hadoop resource manager
   versions, cloud computing environments, browsers, and other resources
   recommended to run H2O. At a minimum, we recommend the following for
   compatibility with H2O:

-  **Operating Systems**: Windows 7 or later; OS X 10.9 or later, Ubuntu
   12.04, or RHEL/CentOS 6 or later
-  **Languages**: Java 7 or later; Scala v 2.10 or later; R v.3 or
   later; Python 2.7.x or 3.5.x (Scala, R, and Python are not required
   to use H2O unless you want to use H2O in those environments, but Java
   is always required)
-  **Browsers**: Latest version of Chrome, Firefox, Safari, or Internet
   Explorer (An internet browser is required to use H2O's web UI, Flow)
-  **Hadoop**: Cloudera CDH 5.2 or later (5.3 is recommended); MapR
   v.3.1.1 or later; Hortonworks HDP 2.1 or later (Hadoop is not
   required to run H2O unless you want to deploy H2O on a Hadoop
   cluster)
-  **Spark**: v 1.3 or later (Spark is only required if you want to run
   `Sparkling Water <https://github.com/h2oai/sparkling-water>`__)

-  Downloads page: First things first - download a copy of H2O here by
   selecting a build under "Download H2O" (the "Bleeding Edge" build
   contains the latest changes, while the latest alpha release is a more
   stable build), then use the installation instruction tabs to install
   H2O on your client of choice
   (`standalone <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html>`__,
   `R <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#R>`__,
   `Python <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Python>`__,
   `Hadoop <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Hadoop>`__,
   or
   `Maven <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Maven>`__)
   .

   For first-time users, we recommend downloading the latest alpha
   release and the default standalone option (the first tab) as the
   installation method. Make sure to install Java if it is not already
   installed.

-  **Tutorials**: To see a step-by-step example of our algorithms in
   action, select a model type from the following list:

   -  Deep Learning
   -  Gradient Boosting Machine (GBM)
   -  Generalized Linear Model (GLM)
   -  K-means
   -  Distributed Random Forest (DRF)

-  Getting Started with Flow: This document describes our new intuitive
   web interface, Flow. This interface is similar to IPython notebooks,
   and allows you to create a visual workflow to share with others.

-  Launch from the command line: This document describes some of the
   additional options that you can configure when launching H2O (for
   example, to specify a different directory for saved Flow data,
   allocate more memory, or use a flatfile for quick configuration of a
   cluster).

-  Algorithms: This document describes the science behind our algorithms
   and provides a detailed, per-algo view of each model type.

--------------

 ##Experienced Users

If you've used previous versions of H2O, the following links will help
guide you through the process of upgrading to H2O 3.0.

-  Recommended Systems: This one-page PDF provides a basic overview of
   the operating systems, languages and APIs, Hadoop resource manager
   versions, cloud computing environments, browsers, and other resources
   recommended to run H2O.

-  Migration Guide: This document provides a comprehensive guide to
   assist users in upgrading to H2O 3.0. It gives an overview of the
   changes to the algorithms and the web UI introduced in this version
   and describes the benefits of upgrading for users of R, APIs, and
   Java.

-  Porting R Scripts: This document is designed to assist users who have
   created R scripts using previous versions of H2O. Due to the many
   improvements in R, scripts created using previous versions of H2O
   need some revision to work with H2O 3.0. This document provides a
   side-by-side comparison of the changes in R for each algorithm, as
   well as overall structural enhancements R users should be aware of,
   and provides a link to a tool that assists users in upgrading their
   scripts.

-  Recent Changes: This document describes the most recent changes in
   the latest build of H2O. It lists new features, enhancements
   (including changed parameter default values), and bug fixes for each
   release, organized by sub-categories such as Python, R, and Web UI.

-  H2O Classic vs H2O 3.0: This document presents a side-by-side
   comparison of H2O 3.0 and the previous version of H2O. It compares
   and contrasts the features, capabilities, and supported algorithms
   between the versions. If you'd like to learn more about the benefits
   of upgrading, this is a great source of information.

-  Algorithms Roadmap: This document outlines our currently implemented
   features and describes which features are planned for future software
   versions. If you'd like to know what's up next for H2O, this is the
   place to go.

-  Contributing code: If you're interested in contributing code to H2O,
   we appreciate your assistance! This document describes how to access
   our list of Jiras that are suggested tasks for contributors and how
   to contact us.

--------------

 ##Enterprise Users

If you're considering using H2O in an enterprise environment, you'll be
happy to know that the H2O platform is supported on all major Hadoop
distributions including Cloudera Enterprise, Hortonworks Data Platform
and the MapR Apache Hadoop Distribution.

H2O can be deployed in-memory directly on top of existing Hadoop
clusters without the need for data transfers, allowing for unmatched
speed and ease of use. To ensure the integrity of data stored in Hadoop
clusters, the H2O platform supports native integration of the Kerberos
protocol.

For additional sales or marketing assistance, please email sales@h2o.ai.

-  Recommended Systems: This one-page PDF provides a basic overview of
   the operating systems, languages and APIs, Hadoop resource manager
   versions, cloud computing environments, browsers, and other resources
   recommended to run H2O.

-  H2O Enterprise Edition: This web page describes the benefits of H2O
   Enterprise Edition.

-  Security: This document describes how to use the security features
   (available only in H2O Enterprise Edition).

-  How to Pass S3 Credentials to H2O: This document describes the
   necessary step of passing your S3 credentials to H2O so that H2O can
   be used with AWS, as well as how to run H2O on an EC2 cluster.

-  Click
   `here <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Hadoop>`__
   to view instructions on how to set up H2O using Hadoop.

-  Running H2O on Hadoop: This document describes how to run H2O on
   Hadoop.

--------------

 ##Sparkling Water Users

Sparkling Water is a gradle project with the following submodules:

-  Core: Implementation of H2OContext, H2ORDD, and all technical
   integration code
-  Examples: Application, demos, examples
-  ML: Implementation of MLLib pipelines for H2O algorithms
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
installed version of Spark:

-  Use `Sparkling Water
   1.2 <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.2/6/index.html>`__
   for Spark 1.2
-  Use `Sparkling Water
   1.3 <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.3/7/index.html>`__
   for Spark 1.3+
-  Use `Sparkling Water
   1.4 <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.4/3/index.html>`__
   for Spark 1.4
-  Use `Sparkling Water
   1.5 <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.5/3/index.html>`__
   for Spark 1.5

Getting Started with Sparkling Water
------------------------------------

-  Download Sparkling Water: Go here to download Sparkling Water.

-  Sparkling Water Development Documentation: Read this document first
   to get started with Sparkling Water.

-  Launch on Hadoop and Import from HDFS: Go here to learn how to start
   Sparkling Water on Hadoop.

-  Sparkling Water Tutorials: Go here for demos and examples.

   -  Sparkling Water K-means Tutorial: Go here to view a demo that uses
      Scala to create a K-means model.

   -  Sparkling Water GBM Tutorial: Go here to view a demo that uses
      Scala to create a GBM model.

-  Sparkling Water on YARN: Follow these instructions to run Sparkling
   Water on a YARN cluster.

-  Building Applications on top of H2O: This short tutorial describes
   project building and demonstrates the capabilities of Sparkling Water
   using Spark Shell to build a Deep Learning model.

-  Sparkling Water FAQ: This FAQ provides answers to many common
   questions about Sparkling Water.

-  Connecting RStudio to Sparkling Water: This illustrated tutorial
   describes how to use RStudio to connect to Sparkling Water.

Sparkling Water Blog Posts
--------------------------

-  How Sparkling Water Brings H2O to Spark

-  H2O - The Killer App on Spark

-  In-memory Big Data: Spark + H2O

Sparkling Water Meetup Slide Decks
----------------------------------

-  Sparkling Water Meetup 02/03/2015

-  Sparkling Water Meetup

-  Interactive Session on Sparkling Water

-  Sparkling Water Hands-On

PySparkling
-----------

    *Note*: PySparkling requires `Sparkling Water
    1.5 <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.5/3/index.html>`__
    or later.

H2O's PySparkling package is not available through ``pip`` (there is
`another <https://pypi.python.org/pypi/pysparkling/>`__ similarly-named
package). H2O's PySparkling package requires
`EasyInstall <http://peak.telecommunity.com/DevCenter/EasyInstall>`__.

To install H2O's PySparkling package, use the egg file included in the
distribution.

0. Download `Spark 1.5.1 <https://spark.apache.org/downloads.html>`__.
1. Set the ``SPARK_HOME`` and ``MASTER`` variables as described on the
   `Downloads
   page <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.5/6/index.html>`__.
2. Download `Sparkling Water
   1.5 <http://h2o-release.s3.amazonaws.com/sparkling-water/rel-1.5/6/index.html>`__
3. In the unpacked Sparkling Water directory, run the following command:
   ``easy_install --upgrade sparkling-water-1.5.6/py/dist/pySparkling-1.5.6-py2.7.egg``

--------------

 ##Python Users

Pythonistas will be glad to know that H2O now provides support for this
popular programming language. Python users can also use H2O with IPython
notebooks. For more information, refer to the following links.

-  Click
   `here <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Python>`__
   to view instructions on how to use H2O with Python.

-  Python readme: This document describes how to setup and install the
   prerequisites for using Python with H2O.

-  Python docs: This document represents the definitive guide to using
   Python with H2O.

-  Python Parity: This document is is a list of Python capabilities that
   were previously available only through the H2O R interface but are
   now available in H2O using the Python interface.

-   Grid Search in Python: This notebook demonstrates the use of grid
   search in Python.

--------------

 ##R Users

Don't worry, R users - we still provide R support in the latest version
of H2O, just as before. The R components of H2O have been cleaned up,
simplified, and standardized, so the command format is easier and more
intuitive. Due to these improvements, be aware that any scripts created
with previous versions of H2O will need some revision to be compatible
with the latest version.

We have provided the following helpful resources to assist R users in
upgrading to the latest version, including a document that outlines the
differences between versions and a tool that reviews scripts for
deprecated or renamed parameters.

Currently, the only version of R that is known to be incompatible with
H2O is R version 3.1.0 (codename "Spring Dance"). If you are using that
version, we recommend upgrading the R version before using H2O.

To check which version of H2O is installed in R, use
``versions::installed.versions("h2o")``.

-  Click
   `here <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#R>`__
   to view instructions for using H2O with R.

-  R User Documentation: This document contains all commands in the H2O
   package for R, including examples and arguments. It represents the
   definitive guide to using H2O in R.

-  Porting R Scripts: This document is designed to assist users who have
   created R scripts using previous versions of H2O. Due to the many
   improvements in R, scripts created using previous versions of H2O
   will not work. This document provides a side-by-side comparison of
   the changes in R for each algorithm, as well as overall structural
   enhancements R users should be aware of, and provides a link to a
   tool that assists users in upgrading their scripts.

-  Connecting RStudio to Sparkling Water: This illustrated tutorial
   describes how to use RStudio to connect to Sparkling Water.

Ensembles
---------

Ensemble machine learning methods use multiple learning algorithms to
obtain better predictive performance.

-  H2O Ensemble GitHub repository: Location for the H2O Ensemble R
   package.

-  Ensemble Documentation: This documentation provides more details on
   the concepts behind ensembles and how to use them.

--------------

 ##API Users

API users will be happy to know that the APIs have been more thoroughly
documented in the latest release of H2O and additional capabilities
(such as exporting weights and biases for Deep Learning models) have
been added.

REST APIs are generated immediately out of the code, allowing users to
implement machine learning in many ways. For example, REST APIs could be
used to call a model created by sensor data and to set up auto-alerts if
the sensor data falls below a specified threshold.

-  H2O 3 REST API Overview: This document describes how the REST API
   commands are used in H2O, versioning, experimental APIs, verbs,
   status codes, formats, schemas, payloads, metadata, and examples.

-  REST API Reference: This document represents the definitive guide to
   the H2O REST API.

-  REST API Schema Reference: This document represents the definitive
   guide to the H2O REST API schemas.

-  H2O 3 REST API Overview: This document provides an overview of how
   APIs are used in H2O, including versioning, URLs, HTTP verbs, status
   codes, formats, schemas, and examples.

--------------

 ##Java Users

For Java developers, the following resources will help you create your
own custom app that uses H2O.

-  H2O Core Java Developer Documentation: The definitive Java API guide
   for the core components of H2O.

-  H2O Algos Java Developer Documentation: The definitive Java API guide
   for the algorithms used by H2O.

-  h2o-genmodel (POJO) Javadoc: Provides a step-by-step guide to
   creating and implementing POJOs in a Java application.

SDK Information
---------------

The Java API is generated and accessible from the `download
page <http://h2o.ai/download>`__.

-  `Central
   repository <http://search.maven.org/#search%7Cga%7C1%7Cai.h2o>`__
-  `View code on
   Github <https://github.com/h2oai/h2o-3/tree/%7B%7Blast_commit_hash%7D%7D>`__
-  `Apache
   License <https://github.com/h2oai/h2o-3/blob/master/LICENSE>`__

--------------

 ##Developers

If you're looking to use H2O to help you develop your own apps, the
following links will provide helpful references.

For the latest version of IDEA IntelliJ, run ``./gradlew idea``, then
click **File > Open** within IDEA. Select the ``.ipr`` file in the
repository and click the **Choose** button.

For older versions of IDEA IntelliJ, run ``./gradlew idea``, then
**Import Project** within IDEA and point it to the h2o-3 directory.
>\ **Note**: This process will take longer, so we recommend using the
first method if possible.

For JUnit tests to pass, you may need multiple H2O nodes. Create a
"Run/Debug" configuration with the following parameters:

::

    Type: Application
    Main class: H2OApp
    Use class path of module: h2o-app

After starting multiple "worker" node processes in addition to the JUnit
test process, they will cloud up and run the multi-node JUnit tests.

-  Recommended Systems: This one-page PDF provides a basic overview of
   the operating systems, languages and APIs, Hadoop resource manager
   versions, cloud computing environments, browsers, and other resources
   recommended to run H2O.

-  Developer Documentation: Detailed instructions on how to build and
   launch H2O, including how to clone the repository, how to pull from
   the repository, and how to install required dependencies.

-  Click
   `here <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Maven>`__
   to view instructions on how to use H2O with Maven.

-  Maven install: This page provides information on how to build a
   version of H2O that generates the correct IDE files.

-  apps.h2o.ai: Apps.h2o.ai is designed to support application
   developers via events, networking opportunities, and a new, dedicated
   website comprising developer kits and technical specs, news, and
   product spotlights.

-  H2O Project Templates: This page provides template info for projects
   created in Java, Scala, or Sparkling Water.

-  H2O Scala API Developer Documentation: The definitive Scala API guide
   for H2O.

-  Hacking Algos: This blog post by Cliff walks you through building a
   new algorithm, using K-Means, Quantiles, and Grep as examples.

-  KV Store Guide: Learn more about performance characteristics when
   implementing new algorithms.

-  Contributing code: If you're interested in contributing code to H2O,
   we appreciate your assistance! This document describes how to access
   our list of Jiras that contributors can work on and how to contact
   us. **Note**: To access this link, you must have an `Atlassian
   account <https://id.atlassian.com/signup?application=mac&tenant=&continue=https%3A%2F%2Fmy.atlassian.com>`__.

--------------

Downloading H2O
===============

-  `Download page for this
   build <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html>`__
-  `h2o.ai main download page <http://www.h2o.ai/download>`__

To download H2O, go to our `downloads
page <http://www.h2o.ai/download>`__. Select a build type (bleeding edge
or latest alpha), then select an installation method
(`standalone <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html>`__,
`R <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#R>`__,
`Python <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Python>`__,
`Hadoop <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Hadoop>`__,
or
`Maven <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/index.html#Maven>`__)
by clicking the tabs at the top of the page. Follow the instructions in
the tab to install H2O.

Starting H2O ...
================

There are a variety of ways to start H2O, depending on which client you
would like to use.

... From R
==========

To use H2O in R, follow the instructions on the download page.

... From Python
===============

To use H2O in Python, follow the instructions on the download page.

... On Spark
============

To use H2O on Spark, follow the instructions on the Sparkling Water
`download
page <http://h2o-release.s3.amazonaws.com/sparkling-water/master/latest.html>`__.
