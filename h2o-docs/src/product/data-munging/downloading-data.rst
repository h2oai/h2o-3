Downloading data
----------------

Sometimes it is desirable to download data from the H2O cluster. For example, when computing model predictions, it might be desirable to save these predictions for later.

Download to local memory
~~~~~~~~~~~~~~~~~~~~~~~~

H2O has functions like ``as_data_frame`` and ``get_frame_data`` in Python and ``as.data.frame`` in R that that allow you to download the data directly into the client program memory.

**Note**: For very large data this might not be feasible since the whole frame is downloaded as CSV into the client program memory.

.. tabs::
   .. code-tab:: r R
	
		library(h2o)
		h2o.init()
		iris.hex <- h2o.importFile("iris/iris_wheader.csv")
		iris.df <- as.data.frame(iris.hex)
	  
   .. code-tab:: python
   
		import h2o
		h2o.init()
		iris_hex = h2o.import_file("iris/iris_wheader.csv")
		iris_csv_string = iris_hex.get_frame_data()
		iris_pd = iris_hex.as_data_frame(use_pandas=True)

Save to a file system
~~~~~~~~~~~~~~~~~~~~~

The export file function can be used to save the data to an arbitrary location. The location has to be one that the server has access to, so either the server filesystem or a distributed filesystem like HDFS or S3. This function will save the data in CSV format.

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()
		iris.hex <- h2o.importFile("iris/iris_wheader.csv")
		h2o.exportFile(iris.hex, path = "hdfs://path/in/hdfs/iris.csv")
	  
   .. code-tab:: python
   
		import h2o
		h2o.init()
		iris_hex = h2o.import_file("iris/iris_wheader.csv")
		h2o.export_file(iris_hex, path = "/tmp/pred.csv", force = True)

Save as a Hive table
~~~~~~~~~~~~~~~~~~~~

When running on Hadoop, H2O can also export data into Hive tables. In order to do so, the user running the H2O cluster must have the privileges to create new Hive tables. The user can specify the table name and storage format (currently supported are ``csv`` and ``parquet``) as well as table location for external tables.

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()
		iris.hex <- h2o.importFile("iris/iris_wheader.csv")
		h2o.save_to_hive(iris.hex, jdbc_url = "jdbc:hive2://hive-server:10000/default", table_name = "airlines")	

   .. code-tab:: python
   
		import h2o
		h2o.init()
		iris_hex = h2o.import_file("iris/iris_wheader.csv")
		iris_hex.save_to_hive(
			jdbc_url = "jdbc:hive2://hive-server:10000/default", 
			table_name = "airlines",
			format = "parquet",
			table_path = "/user/bob/tables/iris"
        )

**Note:** Provided JDBC URL must include necessary authentication details, for example when running on a kerberized Hadoop cluster some form of ``auth`` parameter must be used in the URL.
