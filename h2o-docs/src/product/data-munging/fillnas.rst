Fill NA values
==============

Use this function to fill in NA values in a sequential manner up to a specified limit. When using this function, you will specify the following: 

- Whether the method to fill the NA values should go forward (default) or backward. 
- Whether the NA values should be filled along rows (default) or columns. 
- The maximum number of consecutive NA values to fill (defaults to 1). 

.. tabs::
   .. code-tab:: python

       import h2o
       h2o.init()

       # Create a random data frame with 10 rows and 3 columns. 
       # Specify that no more than 20% of the values are NAs.
       df = h2o.create_frame(rows=10, 
                             cols=3, 
                             real_fraction=1.0, 
                             real_range=100, 
                             missing_fraction=0.2, 
                             seed=123)
       df
             C1        C2        C3
       --------  --------  --------
       nan       nan       -77.1047
       -93.6409  -13.6593   57.4439
       -93.71     25.4342   39.1013
       -95.8291  -92.4271   55.4314
        84.6372  -43.4759   53.1715
       -57.9583   27.4148  -26.9013
        83.0921  -62.7819  -91.9426
       -77.9814   64.3228  -93.954
       nan       -80.6142  nan
        27.1672   60.5492  -13.2275

       [10 rows x 3 columns]

       # Forward fill a row. In Python, the values for axis are 0 (row-wise) and 1 (column-wise)
       filled = df.fillna(method="forward",axis=0,maxlen=1)
       filled

       filled
             C1        C2        C3
       --------  --------  --------
       nan       nan       -77.1047
       -93.6409  -13.6593   57.4439
       -93.71     25.4342   39.1013
       -95.8291  -92.4271   55.4314
        84.6372  -43.4759   53.1715
       -57.9583   27.4148  -26.9013
        83.0921  -62.7819  -91.9426
       -77.9814   64.3228  -93.954
       -77.9814  -80.6142  -93.954
       27.1672   60.5492  -13.2275

       [10 rows x 3 columns]

   .. code-tab:: r R

       library(h2o)
       h2o.init()

       # Create a random data frame with 6 rows and 2 columns. 
       # Specify that no more than 70% of the values are NAs.
       fr_with_nas = h2o.createFrame(categorical_fraction = 0.0,
                                     missing_fraction = 0.7,
                                     rows = 6,
                                     cols = 2,
                                     seed = 123)
       fr_with_nas
                C1        C2
       1       NaN       NaN
       2 -77.10471 -93.64087
       3 -13.65926  57.44389
       4       NaN       NaN
       5  39.10130       NaN
       6       NaN  55.43136

       [6 rows x 2 columns]

       # Forward fill a row. In R, the values for axis are 1 (row-wise) and 2 (column-wise)
       fr <- h2o.fillna(fr_with_nas, "forward", axis = 1, maxlen = 1L)
       fr
                C1        C2
       1       NaN       NaN
       2 -77.10471 -93.64087
       3 -13.65926  57.44389
       4       NaN       NaN
       5  39.10130  39.10130
       6       NaN  55.43136

       [6 rows x 2 columns] 
