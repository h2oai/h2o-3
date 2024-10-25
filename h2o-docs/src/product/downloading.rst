Downloading and installing H2O-3
================================

This section describes how to download and install the latest stable version of H2O-3. These instructions are also available on the `H2O-3 Download page <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__.  Please first make sure you meet `the requirements to download and use H2O-3 <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/welcome.html#requirements>`__.  Java is a prerequisite for H2O-3, even if using it from the R or Python packages.

.. note::
	
	Check our `Downloads page to download the nightly bleeding edge release <https://h2o-release.s3.amazonaws.com/h2o/master/latest.html>`__. 

	Scroll down to the H2O-3 section and select Nightly Bleeding Edge. Then, choose the type of installation you want to perform (for example, "Install in Python") by clicking on the tab. 

Choose your desired method of use below.  Most users will want to use H2O-3 from either `R <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/downloading.html#install-in-r>`__ or `Python <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/downloading.html#install-in-python>`__. However, we also include instructions for using H2O-3's web GUI Flow and Hadoop below.


Download and run Flow from the command line
-------------------------------------------

If you plan to exclusively use H2O-3's web GUI, `Flow <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/flow.html>`__, this is the method you should use.

1. Go to our `latest stable release page <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__.
2. Click the ``Download H2O`` button from the Download and Run tab. This downloads a ZIP file that contains everything you need to get started.

.. note::
	
	By default, this setup is open. Follow `security guidelines <security.html>`__ if you want to secure your installation.

3. From your terminal, unzip and start H2O-3 as in the example below. 

 .. substitution-code-block:: bash

	cd ~/Downloads
	unzip h2o-|version|.zip
	cd h2o-|version|
	java -jar h2o.jar

4. Point your browser to http://localhost:54321 to open up the H2O-3 Flow web GUI.

Install in R
------------

Perform the following steps in R to install H2O-3. Copy and paste these commands one line at a time.

.. note::
	
	By default, this setup is open. Follow `security guidelines <security.html>`__ if you want to secure your installation.

1. The following two commands remove any previously installed H2O-3 packages for R.

 .. code-block:: r

	if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
	if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }

2. Next, `download packages that H2O-3 depends on <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__.

 .. code-block:: r

    pkgs <- c("RCurl","jsonlite")
    for (pkg in pkgs) {
      if (! (pkg %in% rownames(installed.packages()))) { install.packages(pkg) }
    }

3. Download and install the H2O-3 package for R.

 .. code-block:: r

	install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/latest_stable_R")))

4. Optionally initialize H2O-3 and run a demo to see H2O-3 at work.

 .. code-block:: r

	library(h2o)
	localH2O = h2o.init() 
	demo(h2o.kmeans) 

Install H2O-3's R package from CRAN
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Alternatively you can install H2O-3’s R package from `CRAN <https://cran.r-project.org/web/packages/h2o/>`__ or by typing ``install.packages("h2o")`` in R. 

.. note::
	
	Sometimes there can be a delay in publishing the latest stable release to CRAN. To guarantee you have the latest stable version, use the instructions above to install directly from the H2O.ai website.

Install in Python
-----------------

.. note::
	
	By default, this setup is open. Follow `security guidelines <security.html>`__ if you want to secure your installation.

Run the following commands in a Terminal window to install H2O-3 for Python. 

1. Install dependencies (prepending with ``sudo`` if needed):

 .. code-block:: bash

	pip install requests
	pip install tabulate
	pip install future
	
	# Required for plotting:
	pip install matplotlib

.. note::
	
	These are the dependencies required to run H2O-3. ``matplotlib`` is optional and only required to plot in H2O-3. See our `complete list of dependencies <https://github.com/h2oai/h2o-3/blob/master/h2o-py/conda/h2o-main/meta.yaml>`__.

2. Run the following command to remove any existing H2O-3 module for Python.

 .. code-block:: bash

  pip uninstall h2o

3. Use ``pip`` to install `the H2O-3 Python module <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__.

 .. code-block:: bash

	pip install -f http://h2o-release.s3.amazonaws.com/h2o/latest_stable_Py.html h2o

4. Optionally initialize H2O-3 in Python and run a demo to see H2O-3 at work.

  .. code-block:: python

    import h2o
    h2o.init()
    h2o.demo("glm")

Install on Anaconda Cloud
~~~~~~~~~~~~~~~~~~~~~~~~~

This section describes how to set up and run H2O-3 in an Anaconda Cloud environment. Conda 2.7, 3.5, and 3.6 repos are supported as are a number of H2O-3 versions. See `which H2O-3 versions are available on Anaconda <https://anaconda.org/h2oai/h2o/files>`__.

Open a terminal window and run the following command to install H2O-3 on the Anaconda Cloud. The H2O-3 version in this command should match the version that you want to download. If you leave the H2O-3 version blank and specify just ``h2o``, then the latest version will be installed. For example: 
      
  .. substitution-code-block:: bash

     user$ conda install -c h2oai h2o=|version|

or:

  .. code-block:: bash

     user$ conda install -c h2oai h2o    

.. note::
	
	For Python 3.6 users, H2O-3 has ``tabulate>=0.75`` as a dependency; however, there is no ``tabulate`` available in the default channels for Python 3.6. This is available in the conda-forge channel. As a result, Python 3.6 users must add the ``conda-forge`` channel in order to load the latest version of H2O-3. This can be done by performing the following steps:

 	.. code-block:: bash

	   conda create -n py36 python=3.6 anaconda
	   source activate py36
	   conda config --append channels conda-forge
	   conda install -c h2oai h2o 

After H2O-3 is installed, see the `Starting H2O-3 from Anaconda <starting-h2o.html#from-anaconda>`__ section for information on how to start H2O-3 and to view a GBM example run in Jupyter Notebook. 

Install on Hadoop
-----------------

1. Go to the `Downloads page <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__. Click on the **Install on Hadoop** tab, and download H2O-3 for your version of Hadoop. This is a ZIP file that contains everything you need to get started.

2. Unpack the ZIP file and launch a 6g instance of H2O-3. For example:

 .. substitution-code-block:: bash

	unzip h2o-|version|-*.zip
	cd h2o-|version|-*
	hadoop jar h2odriver.jar -nodes 1 -mapperXmx 6g

3. Point your browser to H2O-3. (See "Open H2O Flow in your web browser" in the output below.)

 .. code-block:: bash

	Determining driver host interface for mapper->driver callback...
	[Possible callback IP address: 172.16.2.181]
	[Possible callback IP address: 127.0.0.1]
	...
	Waiting for H2O cluster to come up...
	H2O node 172.16.2.188:54321 requested flatfile
	Sending flatfiles to nodes...
	[Sending flatfile to node 172.16.2.188:54321]
	H2O node 172.16.2.188:54321 reports H2O cluster size 1
	H2O cluster (1 nodes) is up
	(Note: Use the -disown option to exit the driver after cluster formation)

	Open H2O Flow in your web browser: http://172.16.2.188:54321

	(Press Ctrl-C to kill the cluster)
	Blocking until the H2O cluster shuts down...

