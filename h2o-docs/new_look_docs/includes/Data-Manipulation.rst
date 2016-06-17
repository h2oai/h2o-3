Data Manipulation
=================

This section provides examples of common tasks performed when preparing data for machine learning. 

-  `Importing Data`_
-  `Uploading Data`_
-  `Merging Two Data Frames`_
-  `Slicing Columns`_
-  `Slicing Rows`_
-  `Replacing Values in a Frame`_
-  `Splitting Datasets into Training/Testing/Validating`_


Importing Data
--------------

There are several ways to load data from one machine into the machine that is running H2O. 

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
   
	# Import a local file:
	>>> import h2o
	>>> h2o.init()
	>>> airlines = "../../../smalldata/allyears2k.csv"
	>>> airlines_df = h2o.import_file(path=airlines)
	
	# Import a file from HDFS:
	>>> import h2o
	>>> h2o.init()
	>>> prostate = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv"
	>>> prostate_df = h2o.import_file(path=prostate)



Uploading Data
--------------

Run the following command to load data that is currently on the same machine that is running H2O. 

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


Merging Two Data Frames
-----------------------

You can merge a column from to data frames that share a common column name. 
Note that in order for a merge to work in multinode clusters, one of the datasets must be small enough to exist in every node.  


.. example-code::
   .. code-block:: h2o-r
   
	# Currently, this function only supports `all.x = TRUE`. All other permutations will fail.
	> h2o.init()
	> left <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','blueberry'), color = c('red','orange','yellow','yellow','red','blue'))
	> right <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','watermelon'), citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
	> l.hex <- as.h2o(left)
	> r.hex <- as.h2o(right)
	> left.hex <- h2o.merge(l.hex, r.hex, all.x = TRUE)

   .. code-block:: h2o-python
   
	# Combine the 'n' column from two datasets 
	>>> h2o.init()
	>>> import numpy as np
	>>> df1 = h2o.H2OFrame.from_python({'A':['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'], 'n': [0,1,2,3,4,5]})
	>>> df2 = h2o.H2OFrame.from_python([[x] for x in np.random.randint(0, 10, size=100).tolist()], column_names=['n'])
	>>> df3 = df2.merge(df1)


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






