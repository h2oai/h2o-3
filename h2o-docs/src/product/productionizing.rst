.. _productionizing-h2o:

Productionizing H2O
===================

About POJOs and MOJOs
---------------------

H2O allows you to convert the models you have built to either a `Plain Old Java Object <https://en.wikipedia.org/wiki/Plain_Old_Java_Object>`__ (POJO) or a Model ObJect, Optimized (MOJO). 

H2O-generated MOJO and POJO models are intended to be easily embeddable in any Java environment. The only compilation and runtime dependency for a generated model is the ``h2o-genmodel.jar`` file produced as the build output of these packages. This file is a library that supports scoring. For POJOs, it contains the base classes from which the POJO is derived from. (You can see "extends GenModel" in a POJO class. The GenModel class is part of this library.) For MOJOs, it also contains the required readers and interpreters. The ``h2o-genmodel.jar`` file is required when POJO/MOJO models are deployed to production.

Users can refer to the Quick Start topics that follow for more information about generating POJOs and MOJOs.

Developers can refer to the the `POJO and MOJO Model Javadoc <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`__.


MOJO Quick Start
~~~~~~~~~~~~~~~~

This section describes how to build and implement a MOJO (Model Object, Optimized) to use predictive scoring. Java developers should refer to the `Javadoc <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`__ for more information, including packages.

What is a MOJO?
'''''''''''''''

A MOJO (Model Object, Optimized) is an alternative to H2O's POJO. As with POJOs, H2O allows you to convert models that you build to MOJOs, which can then be deployed for scoring in real time.

**Note**: MOJOs are supported for Deep Learning, DRF, GBM, GLM, GLRM, K-Means, SVM, Word2vec, and XGBoost models only.

Benefit over POJOs
''''''''''''''''''

While POJOs continue to be supported, some customers encountered issues with large POJOs not compiling. (Note that POJOs are not supported for source files larger than 1G.) MOJOs do not have a size restriction and address the size issue by taking the tree out of the POJO and using generic tree-walker code to navigate the model. The resulting executable is much smaller and faster than a POJO.

At large scale, new models are roughly 20-25 times smaller in disk space, 2-3 times faster during "hot" scoring (after JVM is able to optimize the typical execution paths), and 10-40 times faster in "cold" scoring (when JVM doesn't know yet know the execution paths) compared to POJOs. The efficiency gains are larger the bigger the size of the model.

H2O conducted in-house testing using models with 5000 trees of depth 25. At very small scale (50 trees / 5 depth), POJOs were found to perform ≈10% faster than MOJOs for binomial and regression models, but 50% slower than MOJOs for multinomial models.

Building a MOJO
'''''''''''''''

MOJOs are built in much the same way as POJOs. The example code below shows how to start H2O, build a model using either R or Python, and then compile and run the MOJO. This example uses GBM, but any supported algorithm can be used to build a model and run the MOJO. 

**Step 1: Start H2O, then build and extract the model**

