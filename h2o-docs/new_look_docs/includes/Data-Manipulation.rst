Data Manipulation
=================

This section provides examples of common tasks performed when preparing data for machine learning. 

-  `Importing Data`_
-  `Uploading Data`_
-  `Merging Two Datasets`_
-  `Combining Rows from Two Datasets`_
-  `Combining Columns from Two Datasets`_
-  `Slicing Columns`_
-  `Slicing Rows`_
-  `Replacing Values in a Frame`_
-  `Splitting Datasets into Training/Testing/Validating`_


Importing Data
--------------

The import function is a parallelized reader and pulls information from the server from a location specified by the client. The path is a server-side path. This is a fast, scalable, highly optimized way to read data. H2O pulls the data from a data store and initiates the data transfer as a read operation.

.. example-code::
   .. code-block:: h2o-r
	
	# To import small iris data file from H2O’s package:
	> library(h2o)
	> h2o.init()
	> irisPath = system.file("extdata", "iris.csv", package="h2o")
	> iris.hex = h2o.importFile(path = irisPath, destination_frame = "iris.hex")
	  
	# To import from HDFS:
	> library(h2o)
	> h2o.init()
	> airlinesURL = "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv" 
	> airlines.hex = h2o.importFile(path = airlinesURL, destination_frame = "airlines.hex")
	  
   .. code-block:: h2o-python

	# Import a file from HDFS:
	>>> import h2o
	>>> h2o.init()
	>>> prostate = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv"
	>>> prostate_df = h2o.import_file(path=prostate)


Uploading Data
--------------

Unlike the import function, which is a parallelized reader, the upload function is a push from the client to the server. The specified path must be a client-side path. This is not scalable and is only intended for smaller data sizes. The client pushes the data from a local filesystem (for example on your laptop where R or Python is running) to H2O. For big-data operations, you don't want the data stored on or flowing through the client.

Run the following command to load data that resides on the same machine that is running H2O. 

.. example-code::
   .. code-block:: h2o-r
	
	> library(h2o)
	> h2o.init()
	> irisPath = "../../../smalldata/iris/iris_wheader.csv"
	> iris.hex = h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
	  
   .. code-block:: h2o-python
   
	 >>> import h2o
	 >>> h2o.init()
	 >>> df = h2o.upload_file("../smalldata/iris/iris_wheader.csv")


Merging Two Datasets
-----------------------

You can use the Merge function to combine two datasets that share a common column name. By default, all columns in common are used as the merge key; uncommon will be ignored. Also, if you want to use only a subset of the columns in common, rename the other columns so the columns are unique in the merged result.

Note that in order for a merge to work in multinode clusters, one of the datasets must be small enough to exist in every node.  


