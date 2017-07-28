Downloading & Installing H2O
============================

This section describes how to download and install the latest stable version of H2O. These instructions are also available on the `H2O Download page <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__. 

**Note**: To download the nightly bleeding edge release, go to `h2o-release.s3.amazonaws.com/h2o/master/latest.html <https://h2o-release.s3.amazonaws.com/h2o/master/latest.html>`__. Choose the type of installation you want to perform (for example, "Install in Python") by clicking on the tab. 

Download and Run
----------------

1. Click the ``Download H2O`` button on the `http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__ page. This downloads a zip file that contains everything you need to get started.

2. From your terminal, run:

  ::

	cd ~/Downloads
	unzip h2o-3.10.4.3.zip
	cd h2o-3.10.4.3
	java -jar h2o.jar

3. Point your browser to http://localhost:54321.


Install in R
------------

Perform the following steps in R to install H2O. Copy and paste these commands one line at a time.

1. The following two commands remove any previously installed H2O packages for R.

 ::

	if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
	if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }

2. Next, download packages that H2O depends on.

 ::

	if (! ("methods" %in% rownames(installed.packages()))) { install.packages("methods") }
	if (! ("statmod" %in% rownames(installed.packages()))) { install.packages("statmod") }
	if (! ("stats" %in% rownames(installed.packages()))) { install.packages("stats") }
	if (! ("graphics" %in% rownames(installed.packages()))) { install.packages("graphics") }
	if (! ("RCurl" %in% rownames(installed.packages()))) { install.packages("RCurl") }
	if (! ("jsonlite" %in% rownames(installed.packages()))) { install.packages("jsonlite") }
	if (! ("tools" %in% rownames(installed.packages()))) { install.packages("tools") }
	if (! ("utils" %in% rownames(installed.packages()))) { install.packages("utils") }

3. Download and install the H2O package for R.

 ::

	install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/latest_stable_R")))

4. Optionally initialize H2O and run a demo to see H2O at work.

 ::

	library(h2o)
	localH2O = h2o.init() 
	demo(h2o.kmeans) 

Installing H2O's R Package from CRAN
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You can alternatively install H2O’s R package from CRAN at `https://cran.r-project.org/web/packages/h2o/ <https://cran.r-project.org/web/packages/h2o/>`__.

Install in Python
-----------------

Run the following commands in a Terminal window to install H2O for Python. 

1. Install dependencies (prepending with `sudo` if needed):

 ::

	pip install requests
	pip install tabulate
	pip install scikit-learn
	pip install colorama
	pip install future

 **Note**: These are the dependencies required to run H2O. A complete list of dependencies is maintained in the following file: `https://github.com/h2oai/h2o-3/blob/master/h2o-py/conda/h2o/meta.yaml <https://github.com/h2oai/h2o-3/blob/master/h2o-py/conda/h2o/meta.yaml>`__

2. Run the following command to remove any existing H2O module for Python.

 ::

  pip uninstall h2o

3. Use ``pip`` to install this version of the H2O Python module.

 ::

	pip install -f http://h2o-release.s3.amazonaws.com/h2o/latest_stable_Py.html h2o

 **Note**: When installing H2O from ``pip`` in OS X El Capitan, users must include the ``--user`` flag. For example:

 ::
	
   pip install -f http://h2o-release.s3.amazonaws.com/h2o/latest_stable_Py.html h2o --user

4. Optionally initialize H2O in Python and run a demo to see H2O at work.

  ::

    python
    import h2o
    h2o.init()
    h2o.demo("glm")

Install on Anaconda Cloud
~~~~~~~~~~~~~~~~~~~~~~~~~

This section describes how to set up and run H2O in an Anaconda Cloud environment. Conda 2.7 and 3.5 repos are supported as are a number of H2O versions. Refer to `https://anaconda.org/h2oai/h2o/files <https://anaconda.org/h2oai/h2o/files>`__ to view a list of available H2O versions.

Open a terminal window and run the following command to install H2O on the Anaconda Cloud. 
      
   ::

     user$ conda install -c h2oai h2o=3.10.4.3

**Note**: The H2O version in the above command should match the version that you want to download. 

After H2O is installed, refer to the `Starting H2O from Anaconda <starting-h2o.html#from-anaconda>`__ section for information on how to start H2O and to view a GBM example run in Jupyter Notebook. 

Install on Hadoop
-----------------

1. Go to `http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__. Click on the **Install on Hadoop** tab, and download H2O for your version of Hadoop. This is a zip file that contains everything you need to get started.

2. Unpack the zip file and launch a 6g instance of H2O. The example below describes how to unpack version 3.10.4.3. Replace this version with the version that you downloaded.

 ::

	unzip h2o-3.10.4.3-*.zip
	cd h2o-3.10.4.3-*
	hadoop jar h2odriver.jar -nodes 1 -mapperXmx 6g -output hdfsOutputDirName

3. Point your browser to H2O. (See "Open H2O Flow in your web browser" in the output below.)

 ::

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

