Upload a file
=============

Unlike the import function, which is a parallelized reader, the upload function is a push from the client to the server. The specified path must be a client-side path. This is not scalable and is only intended for smaller data sizes. The client pushes the data from a local filesystem (for example, on your machine where Python or R is running) to H2O-3. For big-data operations, you don't want the data stored on or flowing through the client.

`See more on supported file formats <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/getting-data-into-h2o.html#supported-file-formats>`__ to ensure that you are using a supported file type.

.. note::
	
	When parsing a data file containing timestamps that do not include a timezone, the timestamps will be interpreted as UTC (GMT). You can override the parsing timezone using the following:

	- **Python**: ``h2o.cluster().timezone = "America/Los Angeles"``
	- **R**: ``h2o.setTimezone("America/Los Angeles")``


Run the following command to load data that resides on the same machine that is running H2O-3. 

.. tabs::
   .. code-tab:: python
   
		import h2o
		h2o.init()
		iris_df = h2o.upload_file("../smalldata/iris/iris_wheader.csv")
   
   .. code-tab:: r R
	
		library(h2o)
		h2o.init()
		iris_path <- "../smalldata/iris/iris_wheader.csv"
		iris <- h2o.uploadFile(path = iris_path)