.. example-code::
   .. code-block:: h2o-r
   
	# Currently, this function only supports `all.x = TRUE`. All other permutations will fail.
	> library(h2o)
	> h2o.init()
	
	# Create two simple, two-column R data frames by inputting values, ensuring that both have a common column (in this case, "fruit").
	> left <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','blueberry'), color = c('red','orange','yellow','yellow','red','blue'))
	> right <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','watermelon'), citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
	
	# Create the H2O data frames from the inputted data.
	> l.hex <- as.h2o(left)
	> print(l.hex)
	        fruit  color
	 1      apple    red
	 2     orange orange
	 3     banana yellow
	 4      lemon yellow
	 5 strawberry    red
	 6  blueberry   blue
	
	[6 rows x 2 columns]
	
	> r.hex <- as.h2o(right)
	> print(r.hex)
	        fruit  color
	 1      apple  FALSE
	 2     orange   TRUE
	 3     banana  FALSE
	 4      lemon   TRUE
	 5 strawberry  FALSE
	 6 watermelon  FALSE

	[6 rows x 2 columns]
	
	# Merge the data frames. The result is a single dataset with three columns.
	> left.hex <- h2o.merge(l.hex, r.hex, all.x = TRUE)
	> print(left.hex)
    		fruit citrus  color
	 1      apple  FALSE    red
	 2     orange   TRUE orange
	 3     banana  FALSE yellow
	 4      lemon   TRUE yellow
	 5 strawberry  FALSE    red
	 6 watermelon  FALSE   <NA>
	
	[6 rows x 3 columns] 
   
   .. code-block:: h2o-python
   
	>>> h2o.init()
	>>> import h2o
	>>> import numpy as np
	
	# Create a dataset by inputting raw data. 
	>>> df1 = h2o.H2OFrame.from_python({'A':['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'], 'n': [0,1,2,3,4,5]})
	>>> df1.describe
	A          n
	-------  ---
	Hello      0
	World      1
	Welcome    2
	To         3
	H2O        4
	World      5
	
	[6 rows x 2 columns]
	
	# Generate a random dataset from python. 
	>>> df2 = h2o.H2OFrame.from_python([[x] for x in np.random.randint(0, 10, size=20).tolist()], column_names=['n'])
	>>> df2.describe
	  n
	---
	nan
	  0
	  8
	  6
	  1
	  7
	  8
	  5
	  1
	  3
	  
	[21 rows x 1 column]
	
	# Merge the first dataset into the second dataset. Note that only columns in common are merged (i.e, values in df2 greater than 5 will not be merged).
	>>> df3 = df2.merge(df1)
	>>> df3.describe
	  n  A
	---  -------
	nan  Hello
	  3  To
	  3  To
	  0  Hello
	  5  World
	  3  To
	  0  Hello
	  5  World
	  1  World
	  2  Welcome
	  
	[14 rows x 2 columns]
	
	# Merge all of df2 into df1. Note that this will result in missing values for column A, which does not include values greater than 5.
	>>> df4 = df2.merge(df1, all_x=True)
	>>> df4.describe
	  n  A
	---  -----
	nan  Hello
	  0  Hello
	  8
	  6
	  1  World
	  7
	  8
	  5  World
	  1  World
	  3  To
	
	[21 rows x 2 columns]
	

Combining Rows from Two Datasets
--------------------------------

You can use the ``rbind`` function to combine two similar datasets into a single large dataset. This can be used, for example, to create a larger dataset by combining data from a validation dataset with its training or testing dataset.

Note that when using ``rbind``, the two datasets must have the same set of columns.

.. example-code::
   .. code-block:: h2o-r
   
	> library(h2o)
	> h2o.init()
	
	# Import exsiting training and testing datasets
	> ecg1Path = "../../../smalldata/anomaly/ecg_discord_train.csv"
	> ecg1.hex = h2o.importFile(path=ecg1Path, destination_frame="ecg1.hex")
	> ecg2Path = "../../../smalldata/anomaly/ecg_discord_test.csv"
	> ecg2.hex = h2o.importFile(path=ecg2Path, destination_frame="ecg2.hex")

	# Combine the two datasets into a single, larger dataset
	> ecgCombine.hex <- h2o.rbind(ecg1.hex, ecg2.hex)

   .. code-block:: h2o-python

	>>> import h2o
	>>> import numpy as np
	>>> h2o.init()
	
	# Generate a random dataset with 100 rows 4 columns. Label the columns A, B, C, and D.
	>>> df1 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))
	>>> df1.describe
	          A           B            C            D
	-----------  ----------  -----------  -----------
	nan          nan         nan          nan
	 -0.148045     0.516651   -0.218871    -2.11336
	  0.818191    -1.07749    -0.303827     0.0234708
	 -0.894042    -1.83727     1.69621     -0.306524
	 -1.90056      0.528147   -0.745829     0.325673
	 -1.14653      0.146565   -1.12463     -1.39162
	  0.81608      0.21313    -0.122169     1.47247
	  0.419028     1.14975     0.913349     0.975779
	  0.419134    -1.63199     0.633799     0.482761
	  0.0366856   -1.09199    -0.0831492    2.17306
	
	[101 rows x 4 columns]
	
	# Generate a second random dataset with 100 rows and 4 columns. Again, label the columns, A, B, C, and D.
	>>> df2 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))
	>>> df2.describe
	          A            B           C           D
	-----------  -----------  ----------  ----------
	nan          nan          nan         nan
	  0.626459    -1.80634     -1.08245     1.29828
	  1.31526     -0.223264     0.172243   -0.76666
	  1.70095     -0.666482    -0.486086   -1.16518
	 -0.241271    -1.08439      1.75451     1.37618
	 -0.151067    -0.830386     0.7113     -0.979204
	 -2.18042     -1.85949     -0.466211    0.707786
	 -0.0657297   -0.0092001    1.3721     -0.570298
	  1.59816     -0.149408    -0.874023   -0.883033
	 -0.367047    -0.586965    -0.98553    -1.33043
	
	[101 rows x 4 columns]
	
	# Bind the rows from the second dataset into the first dataset.
	>>> df1.rbind(df2)
	>>> df1.describe
          	A           B            C            D
	-----------  ----------  -----------  -----------
	nan          nan         nan          nan
	 -0.148045     0.516651   -0.218871    -2.11336
	  0.818191    -1.07749    -0.303827     0.0234708
	 -0.894042    -1.83727     1.69621     -0.306524
	 -1.90056      0.528147   -0.745829     0.325673
	 -1.14653      0.146565   -1.12463     -1.39162
	  0.81608      0.21313    -0.122169     1.47247
	  0.419028     1.14975     0.913349     0.975779
	  0.419134    -1.63199     0.633799     0.482761
	  0.0366856   -1.09199    -0.0831492    2.17306
	
	[202 rows x 4 columns]


