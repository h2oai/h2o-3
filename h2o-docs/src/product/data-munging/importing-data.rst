Importing a File
----------------

Unlike the `upload <uploading-data.html>`__ function, which is a push from the client to the server, the import function is a parallelized reader and pulls information from the server from a location specified by the client. The path is a server-side path. This is a fast, scalable, highly optimized way to read data. H2O pulls the data from a data store and initiates the data transfer as a read operation.

Refer to the `Supported File Formats <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/getting-data-into-h2o.html#supported-file-formats>`__ topic to ensure that you are using a supported file type.

**Note**: When parsing a data file containing timestamps that do not include a timezone, the timestamps will be interpreted as UTC (GMT). You can override the parsing timezone using the following:

  - R: ``h2o.setTimezone("America/Los Angeles")``
  - Python: ``h2o.cluster().timezone = "America/Los Angeles"``

.. example-code::
   .. code-block:: r
	
	# To import airlines file from H2O’s package:
	library(h2o)
	h2o.init()
	irisPath <- "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv" 
	iris.hex <- h2o.importFile(path = irisPath, destination_frame = "iris.hex")
	  
	# To import from S3:
	library(h2o)
	h2o.init()
	airlinesURL <- "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv" 
	airlines.hex <- h2o.importFile(path = airlinesURL, destination_frame = "airlines.hex")

	# To import from HDFS, you must include the node name:
	library(h2o)
	h2o.init()
	airlinesURL <- "hdfs://node-1:/user/smalldata/airlines/allyears2k_headers.zip" 
	airlines.hex <- h2o.importFile(path = airlinesURL, destination_frame = "airlines.hex")
	  
   .. code-block:: python

	# Import a file from S3:
	import h2o
	h2o.init()
	airlines = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip"
	airlines_df = h2o.import_file(path=airlines)

	# Import a file from HDFS, you must include the node name:
	import h2o
	h2o.init()
	airlines = "hdfs://node-1:/user/smalldata/airlines/allyears2k_headers.zip"
	airlines_df = h2o.import_file(path=airlines)

