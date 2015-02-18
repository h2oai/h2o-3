Launching iPython Example
=========================

Prerequisites:

    - Python 2.7

Install iPython Notebook
-------------------------

Download pip if it's not already installed in order to install iPython:

    $ sudo easy_install pip

Install iPython using pip install:

    $ pip install "ipython[notebook]"

Install dependencies
--------------------

This module depends on requests and tabulate modules. Both of which are available on pypi.

    $ pip install requests
    $ pip install tabulate
  
Install H2O Module
------------------

The following command removes the H2O module for Python, and then pip install the latest version of the H2O Python module:

  
    $ pip uninstall h2o
    $ pip install http://h2o-release.s3.amazonaws.com/h2o-dev/master/1064/Python/h2o-0.0.0a5-py2.py3-none-any.whl

Launch H2O 
----------

Launch H2O outside of the iPython notebook, you can do this in the top directory of your h2o build download. Keep in mind 
that the version of H2O running must match the version of the H2O Python module in order for Python to connect to H2O. 
Find can access the H2O Web UI at [https://localhost:54321](https://localhost:54321).

    $ cd h2o-dev-0.1.27.1064
    $ java -jar h2o.jar

Open Demos Notebook
-------------------

Open the prostateGBM.ipynb file. The notebook will contain a demo that will start up H2O, import a prostate dataset 
into H2O, build a GBM model, and predict on the training set with the recently built model. Use Ctrl+Return to execute 
each cell in the notebook.

    $ ipython notebook prostateGBM.ipynb
