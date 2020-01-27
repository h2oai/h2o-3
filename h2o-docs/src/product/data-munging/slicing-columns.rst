Slicing Columns
---------------

H2O lazily slices out columns of data and will only materialize a shared copy upon some type of triggering IO. This example shows how to slice columns from a frame of data.

.. example-code::
   .. code-block:: r
	
	library(h2o)
	h2o.init()

	# Import the iris with headers dataset
	path <- "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
	df <- h2o.importFile(path)
	print(df)
	  sepal_len sepal_wid petal_len petal_wid       class
	1       5.1       3.5       1.4       0.2 Iris-setosa
	2       4.9       3.0       1.4       0.2 Iris-setosa
	3       4.7       3.2       1.3       0.2 Iris-setosa
	4       4.6       3.1       1.5       0.2 Iris-setosa
	5       5.0       3.6       1.4       0.2 Iris-setosa
	6       5.4       3.9       1.7       0.4 Iris-setosa

	[150 rows x 5 columns] 

	# Slice 1 column by index
	c1 <- df[,1]
	print(c1)
	  sepal_len
	1       5.1
	2       4.9
	3       4.7
	4       4.6
	5       5.0
	6       5.4

	[150 rows x 1 column] 
	  
	# Slice 1 column by name
	c1_1 <- df[, "petal_len"]
	print(c1_1)
	  petal_len
	1       1.4
	2       1.4
	3       1.3
	4       1.5
	5       1.4
	6       1.7

	[150 rows x 1 column] 
 	  
	# Slice cols by vector of indexes
	cols <- df[, 1:4]
	print(cols)
	  sepal_len sepal_wid petal_len petal_wid
	1       5.1       3.5       1.4       0.2
	2       4.9       3.0       1.4       0.2
	3       4.7       3.2       1.3       0.2
	4       4.6       3.1       1.5       0.2
	5       5.0       3.6       1.4       0.2
	6       5.4       3.9       1.7       0.4

	[150 rows x 4 columns] 

	# Slice cols by vector of names
	cols_1 <- df[, c("sepal_len", "sepal_wid", "petal_len", "petal_wid")]
	print(cols_1)
	  sepal_len sepal_wid petal_len petal_wid
	1       5.1       3.5       1.4       0.2
	2       4.9       3.0       1.4       0.2
	3       4.7       3.2       1.3       0.2
	4       4.6       3.1       1.5       0.2
	5       5.0       3.6       1.4       0.2
	6       5.4       3.9       1.7       0.4

	[150 rows x 4 columns] 

   .. code-block:: python

    import h2o
    h2o.init()

    # Import the iris with headers dataset
    path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
    df = h2o.import_file(path=path)

    # Slice a column by index. The resulting dataset will include the first (left-most) 
    # colum of the original dataset. 
    c1 = df[:,0]
    c1.describe
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

    # Slice 1 column by name. The resulting dataset will include only the sepal_len column
    # from the original dataset. 
    c1_1 = df[:, "sepal_len"]
    c1_1.describe
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

    [150 rows x 1 column[]

    # Slice columns by list of indexes. The resulting dataset will include the first three 
    # columns from the original dataset. 
    cols = df[:, range(3)]
    cols.describe
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
    cols_1 = df[:, ["sepal_wid", "petal_len", "petal_wid"]]
    cols_1 
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
