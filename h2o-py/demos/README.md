Launching iPython Examples
=========================

##Prerequisites:

- Python 2.7

---

Install iPython Notebook
-------------------------

1. To install iPython, download pip, a Python package manager, if it's not already installed:

    `$ sudo easy_install pip`

2. Install iPython using pip install:

    `$ sudo pip install "ipython[notebook]"`

---

Install dependencies
--------------------

This module uses requests and tabulate modules, both of which are available on pypi, the Python package index.

    $ sudo pip install requests
    $ sudo pip install tabulate
  
---

Install H2O Module
------------------

The following command removes the H2O module for Python, and then use pip install the latest version of the H2O Python module:

    $ sudo pip uninstall h2o
    $ sudo pip install http://h2o-release.s3.amazonaws.com/h2o-dev/master/1064/Python/h2o-0.0.0a5-py2.py3-none-any.whl

---


Launch H2O 
----------

Launch H2O outside of the iPython notebook. You can do this in the top directory of your H2O build download. The version of H2O running must match the version of the H2O Python module for Python to connect to H2O. 
To access the H2O Web UI, go to [https://localhost:54321](https://localhost:54321) in your web browser.

    $ cd h2o-dev-0.1.27.1064
    $ java -jar h2o.jar

---

Open Demos Notebook
-------------------

Open the prostateGBM.ipynb file. The notebook contains a demo that starts H2O, imports a prostate dataset into H2O, builds a GBM model, and predicts on the training set with the recently built model. Use Ctrl+Return to execute each cell in the notebook.

    $ ipython notebook prostateGBM.ipynb

All demos are available here:

 * [iPython Demos](https://github.com/h2oai/h2o-3/tree/master/h2o-py/demos)

---
