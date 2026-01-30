:tocdepth: 2


The H2O Python Module
=====================

This Python module provides access to the H2O JVM, as well as its extensions, objects,
machine-learning algorithms, and modeling support capabilities, such as basic
munging and feature generation.

The H2O JVM provides a web server so that all communication occurs on a socket (specified
by an IP address and a port) via a series of REST calls (see connection.py for the REST
layer implementation and details).

There is a single active connection to the H2O JVM at any time, and this handle is stashed
out of sight in a singleton instance of :class:`H2OConnection`. In other words, this
package does not rely on Jython, and there
is no direct manipulation of the JVM.

The H2O Python module is not intended as a replacement for other popular machine learning
frameworks such as scikit-learn, pylearn2, and their ilk, but is intended to bring H2O to
a wider audience of data and machine learning devotees who work exclusively with Python.

H2O from Python is a tool for rapidly turning over models, doing data munging, and
building applications in a fast, scalable environment without any of the mental anguish
about parallelism and distribution of work.

What is H2O?
------------

H2O is a Java-based software for data modeling and general computing. The H2O software is
many things, but the primary purpose of H2O is as a distributed (many machines),
parallel (many CPUs), in memory (several hundred GBs Xmx) processing engine.

There are two levels of parallelism:

    * within node
    * across (or between) nodes