Combining Columns from Two Datasets
-----------------------------------

The ``cbind`` function allows you to combine datasets by adding columns from one dataset into another. Note that when using ``cbind``, the two datasets must have the same number of rows. In addition, if the datasets contain common column names, H2O will append the joined column with ``0``. 

.. example-code::
   .. code-block:: h2o-r
	
	> library(h2o)
	> h2o.init()
	
	# Create two simple, two-column R data frames by inputting values, ensuring that both have a common column (in this case, "fruit").
	> left <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','blueberry'), color = c('red','orange','yellow','yellow','red','blue'))
	> right <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','watermelon'), citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
	
	# Create the H2O data frames from the inputted data.
	> l.hex <- as.h2o(left)
	> print(l.hex)
	        fruit  color
	 1      apple    red
	 2     orange orange
	 3     banana yellow
	 4      lemon yellow
	 5 strawberry    red
	 6  blueberry   blue
	
	[6 rows x 2 columns]
	
	> r.hex <- as.h2o(right)
	> print(r.hex)
	        fruit  color
	 1      apple  FALSE
	 2     orange   TRUE
	 3     banana  FALSE
	 4      lemon   TRUE
	 5 strawberry  FALSE
	 6 watermelon  FALSE

	[6 rows x 2 columns]

	# Combine the l.hex and r.hex datasets into a single dataset. 
	#The columns from r.hex will be appended to the right side of the final dataset. In addition, because both datasets include a "fruit" column, H2O will append the second "fruit" column name with "0". 
	#Note that this is different than ``merge``, which combines data from two commonly named columns in two datasets. 
	
	> columns.hex <- h2o.cbind(l.hex, r.hex)
	> print(columns.hex)
	       fruit  color     fruit0 citrus
	1      apple    red      apple  FALSE
	2     orange orange     orange   TRUE
	3     banana yellow     banana  FALSE
	4      lemon yellow      lemon   TRUE
	5 strawberry    red strawberry  FALSE
	6  blueberry   blue watermelon  FALSE
	
	[6 rows x 4 columns]

		
   .. code-block:: h2o-python
   
	>>> import h2o
	>>> h2o.init()
	>>> import numpy as np
	
	# Generate a random dataset with 10 rows 4 columns. Label the columns A, B, C, and D.
	>>> cols1_df = h2o.H2OFrame.from_python(np.random.randn(10,4).tolist(), column_names=list('ABCD'))
	>>> cols1_df.describe
	         A           B           C           D
	----------  ----------  ----------  ----------
	nan         nan         nan         nan
	 -0.372305   -0.744047   -1.89198    -0.66457
	  0.18704     0.176037    0.38628    -1.55655
	 -1.19211     0.579382    1.99508     1.13262
	  0.144151    1.39129    -1.01831    -0.678329
	  0.660908   -0.276543    0.366156    0.861158
	 -0.373436    0.280039   -0.312323    1.59981
	  0.257874    3.93677    -0.681923    0.335323
	  0.193658   -1.20955    -1.57454    -0.825441
	  0.961897    0.194851    0.807101   -1.56672
	
	[11 rows x 4 columns]
	
	# Generate a second random dataset with 10 rows and 1 column. Label the columns, Y and D.
	>>> cols2_df = h2o.H2OFrame.from_python(np.random.randn(10,4).tolist(), column_names=list('YZ'))
	>>> cols2_df.describe
         	  Y            Z
	------------  -----------
	nan           nan
	  0.00313617   -0.171366
	 -1.14186       0.932378
	  0.251192     -0.384113
	  0.603271     -0.275116
	 -0.435936     -0.284039
	 -1.13324      -0.163877
	 -0.0475909    -2.65027
	  1.49039      -0.0887757
	  0.906927     -1.12668
	
	[11 rows x 2 columns]

	# Add the columns from the second dataset into the first. H2O will append these as the right-most columns.
	>>> colsCombine_df = cols1_df.cbind(cols2_df)
	>>> colsCombine_df.describe
         	A           B           C           D             Y            Z
	----------  ----------  ----------  ----------  ------------  -----------
	nan         nan         nan         nan         nan           nan
	 -0.372305   -0.744047   -1.89198    -0.66457     0.00313617   -0.171366
	  0.18704     0.176037    0.38628    -1.55655    -1.14186       0.932378
	 -1.19211     0.579382    1.99508     1.13262     0.251192     -0.384113
	  0.144151    1.39129    -1.01831    -0.678329    0.603271     -0.275116
	  0.660908   -0.276543    0.366156    0.861158   -0.435936     -0.284039
	 -0.373436    0.280039   -0.312323    1.59981    -1.13324      -0.163877
	  0.257874    3.93677    -0.681923    0.335323   -0.0475909    -2.65027
	  0.193658   -1.20955    -1.57454    -0.825441    1.49039      -0.0887757
	  0.961897    0.194851    0.807101   -1.56672     0.906927     -1.12668
	

