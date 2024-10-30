Sparkling Water users
=====================

Sparkling Water is a gradle project with the following submodules:

- **Core**: Implementation of H2OContext, H2ORDD, and all technical integration code.
- **Examples**: Application, demos, and examples.
- **ML**: Implementation of `MLlib <https://spark.apache.org/mllib/>`__ pipelines for H2O-3 algorithms.
- **Assembly**: This creates "fatJar" (composed of all other modules).
- **py**: Implementation of (H2O-3) Python binding to Sparkling Water.

The best way to get started is to modify the core module or create a new module (which extends the project).

.. note::
	
	Sparkling Water is only supported with the latest version of H2O-3. 

	Sparkling Water is versioned according to the Spark versioning, so make sure to use the Sparkling Water version that corresponds to your installed version of spark.

Getting started with Sparking Water
-----------------------------------

This section contains links that will help you get started using Sparkling Water.

Download Sparkling Water
~~~~~~~~~~~~~~~~~~~~~~~~

1. Navigate to the `Downloads page <https://h2o.ai/resources/download/>`__.
2. Click Sparkling Water or scroll down to the Sparkling Water section. 
3. Select the version of Spark you have to download the corresponding version of Sparkling Water.

Sparkling Water documentation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The documentation for Sparkling Water is separate from the H2O-3 user guide. Read this documentation to get started with Sparkling Water.

- `Sparkling Water for Spark 3.5 <https://docs.h2o.ai/sparkling-water/3.5/latest-stable/doc/index.html>`__
- `Sparkling Water for Spark 3.4 <https://docs.h2o.ai/sparkling-water/3.4/latest-stable/doc/index.html>`__
- `Sparkling Water for Spark 3.3 <https://docs.h2o.ai/sparkling-water/3.3/latest-stable/doc/index.html>`__
- `Sparkling Water for Spark 3.2 <https://docs.h2o.ai/sparkling-water/3.2/latest-stable/doc/index.html>`__
- `Sparkling Water for Spark 3.1 <https://docs.h2o.ai/sparkling-water/3.1/latest-stable/doc/index.html>`__
- `Sparkling Water for Spark 3.0 <https://docs.h2o.ai/sparkling-water/3.0/latest-stable/doc/index.html>`__
- `Sparkling Water for Spark 2.4 <https://docs.h2o.ai/sparkling-water/2.4/latest-stable/doc/index.html>`__
- `Sparkling Water for Spark 2.3 <https://docs.h2o.ai/sparkling-water/2.3/latest-stable/doc/index.html>`__

Sparkling Water tutorials
~~~~~~~~~~~~~~~~~~~~~~~~~

This section contains `demos and examples showcasing Sparkling Water <https://github.com/h2oai/sparkling-water/tree/master/examples>`__.

- `Sparkling Water K-Means tutorial <https://github.com/h2oai/sparkling-water/blob/master/examples/src/main/scala/ai/h2o/sparkling/examples/ProstateDemo.scala>`__: This tutorial uses Scala to create a K-Means model.
- `Sparkling Water GBM tutorial <https://github.com/h2oai/sparkling-water/blob/master/examples/src/main/scala/ai/h2o/sparkling/examples/CityBikeSharingDemo.scala>`__: This tutorial uses Scala to create a GBM model.
- `Sparkling Water on YARN <https://www.h2o.ai/blog/sparkling-water-on-yarn-example/>`__: This tutorial walks you through how to run Sparkling Water on a YARN cluster.
- `Building machine learning applications with Sparkling Water <https://h2o.ai/blog/2014/sparkling-water-tutorials/>`__: This tutorial describes project building and demonstrates the capabilities of Sparkling Water using Spark Shell to build a Deep Learning model.
- `Connecting RStudio to Sparkling Water <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/Connecting_RStudio_to_Sparkling_Water.md>`__: This illustrated tutorial describes how to use RStudio to connect to Sparkling Water.

Sparkling Water FAQ
~~~~~~~~~~~~~~~~~~~

The frequently asked questions provide answers to many common questions about Sparkling Water.

