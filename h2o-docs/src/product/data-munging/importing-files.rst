Importing Multiple Files
------------------------

The ``importFolder`` (R)/``import_file`` (Python) function can be used to import multiple files by specifying a directory and a pattern. Example patterns include:

- ``pattern="/A/.*/iris_.*"``: Import all files that have the pattern ``/A/.*/iris_.*`` in the specified directory.
- ``pattern="/A/iris_.*"``: Import all files that have the pattern ``/A/iris_.*`` in the specified directory.
- ``pattern="/A/B/iris_.*"``: Import all files that have the pattern ``/A/B/iris_.*`` in the specified directory.
- ``pattern="iris_.*"``: Import all files that have the pattern ``iris_.*`` in the specified directory.

**Notes**: 

- All files that are specified to be included must have the same number and set of columns. 
- The Python example below assumes that the H2O-3 GitHub repository has been cloned, and that the following command was run in the **h2o** folder to retrieve the **smalldata** datasets. 

  :: 

    ./gradlew syncSmalldata


.. example-code::
   .. code-block:: r
	
	# To import all .csv files from the prostate_folder directory:
	> library(h2o)
	> h2o.init()
	> prosPath <- system.file("extdata", "prostate_folder", package = "h2o")
	> prostate_pattern.hex <- h2o.importFolder(path = prosPath, pattern = ".*.csv", destination_frame = "prostate.hex")
	> class(prostate_pattern.hex)
	> summary(prostate_pattern.hex)
	  
   .. code-block:: python

	# To import all files in the iris folder matching the regex "iris_.*\.csv"
	>>> import h2o
	>>> h2o.init()
	>>> iris_pattern = h2o.import_file(path = "../smalldata/iris",pattern = "iris_.*\.csv")
