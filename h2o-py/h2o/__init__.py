# encoding: utf-8
# module h2o
# from (h2o)
"""
The H2O Python Module
=====================

This module provides access to the H2O JVM, as well as its extensions, objects,
machine-learning algorithms, and modeling support capabilities, such as basic
munging and feature generation.

The H2O JVM uses a web server so that all communication occurs on a socket (specified
by an IP address and a port) via a series of REST calls (see connection.py for the REST
layer implementation and details). There is a single active connection to the H2O JVM at
any time, and this handle is stashed out of sight in a singleton instance of
:class:`H2OConnection` (this is the global  :envvar:`__H2OConn__`). In other words,
this package does not rely on Jython, and there is no direct manipulation of the JVM.

The H2O python module is not intended as a replacement for other popular machine learning
frameworks such as scikit-learn, pylearn2, and their ilk, but is intended to bring H2O to
a wider audience of data and machine learning devotees who work exclusively with Python.

H2O from Python is a tool for rapidly turning over models, doing data munging, and
building applications in a fast, scalable environment without any of the mental anguish
about parallelism and distribution of work.

What is H2O?
------------

H2O is a Java-based software for data modeling and general computing. There are many
different perceptions of the H2O software, but the primary purpose of H2O is as a
distributed (many machines), parallel (many CPUs), in memory (several hundred GBs Xmx)
processing engine.

There are two levels of parallelism:

    * within node
    * across (or between) nodes

The goal, remember, is to easily add more processors to a given problem in order to
produce a solution faster. The conceptual paradigm MapReduce (also known as
"divide and conquer and combine"), along with a good concurrent application structure,
(c.f. jsr166y and NonBlockingHashMap) enable this type of scaling in H2O -- we're really
cooking with gas now!

For application developers and data scientists, the gritty details of thread-safety,
algorithm parallelism, and node coherence on a network are concealed by simple-to-use REST
calls that are all documented here. In addition, H2O is an open-source project under the
Apache v2 licence. All of the source code is on
`github <https://github.com/h2oai/h2o-dev>`_, there is an active
`google group mailing list <https://groups.google.com/forum/#!forum/h2ostream>`_, our
`nightly tests <http://test.0xdata.com/>`_ are open for perusal, and our `JIRA ticketing
system <http://jira.0xdata.com>`_ is also open for public use. Last, but not least, we
regularly engage the machine learning community all over the nation with a very busy
`meetup schedule <http://h2o.ai/events/>`_ (so if you're not in The Valley, no sweat,
we're probably coming to your area soon!), and finally, we host our very own `H2O World
conference <http://h2o.ai/h2o-world/>`_. We also sometimes host hack-a-thons at our
campus in Mountain View, CA. Needless to say, H2O provides a lot of support for
application developers.

In order to make the most out of H2O, there are some key conceptual pieces that are important
to know before getting started. Mainly, it's helpful to know about the different types of
objects that live in H2O and what the rules of engagement are in the context of the REST
API (which is what any non-JVM interface is all about).

Let's get started!

The H2O Object System
+++++++++++++++++++++

H2O uses a distributed key-value store (the "DKV") that contains pointers to the
various objects of the H2O ecosystem. The DKV is a kind of biosphere in that it
encapsulates all shared objects; however, it may not encapsulate all objects. Some shared
objects are mutable by the client; some shared objects are read-only by the client, but are
mutable by H2O (e.g. a model being constructed will change over time); and actions by the
client may have side-effects on other clients (multi-tenancy is not a supported model of
use, but it is possible for multiple clients to attach to a single H2O cloud).

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


Objects In This Module
----------------------

The objects that are of primary concern to the python user are (in order of importance)
- IDs/Keys
- Frames
- Models
- ModelMetrics
- Jobs (to a lesser extent)
Each of these objects are described in greater detail in this documentation,
but a few brief notes are provided here.


H2OFrame
++++++++

An H2OFrame is a 2D array of uniformly-typed columns. Data in H2O is compressed (often
achieving 2-4x better compression than gzip on disk) and is held in the JVM heap (i.e.
data is "in memory"), and *not* in the python process local memory. The H2OFrame is an
iterable (supporting list comprehensions). All an H2OFrame object is, therefore, is a
wrapper on a list that supports various types of operations that may or may not be lazy.
Here's an example showing how a list comprehension is combined with lazy expressions to
compute the column means for all columns in the H2OFrame::

  >>> df = h2o.import_file(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> colmeans = df.mean()                                        # compute column means
  >>>
  >>> colmeans                                                    # print the results
  [5.843333333333335, 3.0540000000000007, 3.7586666666666693, 1.1986666666666672]

Lazy expressions will be discussed briefly in the coming sections, as they are not
necessarily going to be integral to the practicing data scientist. However, their primary
purpose is to cut down on the chatter between the client (a.k.a the python interface) and
H2O. Lazy expressions are `Katamari'd <http://www.urbandictionary.com/define.php?term=Katamari>`_
together and only ever evaluated when some piece of output is requested (e.g. print-to-screen).

The set of operations on an H2OFrame is described in a dedicated chapter, but
in general, this set of operations closely resembles those that may be
performed on an R data.frame. This includes all types of slicing (with complex
conditionals), broadcasting operations, and a slew of math operations for transforming and
mutating a Frame -- all the while the actual Big Data is sitting in the H2O cloud. The semantics
for modifying a Frame closely resemble R's copy-on-modify semantics, except when it comes
to mutating a Frame in place. For example, it's possible to assign all occurrences of the
number `0` in a column to missing (or `NA` in R parlance) as demonstrated in the following
snippet::


  >>> df = h2o.import_file(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> vol = df['VOL']                                              # select the VOL column
  >>>
  >>> vol[vol == 0] = None                                         # 0 VOL means 'missing'

After this operation, `vol` has been permanently mutated in place (it is not a copy!).

ExprNode
++++++++
In the guts of this module is the Expr class, which defines objects holding
the cumulative, unevaluated expressions that may become H2OFrame objects.
For example:

  >>> fr = h2o.import_file(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> a = fr + 3.14159                                             # "a" is now an Expr
  >>>
  >>> type(a)                                                      # <class 'h2o.expr.Expr'>

These objects are not as important to distinguish at the user level, and all operations
can be performed with the mental model of operating on 2D frames (i.e. everything is an
H2OFrame).

In the previous snippet, `a` has not yet triggered any big data evaluation and is, in
fact, a pending computation. Once `a` is evaluated, it stays evaluated. Additionally,
all dependent subparts composing `a` are also evaluated.

This module relies on reference counting of python objects to dispose of
out-of-scope objects. The Expr class destroys objects and their big data
counterparts in the H2O cloud using a remove call:

  >>> fr = h2o.import_file(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> h2o.remove(fr)                                               # remove prostate data
  >>> fr                                                           # attempting to use fr results in a ValueError

Notice that attempting to use the object after a remove call has been issued will
result in a ValueError. Therefore, any working references may not be cleaned up,
but they will no longer be functional. Deleting an unevaluated expression will not
delete all subparts.

Models
++++++

The model-building experience with this module is unique, especially for those coming
from a background in scikit-learn. Instead of using objects to build the model,
builder functions are provided in the top-level module, and the result of a call
is a model object belonging to one of the following categories:

    * Regression
    * Binomial
    * Multinomial
    * Clustering
    * Autoencoder

To better demonstrate this concept, refer to the following example:

  >>> fr = h2o.import_file(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> fr[1] = fr[1].asfactor()                                     # make 2nd column a factor
  >>>
  >>> m = h2o.glm(x=fr[3:], y=fr[2])                               # build a glm with a method call
  >>>
  >>> m.__class__                                                  # <h2o.model.binomial.H2OBinomialModel object at 0x104659cd0>
  >>>
  >>> m.show()                                                     # print the model details
  >>>
  >>> m.summary()                                                  # print a model summary

As you can see in the example, the result of the GLM call is a binomial model. This example also showcases
an important feature-munging step needed for GLM to perform a classification task rather than a
regression task. Namely, the second column is initially read as a numeric column,
but it must be changed to a factor by way of the operation `asfactor`. Let's take a look
at this more deeply:

  >>> fr = h2o.import_file(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> fr[1].isfactor()                                             # produces False
  >>>
  >>> m = h2o.gbm(x=fr[2:],y=fr[1])                                # build the gbm
  >>>
  >>> m.__class__                                                  # <h2o.model.regression.H2ORegressionModel object at 0x104d07590>
  >>>
  >>> fr[1] = fr[1].asfactor()                                     # cast the 2nd column to a factor column
  >>>
  >>> fr[1].isfactor()                                             # produces True
  >>>
  >>> m = h2o.gbm(x=fr[2:],y=fr[1])                                # build the gbm
  >>>
  >>> m.__class__                                                  # <h2o.model.binomial.H2OBinomialModel object at 0x104d18f50>

The above example shows how to properly deal with numeric columns you would like to use in a
classification setting. Additionally, H2O can perform on-the-fly scoring of validation
data and provide a host of metrics on the validation and training data. Here's an example
of this functionality, where we additionally split the data set into three pieces for training,
validation, and finally testing:

  >>> fr = h2o.import_file(path="smalldata/logreg/prostate.csv")  # import prostate
  >>>
  >>> fr[1] = fr[1].asfactor()                                     # cast to factor
  >>>
  >>> r = fr[0].runif()                                            # Random UNIform numbers, one per row
  >>>
  >>> train = fr[ r < 0.6 ]                                        # 60% for training data
  >>>
  >>> valid = fr[ (0.6 <= r) & (r < 0.9) ]                         # 30% for validation
  >>>
  >>> test  = fr[ 0.9 <= r ]                                       # 10% for testing
  >>>
  >>> m = h2o.deeplearning(x=train[2:],y=train[1],validation_x=valid[2:],validation_y=valid[1])  # build a deeplearning with a validation set (yes it's this simple)
  >>>
  >>> m                                                            # display the model summary by default (can also call m.show())
  >>>
  >>> m.show()                                                     # equivalent to the above
  >>>
  >>> m.model_performance()                                        # show the performance on the training data, (can also be m.performance(train=True)
  >>>
  >>> m.model_performance(valid=True)                              # show the performance on the validation data
  >>>
  >>> m.model_performance(test_data=test)                          # score and compute new metrics on the test data!

Expanding on this example, there are a number of ways of querying a model for its attributes.
Here are some examples of how to do just that:

  >>> m.mse()           # MSE on the training data
  >>>
  >>> m.mse(valid=True) # MSE on the validation data
  >>>
  >>> m.r2()            # R^2 on the training data
  >>>
  >>> m.r2(valid=True)  # R^2 on the validation data
  >>>
  >>> m.confusion_matrix()  # confusion matrix for max F1
  >>>
  >>> m.confusion_matrix("tpr") # confusion matrix for max true positive rate
  >>>
  >>> m.confusion_matrix("max_per_class_error")   # etc.

All of our models support various accessor methods such as these. The following section will
discuss model metrics in greater detail.

On a final note, each of H2O's algorithms handles missing (colloquially: "missing" or "NA")
and categorical data automatically differently, depending on the algorithm. You can find
out more about each of the individual differences at the following link: http://docs2.h2o.ai/datascience/top.html

Metrics
+++++++

H2O models exhibit a wide array of metrics for each of the model categories:
- Clustering
- Binomial
- Multinomial
- Regression
- AutoEncoder
In turn, each of these categories is associated with a corresponding H2OModelMetrics class.

All algorithm calls return at least one type of metrics: the training set metrics. When building
a model in H2O, you can optionally provide a validation set for on-the-fly evaluation of
holdout data. If the validation set is provided, then two types of metrics are returned:
the training set metrics and the validation set metrics.

In addition to the metrics that can be retrieved at model-build time, there is a
possible third type of metrics available post-build for the final holdout test set that
contains data that does not appear in either the training or validation sets: the
test set metrics. While the returned object is an H2OModelMetrics rather than an H2O model,
it can be queried in the same exact way. Here's an example:

  >>> fr = h2o.import_file(path="smalldata/iris/iris_wheader.csv")   # import iris
  >>>
  >>> r = fr[0].runif()                       # generate a random vector for splitting
  >>>
  >>> train = fr[ r < 0.6 ]                   # split out 60% for training
  >>>
  >>> valid = fr[ 0.6 <= r & r < 0.9 ]        # split out 30% for validation
  >>>
  >>> test = fr[ 0.9 <= r ]                   # split out 10% for testing
  >>>
  >>> my_model = h2o.glm(x=train[1:], y=train[0], validation_x=valid[1:], validation_y=valid[0])  # build a GLM
  >>>
  >>> my_model.coef()                         # print the GLM coefficients, can also perform my_model.coef_norm() to get the normalized coefficients
  >>>
  >>> my_model.null_deviance()                # get the null deviance from the training set metrics
  >>>
  >>> my_model.residual_deviance()            # get the residual deviance from the training set metrics
  >>>
  >>> my_model.null_deviance(valid=True)      # get the null deviance from the validation set metrics (similar for residual deviance)
  >>>
  >>> # now generate a new metrics object for the test hold-out data:
  >>>
  >>> my_metrics = my_model.model_performance(test_data=test) # create the new test set metrics
  >>>
  >>> my_metrics.null_degrees_of_freedom()    # returns the test null dof
  >>>
  >>> my_metrics.residual_deviance()          # returns the test res. deviance
  >>>
  >>> my_metrics.aic()                        # returns the test aic

As you can see, the new model metrics object generated by calling `model_performance` on the
model object supports all of the metric accessor methods as a model. For a complete list of
the available metrics for various model categories, please refer to the "Metrics in H2O" section
of this document.

Example of H2O on Hadoop
------------------------

Here is a brief example of H2O on Hadoop:

.. code-block:: python

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
  pathDataTrain = ["hdfs://192.168.1.10/user/data/data_train.csv"]
  pathDataTest = ["hdfs://192.168.1.10/user/data/data_test.csv"]
  trainFrame = h2o.import_file(path=pathDataTrain)
  testFrame = h2o.import_file(path=pathDataTest)

  #Parse Progress: [##################################################] 100%
  #Imported [hdfs://192.168.1.10/user/data/data_train.csv'] into cluster with 60000 rows and 500 cols

  #Parse Progress: [##################################################] 100%
  #Imported ['hdfs://192.168.1.10/user/data/data_test.csv'] into cluster with 10000 rows and 500 cols

  trainFrame[499]._name = "label"
  testFrame[499]._name = "label"

  model = h2o.gbm(x=trainFrame.drop("label"),
              y=trainFrame["label"],
              validation_x=testFrame.drop("label"),
              validation_y=testFrame["label"],
              ntrees=100,
              max_depth=10
              )

  #gbm Model Build Progress: [##################################################] 100%

  predictFrame = model.predict(testFrame)
  model.model_performance(testFrame)
"""
__version__ = "SUBST_PROJECT_VERSION"
from h2o import *
from model import *
from demo import *
from h2o_logging import *
from frame import H2OFrame
from group_by import GroupBy
from two_dim_table import H2OTwoDimTable
from assembly import H2OAssembly

__all__ = ["H2OFrame", "H2OConnection", "H2OTwoDimTable", "GroupBy"]
