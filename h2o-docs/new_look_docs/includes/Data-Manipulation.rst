Data Manipulation
=================

This section provides examples of common tasks performed when preparing data for machine learning. 

-  `Importing Data`_
-  `Uploading Data`_
-  `Merging Two Data Frames`_
-  `Slicing Columns`_
-  `Slicing Rows`_
-  `Replacing Values in a Frame`_


Importing Data
--------------

Run the following command to load data from a machine running Python/R into the machine that is running H2O. 

.. example-code::
   .. code-block:: h2o-r
	
	  library(h2o)
	  h2o.init()
	  airlinesURL = "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv" 
	  airlines.hex = h2o.importFile(path = airlinesURL, destination_frame = "airlines.hex")
	  
   .. code-block:: h2o-python
   
	   import h2o
	   h2o.init()
	   path = "data/iris/allyears2k.csv"
	   df = h2o.import_file(path=path)



Uploading Data
--------------

Run the following command to load data that is currently on the same machine that is running H2O. 

.. example-code::
   .. code-block:: h2o-r
	
	  library(h2o)
	  h2o.init()
	  irisPath = system.file("extdata", "iris.csv", package="h2o")
	  iris.hex = h2o.uploadFile(path = irisPath, destination_frame = iris.hex")
	  
   .. code-block:: h2o-python
   
	   import h2o
	   h2o.init()
	   df = h2o.upload_file("../../SmallData/iris.csv")


Merging Two Data Frames
-----------------------



Slicing Columns
---------------

H2O lazily slices out columns of data and will only materialize a shared copy upon some type of triggering IO. This example shows how to slice columns from a frame of data.

.. example-code::
   .. code-block:: h2o-R
	
	  library(h2o)
	  path <- "data/iris/iris_wheader.csv"
	  h2o.init()
	  df <- h2o.importFile(path)
 	  
	  # slice 1 column by index
	  c1 <- df[,1]
	  
	  # slice 1 column by name
	  c1_1 <- df[, "sepal_len"]
 	  
	  # slice cols by vector of indexes
	  cols <- df[, 1:4]
	  
	  # slice cols by vector of names
	  cols_1 <- df[, c("sepal_len", "sepal_wid", "petal_len", "petal_wid")]

   .. code-block:: h2o-python
   
	   import h2o
	   h2o.init()
	   path = "data/iris/iris_wheader.csv"
	   df = h2o.import_file(path=path)

	   # slice 1 column by index
	   c1 = df[:,0]

	   # slice 1 column by name
	   c1_1 = df[:, "sepal_len"]

	   # slice cols by list of indexes
	   cols = df[:, range(4)]

	   # slice cols by a list of names
	   cols_1 = df[:, ["sepal_len", "sepal_wid", "petal_len", "petal_wid"]]

Slicing Rows
------------

H2O lazily slices out rows of data and will only materialize a shared copy upon IO. This example shows how to slice rows from a frame of data.

.. example-code::
   .. code-block:: h2o-R
   
	library(h2o)
	path <- "data/iris/iris_wheader.csv"
	h2o.init()
	df <- h2o.importFile(path)

	# slice 1 row by index
	c1 <- df[15,]

	# slice a range of rows
	c1_1 <- df[25:49,]

	# slice with a boolean mask
	mask <- df[,"sepal_len"] < 4.4
	cols <- df[mask,]

	# filter out missing values
	mask <- is.na(df[,"sepal_len"])
	cols <- df[!mask,]

   .. code-block:: h2o-python

	import h2o
	h2o.init()
	path = "data/iris/iris_wheader.csv"
	df = h2o.import_file(path=path)

	# slice 1 row by index
	c1 = df[15,:]

	# slice a ramge of rows
	c1_1 = df[range(25,50,1), :]

	# slice with a boolean mask
	mask = df["sepal_len"] < 4.4
	cols = df[mask,:]

	# filter out missing values
	mask = df["sepal_len"].isna()
	cols = df[~mask,:]  # note how to perform a logical not with the '~'

Replacing Values in a Frame
-------------------------

This example shows how to replace values in a frame of data.    

.. example-code::
   .. code-block:: h2o-R
   
	library(h2o)
	path <- "data/iris/iris_wheader.csv"
	h2o.init()
	df <- h2o.importFile(path)

	# replace a single numerical datum
	df[15,3] <- 2

	# replace a single categorical datum
	# unimplemented as of 3.6.0.8 (tibshirani)

	# replace a whole column
	df[,1] <- 3*df[,1]

	# replace by row mask
	df[df[,"sepal_len"] < 4.4, "sepal_len"] <- 22  # BUG: https://	0xdata.atlassian.net/browse/PUBDEV-2520

	# replacement with ifelse
	df[,"sepal_len"] <- h2o.ifelse(df[,"sepal_len"] < 4.4, 22, df[,"sepal_len"])

	# replace missing values with 0
	df[is.na(df[,"sepal_len"]), "sepal_len"] <- 0

	# alternative with ifelse
	df[,"sepal_len"] <- h2o.ifelse(is.na(df[,"sepal_len"]), 0, df[,"sepal_len"])

   .. code-block:: h2o-python

	import h2o
	h2o.init()
	path = "data/iris/iris_wheader.csv"
	df = h2o.import_file(path=path)

	# replace a single numerical datum
	df[14,2] = 2

	# replace a single categorical datum
	# unimplemented as of 3.6.0.8 (tibshirani)

	# replace a whole column
	df[0] = 3*df[0]

	# replace by row mask
	df[df["sepal_len"] < 4.4, "sepal_len"] = 22  # BUG: https://0xdata.atlassian.net/browse/PUBDEV-2520

	# replacement with ifelse
	df["sepal_len"] = (df["sepal_len"] < 4.4).ifelse(22, df["sepal_len"])

	# replace missing values with 0
	df[df["sepal_len"].isna(), "sepal_len"] <- 0

	# alternative with ifelse
	df["sepal_len"] <- (df["sepal_len"].isna()).ifelse(0, df["sepal_len"])  
	# note the parantheses!








