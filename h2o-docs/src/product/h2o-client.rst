H2O Clients
===========

These clients allow you to connect to and interact with H2O.

H2O Flow
--------

H2O Flow is a Web based (GUI) user interface. It allows users to interactively run their H2O machine learning workflows and iteratively improve them. It combines code execution, text, mathematics, plots, and rich media in a single document. Documentation for H2O Flow can be found `here <flow.html>`__.

R Client
--------

R users can use H2O-R library which internally uses H2O REST API calls to connect to H2O (Server) and allows users to run their H2O workflow via R. Documentation for the H2O-R Client can be found `here <../h2o-r/docs/index.html>`__.

Python Client
-------------

Python users can connect to H2O using the H2O Python package that internally uses H2O REST API calls to connect to H2O (Server) and allows users to run their H2O workflow via Python. Documentation for the H2O-Python Client can be found `here <../h2o-py/docs/index.html>`__. 

Sklearn Support
~~~~~~~~~~~~~~~

Most H2O estimators available in the H2O-Python client can also be used in the standard ``sklearn`` API. The ``h2o.sklearn`` module provides a collection of wrappers auto-generated on top of the original estimators and transformers, as well as on top of ``H2OAutoML``.

For examples on how to integrate H2O estimators into your Sklearn workflow, please click `here <https://github.com/h2oai/h2o-tutorials/tree/master/tutorials/sklearn-integration>`__.