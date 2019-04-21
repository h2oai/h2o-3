Imputing Data
-------------

The impute function allows you to perform in-place imputation by filling missing values with aggregates computed on the "na.rm’d" vector. Additionally, you can also perform imputation based on groupings of columns from within the dataset. These columns can be passed by index or by column name to the ``by`` parameter. Note that if a factor column is supplied, then the method must be ``mode``.

The ``impute`` function accepts the following arguments:

- ``dataset``: The dataset containing the column to impute
- ``column``: A specific column to impute. The default of ``0`` specifies to impute the entire frame.
- ``method``: The type of imputation to perform. ``mean`` replaces NAs with the column mean; ``median`` replaces NAs with the column median; ``mode`` replaces with the most common factor (for factor columns only).
- ``combine_method``: If method is ``median``, then choose how to combine quantiles on even sample sizes. This parameter is ignored in all other cases. Available options for ``combine_method`` include ``interpolate``, ``average``, ``low``, and ``high``. 
- ``by``: Group by columns
- ``groupByFrame`` or ``group_by_frame``: Impute the column with this pre-computed grouped frame.
- ``values``:  A vector of impute values (one per column). NaN indicates to skip the column.

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

   	# Upload the Airlines dataset
   	filePath <- "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv"
   	air <- h2o.importFile(filePath, "air")
   	print(dim(air))
   	43978    31

   	# Show the number of rows with NA.
   	print(numNAs <- sum(is.na(air$DepTime)))
   	[1] 1086

   	DepTime_mean <- mean(air$DepTime, na.rm = TRUE)
   	print(DepTime_mean)
   	[1] 1345.847

   	# Mean impute the DepTime column
   	h2o.impute(air, "DepTime", method = "mean")
   	 [1]     NaN      NaN      NaN      NaN 1345.847      NaN      NaN      NaN
	 [9]     NaN      NaN      NaN      NaN      NaN      NaN      NaN      NaN
	[17]     NaN      NaN      NaN      NaN      NaN      NaN      NaN      NaN
	[25]     NaN      NaN      NaN      NaN      NaN      NaN      NaN

	# Revert the imputations
	air <- h2o.importFile(filePath, "air")

	# Impute the column using a grouping based on the Origin and Distance
	# If the Origin and Distance produce groupings of NAs, then no imputation will be done (NAs will result).
	h2o.impute(air, "DepTime", method = "mean", by = c("Dest"))
	  Dest mean_DepTime
	1  ABE     1671.795
	2  ABQ     1308.074
	3  ACY     1651.095
	4  ALB     1405.412
	5  AMA     1404.333
	6  ANC     2022.000

	[134 rows x 2 columns]

	# Revert the imputations
	air <- h2o.importFile(filePath, "air")

	# Impute a factor column by the most common factor in that column
	h2o.impute(air, "TailNum", method = "mode")
	 [1]  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN 3499  NaN  NaN  NaN  NaN
	[16]  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN  NaN
	[31]  NaN

	# Revert imputations
	air <- h2o.importFile(filePath, "air")

	# Impute a factor column using a grouping based on the Month
	h2o.impute(air, "TailNum", method = "mode", by=c("Month"))
	  Month mode_TailNum
	1     1         3499
	2    10         3499

   .. code-block:: python

    import h2o
    h2o.init()

    # Import the airlines dataset
    air_path = "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv"
    air = h2o.import_file(path=air_path)
    air.dim
    [43978, 31]

    # Mean impute the DepTime column based on the Origin and Distance columns
    DeptTime_impute = air.impute("DepTime", method = "mean", by = ["Origin", "Distance"])
    DeptTime_impute
    Origin      Distance    mean_DepTime
    --------  ----------  --------------
    ABE              253         1149.7
    ABE              481          812
    ABQ              223         1229.33
    ABQ              277         1565
    ABQ              289         1529
    ABQ              321         1267.06
    ABQ              328         1301.85
    ABQ              332         1655
    ABQ              349          813.28
    ABQ              487         1536.14

    [1497 rows x 3 columns]

    # Revert imputations
    air = h2o.import_file(path=air_path)

    # Mode impute the TailNum column
    mode_impute = air.impute("TailNum", method = "mode")
    mode_impute
    [nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, 3499.0, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan, nan]

    # Revert imputations
    air = h2o.import_file(path=air_path)

    # Mode impute the TailNum column based on the Month and Year columns
    mode_impute = air.impute("TailNum", method = "mode", by=["Month", "Year"])
    mode_impute
    Year    Month    mode_TailNum
    ------  -------  --------------
      1987       10            3499
      1988        1            3499
      1989        1            3499
      1990        1            3499
      1991        1            3499
      1992        1            3499
      1993        1            3499
      1994        1            3499
      1995        1            3500
      1996        1             672

    [22 rows x 3 columns]