The examples below describe how to start H2O and create a model using R and Python. The ``download_mojo()`` function saves the model as a zip file. You can unzip the file to view the options used to build the file along with each tree built in the model. Note that each tree file is saved as a binary file type.

 **Build and extract a model using R**:

 1. Open a terminal window and start R.
 2. Run the following commands to build a simple GBM model.

   ::

       library(h2o)
       h2o.init(nthreads=-1)
       path <- system.file("extdata", "prostate.csv", package="h2o")
       h2o_df <- h2o.importFile(path)
       h2o_df$CAPSULE <- as.factor(h2o_df$CAPSULE)
       model <- h2o.gbm(y="CAPSULE",
               x=c("AGE", "RACE", "PSA", "GLEASON"),
               training_frame=h2o_df,
               distribution="bernoulli",
               ntrees=100,
               max_depth=4,
               learn_rate=0.1)

 3. Download the MOJO and the resulting h2o-genmodel.jar file to a new **experiment** folder. Note that the ``h2o-genmodel.jar`` file is a library that supports scoring and contains the required readers and interpreters. This file is required when MOJO models are deployed to production.

   ::

       modelfile <- h2o.download_mojo(model,path="~/experiments/", get_genmodel_jar=TRUE)
       print("Model saved to " + modelfile)
       Model saved to /Users/user/GBM_model_R_1475248925871_74.zip"

 **Build and extract a model using Python**

 1. Open a terminal window and start python.
 2. Run the following commands to build a simple GBM model. The model, along with the **h2o-genmodel.jar** file will then be downloaded to an **experiment** folder.

   ::

       import h2o
       from h2o.estimators.gbm import H2OGradientBoostingEstimator
       h2o.init()
       h2o_df = h2o.load_dataset("prostate.csv")
       h2o_df["CAPSULE"] = h2o_df["CAPSULE"].asfactor()
       model=H2OGradientBoostingEstimator(distribution="bernoulli",
                          ntrees=100,
                          max_depth=4,
                          learn_rate=0.1)
       model.train(y="CAPSULE",
           x=["AGE","RACE","PSA","GLEASON"],
           training_frame=h2o_df)

 3. Download the MOJO and the resulting ``h2o-genmodel.jar`` file to a new **experiment** folder. Note that the ``h2o-genmodel.jar`` file is a library that supports scoring and contains the required readers and interpreters. This file is required when MOJO models are deployed to production.

   ::

       modelfile = model.download_mojo(path="~/experiment/", get_genmodel_jar=True)
       print("Model saved to " + modelfile)
       Model saved to /Users/user/GBM_model_python_1475248925871_888.zip           

 **Step 2: Compile and run the MOJO**

 1. Open a *new* terminal window and change directories to the **experiment** folder:
 
   ::

       $ cd experiment

 2. Create your main program in the **experiment** folder by creating a new file called main.java (for example, using "vim main.java"). Include the following contents. Note that this file references the GBM model created above using R.

   .. code:: java

       import java.io.*;
       import hex.genmodel.easy.RowData;
       import hex.genmodel.easy.EasyPredictModelWrapper;
       import hex.genmodel.easy.prediction.*;
       import hex.genmodel.MojoModel;

       public class main {
         public static void main(String[] args) throws Exception {
           EasyPredictModelWrapper model = new EasyPredictModelWrapper(MojoModel.load("GBM_model_R_1475248925871_74.zip"));

           RowData row = new RowData();
           row.put("AGE", "68");
           row.put("RACE", "2");
           row.put("DCAPS", "2");
           row.put("VOL", "0");
           row.put("GLEASON", "6");

           BinomialModelPrediction p = model.predictBinomial(row);
           System.out.println("Has penetrated the prostatic capsule (1=yes; 0=no): " + p.label);
           System.out.print("Class probabilities: ");
           for (int i = 0; i < p.classProbabilities.length; i++) {
             if (i > 0) {
           System.out.print(",");
             }
             System.out.print(p.classProbabilities[i]);
           }
           System.out.println("");
         }
       }

 3. Compile in terminal window 2.

   .. code:: bash

       $ javac -cp h2o-genmodel.jar -J-Xms2g -J-XX:MaxPermSize=128m main.java

 4. Run in terminal window 2.

   .. code:: bash

       # Linux and OS X users
       $ java -cp .:h2o-genmodel.jar main 

       # Windows users
       $ java -cp .;h2o-genmodel.jar main  

   The following output displays:

   .. code:: bash

	    Has penetrated the prostatic capsule (1 yes; 0 no): 0
	    Class probabilities: 0.8059929056296662,0.19400709437033375


POJO Quick Start
~~~~~~~~~~~~~~~~

This section describes how to build and implement a POJO to use predictive scoring. Java developers should refer to the `Javadoc <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`__ for more information, including packages.

**Notes**: POJOs are not supported for source files larger than 1G. For more information, refer to the `FAQ <#POJO_Err>`__ below. POJOs are also not supported for XGBoost, Stacked Ensembles, or AutoML models. 

What is a POJO?
'''''''''''''''

H2O allows you to convert the models you have built to a Plain Old Java Object (POJO), which can then be easily deployed within your Java app and scheduled to run on a specified dataset.

POJOs allow users to build a model using H2O and then deploy the model to score in real-time, using the POJO model or a REST API call to a scoring server.

The only compilation and runtime dependency for a generated model is the ``h2o-genmodel.jar`` file produced as the build output of these packages. This file is a library that supports scoring, and it contains the base classes from which the POJO is derived from. (You can see "extends GenModel" in a pojo class. The GenModel class is part of this library.) The ``h2o-genmodel.jar`` file is required when POJO models are deployed to production.

Building a POJO
'''''''''''''''

The example code below shows how to start H2O, build a model using either R or Python, and then compile and run the POJO. This example uses GBM, but any supported algorithm can be used to build a model and run the POJO. 

1. Start H2O in terminal window #1:

	``$ java -jar h2o.jar``

