# __version__ = "0.0.4a1"
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
:class:`H2OConnection` (this is the global  :envvar:`__H2OConn__`).

The H2O python module is not intended as a replacement for other popular machine learning
modules such as scikit-learn, pylearn2, and their ilk. This module is a complementary
interface to a modeling engine intended to make the transition of models from development
to production as seamless as possible. Additionally, it is designed to bring H2O to a
wider audience of data and machine learning devotees that work exclusively with Python
(rather than R or scala or Java -- which are other popular interfaces that H2O supports),
and are wanting another tool for building applications or doing data munging in a fast,
scalable environment without any extra mental anguish about threads and parallelism.


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
(c.f. JSR166y and NonBlockingHashMap) enable this type of scaling in H2O (we're really
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
data is "in memory"). The H2OFrame is an iterable (supporting list comprehensions) wrapper
around an array of H2OVec objects.

The set of operations on an H2OFrame is described in a chapter devoted to this object, but
suffice it to say that this set of operations closely resembles those that may be
performed on an R data.frame. This includes all manner of slicing (with complex
conditionals), broadcasting operations, and a slew of math operations for transforming and
mutating a Frame (the actual Big Data sitting in the H2O cloud). The semantics for
modifying a Frame closely resembles R's copy-on-modify semantics, except when it comes
to mutating a Frame in place. For example, it's possible to assign all occurrences of the
number `0` in a column to missing (or `NA` in R parlance) as demonstrated in the following
snippet::


>>> df = h2o.import_frame(path="smalldata/logreg/prostate.csv")  # import prostate data
>>>
>>> vol = df['VOL']                                              # select the VOL column
>>>
>>> vol[vol == 0] = None                                         # 0 VOL means 'missing'

After this operation, `vol` has been permanently mutated (and is not a copy!) in place.


H2OVec
++++++
An H2OVec is...

Expr
++++

* Expressions are lazy...
* DAGs of Exprs ... oh joy!


Models
++++++

* No explicit model objects -- have model categories
* How to create new models
* train and validation data
* parameter specification

* See more on the chapter on Models

Metrics
+++++++

* Metrics for different types of model categories
* See more in the chapter on Metrics

"""
__version__ = "0.0.0a5"
from h2o import *
from model import *
from frame import H2OFrame
from frame import H2OVec
from two_dim_table import H2OTwoDimTable
#
__all__ = ["H2OFrame", "H2OConnection", "H2OVec", "H2OTwoDimTable"]
