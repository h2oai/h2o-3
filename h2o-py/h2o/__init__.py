# encoding: utf-8
# module h2o
# from (h2o)
"""
The H2O Python Module
=====================

This module provides access to the H2O JVM (and extensions thereof), its objects, its
machine-learning algorithms, and modeling support (basic munging and feature generation)
capabilities.

The H2O JVM sports a web server such that all communication occurs on a socket (specified
by an IP address and a port) via a series of REST calls (see connection.py for the REST
layer implementation and details). There is a single active connection to the H2O JVM at
any one time, and this handle is stashed away out of sight in a singleton instance of
:class:`H2OConnection` (this is the global  :envvar:`__H2OConn__`). In other words,
this package does not rely on Jython, and there is no direct manipulation of the JVM.

The H2O python module is not intended as a replacement for other popular machine learning
modules such as scikit-learn, pylearn2, and their ilk. This module is a complementary
interface to a modeling engine intended to make the transition of models from development
to production as seamless as possible. Additionally, it is designed to bring H2O to a
wider audience of data and machine learning devotees that work exclusively with Python
(rather than R or scala or Java -- which are other popular interfaces that H2O supports),
and are wanting another tool for building applications or doing data munging in a fast,
scalable environment without any extra mental anguish about threads and parallelism. There
are additional treasures that H2O incorporates meant to alleviate the pain of doing some
basic feature manipulation (e.g. automatic categorical handling and not having to one-hot
encode).


What is H2O?
------------

H2O is a piece of java software for data modeling and general computing. There are many
different views of the H2O software, but the primary view of H2O is that of a distributed
(many machines), parallel (many CPUs), in memory (several hundred GBs Xmx) processing
engine.

There are two levels of parallelism:

    * within node
    * across (or between) node.

The goal, remember, is to "simply" add more processors to a given problem in order to
produce a solution faster. The conceptual paradigm MapReduce (also known as
"divide and conquer and combine") along with a good concurrent application structure
(c.f. jsr166y and NonBlockingHashMap) enable this type of scaling in H2O (we're really
cooking with gas now!).

For application developers and data scientists, the gritty details of thread-safety,
algorithm parallelism, and node coherence on a network are concealed by simple-to-use REST
calls that are all documented here. In addition, H2O is an open-source project under the
Apache v2 licence. All of the source code is on
`github <https://github.com/h2oai/h2o-dev>`_, there is an active
`google group mailing list <https://groups.google.com/forum/#!forum/h2ostream>`_, our
`nightly tests <http://test.0xdata.com/>`_ are open for perusal, our `JIRA ticketing
system <http://jira.0xdata.com>`_ is also open for public use. Last, but not least, we
regularly engage the machine learning community all over the nation with a very busy
`meetup schedule <http://h2o.ai/events/>`_ (so if you're not in The Valley, no sweat,
we're probably coming to you soon!), and finally, we host our very own `H2O World
conference <http://h2o.ai/h2o-world/>`_. We also sometimes host hack-a-thons at our
campus in Mountain View, CA. Needless to say, there is a lot of support for the
application developer.

In order to make the most out of H2O, there are some key conceptual pieces that are helpful
to know before getting started. Mainly, it's helpful to know about the different types of
objects that live in H2O and what the rules of engagement are in the context of the REST
API (which is what any non-JVM interface is all about).

Let's get started!

The H2O Object System
+++++++++++++++++++++

H2O sports a distributed key-value store (the "DKV"), which contains pointers to the
various objects that make up the H2O ecosystem. The DKV is a kind of biosphere in that it
encapsulates all shared objects (though, it may not encapsulate all objects). Some shared
objects are mutable by the client; some shared objects are read-only by the client, but
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

Many of these objects have no meaning to an end python user, but in order to make sense of
the objects available in this module it is helpful to understand how these objects map to
objects in the JVM (because after all, this module is an interface that allows the
manipulation of a distributed system).


Objects In This Module
----------------------

The objects that are of primary concern to the python user are (in order) Keys, Frames,
Vecs, Models, ModelMetrics, and to a lesser extent Jobs. Each of these objects are
described in greater detail throughout this documentation, but a few brief notes are
warranted here.


H2OFrame
++++++++

An H2OFrame is 2D array of uniformly-typed columns. Data in H2O is compressed (often
achieving 2-4x better compression the gzip on disk) and is held in the JVM heap (i.e.
data is "in memory"), and *not* in the python process local memory.. The H2OFrame is an
iterable (supporting list comprehensions) wrapper around a list of H2OVec objects. All an
H2OFrame object is, therefore, is a wrapper on a list that supports various types of operations
that may or may not be lazy. Here's an example showing how a list comprehension is combined
with lazy expressions to compute the column means for all columns in the H2OFrame::

  >>> df = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> colmeans = [v.mean() for v in df]                            # compute column means
  >>>
  >>> colmeans                                                     # print the results
  [5.843333333333335, 3.0540000000000007, 3.7586666666666693, 1.1986666666666672]

Lazy expressions will be discussed lightly in the coming sections, as they are not
necessarily going to be front-and-center to the practicing data scientist, but their primary
purpose is to cut down on the chatter between the client (a.k.a this python interface) and
H2O. Lazy expressions are `Katamari'd <http://www.urbandictionary.com/define.php?term=Katamari>`_ together and only
ever evaluated when some piece of output is requested (e.g. print-to-screen).

The set of operations on an H2OFrame is described in a chapter devoted to this object, but
suffice it to say that this set of operations closely resembles those that may be
performed on an R data.frame. This includes all manner of slicing (with complex
conditionals), broadcasting operations, and a slew of math operations for transforming and
mutating a Frame (all the while the actual Big Data is sitting in the H2O cloud). The semantics for
modifying a Frame closely resembles R's copy-on-modify semantics, except when it comes
to mutating a Frame in place. For example, it's possible to assign all occurrences of the
number `0` in a column to missing (or `NA` in R parlance) as demonstrated in the following
snippet::


  >>> df = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> vol = df['VOL']                                              # select the VOL column
  >>>
  >>> vol[vol == 0] = None                                         # 0 VOL means 'missing'

After this operation, `vol` has been permanently mutated in place (it is not a copy!).

H2OVec
++++++
An H2OVec is a single column of data that is uniformly typed and possibly lazily computed.
As with H2OFrame, an H2OVec is a pointer to a distributed java object residing in the H2O
cloud (and truthfully, an H2OFrame is simply a collection of H2OVec pointers along with
some metadata and various member methods).

Expr
++++
Deep in the guts of this module is the Expr class, which defines those objects holding
the cumulative, unevaluated expressions that may become H2OFrame/H2OVec objects.
For example:

  >>> fr = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> a = fr + 3.14159                                             # "a" is now an Expr
  >>>
  >>> type(a)                                                      # <class 'h2o.expr.Expr'>

These objects are not too important to distinguish at the user level, and all operations
can be performed with the mental model of operating on 2D frames (i.e. everything is an
H2OFrame), but it is worth mentioning them here for completeness, as they will not discussed
elsewhere.

In the previous snippet, `a` has not yet triggered any big data evaluation and is, in
fact, a pending computation. Once `a` is evaluated, it stays evaluated. Additionally,
if all dependent subparts composing `a` are also evaluated.

It is worthwhile mentioning at this point that this module relies on reference counting
of python objects to dispose of out-of-scope objects. The Expr class destroys objects
and their big data counterparts in the H2O cloud by way of a remove call:

  >>> fr = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate data
  >>>
  >>> h2o.remove(fr)                                               # remove prostate data
  >>> fr                                                           # attempting to use fr results in a ValueError

Notice that when attempting to use the object after a remove call has been issued, it will
result in a ValueError. Therefore, any working reference is not necessarily cleaned up,
but it will no longer be functional. Note that deleting an unevaluated expression will not
delete all subparts!

Models
++++++

The model-building experience with this module is unique, and is not the same experience
for those coming from a background in scikit-learn. Instead of using objects to build the
model, builder functions are provided in the top-level module, and the result of a call
is an model object belonging to one of the following categories:

    * Regression
    * Binomial
    * Multinomial
    * Clustering
    * Autoencoder

This is better demonstrated by way of an example:

  >>> fr = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate data
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

As you can see, the result of the glm call is a binomial model. This example also showcases
an important feature-munging step in order to cause the gbm to perform a classification task
over a regression task. Namely, the second column is a numeric column when it's initially read in,
but it must be cast to a factor by way of the H2OVec operation `asfactor`. Let's take a look
at this more deeply:

  >>> fr = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate data
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
of doing this, where we additionally split the data set into three pieces for training, validation,
and finally testing:

  >>> fr = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate
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

Continuing from this example, there are a number of ways of querying a model for its attributes.
Here are some examples doing just that:

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
  >>> m.confusion_matrix("tpr") # confusion marix for max true positive rate
  >>>
  >>> m.confusion_matrix("max_per_class_error") #

All of our models support various accessors such as these. Please refer to the relevant documentation
for each model category
* parameter specification
* categoricals are dealt with internally (no need to one-hot expand them!)
* what about categoricals in my response?
* what about an integral response column that I want to do classification on
* See more on the chapter on Models

Metrics
+++++++

* Metrics for different types of model categories
* See more in the chapter on Metrics

"""
__version__ = "SUBST_PROJECT_VERSION"
from h2o import *
from model import *
from frame import H2OFrame
from frame import H2OVec
from two_dim_table import H2OTwoDimTable

__all__ = ["H2OFrame", "H2OConnection", "H2OVec", "H2OTwoDimTable"]



###
# inspect.getcallargs(h2o.init)
###
