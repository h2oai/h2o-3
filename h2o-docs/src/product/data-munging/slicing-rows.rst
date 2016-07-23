Slicing Rows
------------

H2O lazily slices out rows of data and will only materialize a shared copy upon IO. This example shows how to slice rows from a frame of data.

.. example-code::
   .. code-block:: r
   
	> library(h2o)
	> h2o.init(nthreads=-1)
	> df <- h2o.importFile(path)
	> path <- "data/iris/iris_wheader.csv"

	# Slice 1 row by index. 
	> c1 <- df[15,]

	# Slice a range of rows. 
	> c1_1 <- df[25:49,]

	# Slice using a boolean mask. The output dataset will include rows with a sepal length less than 4.6.
	> mask <- df[,"sepal_len"] < 4.6
	> cols <- df[mask,]

	# Filter out rows that contain missing values in a column. Note the use of '!' to perform a logical not.
	> mask <- is.na(df[,"sepal_len"])
	> cols <- df[!mask,]

   .. code-block:: python

	>>> import h2o
	>>> h2o.init()
	>>> path = "data/iris/iris_wheader.csv"
	>>> df = h2o.import_file(path=path)

	# Slice 1 row by index.
	>>> c1 = df[15,:]

	# Slice a range of rows.
	>>> c1_1 = df[range(25,50,1),:]

	# Slice using a boolean mask. The output dataset will include rows with a sepal length less than 4.6.  
	>>> mask = df["sepal_len"] < 4.6
	>>> cols = df[mask,:]
	>>> cols.describe
	  sepal_len    sepal_wid    petal_len    petal_wid  class
	-----------  -----------  -----------  -----------  -----------
	        4.4          2.9          1.4          0.2  Iris-setosa
	        4.3          3            1.1          0.1  Iris-setosa
	        4.4          3            1.3          0.2  Iris-setosa
	        4.5          2.3          1.3          0.3  Iris-setosa
	        4.4          3.2          1.3          0.2  Iris-setosa	

	# Filter out rows that contain missing values in a column. Note the use of '~' to perform a logical not.
	>>> mask = df["sepal_len"].isna()
	>>> cols = df[~mask,:]  