Slicing Columns
---------------

H2O lazily slices out columns of data and will only materialize a shared copy upon some type of triggering IO. This example shows how to slice columns from a frame of data.

.. example-code::
   .. code-block:: h2o-r
	
	> library(h2o)
	> h2o.init()
	> path <- "data/iris/iris_wheader.csv"
	> df <- h2o.importFile(path)

	# slice 1 column by index
	> c1 <- df[,1]
	  
	# slice 1 column by name
	> c1_1 <- df[, "sepal_len"]
 	  
	# slice cols by vector of indexes
	> cols <- df[, 1:4]
	  
	# slice cols by vector of names
	> cols_1 <- df[, c("sepal_len", "sepal_wid", "petal_len", "petal_wid")]

   .. code-block:: h2o-python
   
	>>> import h2o
	>>> h2o.init()
	
	# Import the iris with headers dataset
	>>> path = "data/iris/iris_wheader.csv"
	>>> df = h2o.import_file(path=path)
	>>> df.describe
	  sepal_len    sepal_wid    petal_len    petal_wid  class
	-----------  -----------  -----------  -----------  -----------
    		5.1          3.5          1.4          0.2  Iris-setosa
        	4.9          3            1.4          0.2  Iris-setosa
	    	4.7          3.2          1.3          0.2  Iris-setosa
        	4.6          3.1          1.5          0.2  Iris-setosa
	        5            3.6          1.4          0.2  Iris-setosa
	        5.4          3.9          1.7          0.4  Iris-setosa
        	4.6          3.4          1.4          0.3  Iris-setosa
        	5            3.4          1.5          0.2  Iris-setosa
	        4.4          2.9          1.4          0.2  Iris-setosa
        	4.9          3.1          1.5          0.1  Iris-setosa

	[150 rows x 5 columns]

	# Slice a column by index. The resulting dataset will include the first (left-most) colum of the original dataset. 
	>>> c1 = df[:,0]
	>>> c1.describe
	  sepal_len
	-----------
          	5.1
       		4.9
	        4.7
        	4.6
	        5
        	5.4
	        4.6
	        5
	        4.4
	        4.9

	[150 rows x 1 column]

	# Slice 1 column by name. The resulting dataset will include only the sepal_len column from the original dataset. 
	>>> c1_1 = df[:, "sepal_len"]
	>>> c1_1.describe
	  sepal_len
	-----------
        	5.1
       		4.9
	        4.7
        	4.6
	        5
         	5.4
	        4.6
	        5
	        4.4
	        4.9

	[150 rows x 1 column]	

	# Slice columns by list of indexes. The resulting dataset will include the first three columns from the original dataset. 
	>>> cols = df[:, range(3)]
	>>> cols.describe
	  sepal_len    sepal_wid    petal_len
	-----------  -----------  -----------
        	5.1          3.5          1.4
	        4.9          3            1.4
	        4.7          3.2          1.3
	        4.6          3.1          1.5
	        5            3.6          1.4
	        5.4          3.9          1.7
	        4.6          3.4          1.4
	        5            3.4          1.5
	        4.4          2.9          1.4
	        4.9          3.1          1.5
	
	[150 rows x 3 columns]


	# Slice cols by a list of names.
	>>> cols_1 = df[:, ["sepal_wid", "petal_len", "petal_wid"]]
	>>> cols_1 
	  sepal_wid    petal_len    petal_wid
	-----------  -----------  -----------
        	3.5          1.4          0.2
	        3            1.4          0.2
	        3.2          1.3          0.2
	        3.1          1.5          0.2
	        3.6          1.4          0.2
	        3.9          1.7          0.4
	        3.4          1.4          0.3
	        3.4          1.5          0.2
	        2.9          1.4          0.2
	        3.1          1.5          0.1
	
	[150 rows x 3 columns]
	

