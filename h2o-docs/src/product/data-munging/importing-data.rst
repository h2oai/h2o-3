Importing a File
----------------

Unlike the `upload <uploading-data.html>`__ function, which is a push from the client to the server, the import function is a parallelized reader and pulls information from the server from a location specified by the client. The path is a server-side path. This is a fast, scalable, highly optimized way to read data. H2O pulls the data from a data store and initiates the data transfer as a read operation.

Refer to the `Supported File Formats <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/getting-data-into-h2o.html#supported-file-formats>`__ topic to ensure that you are using a supported file type.

.. example-code::
   .. code-block:: r
	
	# To import small iris data file from H2O’s package:
	> library(h2o)
	> h2o.init()
	> irisPath <- "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv" 
	> iris.hex <- h2o.importFile(path = irisPath, destination_frame = "iris.hex")
	  
	# To import from HDFS:
	> library(h2o)
	> h2o.init()
	> airlinesURL <- "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv" 
	> airlines.hex <- h2o.importFile(path = airlinesURL, destination_frame = "airlines.hex")
	  
   .. code-block:: python

	# Import a file from HDFS:
	>>> import h2o
	>>> h2o.init()
	>>> prostate = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv"
	>>> prostate_df = h2o.import_file(path=prostate)