2. Build a model using your web browser:

   a. Go to http://localhost:54321
   b. Click **View Example Flows** near the right edge of the screen. Here is a screenshot of what to look for:

    .. figure:: /howto/images/view_example_flows.png
      :alt: View Example Flows
      :width: 272
      :height: 237

   c. Click ``GBM_Airlines_Classification.flow``

   d. If a confirmation prompt appears asking you to "Load Notebook", click it.

   e. From the "Flow" menu choose the "Run all cells" option

   f. Scroll down and find the "Model" cell in the notebook. Click on the **Download POJO** button as shown in the following screenshot:

    .. figure:: /howto/images/download_pojo.png
       :alt: Download POJO

    **Note**: The instructions below assume that the POJO model was downloaded to the "Downloads" folder.

3. Download model pieces in a *new* terminal window - H2O must still be running in terminal window #1:

   ::

       $ mkdir experiment
       $ cd experiment
       $ mv ~/Downloads/gbm_pojo_test.java .
       $ curl http://localhost:54321/3/h2o-genmodel.jar > h2o-genmodel.jar

4. Create your main program in terminal window #2 by creating a new file called main.java (``vim main.java``) with the following contents:

   ::

       import java.io.*;
       import hex.genmodel.easy.RowData;
       import hex.genmodel.easy.EasyPredictModelWrapper;
       import hex.genmodel.easy.prediction.*;

       public class main {
         private static String modelClassName = "gbm_pojo_test";

         public static void main(String[] args) throws Exception {
           hex.genmodel.GenModel rawModel;
           rawModel = (hex.genmodel.GenModel) Class.forName(modelClassName).newInstance();
           EasyPredictModelWrapper model = new EasyPredictModelWrapper(rawModel);

           RowData row = new RowData();
           row.put("Year", "1987");
           row.put("Month", "10");
           row.put("DayofMonth", "14");
           row.put("DayOfWeek", "3");
           row.put("CRSDepTime", "730");
           row.put("UniqueCarrier", "PS");
           row.put("Origin", "SAN");
           row.put("Dest", "SFO");

           BinomialModelPrediction p = model.predictBinomial(row);
           System.out.println("Label (aka prediction) is flight departure delayed: " + p.label);
           System.out.print("Class probabilities: ");
           for (int i = 0; i < p.classProbabilities.length; i++) {
             if (i > 0) {
               System.out.print(",");
             }
             System.out.print(p.classProbabilities[i]);
           }
           System.out.println("");
         }
       }

5. Compile the POJO in terminal window 2:

   ::

       $ javac -cp h2o-genmodel.jar -J-Xmx2g -J-XX:MaxPermSize=128m gbm_pojo_test.java main.java

6. Run the POJO in terminal window 2.

 For Linux and OS X users: ``$ java -cp .:h2o-genmodel.jar main``

 For Windows users: ``$ java -cp .;h2o-genmodel.jar main``

 The following output displays:

 ::

     Label (aka prediction) is flight departure delayed: YES
     Class probabilities: 0.4319916897116479,0.5680083102883521

Extracting Models from H2O
''''''''''''''''''''''''''

Generated models can be extracted from H2O in the following ways:

-  **From the H2O Flow Web UI:**

   When viewing a model, click the **Download POJO** button at the top of the model cell, as shown in the example in the Quick start section. You can also preview the POJO inside Flow, but it will only show the first thousand lines or so in the web browser, truncating large models.

-  **From R:**

   The following code snippet shows an example of H2O building a model and downloading its corresponding POJO from an R script.

   ::

	    library(h2o)
	    h2o.init()
	    path <- system.file("extdata", "prostate.csv", package = "h2o")
	    h2o_df <- h2o.importFile(path)
	    h2o_df$CAPSULE <- as.factor(h2o_df$CAPSULE)
	    model <- h2o.glm(y = "CAPSULE",
	                    x = c("AGE", "RACE", "PSA", "GLEASON"),
	                    training_frame = h2o_df,
	                    family = "binomial")
	    h2o.download_pojo(model)

-  **From Python:**

   The following code snippet shows an example of building a model and downloading its corresponding POJO from a Python script.

	::

	    import h2o
	    h2o.init()
	    from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	    path = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"
	    h2o_df = h2o.import_file(path)
	    h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
	    model = H2OGeneralizedLinearEstimator(family = "binomial")
	    model.train(y = "CAPSULE",
	                x = ["AGE", "RACE", "PSA", "GLEASON"],
	                training_frame = h2o_df)
	    h2o.download_pojo(model)