The goal of H2O is to allow simple horizontal scaling to a given problem in order to
produce a solution faster. The conceptual paradigm MapReduce (AKA "divide and conquer
and combine"), along with a good concurrent application structure,
(c.f. jsr166y and NonBlockingHashMap) enable this type of scaling in H2O.

For application developers and data scientists, the gritty details of thread-safety, algorithm parallelism, and node coherence on a network are concealed by simple-to-use REST calls that are all documented here. In addition, H2O is an open-source project under the Apache v2 licence. All of the source code is on `github <https://github.com/h2oai/h2o-3>`_. 

For questions, there is an active `google group mailing list <https://groups.google.com/forum/#!forum/h2ostream>`_, or questions can be posted on the `H2O community site on Stack Overflow <http://stackoverflow.com/questions/tagged/h2o>`__. Our `GitHub issues <https://github.com/h2oai/h2o-3/issues>`_ are also open for public use. 

Last, but not least, we regularly engage the machine learning community all over the nation with a very busy `meetup schedule <https://www.h2o.ai/community/>`_ (so if you're not in The Valley, no sweat, we're probably coming to your area soon!), and finally, we host our very own `H2O World conference <http://h2oworld.h2o.ai/>`_.

The rest of this document explains a few of the client-server details and the general
programming model for interacting with H2O from Python.

The H2O Object System
+++++++++++++++++++++

H2O uses a distributed key-value store (the "DKV") that contains pointers to the
various objects of the H2O ecosystem. Some shared objects are mutable by the client;
some shared objects are read-only by the client, but are mutable by H2O (e.g. a model
being constructed will change over time); and actions by the client may have side-effects
on other clients (multi-tenancy is not a supported model of use, but it is possible for
multiple clients to attach to a single H2O cluster).

Briefly, these objects are:

     * :mod:`Key`:    A key is an entry in the DKV that maps to an object in H2O.

     * :mod:`Frame`:  A Frame is a collection of Vec objects. It is a 2D array of elements.

     * :mod:`Vec`:    A Vec is a collection of Chunk objects. It is a 1D array of elements.

     * :mod:`Chunk`:  A Chunk holds a fraction of the BigData. It is a 1D array of elements.

     * :mod:`ModelMetrics`:   A collection of metrics for a given category of model.

     * :mod:`Model`:  A model is an immutable object having `predict` and `metrics` methods.

     * :mod:`Job`:    A Job is a non-blocking task that performs a finite amount of work.

Many of these objects have no meaning to a Python end-user, but to make sense of
the objects available in this module it is helpful to understand how these objects map to
objects in the JVM. After all, this module is an interface that allows the
manipulation of a distributed system.

Let's get started!

Installing H2O-3
----------------

Run the following commands in a Terminal window to install H2O for Python. 

1. Install dependencies (prepending with ``sudo`` if needed):

 ::

  pip install requests
  pip install tabulate
  pip install future

  # Required for plotting:
  pip install matplotlib

2. Run the following command to remove any existing H2O module for Python (append with ``--user`` if needed):

 ::

  pip uninstall h2o

3. Use ``pip`` to install this version of the H2O Python module. 

 ::

  pip install -f http://h2o-release.s3.amazonaws.com/h2o/latest_stable_Py.html h2o

Starting H2O and Inspecting the Cluster
---------------------------------------

There are many tools for directly interacting with user-visible objects in the H2O cluster.
Every new python session begins by initializing a connection between the python client and
the H2O cluster. Note that ``h2o.init()`` accepts a number of arguments that are described 
in the `h2o.init <h2o.html#h2o.init>`__ section.

::

    import h2o
    h2o.init()

By default, this will attempt to discover an H2O at ``localhost:54321``. Note that If it fails to find
a running H2O instance at this address, it will seek out an h2o jar at several possible
locations. If no jar is found, then an :class:`H2OStartupError` will be raised:

::

    h2o.init()
    Connecting to H2O server at http://localhost:54321....... failed.
    H2OStartupError:
        Cannot start local server: h2o.jar not found. Paths searched:
        /Users/me/github/h2o-3/build/h2o.jar
        /Library/Frameworks/Python.framework/Versions/2.7/h2o_jar/h2o.jar
        /usr/local/h2o_jar/h2o.jar
        /Library/Frameworks/Python.framework/Versions/2.7/local/h2o_jar/h2o.jar
        /Users/me/Library/Python/2.7/h2o_jar/h2o.jar
        /Library/Frameworks/Python.framework/Versions/2.7/h2o_jar/h2o.jar

After making a successful connection, you can obtain a high-level summary of the cluster
status:

::

    h2o.cluster_info()
    --------------------------  ---------------------------
    H2O cluster uptime:         01 secs
    H2O cluster timezone:       America/Los_Angeles
    H2O data parsing timezone:  UTC
    H2O cluster version:        3.26.0.6
    H2O cluster version age:    27 days
    H2O cluster name:           spIdea
    H2O cluster total nodes:    1
    H2O cluster free memory:    3.556 Gb
    H2O cluster total cores:    8
    H2O cluster allowed cores:  8
    H2O cluster status:         accepting new members, healthy
    H2O connection url:         http://127.0.0.1:54321
    H2O connection proxy:
    H2O internal security:      False
    H2O API Extensions:         Amazon S3, XGBoost, Algos, AutoML, Core V3, TargetEncoder, Core V4
    Python version:             2.7.15 final
    --------------------------  ---------------------------

Listing Cluster Contents
++++++++++++++++++++++++

To list the current contents of the H2O cluster, you can use the :mod:`h2o.ls` command:

::

  h2o.ls()
                                                   key
  0                   GBM_model_python_1447790800404_2
  1  modelmetrics_GBM_model_python_1447790800404_2@...
  2                                       prostate.hex
  3                                               py_2

There are models, data, and model metrics all floating around in the DKV.

Removing Objects From the Cluster
+++++++++++++++++++++++++++++++++

If you want to delete something from the DKV, you can do this with the :mod:`h2o.remove`
method:

::

  h2o.remove("py_2")
  h2o.ls()
                                                   key
  0                   GBM_model_python_1447790800404_2
  1  modelmetrics_GBM_model_python_1447790800404_2@...
  2                                       prostate.hex

Recovering From An Unexpected Session Exit
++++++++++++++++++++++++++++++++++++++++++

If the Python interpreter fails, for whatever reason, but the H2O cluster survives, then
you can attach a new python session, and pick up where you left off by using
:mod:`h2o.get_frame`, :mod:`h2o.get_model`, and :mod:`h2o.get_grid`.

The usage details of these methods are spelled out elsewhere, but here's a sample
usage of :mod:`h2o.get_frame`:

::

  h2o.ls()
              key
  0  prostate.hex
  1          py_7
  some_frame = h2o.get_frame("py_7")
  some_frame.head()


Objects In This Module
----------------------

H2OFrame
++++++++

An H2OFrame is a 2D array of uniformly-typed columns. Data in H2O is compressed and is
held in the JVM heap (i.e. data is "in memory"), and *not* in the python process local
memory. The H2OFrame is an iterable (supporting list comprehensions). All an H2OFrame
object is, therefore, is a wrapper on a list that supports various types of operations
that may or may not be lazy. Here's an example showing how a list comprehension is
combined with lazy expressions to compute the column means for all columns in the
H2OFrame:

::

  # import the prostate data
  df = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
  
  # compute column means
  colmeans = df.mean()
  
  # print the results
  colmeans
  [13074.169141456336, 0.2075591357851537, 13.715904065566173, 5.68435293299533, nan, 71915.67051974901, nan, nan, 15.881530121290117, 0.2273570060625282, 54.07917280242258, 24.579733834274638, 0.1830388994249544, 14.854273655448353, 0.6392701860513333]

Lazy expressions will be discussed briefly in the coming sections, as they are not
necessarily going to be integral to the practicing data scientist. However, their primary
purpose is to cut down on the chatter between the client (a.k.a the python interface) and
H2O. Lazy expressions are
`Katamari'd <http://www.urbandictionary.com/define.php?term=Katamari>`_ together and only
ever evaluated when some piece of output is requested (e.g. print-to-screen).

The set of operations on an H2OFrame is described in a dedicated chapter, but
in general, this set of operations closely resembles those that may be
performed on an R data.frame. This includes all types of slicing (with complex
conditionals), broadcasting operations, and a slew of math operations for transforming and
mutating a Frame -- all the while the actual Big Data is sitting in the H2O cluster. The
semantics for modifying a Frame closely resemble R's copy-on-modify semantics, except
when it comes to mutating a Frame in place. For example, it's possible to assign all
occurrences of the number `0` in a column to missing (or `NA` in R parlance) as
demonstrated in the following snippet:

::

  # import the prostate data
  df = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
  
  # select the VOL column
  vol = df['VOL']
  
  # 0 VOL means 'missing'
  vol[vol == 0] = None                                         

After this operation, `vol` has been permanently mutated in place (it is not a copy!).

ExprNode
++++++++
In the guts of this module is the ExprNode class, which defines objects holding
the cumulative, unevaluated expressions that underpin H2OFrame objects.

For example:

::

  # import the prostate data
  fr = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
  
  # "a" is an H2OFrame, but unevaluated
  a = fr + 3.14159

These objects are not as important to distinguish at the user level, and all operations
can be performed with the mental model of operating on 2D frames (i.e. everything is an
H2OFrame).

In the previous snippet, `a` has not yet triggered any big data evaluation and is, in
fact, a pending computation. Once `a` is evaluated, it stays evaluated. Additionally,
all dependent subparts composing `a` are also evaluated.

This module relies on reference counting of python objects to dispose of
out-of-scope objects. The ExprNode class destroys objects and their big data
counterparts in the H2O cluster using a remove call:

::

  # import the prostate data
  fr = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")  
  
  # remove prostate data
  h2o.remove(fr)
  
  # attempting to use fr results in an attribute error
  fr + 2                                                       

Notice that attempting to use the object after a remove call has been issued will
result in an :mod:`AttributeError`. Therefore, any working references may not be cleaned
up, but they will no longer be functional.

Models
++++++

Model building in this python module is influenced by both scikit-learn and the H2O R
package. A section of documentation is devoted to discussing the way to use the existing
scikit-learn software with H2O-powered algorithms.

Every model object inherits from the :class:`H2OEstimator` from the :mod:`h2o.estimators`
submodule. After an estimator has been specified and trained, it will additionally inherit
methods to the following five model categories:

    * Regression
    * Binomial
    * Multinomial
    * Clustering
    * Autoencoder

Let's build a logistic regression using H2O's GLM:

::

  # import the glm estimator object
  from h2o.estimators.glm import H2OGeneralizedLinearEstimator

  # import the prostate data
  fr = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

  # make the 2nd column a factor
  fr[1] = fr[1].asfactor()
  
  # specify the model
  m = H2OGeneralizedLinearEstimator(family="binomial")

  # <class 'h2o.estimators.glm.H2OGeneralizedLinearEstimator'>
  m.__class__

  # train the model
  m.train(x=fr.names[2:], y="CAPSULE", training_frame=fr)

  # print the model to screen
  m                                                              

As you can see the model setup and train is akin to the scikit-learn style. The reason
for the :mod:`train` verb over :mod:`fit` is because `x` and `y` are column references
(rather than data objects as they would be in scikit). H2OEstimator implements a fit
method, but its usage is meant strictly for the scikit-learn Pipeline and grid search
framework. Use of :mod:`fit` outside of this framework will result in a usage warning.

This example also showcases an important feature-munging step needed for GLM to perform a
classification task rather than a regression task. Namely, the second column is initially
read as a numeric column, but it must be changed to a factor by way of the operation
`asfactor`. This is a necessary step for all model building, in fact. So let's take a look
at this again for gradient boosting:

::

  # import the prostate data
  fr = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
  
  # import gbm estimator
  from h2o.estimators.gbm import H2OGradientBoostingEstimator  
  
  # produces False
  fr[1].isfactor()
  
  # set up the gbm
  m = H2OGradientBoostingEstimator(ntrees=10, max_depth=5)

  # train the model
  m.train(x=fr.names[2:], y="CAPSULE", training_frame=fr)

  # type is "regressor"
  print m.type

  # cast the 2nd column to a factor column
  fr[1] = fr[1].asfactor()
  
  # produces True
  fr[1].isfactor()
  
  # train the model
  m.train(x=fr.names[2:], y="CAPSULE", training_frame=fr)

  # type is "classifier"
  print m.type

The above example shows how to properly deal with numeric columns you would like to use in a
classification setting. Additionally, H2O can perform on-the-fly scoring of validation
data and provide a host of metrics on the validation and training data. Here's an example
of this functionality, where we additionally split the data set into three pieces for training,
validation, and finally testing. Let's use deeplearning this time:

::

  # import the prostate data
  fr = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")  

  # import the deeplearning estimator
  from h2o.estimators.deeplearning import H2ODeepLearningEstimator  
  
  # cast to factor
  fr[1] = fr[1].asfactor()                                          
  
  # Random UNIform numbers, one per row
  r = fr[0].runif()
  
  # 60% for training data
  train = fr[ r < 0.6 ]
  
  # 30% for validation
  valid = fr[ (0.6 <= r) & (r < 0.9) ]
  
  # 10% for testing
  test  = fr[ 0.9 <= r ]
  
  # default DL setup
  m = H2ODeepLearningEstimator()
  
  # pass a validation frame in addition to the training frame
  m.train(x=train.names[2:], y=train.names[1], training_frame=train, validation_frame=valid)

  # display the model summary by default (can also call m.show())
  m
  
  # equivalent to the above
  m.show()
  
  # show the performance on the training data, (can also be m.performance(train=True)
  m.model_performance()
  
  # show the performance on the validation data
  m.model_performance(valid=True)
  
  # score and compute new metrics on the test data!
  m.model_performance(test_data=test)

Expanding on this example, there are a number of ways of querying a model for its
attributes. Here are some examples of how to do just that:

::

  # MSE on the training data
  m.mse()
  
  # MSE on the validation data
  m.mse(valid=True)

  # R^2 on the training data
  m.r2()

  # R^2 on the validation data
  m.r2(valid=True)

  # confusion matrix for max F1
  m.confusion_matrix()

  # confusion matrix for the maximum accuracy
  m.confusion_matrix(metrics="accuracy")

  # check out the help for more!
  m.confusion_matrix("min_per_class_accuracy")

All of our models support various accessor methods such as these. The following sections
will discuss model metrics in greater detail.

On a final note, each of H2O's algorithms handles missing (colloquially: "missing" or "NA")
and categorical data automatically differently, depending on the algorithm. You can find
out more about each of the individual differences at the up-to-date docs on H2O's
algorithms under the `Algorithms <../h2o-docs/data-science.html>`__ section in the H2O-3 
User Guide.

Metrics
+++++++

In accordance to the model categories above, each model supports an array of metrics
that go in hand with the model category, each type of metrics inherits from
:class:`MetricsBase`.

As has been shown in previous examples, all supervised models deliver metrics on the data
the model was trained upon. In the last example, a validation data set was also provided
during model training, so there is an extra set of metrics on this validation set that is
produced as a result of the training (and stored in the model). Any additional data set
provided to the model post-build via the :mod:`model_performance` call will produce a set
of metrics.

::

  # import iris
  fr = iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/   
  
  # generate a random vector for splitting
  r = fr[0].runif()                       
  
  # split out 60% for training
  train = fr[ r < 0.6 ]                   
  
  # split out 30% for validation
  valid = fr[ (0.6 <= r) & (r < 0.9) ]        
  
  # split out 10% for testing
  test = fr[ 0.9 <= r ]                   
  
  # import the glm estimator and train the model
  from h2o.estimators.glm import H2OGeneralizedLinearEstimator  
  my_model = H2OGeneralizedLinearEstimator()
  my_model.train(x=train.names[1:], y=train.names[0], training_frame=train, validation_frame=valid)
  
  # print the GLM coefficients, can also perform my_model.coef_norm() to get the normalized coefficients
  my_model.coef()
  
  # get the null deviance from the training set metrics
  my_model.null_deviance()
  
  # get the residual deviance from the training set metrics
  my_model.residual_deviance()
  
  # get the null deviance from the validation set metrics (similar for residual deviance)
  my_model.null_deviance(valid=True)

  # now generate a new metrics object for the test hold-out data:
  # create the new test set metrics
  my_metrics = my_model.model_performance(test_data=testa0
  
  # returns the test null dof
  my_metrics.null_degrees_of_freedom()
  
  # returns the test res. deviance
  my_metrics.residual_deviance()
  
  # returns the test aic
  my_metrics.aic()

As you can see, the new model metrics object generated by calling :mod:`model_performance` on the
model object supports all of the metric accessor methods as a model. For a complete list of
the available metrics for various model categories, please refer to the `Metrics in H2O <metrics.html>`__ section
in this document.

Example of H2O on Hadoop
------------------------

Here is a brief example of H2O on Hadoop:

::


  import h2o
  h2o.init(ip="192.168.1.10", port=54321)
  --------------------------  ------------------------------------
  H2O cluster uptime:         2 minutes 1 seconds 966 milliseconds
  H2O cluster version:        0.1.27.1064
  H2O cluster name:           H2O_96762
  H2O cluster total nodes:    4
  H2O cluster total memory:   38.34 GB
  H2O cluster total cores:    16
  H2O cluster allowed cores:  80
  H2O cluster healthy:        True
  --------------------------  ------------------------------------
  path_train = ["hdfs://192.168.1.10/user/data/data_train.csv"]
  path_test = ["hdfs://192.168.1.10/user/data/data_test.csv"]
  train = h2o.import_file(path=path_train)
  test  = h2o.import_file(path=path_test)

  #Parse Progress: [##################################################] 100%
  #Imported [hdfs://192.168.1.10/user/data/data_train.csv'] into cluster with 60000 rows and 500 cols

  #Parse Progress: [##################################################] 100%
  #Imported ['hdfs://192.168.1.10/user/data/data_test.csv'] into cluster with 10000 rows and 500 cols

  train[499]._name = "label"
  test[499]._name = "label"

  from h2o.estimators.gbm import H2OGradientBoostingEstimator

  model = H2OGradientBoostingEstimator(ntrees=100, max_depth=10)
  model.train(x=list(set(train.names)-{"label"}), y="label", training_frame=train, validation_frame=test)

  #gbm Model Build Progress: [##################################################] 100%

  preds = model.predict(test)
  model.model_performance(test)
