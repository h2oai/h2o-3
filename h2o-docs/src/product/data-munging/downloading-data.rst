Download data
=============

Sometimes you need to download data from the H2O-3 cluster. For example, when computing model predictions, it might be desirable to save these predictions for later.

Download to local memory
------------------------

H2O-3 has functions like ``as_data_frame`` and ``get_frame_data`` in Python and ``as.data.frame`` in R that let you download the data directly into the client program memory.

.. note:: 
	
	For very large data this might not be feasible since the whole frame is downloaded as CSV into the client program memory.

.. tabs::
   .. code-tab:: python
   
		import h2o
		h2o.init()
		iris_hex = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
		iris_csv_string = iris_hex.get_frame_data()
		iris_pd = iris_hex.as_data_frame(use_pandas=True)

   .. code-tab:: r R
	
		library(h2o)
		h2o.init()
		iris.hex <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
		iris.df <- as.data.frame(iris.hex)
	  

Save to a file system
---------------------

The export file function can be used to save the data to an arbitrary location. The location has to be one that the server has access to, so either the server filesystem or a distributed filesystem like HDFS or S3. This function can save the data in either CSV format (default) or Parquet format. 

.. tabs::
   .. code-tab:: python
   
		import h2o
		h2o.init()
		iris_hex = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
		h2o.export_file(iris_hex, path = "/tmp/pred.csv", force = True)

		# To save as a parquet:
		path = "file:///tmp/iris.parquet"
		h2o.export_file(iris_hex, path, format="parquet")

   .. code-tab:: r R

		library(h2o)
		h2o.init()
		iris.hex <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
		h2o.exportFile(iris.hex, path = "hdfs://path/in/hdfs/iris.csv")

		# To save as a parquet:
		path <- "file:///tmp/prostate.parquet"
		h2o.exportFile(iris.hex, path, format="parquet")
	  

Save as a Hive table
--------------------

When running on Hadoop, H2O-3 can also export data into Hive tables. In order to do so, you must have the privileges to create new Hive tables. You can specify the table name and storage format (currently supported are ``csv`` and ``parquet``) as well as table location for external tables.

.. tabs::
   .. code-tab:: python
   
		import h2o
		h2o.init()
		iris_hex = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
		iris_hex.save_to_hive(
			jdbc_url = "jdbc:hive2://hive-server:10000/default", 
			table_name = "airlines",
			format = "parquet",
			table_path = "/user/bob/tables/iris"
		)

   .. code-tab:: r R

		library(h2o)
		h2o.init()
		iris.hex <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
		h2o.save_to_hive(iris.hex, jdbc_url = "jdbc:hive2://hive-server:10000/default", table_name = "airlines")	


.. note:: 
	
	The provided JDBC URL must include the necessary authentication details. For example, when running on a Kerberized Hadoop cluster, some form of ``auth`` parameter must be used in the URL.
