Setup and Installation
======================

This module depends on *requests* and *tabulate* modules. Both of which are available on pypi.

    $ pip install requests

    $ pip install tabulate


The H2O Python Module
=====================

This module provides access to the H2O JVM (and extensions thereof), its objects, its
machine-learning algorithms, and modeling support (basic munging and feature generation)
capabilities.

The H2O JVM sports a web server such that all communication occurs on a socket (specified
by an IP address and a port) via a series of REST calls (see connection.py for the REST
layer implementation and details). There is a single active connection to the H2O JVM at
any one time, and this handle is stashed in the \_\_H2OCONN\_\_ global object. The \_\_H2OCONN\_\_
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
==============

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


H2O
===

H2O makes Hadoop do math! H2O scales statistics, machine learning and math over BigData. H2O is extensible and users can build blocks using simple math legos in the core. H2O keeps familiar interfaces like python, R, Excel & JSON so that BigData enthusiasts & experts can explore, munge, model and score datasets using a range of simple to advanced algorithms. Data collection is easy. Decision making is hard. H2O makes it fast and easy to derive insights from your data through faster and better predictive modeling. H2O has a vision of online scoring and modeling in a single platform.

Product Vision for first cut
============================
H2O product, the Analytics Engine will scale Classification and Regression.
- RandomForest, Generalized Linear Modeling (GLM), logistic regression, k-Means, available over R / REST / JSON-API
- Basic Linear Algebra as building blocks for custom algorithms
- High predictive power of the models
- High speed and scale for modeling and scoring over BigData

Data Sources
- We read and write from/to HDFS, S3, NoSQL, SQL
- We ingest data in CSV format from local and distributed filesystems (nfs)
- A JDBC driver for SQL and DataAdapters for NoSQL datasources is in the roadmap. (v2)

Console provides Adhoc Data Analytics at scale via R-like Parser on BigData
 - Able to pass and evaluate R-like expressions, slicing and filters make this the most powerful web calculator on BigData

Users
=====
Primary users are Data Analysts looking to wield a powerful tool for Data Modeling in the Real-Time. Microsoft Excel, R, Python, SAS wielding Data Analysts and Statisticians.
Hadoop users with data in HDFS will have a first class citizen for doing Math in Hadoop ecosystem.
Java and Math engineers can extend core functionality by using and extending legos in a simple java that reads like math. See package hex.
Extensibility can also come from writing R expressions that capture your domain.

Design
======

We use the best execution framework for the algorithm at hand. For first cut parallel algorithms: Map Reduce over distributed fork/join framework brings fine grain parallelism to distributed algorithms.
Our algorithms are cache oblivious and fit into the heterogeneous datacenter and laptops to bring best performance.
Distributed Arraylets & Data Partitioning to preserve locality.
Move code, not data, not people.

Extensions
==========

One of our first powerful extension will be a small tool belt of stats and math legos for Fraud Detection. Dealing with Unbalanced Datasets is a key focus for this.
Users will use JSON/REST-api via H2O.R through connects the Analytics Engine into R-IDE/RStudio.

Community
=========
We will build & sustain a vibrant community with the focus of taking software engineering approaches to data science and empowering everyone interested in data to be able to hack data using math and algorithms.
Join us on google groups [h2ostream](https://groups.google.com/forum/#!forum/h2ostream).

Team
```
SriSatish Ambati
Cliff Click
Tom Kraljevic
Earl Hathaway
Tomas Nykodym
Michal Malohlava
Kevin Normoyle
Irene Lang
Spencer Aiello
Anqi Fu
Nidhi Mehta
Arno Candel
Nikole Sanchez
Josephine Wang
Amy Wang
Max Schloemer
Ray Peck
Anand Avati
Sebastian Vidrio
Eric Eckstrand
```

Open Source
```
Jan Vitek
Mr.Jenkins
Petr Maj
Matt Fowles
```

Advisors
========
Scientific Advisory Council
```
Stephen Boyd
Rob Tibshirani
Trevor Hastie
```

Systems, Data, FileSystems and Hadoop
```
Doug Lea
Chris Pouliot
Dhruba Borthakur
Charles Zedlewski
```

Investors
=========
```
Jishnu Bhattacharjee, Nexus Venture Partners
Anand Babu Periasamy
Anand Rajaraman
Dipchand Nishar
```