- `Sparkling Water FAQ for 3.5 <https://docs.h2o.ai/sparkling-water/3.5/latest-stable/doc/FAQ.html>`__
- `Sparkling Water FAQ for 3.4 <https://docs.h2o.ai/sparkling-water/3.4/latest-stable/doc/FAQ.html>`__
- `Sparkling Water FAQ for 3.3 <https://docs.h2o.ai/sparkling-water/3.3/latest-stable/doc/FAQ.html>`__
- `Sparkling Water FAQ for 3.2 <https://docs.h2o.ai/sparkling-water/3.2/latest-stable/doc/FAQ.html>`__
- `Sparkling Water FAQ for 3.1 <https://docs.h2o.ai/sparkling-water/3.1/latest-stable/doc/FAQ.html>`__
- `Sparkling Water FAQ for 3.0 <https://docs.h2o.ai/sparkling-water/3.0/latest-stable/doc/FAQ.html>`__
- `Sparkling Water FAQ for 2.4 <https://docs.h2o.ai/sparkling-water/2.4/latest-stable/doc/FAQ.html>`__
- `Sparkling Water FAQ for 2.3 <http://docs.h2o.ai/sparkling-water/2.3/latest-stable/doc/FAQ.html>`__

Sparkling Water blog posts
--------------------------

-  `How Sparkling Water Brings H2O-3 to Spark <https://www.h2o.ai/blog/how-sparkling-water-brings-h2o-to-spark/>`_
-  `H2O - The Killer App on Spark <https://www.h2o.ai/blog/h2o-killer-application-spark/>`_
-  `In-memory Big Data: Spark + H2O <https://www.h2o.ai/blog/spark-h2o/>`_

PySparkling
-----------

PySparkling can be installed by downloading and running the PySparkling shell or by using ``pip``. PySparkling can also be installed from the `PyPI <https://pypi.org/>`__ repository. Follow the instructions for how to install PySparkling on the `Download page <http://h2o.ai/download>`__ for Sparkling Water.

PySparkling documentation
~~~~~~~~~~~~~~~~~~~~~~~~~

Documentation for PySparkling is available for the following versions:

- `PySparkling 3.5 <http://docs.h2o.ai/sparkling-water/3.5/latest-stable/doc/pysparkling.html>`__
- `PySparkling 3.4 <http://docs.h2o.ai/sparkling-water/3.4/latest-stable/doc/pysparkling.html>`__
- `PySparkling 3.3 <http://docs.h2o.ai/sparkling-water/3.3/latest-stable/doc/pysparkling.html>`__
- `PySparkling 3.2 <http://docs.h2o.ai/sparkling-water/3.2/latest-stable/doc/pysparkling.html>`__
- `PySparkling 3.1 <http://docs.h2o.ai/sparkling-water/3.1/latest-stable/doc/pysparkling.html>`__
- `PySparkling 3.0 <http://docs.h2o.ai/sparkling-water/3.0/latest-stable/doc/pysparkling.html>`__
- `PySparkling 2.4 <http://docs.h2o.ai/sparkling-water/2.4/latest-stable/doc/pysparkling.html>`__
- `PySparkling 2.3 <http://docs.h2o.ai/sparkling-water/2.3/latest-stable/doc/pysparkling.html>`__

RSparkling
----------

The RSparkling R package is an extension package for `sparklyr <https://spark.posit.co/>`__ that creates an R front-end for the Sparkling Water package from H2O-3. This provides an interface to H2O-3's high performance, distributed machine learning algorithms on Spark using R.

This package implements basic functionality by creating an H2OContext, showing the H2O Flow interface, and converting between Spark DataFrames. The main purpose of this package is to provide a connector between sparklyr and H2O-3's machine learning algorithms.

The RSparkling package uses sparklyr for Spark job deployment and initialization of Sparkling Water. After that, you can use the regular H2O R package for modeling.

RSparkling documentation
~~~~~~~~~~~~~~~~~~~~~~~~

Documentation for RSparkling is available for the following versions:

- `RSparkling 3.5  <https://docs.h2o.ai/sparkling-water/3.5/latest-stable/doc/rsparkling.html>`__
- `RSparkling 3.4  <https://docs.h2o.ai/sparkling-water/3.4/latest-stable/doc/rsparkling.html>`__
- `RSparkling 3.3 <https://docs.h2o.ai/sparkling-water/3.3/latest-stable/doc/rsparkling.html>`__
- `RSparkling 3.2  <https://docs.h2o.ai/sparkling-water/3.2/latest-stable/doc/rsparkling.html>`__
- `RSparkling 3.1  <https://docs.h2o.ai/sparkling-water/3.1/latest-stable/doc/rsparkling.html>`__
- `RSparkling 3.0  <https://docs.h2o.ai/sparkling-water/3.0/latest-stable/doc/rsparkling.html>`__
- `RSparkling 2.4  <https://docs.h2o.ai/sparkling-water/2.4/latest-stable/doc/rsparkling.html>`__
- `RSparkling 2.3  <https://docs.h2o.ai/sparkling-water/2.3/latest-stable/doc/rsparkling.html>`__


