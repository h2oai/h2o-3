R users
=======

R users rejoice: H2O-3 supports your chosen programming language!


Getting started with R
----------------------

The following sections will help you begin using R for H2O-3. 

See `this cheatsheet on H2O-3 in R <https://github.com/rstudio/cheatsheets/blob/main/h2o.pdf>`__ for a quick start.

.. note::
	
	If you are running R on Linux, then you must install ``libcurl`` which allows H2O-3 to communicate with R. We also recommend disabling SElinux and any firewalls (at least initially until you confirmed H2O-3 can initialize).

	- On Ubuntu, run: ``apt-get install libcurl4-openssl-dev``
	- On CentOS, run: ``yum install libcurl-devel``

Installing H2O with R
~~~~~~~~~~~~~~~~~~~~~

You can find instructions for using H2O-3 with Python in the `Downloading and installing H2O <../downloading.html#install-in-r>`__ section and on the `Downloads page <http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html>`__.

From the Downloads page:

1. Click the Install in R tab.
2. Follow the on-page instructions.

Checking your R version for H2O-3
'''''''''''''''''''''''''''''''''

To check which version of H2O-3 is installed in R, run the following:

::

	versions::installed.versions("h2o")

.. note::
	
	R version 3.1.0 ("Spring Dance") is incompatible with H2O-3. If you are using that version, we recommend upgrading your R version before using H2O-3.


R documentation
~~~~~~~~~~~~~~~

See our `R-specific documentation <https://docs.h2o.ai/h2o/latest-stable/h2o-r/docs/index.html>`__. This documentation also exists as a PDF: `R user PDF <https://docs.h2o.ai/h2o/latest-stable/h2o-r/h2o_package.pdf>`__.

Connecting RStudio to Sparkling Water
-------------------------------------

See our `illustrated tutorial on how to use RStudio to connect to Sparkling Water <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/Connecting_RStudio_to_Sparkling_Water.md>`__.