Slicing Rows
------------

H2O lazily slices out rows of data and will only materialize a shared copy upon IO. This example shows how to slice rows from a frame of data.

.. example-code::
   .. code-block:: r
   
    library(h2o)
    h2o.init()
    path <- "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
    df <- h2o.importFile(path)

    # Slice 1 row by index. 
    c1 <- df[15,]
    print(c1)
      sepal_len sepal_wid petal_len petal_wid       class
    1       5.8         4       1.2       0.2 Iris-setosa

    [1 row x 5 columns] 

    # Slice a range of rows.
    c1_1 <- df[25:49,]
    print(c1_1)
      sepal_len sepal_wid petal_len petal_wid       class
    1       4.8       3.4       1.9       0.2 Iris-setosa
    2       5.0       3.0       1.6       0.2 Iris-setosa
    3       5.0       3.4       1.6       0.4 Iris-setosa
    4       5.2       3.5       1.5       0.2 Iris-setosa
    5       5.2       3.4       1.4       0.2 Iris-setosa
    6       4.7       3.2       1.6       0.2 Iris-setosa

    [25 rows x 5 columns] 

    # Slice using a boolean mask. The output dataset will include rows with a sepal length less than 4.6.
    mask <- df[,"sepal_len"] < 4.6
    cols <- df[mask,]
    print(cols)
      sepal_len sepal_wid petal_len petal_wid       class
    1       4.4       2.9       1.4       0.2 Iris-setosa
    2       4.3       3.0       1.1       0.1 Iris-setosa
    3       4.4       3.0       1.3       0.2 Iris-setosa
    4       4.5       2.3       1.3       0.3 Iris-setosa
    5       4.4       3.2       1.3       0.2 Iris-setosa

    [5 rows x 5 columns] 

    # Filter out rows that contain missing values in a column. Note the use of '!' to perform a logical not.
    mask <- is.na(df[,"sepal_len"])
    cols <- df[!mask,]
    print(cols)
      sepal_len sepal_wid petal_len petal_wid       class
    1       5.1       3.5       1.4       0.2 Iris-setosa
    2       4.9       3.0       1.4       0.2 Iris-setosa
    3       4.7       3.2       1.3       0.2 Iris-setosa
    4       4.6       3.1       1.5       0.2 Iris-setosa
    5       5.0       3.6       1.4       0.2 Iris-setosa
    6       5.4       3.9       1.7       0.4 Iris-setosa

    [150 rows x 5 columns] 

   .. code-block:: python

    import h2o
    h2o.init()

    # Import the iris with headers dataset
    path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
    df = h2o.import_file(path=path)

    # Slice 1 row by index
    c1 = df[15,:]
    c1.describe

    # Slice a range of rows
    c1_1 = df[range(25,50,1),:]
    c1_1.describe

    # Slice using a boolean mask. The output dataset will include rows with a sepal length
    # less than 4.6.
    mask = df["sepal_len"] < 4.6
    cols = df[mask,:]
    cols.describe

    # Filter out rows that contain missing values in a column. Note the use of '~' to 
    # perform a logical not.
    mask = df["sepal_len"].isna()
    cols = df[~mask,:]
    cols.describe
     sepal_len   sepal_wid   petal_len    petal_wid  clas
    ----------  ----------  ----------  -----------  -----------
           5.1         3.5         1.4          0.2  Iris-setosa
           4.9         3           1.4          0.2  Iris-setosa
           4.7         3.2         1.3          0.2  Iris-setosa
           4.6         3.1         1.5          0.2  Iris-setosa
           5           3.6         1.4          0.2  Iris-setosa
           5.4         3.9         1.7          0.4  Iris-setosa
           4.6         3.4         1.4          0.3  Iris-setosa
           5           3.4         1.5          0.2  Iris-setosa
           4.4         2.9         1.4          0.2  Iris-setosa
           4.9         3.1         1.5          0.1  Iris-setosa



    [150 rows x 3 columns]


