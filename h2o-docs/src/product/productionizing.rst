.. _productionizing-h2o:

Productionizing H2O
===================

(Note:  This section is new and a work in progress...)


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

Blogs        http://blog.h2o.ai/2015/06/ask-craig-sparkling-water/

             http://blog.h2o.ai/2015/07/ask-craig-sparkling-water-2/

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

=========    ==================================================================================================
Resource     Location
=========    ==================================================================================================
Git repos    https://github.com/h2oai/h2o-tutorials/tree/master/tutorials/hive_udf_template
Tutorials    http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/hive_udf_template/index.html
=========    ==================================================================================================


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
