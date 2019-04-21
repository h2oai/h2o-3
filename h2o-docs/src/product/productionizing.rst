.. _productionizing-h2o:

Productionizing H2O
===================

.. _about-pojo-mojo:

About POJOs and MOJOs
---------------------

H2O allows you to convert the models you have built to either a `Plain Old Java Object <https://en.wikipedia.org/wiki/Plain_Old_Java_Object>`__ (POJO) or a Model ObJect, Optimized (MOJO). 

H2O-generated MOJO and POJO models are intended to be easily embeddable in any Java environment. The only compilation and runtime dependency for a generated model is the ``h2o-genmodel.jar`` file produced as the build output of these packages. This file is a library that supports scoring. For POJOs, it contains the base classes from which the POJO is derived from. (You can see "extends GenModel" in a POJO class. The GenModel class is part of this library.) For MOJOs, it also contains the required readers and interpreters. The ``h2o-genmodel.jar`` file is required when POJO/MOJO models are deployed to production.

Users can refer to the Quick Start topics that follow for more information about generating POJOs and MOJOs.

Developers can refer to the the `POJO and MOJO Model Javadoc <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`__.

.. include:: mojo-quickstart.rst

.. include:: pojo-quickstart.rst


Example Design Patterns
-----------------------

Here is a collection of example design patterns for how to productionize H2O.


.. _app-consumer-loan:

Consumer loan application
~~~~~~~~~~~~~~~~~~~~~~~~~

==================================================  ===========================================================
Characteristic                                      Value
==================================================  ===========================================================
Pattern name                                        Jetty servlet
Example training language                           R
Example training data source                        CSV file
Example scoring data source                         User input to Javascript application running in browser
Scoring environment                                 REST API service provided by Jetty servlet
Scoring engine                                      H2O POJO
Scoring latency SLA                                 Real-time
==================================================  ===========================================================

=========    ==================================================================================================
Resource     Location
=========    ==================================================================================================
Git repos    https://github.com/h2oai/app-consumer-loan
Slides       http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/building-a-smarter-application/index.html
Videos       http://library.fora.tv/2015/11/09/building_a_smart_application_hands_on_tom
=========    ==================================================================================================


Craigslist application
~~~~~~~~~~~~~~~~~~~~~~

==================================================  ===========================================================
Characteristic                                      Value
==================================================  ===========================================================
Pattern name                                        Sparkling water streaming
Example training language                           Scala
Example training data source                        CSV file
Example scoring data source                         User input to Javascript application running in browser
Scoring engine                                      H2O cluster
Scoring latency SLA                                 Real-time
==================================================  ===========================================================

=========    ==================================================================================================
Resource     Location
=========    ==================================================================================================
Git repos    https://github.com/h2oai/app-ask-craig

Blogs        https://www.h2o.ai/blog/ask-craig-sparkling-water/

             https://www.h2o.ai/blog/ask-craig-sparkling-water-2/

Slides       http://www.slideshare.net/0xdata/sparkling-water-ask-craig

             http://www.slideshare.net/0xdata/sparkling-water-applications-meetup-072115
=========    ==================================================================================================


Malicious domain application
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

==================================================  ===========================================================
Characteristic                                      Value
==================================================  ===========================================================
Pattern name                                        AWS Lambda
Example training language                           Python
Example training data source                        CSV file
Example scoring data source                         User input to Javascript application running in browser
Scoring environment                                 AWS Lambda REST API endpoint
Scoring engine                                      H2O POJO
Scoring latency SLA                                 Real-time
==================================================  ===========================================================

=========    ==================================================================================================
Resource     Location
=========    ==================================================================================================
Git repos    https://github.com/h2oai/app-malicious-domains
Slides       https://github.com/h2oai/h2o-meetups/tree/master/2016_05_03_H2O_Open_Tour_Chicago_Application
Videos       http://library.fora.tv/2016/05/03/design_patterns_for_smart_applications_and_data_products
=========    ==================================================================================================


Storm bolt
~~~~~~~~~~

==================================================  ===========================================================
Characteristic                                      Value
==================================================  ===========================================================
Pattern name                                        Storm bolt
Example training language                           R
Example training data source                        CSV file
Example scoring data source                         Storm spout
Scoring environment                                 POJO embedded in a Storm bolt
Scoring engine                                      H2O POJO
Scoring latency SLA                                 Real-time
==================================================  ===========================================================

=========    ==================================================================================================
Resource     Location
=========    ==================================================================================================
Git repos    https://github.com/h2oai/h2o-tutorials/tree/master/tutorials/streaming/storm
Tutorials    http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/streaming/storm/index.html
=========    ==================================================================================================


Invoking POJO directly in R
~~~~~~~~~~~~~~~~~~~~~~~~~~~

==================================================  ===========================================================
Characteristic                                      Value
==================================================  ===========================================================
Pattern name                                        POJO in R
Example training language                           R
Example training data source                        (Need example)
Example scoring data source                         (Need example)
Scoring environment                                 R
Scoring engine                                      H2O POJO
Scoring latency SLA                                 Batch
==================================================  ===========================================================


Hive UDF
~~~~~~~~

==================================================  ===========================================================
Characteristic                                      Value
==================================================  ===========================================================
Pattern name                                        Hive UDF
Example training language                           R
Example training data source                        HDFS directory with hive part files output by a SELECT
Example scoring data source                         Hive
Scoring environment                                 Hive SELECT query (parallel MapReduce) running UDF
Scoring engine                                      H2O POJO
Scoring latency SLA                                 Batch
==================================================  ===========================================================

=============    ==================================================================================================
Resource         Location
=============    ==================================================================================================
Git repos        https://github.com/h2oai/h2o-tutorials/tree/master/tutorials/hive_udf_template
POJO Tutorial    http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/hive_udf_template/hive_udf_pojo_template/index.html
MOJO Tutorial    http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/hive_udf_template/hive_udf_mojo_template/index.html
=============    ==================================================================================================


MOJO as a JAR Resource
~~~~~~~~~~~~~~~~~~~~~~

==================================================  ============================================================
Characteristic                                      Value
==================================================  ============================================================
Pattern name                                        MOJO JAR
Example training language                           R
Example training data source                        Iris
Example scoring data source                         Single Row
Scoring environment                                 Portable
Scoring engine                                      H2O MOJO
Scoring latency SLA                                 Real-time example, but can be adapted (use in Hive UDF etc.)
==================================================  ============================================================

=========    ===================================================================================================
Resource     Location
=========    ===================================================================================================
Git repos    https://github.com/h2oai/h2o-tutorials/tree/master/tutorials/mojo-resource
=========    ===================================================================================================


Steam Scoring Server from H2O.ai
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

==================================================  ===========================================================
Characteristic                                      Value
==================================================  ===========================================================
Pattern name                                        Steam
Scoring data source                                 REST API client
Scoring environment                                 Steam scoring server
Scoring engine                                      H2O POJO
Scoring latency SLA                                 Real-time
==================================================  ===========================================================

=========    ==================================================================================================
Resource     Location
=========    ==================================================================================================
Web sites    http://www.h2o.ai/steam/
=========    ==================================================================================================


Additional Resources
--------------------

* `H2O Generated POJO Model javadoc <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`_
* `H2O Open Tour 2016 New York City: Ways to Productionize H2O <https://github.com/h2oai/h2o-meetups/tree/master/2016_07_19_H2O_Open_Tour_NYC_Prod/>`_