Slicing Rows
------------

H2O lazily slices out rows of data and will only materialize a shared copy upon IO. This example shows how to slice rows from a frame of data.

.. example-code::
   .. code-block:: h2o-r
   
	> library(h2o)
	> path <- "data/iris/iris_wheader.csv"
	> h2o.init()
	> df <- h2o.importFile(path)

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

   .. code-block:: h2o-python

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

Replacing Values in a Frame
-------------------------

This example shows how to replace numeric values in a frame of data. Note that it is currently not possible to replace categorical value in a column.    

.. example-code::
   .. code-block:: h2o-r
   
	> library(h2o)
	> path <- "data/iris/iris_wheader.csv"
	> h2o.init()
	> df <- h2o.importFile(path)

	# Replace a single numerical datum. Note that columns and rows start at 0, so in the example below, the value in the 15th row and 3rd column will be set to 2.0.
	> df[14,2] <- 2.0

	# Replace a whole column. The example below multiplies all values in the second column by 3.
	> df[,1] <- 3*df[,1]

	# Replace by row mask. The example below searches for value less than 4.4 in the sepal_len column and replaces those values with 4.6.
	> df[df[,"sepal_len"] < 4.6, "sepal_len"] <- 4.6  
	
	# Replace using ifelse. Similar to the previous example, this replaces values less than 4.6 with 4.6.
	> df[,"sepal_len"] <- h2o.ifelse(df[,"sepal_len"] < 4.4, 4.6, df[,"sepal_len"])

	# replace missing values with 0
	> df[is.na(df[,"sepal_len"]), "sepal_len"] <- 0

	# alternative with ifelse
	> df[,"sepal_len"] <- h2o.ifelse(is.na(df[,"sepal_len"]), 0, df[,"sepal_len"])

   .. code-block:: h2o-python

	>>> import h2o
	>>> h2o.init()
	>>> path = "data/iris/iris_wheader.csv"
	>>> df = h2o.import_file(path=path)

	# Replace a single numerical datum. Note that columns and rows start at 0, so in the example below, the value in the 15th row and 3rd column will be set to 2.0.
	>>> df[14,2] = 2.0

	# Replace a whole column. The example below multiplies all values in the first column by 3.
	>>> df[0] = 3*df[0]

	# Replace by row mask. The example below searches for value less than 4.6 in the sepal_len column and replaces those values with 4.6.
	>>> df[df["sepal_len"] < 4.6, "sepal_len"] = 4.6

	# Replace using ifelse. Similar to the previous example, this replaces values less than 4.6 with 4.6. 
	>>> df["sepal_len"] = (df["sepal_len"] < 4.6).ifelse(4.6, df["sepal_len"])

	# Replace missing values with 0.
	>>> df[df["sepal_len"].isna(), "sepal_len"] = 0

	# Alternative with ifelse. Note the parantheses. 
	>>> df["sepal_len"] = (df["sepal_len"].isna()).ifelse(0, df["sepal_len"])  
	

