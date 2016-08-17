Downloading H2O
===============

To download H2O, go to our `downloads page <http://www.h2o.ai/download>`__. Select a build type (bleeding edge or latest stable), then select an installation method (standalone, R, Python, Hadoop, or Maven) by clicking the tabs at the top of the page. Follow the instructions in the tab to install H2O.

**Note**: OS X El Capitan users must include the ``--user`` flag when starting H2O from the command line or from Python. For example:

::

	java -jar h2o.jar --user

or

::
	
	pip install http://h2o-release.s3.amazonaws.com/h2o/rel-turing/3/Python/h2o-3.10.0.3-py2.py3-none-any.whl --user