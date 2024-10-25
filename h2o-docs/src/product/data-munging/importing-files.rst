Import multiple files
=====================

The ``import_file`` (Python)/``importFolder`` (R) function can be used to import multiple local files by specifying a directory and a pattern. Example patterns include:

- ``pattern="/A/.*/iris_.*"``: Import all files that have the pattern ``/A/.*/iris_.*`` in the specified directory.
- ``pattern="/A/iris_.*"``: Import all files that have the pattern ``/A/iris_.*`` in the specified directory.
- ``pattern="/A/B/iris_.*"``: Import all files that have the pattern ``/A/B/iris_.*`` in the specified directory.
- ``pattern="iris_.*"``: Import all files that have the pattern ``iris_.*`` in the specified directory.

.. note::

	- All files that are specified to be included must have the same number and set of columns. 
	- When parsing a data file containing timestamps that do not include a timezone, the timestamps will be interpreted as UTC (GMT). You can override the parsing timezone using the following:

	  - **Python**: ``h2o.cluster().timezone = "America/Los Angeles"``
	  - **R**: ``h2o.setTimezone("America/Los Angeles")``
	  
.. attention::

	The following examples assume that you've cloned the H2O-3 GitHub repository and that the following command was run in the ``h2o-3`` folder to retrieve the ``smalldata`` datasets:

	:: 

		./gradlew syncSmalldata

.. tabs::
   .. code-tab:: python

		# To import all .csv files from an anomaly folder stored locally matching the regex ".*\.csv"
		import h2o
		h2o.init()
		ecg_pattern = h2o.import_file(path="../path_to_h2o-3/smalldata/anomaly/",pattern = ".*\.csv")

   .. code-tab:: r R
	
		# To import all .csv files from the prostate_folder directory:
		library(h2o)
		h2o.init()
		pros_path <- system.file("extdata", "prostate_folder", package = "h2o")
		prostate_pattern <- h2o.importFolder(path = pros_path, 
		                                     pattern = ".*.csv")
		class(prostate_pattern)
		summary(prostate_pattern)

		# To import all .csv files from an anomaly folder stored locally
		ecg_path <- "../path_to_h2o-3/smalldata/anomaly/"
		ecg_pattern <- h2o.importFolder(path = ecg_path, 
		                                pattern = ".*.csv")

		class(ecg_pattern)
		summary(ecg_pattern)
	  

