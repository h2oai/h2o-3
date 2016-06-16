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

There are several ways to load data from one machine into the machine that is running H2O. 

.. example-code::
   .. code-block:: h2o-r
	
	 # To import small iris data file from H2O’s package:
	 library(h2o)
	 h2o.init()
	 irisPath = system.file("extdata", "iris.csv", package="h2o")
	 iris.hex = h2o.importFile(path = irisPath, destination_frame = iris.hex")
 	
	 # To import an entire folder of files as one data object:
	 library(h2o)
	 h2o.init()
	 pathToFolder = "/Users/data/airlines/"
	 airlines.hex = h2o.importFile(path = pathToFolder, destination_frame = "airlines.hex")
	  
	 # To import from HDFS:
	 library(h2o)
	 h2o.init()
	 airlinesURL = "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv" 
	 airlines.hex = h2o.importFile(path = airlinesURL, destination_frame = "airlines.hex")
	  
   .. code-block:: h2o-python
   
	  import h2o
	  h2o.init()
	  path = "/Users/data/iris/allyears2k.csv"
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

You can merge a column from to data frames that share a common column name. 
Note that in order for a merge to work in multinode clusters, one of the datasets must be small enough to exist in every node.  


.. example-code::
   .. code-block:: h2o-r
   
	# Currently, this function only supports `all.x = TRUE`. All other permutations will fail.
	
	 h2o.init()
	 left <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','blueberry'),
	 color = c('red','orange','yellow','yellow','red','blue'))
	 right <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','watermelon'),
	 citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
	 l.hex <- as.h2o(left)
	 r.hex <- as.h2o(right)
	 left.hex <- h2o.merge(l.hex, r.hex, all.x = TRUE)

   .. code-block:: h2o-python
   
	 # Combine the 'n' column from two datasets 
	 
	 h2o.init()
	 df10 = h2o.H2OFrame.from_python({'A':['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'], 'n': [0,1,2,3,4,5]})
	 df11 = h2o.H2OFrame.from_python(np.random.randint(0,10,size=100.tolist9), column_names=['n'])
	 df11.merge(df10)
	 
	 # The output is as follows:
	 
	    n      A
	 0  7    NaN
	 1  3     To
	 2  0  Hello
	 3  9    NaN
	 4  9    NaN
	 5  3     To
	 6  4    H2O
	 7  4    H2O
	 8  5  World
	 9  4    H2O
	 

Slicing Columns
---------------

H2O lazily slices out columns of data and will only materialize a shared copy upon some type of triggering IO. This example shows how to slice columns from a frame of data.

.. example-code::
   .. code-block:: h2o-r
	
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
   .. code-block:: h2o-r
   
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
   .. code-block:: h2o-r
   
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








