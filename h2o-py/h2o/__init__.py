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
any one time, and this handle is stashed in the __H2OCONN__ global object. The __H2OCONN__
is not currently a singleton object (but it may become one in the future).

What is H2O?
============

H2O is a piece of java software for data modeling and general computing. There are many
different views of the H2O software, but the primary view of H2O is that of a distributed
(many machines), parallel (many CPUs), in memory (several hundred GBs Xmx) processing
"engine". How H2O achieves within node parallelism and efficient horizontal scaling is
discussed in detail elsewhere, but it suffices to state that Doug Lea's Fork Join
framework (which can be thought of as a classical recursive descent divide and conquer
approach to doing "work") enables parallelism per JVM, and a distributed version of Cliff
Click's non-blocking hash map enables coherency across nodes in a cluster allowing for
lateral scaling.

H2O sports a distributed key-value store (the "DKV"), which contains pointers to the
various objects that make up the H2O ecosystem. Briefly, these objects are:

    Key:    A key is an entry in the DKV that maps to an object in H2O.
    Frame:  A Frame is a collection of Vec objects. It is a 2D array of elements.
    Vec:    A Vec is a collection of Chunk objects. It is a 1D array of elements.
    Chunk:  A Chunk holds a fraction of the BigData. It is a 1D array of elements.
    ModelMetrics:   A collection of metrics for a given category of model.
    Model:  A model is an immutable object having `predict` and `metrics` methods.
    Job:    A Job is a non-blocking task that performs a finite amount of work.

Many of these objects have no meaning to an end python user, but in order to make sense of
the objects available in this module it is helpful to understand how these objects map to
objects in the JVM (because after all, this module is merely a facade that allows the
manipulation of a distributed system).

Objects In This Module
======================




H2OFrame
========





H2OVec
======



Model Builders
============

* How to create new models
* The fit() method
* train and validation data
* parameter specification


Model Results and Metrics
=========================

* After models are built: (show, summary, predict, performance)
* Model categories: binomial, regression, multinomial, clustering


Feature Generation and Extended Data Flows
==========================================

* Discuss Rapids
# Data manipulation in python
* Executing python functions in H2O via Rapids

"""
__version__ = "0.0.0a5"
from h2o import *
from model import *
from frame import H2OFrame
from frame import H2OVec
from two_dim_table import H2OTwoDimTable

__all__ = ["H2OFrame", "H2OConnection", "H2OVec", "H2OTwoDimTable"]
