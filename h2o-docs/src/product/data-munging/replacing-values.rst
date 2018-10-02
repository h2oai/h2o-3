Replacing Values in a Frame
---------------------------

This example shows how to replace numeric values in a frame of data. Note that it is currently not possible to replace categorical value in a column.

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # Upload the iris dataset
    path <- "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
    df <- h2o.importFile(path)

    # Replace a single numerical datum. Note that columns and rows start at 1,
    # so in the example below, the value in the 14th row and 2nd column will be set to 2.0.
    df[14,2] <- 2.0

    # Replace a whole column. The example below multiplies all values in the second column by 3. 
    df[,1] <- 3*df[,1]

    # Replace by row mask. The example below searches for value less than 4.4 in the 
    # sepal_len column and replaces those values with 4.6. 
    df[df[,"sepal_len"] <- 4.4, "sepal_len"] <- 4.6

    # Replace using ifelse. Similar to the previous example, 
    # this replaces values less than 4.6 with 4.6. 
    df[,"sepal_len"] <- h2o.ifelse(df[,"sepal_len"] < 4.4, 4.6, df[,"sepal_len"])

    # Replace missing values with 0 
    df[is.na(df[,"sepal_len"]), "sepal_len"] <- 0

    # Alternative with ifelse 
    df[,"sepal_len"] <- h2o.ifelse(is.na(df[,"sepal_len"]), 0, df[,"sepal_len"])

   .. code-block:: python

    import h2o
    h2o.init()
    path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
    df = h2o.import_file(path=path)

    # Replace a single numerical datum. Note that columns and rows start at 0.
    # so in the example below, the value in the 15th row and 3rd column will be set to 2.0.
    df[14,2] = 2.0

    # Replace a whole column. The example below multiplies all values in the first column by 3.
    df[0] = 3*df[0]

    # Replace by row mask. The example below searches for value less than 4.6 in the 
    # sepal_len column and replaces those values with 4.6.
    df[df["sepal_len"] < 4.6, "sepal_len"] = 4.6

    # Replace using ifelse. Similar to the previous example, this replaces values less than 4.6 with 4.6. 
    df["sepal_len"] = (df["sepal_len"] < 4.6).ifelse(4.6, df["sepal_len"])

    # Replace missing values with 0.
    df[df["sepal_len"].isna(), "sepal_len"] = 0

    # Alternative with ifelse. Note the parantheses. 
    df["sepal_len"] = (df["sepal_len"].isna()).ifelse(0, df["sepal_len"])  