.. raw:: html

   <!---

   **From Java:**

   TODO: provide pointer of doing this directly from Java
   From Sparkling Water:
   TODO: provide pointer of doing this from Sparkling Water

   -->

Use Cases
'''''''''

The following use cases are demonstrated with code examples:

-  **Reading new data from a CSV file and predicting on it**: The PredictCsv class is used by the H2O test harness to make predictions on new data points.
-  **Getting a new observation from a JSON request and returning a prediction**
-  **Calling a user-defined function directly from hive**: See the `H2O-3 training github repository <https://github.com/h2oai/h2o-world-2015-training/tree/master/tutorials/hive_udf_template>`__.

FAQ
'''

-  **How do I score new cases in real-time in a production environment?**

  If you're using the UI, click the **Preview POJO** button for your model. This produces a Java class with methods that you can reference and use in your production app.

-  **What kind of technology would I need to use?**

  Anything that runs in a JVM. The POJO is a standalone Java class with no dependencies on H2O.

-  **How should I format my data before calling the POJO?**

  Here are our requirements (assuming you are using the "easy" Prediction API for the POJO as described in the `Javadoc <http://h2o-release.s3.amazonaws.com/h2o/%7B%7Bbranch_name%7D%7D/%7B%7Bbuild_number%7D%7D/docs-website/h2o-genmodel/javadoc/index.html>`__).

   -  Input columns must only contain categorical levels that were seen during training
   -  Any additional input columns not used for training are ignored
   -  If no input column is specified, it will be treated as an ``NA``
   -  Some models do not handle NAs well (e.g., GLM)
   -  Any transformations applied to data before model training must also be applied before calling the POJO predict method

-  **How do I run a POJO on a Spark Cluster?**

  The POJO provides just the math logic to do predictions, so you won’t find any Spark (or even H2O) specific code there. If you want to use the POJO to make predictions on a dataset in Spark, create a map to call the POJO for each row and save the result to a new column, row-by-row.

-  **How do I communicate with a remote cluster using the REST API?**

  You can dl the POJO using the REST API but when calling the POJO predict function, it's in the same JVM, not across a REST API.

-  **Is it possible to make predictions using my H2O cluster with the
   REST API?**

  Yes, but this way of making predictions is separate from the POJO. For more information about in-H2O predictions (as opposed to POJO predictions), see the documentation for the H2O REST API endpoint /3/Predictions.

-  **Why did I receive the following error when trying to compile the POJO?**

  The following error is generated when the source file is larger than 1G.

  ::

      Michals-MBP:b michal$ javac -cp h2o-genmodel.jar -J-Xmx2g -J-XX:MaxPermSize=128m drf_b9b9d3be_cf5a_464a_b518_90701549c12a.java
      An exception has occurred in the compiler (1.7.0_60). Please file a bug at the Java Developer Connection (http://java.sun.com/webapps/bugreport)  after checking the Bug Parade for duplicates. Include your program and the following diagnostic in your report.  Thank you.
      java.lang.IllegalArgumentException
          at java.nio.ByteBuffer.allocate(ByteBuffer.java:330)
          at com.sun.tools.javac.util.BaseFileManager$ByteBufferCache.get(BaseFileManager.java:308)
          at com.sun.tools.javac.util.BaseFileManager.makeByteBuffer(BaseFileManager.java:280)
          at com.sun.tools.javac.file.RegularFileObject.getCharContent(RegularFileObject.java:112)
          at com.sun.tools.javac.file.RegularFileObject.getCharContent(RegularFileObject.java:52)
          at com.sun.tools.javac.main.JavaCompiler.readSource(JavaCompiler.java:571)
          at com.sun.tools.javac.main.JavaCompiler.parse(JavaCompiler.java:632)
          at com.sun.tools.javac.main.JavaCompiler.parseFiles(JavaCompiler.java:909)
          at com.sun.tools.javac.main.JavaCompiler.compile(JavaCompiler.java:824)
          at com.sun.tools.javac.main.Main.compile(Main.java:439)
          at com.sun.tools.javac.main.Main.compile(Main.java:353)
          at com.sun.tools.javac.main.Main.compile(Main.java:342)
          at com.sun.tools.javac.main.Main.compile(Main.java:333)
          at com.sun.tools.javac.Main.compile(Main.java:76)
          at com.sun.tools.javac.Main.main(Main.java:61)



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