Splitting Datasets into Training/Testing/Validating 
---------------------------------------------------

This example shows how to split a single dataset into two datasets, one used for training and the other used for testing. 

.. example-code::
   .. code-block:: h2o-r
   
	> library(h2o)
	> h2o.init()
	
	# Import the prostate dataset
	> prostate.hex <- h2o.importFile(path = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv", destination_frame = "prostate.hex")
	
	# Split dataset giving the training dataset 75% of the data
	> prostate.split <- h2o.splitFrame(data=prostate.hex, ratios=0.75)
	
	# Create a training set from the 1st dataset in the split
	> prostate.train <- prostate.split[[1]]
	
	# Create a testing set from the 2nd dataset in the split
	> prostate.test <- prostate.split[[2]]
	
	# Generate a GLM model using the training dataset. x represesnts the predictor column, and y represents the target index.
	> prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), training_frame=prostate.train, family="binomial", nfolds=10, alpha=0.5)
	
	# Predict using the GLM model and the testing dataset
	> pred = h2o.predict(object=prostate.glm, newdata=prostate.test)
	
	# View a summary of the prediction with a probability of TRUE
	> summary(pred$p1, exact_quantiles=TRUE)
	p1
	Min.   :0.2044
	1st Qu.:0.2946
	Median :0.3369
	Mean   :0.3928
	3rd Qu.:0.4258
	Max.   :0.9124 

   .. code-block:: h2o-python

	>>> import h2o
	>>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	>>> h2o.init()
	
	# Import the prostate dataset
	>>> prostate = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv"
	>>> prostate_df = h2o.import_file(path=prostate)
	
	# Split the data into Train/Test/Validation with Train having 70% and test and validation 15% each
	>>> train,test,valid = prostate_df.split_frame(ratios=(.7, .15))
	
	# Generate a GLM model using the training dataset
	>>> glm_classifier = H2OGeneralizedLinearEstimator(family="binomial", nfolds=10, alpha=0.5)
	>>> glm_classifier.train(y="CAPSULE", x=["AGE", "RACE", "PSA", "DCAPS"], training_frame=train)
	
	# Predict using the GLM model and the testing dataset
	>>> predict = glm_classifier.predict(test)
	
	# View a summary of the prediction
	>>> predict.head()
	  predict         p0        p1
	---------  ---------  --------
    		0  0.733779   0.266221
        	1  0.314968   0.685032
	        1  0.0899778  0.910022
	        1  0.146287   0.853713
	        1  0.648841   0.351159
	        0  0.83804    0.16196
	        1  0.623304   0.376696
	        1  0.597705   0.402295
	        0  0.757942   0.242058
	        1  0.654244   0.345756
	
	[10 rows x 3 columns]






