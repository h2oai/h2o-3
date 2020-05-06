Importing Multiple Files
------------------------

The ``importFolder`` (R)/``import_file`` (Python) function can be used to import multiple local files by specifying a directory and a pattern. Example patterns include:

- ``pattern="/A/.*/iris_.*"``: Import all files that have the pattern ``/A/.*/iris_.*`` in the specified directory.
- ``pattern="/A/iris_.*"``: Import all files that have the pattern ``/A/iris_.*`` in the specified directory.
- ``pattern="/A/B/iris_.*"``: Import all files that have the pattern ``/A/B/iris_.*`` in the specified directory.
- ``pattern="iris_.*"``: Import all files that have the pattern ``iris_.*`` in the specified directory.

**Notes**: 

- All files that are specified to be included must have the same number and set of columns. 
- When parsing a data file containing timestamps that do not include a timezone, the timestamps will be interpreted as UTC (GMT). You can override the parsing timezone using the following:

  - R: ``h2o.setTimezone("America/Los Angeles")``
  - Python: ``h2o.cluster().timezone = "America/Los Angeles"``

- The examples below assumes that the H2O-3 GitHub repository has been cloned, and that the following command was run in the **h2o-3** folder to retrieve the **smalldata** datasets. 

  :: 

    ./gradlew syncSmalldata


.. tabs::
   .. code-tab:: r R
	
		# To import all .csv files from the prostate_folder directory:
		library(h2o)
		h2o.init()
		prosPath <- system.file("extdata", "prostate_folder", package = "h2o")
		prostate_pattern <- h2o.importFolder(path = prosPath, 
		                                     pattern = ".*.csv")
		class(prostate_pattern)
		summary(prostate_pattern)

		# To import all .csv files from an anomaly folder stored locally
		ecgPath <- "../path_to_h2o-3/smalldata/anomaly/"
		ecg_pattern <- h2o.importFolder(path=ecgPath, 
		                                pattern = ".*.csv")

		class(ecg_pattern)
		summary(ecg_pattern)
	  
   .. code-tab:: python

		# To import all .csv files from an anomaly folder stored locally matching the regex ".*\.csv"
		import h2o
		h2o.init()
		ecg_pattern = h2o.import_file(path="../path_to_h2o-3/smalldata/anomaly/",pattern = ".*\.csv")

