Uploading a File
----------------

Unlike the import function, which is a parallelized reader, the upload function is a push from the client to the server. The specified path must be a client-side path. This is not scalable and is only intended for smaller data sizes. The client pushes the data from a local filesystem (for example, on your machine where R or Python is running) to H2O. For big-data operations, you don't want the data stored on or flowing through the client.

Refer to the `Supported File Formats <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/getting-data-into-h2o.html#supported-file-formats>`__ topic to ensure that you are using a supported file type.

**Note**: When parsing a data file containing timestamps that do not include a timezone, the timestamps will be interpreted as UTC (GMT). You can override the parsing timezone using the following:

  - R: ``h2o.setTimezone("America/Los Angeles")``
  - Python: ``h2o.cluster().timezone = "America/Los Angeles"``

Run the following command to load data that resides on the same machine that is running H2O. 

.. example-code::
   .. code-block:: r
	
	library(h2o)
	h2o.init()
	irisPath <- "../smalldata/iris/iris_wheader.csv"
	iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
	  
   .. code-block:: python
   
	import h2o
	h2o.init()
	iris_df = h2o.upload_file("../smalldata/iris/iris_wheader.csv")
