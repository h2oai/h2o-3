H2O-3 clients
=============

These clients allow you to connect to and interact with H2O-3.

H2O Flow
--------

H2O Flow is a Web based (GUI) user interface. It lets you interactively run your H2O-3 machine learning workflows and iteratively improve them. H2O Flow combines code execution, text, mathematics, plots, and rich media in a single document. See the `documentation for H2O Flow <flow.html>`__.

R client
--------

R users can use H2O-R library which internally uses H2O REST API calls to connect to H2O-3 (Server) and lets you run your H2O-3 workflow via R. See the `documentation for the H2O-R Client <../h2o-r/docs/index.html>`__.

Python client
-------------

Python users can connect to H2O-3 using the H2O Python package that internally uses H2O REST API calls to connect to H2O-3 (Server) and allows users to run their H2O-3 workflow via Python. See the `documentation for the H2O-Python Client <../h2o-py/docs/index.html>`__.

Sklearn support
~~~~~~~~~~~~~~~

Most H2O-3 estimators available in the H2O-Python client can also be used in the standard ``sklearn`` API. The ``h2o.sklearn`` module provides a collection of wrappers auto-generated on top of the original estimators and transformers, as well as on top of ``H2OAutoML``.

See `examples on how to integrate H2O-3 estimators into your Sklearn workflow <https://github.com/h2oai/h2o-tutorials/tree/master/tutorials/sklearn-integration>`__.