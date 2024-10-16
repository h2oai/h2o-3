Docker users
============

This section describes how to use H2O-3 on Docker. It walks you through the following steps:

1. Installing Docker on Mac or Linux OS.
2. Creating and modifying your Dockerfile.
3. Building a Docker image from the Dockerfile.
4. Running the Docker build.
5. Launching H2O-3.
6. Accessing H2O-3 from the web browser or from Python/R.

Prerequisites
-------------

- Linux kernel verison 3.8+ or Mac OS 10.6+
- VirtualBox
- Latest version of Docker installed and configured
- Docker daemon running (enter all following commands in the Docker daemon window)
- In ``User`` directory (not ``root``)

.. note::
	
	- Older Linux kernel versions can cause kernel panics that break Docker. There are ways around it, but attempt these at your own risk. Check the version of your kernel by running ``uname -r``.
	- The Dockerfile always pulls the latest H2O-3 release.
	- The Docker image only needs to be built once.

Walkthrough
-----------

The following steps walk you through how to use H2O-3 on Docker.

.. note::
	
	If the following commands don't work, prepend them with ``sudo``.

Step 1: Install and launch Docker
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Depending on your operating system, select the appropriate installation method:

- `Mac installation <https://docs.docker.com/installation/mac/#installation>`__
- `Ubuntu installation <https://docs.docker.com/installation/ubuntulinux/>`__
- `Other OS installations <https://docs.docker.com/installation/>`__

.. note::
	
	By default, Docker allocates 2GB of memory for Mac installations. Be sure to increase this value. We suggest 3-4 times the size of the dataset for the amount of memory required.

Step 2: Create or download Dockerfile
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

1. Create a folder on the Host OS to host your Dockerfile:

.. code-block:: bash

      mkdir -p /data/h2o-{{branch_name}}

2. Download or create a Dockerfile, which is a build recipe that builds the container. Download and use our `Dockerfile template <https://github.com/h2oai/h2o-3/blob/master/Dockerfile>`__:

.. code-block:: bash
	
	cd /data/h2o-<branch_name>
	wget https://raw.githubusercontent.com/h2oai/h2o-3/master/Dockerfile

This Dockerfile will do the following:

- Obtain and update the base image (Ubuntu 14.0.4).
- Install Java 8.
- Obtain and download the H2O-3 build from H2O-3's S3 repository.
- Expose ports ``54321`` and ``54322`` in preparation for launching H2O-3 on those ports.

Step 3: Build a Docker image from the Dockerfile
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

From the ``/data/h2o-<branch_name>`` directory, run the following (note that ``v5`` represents the current version number):

.. code-block:: bash
	
	docker build -t "h2o.ai/{{branch_name}}:v5"

.. note::
	
	This process can take a few minutes because it assembles all the necessary parts for the image.

Step 4: Run the Docker build
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

On a mac, use the argument ``-p 54321:54321`` to expressly map the port ``54321`` (this is not necessary on Linux). 

.. code-block:: bash
	
	docker run -ti -p 54321:54321 h2o.ai/{{branch_name}}:v5 /bin/bash

Step 5: Launch H2O-3
~~~~~~~~~~~~~~~~~~~~

Navigate to the ``/opt`` directory and launch H2O-3. Update the value of ``-Xmx`` to the amount of memory you want ot allocate to the H2O-3 instance. By default, H2O-3 will launch on port ``54321``.

.. code-block:: bash
	
	cd /opt
	java -Xmx1g -jar h2o.jar

Step 6: Access H2O-3 from the web browser or Python/R
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. tabs::
	.. tab:: On Linux

		After H2O-3 launches, copy and paste the IP address and port of the H2O-3 instance into the address bar of your browser. In the following example, the IP is ``172.17.0.5:54321``.

		.. code-block:: bash

			03:58:25.963 main      INFO WATER: Cloud of size 1 formed [/172.17.0.5:54321 (00:00:00.000)]

	.. tab:: On MacOS

		Locate the IP address of the Docker's network (``192.168.59.103`` in the following example) that bridges to your Host OS by opening a new terminal window (not a bash for your container) and running ``boot2docker ip``.

		.. code-block:: bash

			$ boot2docker ip
			192.168.59.103  		


You can also view the IP address (``192.168.99.100`` in the following example) by scrolling to the top of the Docker daemon window:

::


                            ##         .
                      ## ## ##        ==
                   ## ## ## ## ##    ===
               /"""""""""""""""""\___/ ===
          ~~~ {~~ ~~~~ ~~~ ~~~~ ~~~ ~ /  ===- ~~~
               \______ o           __/
                 \    \         __/
                  \____\_______/


    docker is configured to use the default machine with IP 192.168.99.100
    For help getting started, check out the docs at https://docs.docker.com

Access Flow
'''''''''''

After obtaining the IP address, point your browser to the specified IP address and port to open Flow. In R and Python, you can access the instance by installing the latest version of the H2O R or Python package and then initializing H2O-3:

.. tabs::
	.. code-tab:: python

		# Initialize H2O 
		import h2o
		docker_h2o = h2o.init(ip = "192.168.59.103", port = 54321)

	.. code-tab:: r R

		# Initialize H2O
		library(h2o)
		dockerH2O <- h2o.init(ip = "192.168.59.103", port = 54321)
