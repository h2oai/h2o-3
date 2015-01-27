# __version__ = "0.0.4a1"
# encoding: utf-8
# module h2o
# from (h2o)
"""
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
discussed in detail elsewhere.

H2O has a distributed key-value store (the "DKV"), which contains pointers to the various
objects that make up the H2O ecosystem. Briefly, these objects are:

    Key:    A key is an entry in the DKV that maps to an object in H2O.
    Frame:  A Frame is a collection of Vec objects. It is a 2D array of elements.
    Vec:    A Vec is a collection of Chunk objects. It is a 1D array of elements.
    Chunk:  A Chunk holds a fraction of the BigData. It is a 1D array of elements.
    ModelMetrics:   A collection of metrics for a given category of model.
    Model:  A model is an immutable object having `predict` and `metrics` methods.
    Job:    A Job is a non-blocking task that performs a finite amount of work.

H2O Objects
===========

blah blah blah h2o objects





H2OFrame
========





H2OVec
======


ModelBuilder
============
"""
from h2o import *
from model import *
from frame import H2OFrame
from frame import H2OVec


__all__ = ["H2OFrame", "h2oConn", "H2OVec"]