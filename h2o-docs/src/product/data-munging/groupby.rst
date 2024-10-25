Group by
========

The ``group_by`` function lets you group one or more columns and apply a function to the result. Specifically, the ``group_by`` function performs the following actions on an H2O Frame:

1. Splits the data into groups based on some criteria.
2. Applies a function to each group independently.
3. Combines the results into an H2OFrame.

The result is a new H2OFrame with columns equivalent to the number of groups created. The returned groups are sorted by the natural group-by column sort.

Group by parameters
-------------------

The ``group_by`` function accepts the following parameters:

Python and R
~~~~~~~~~~~~

 - ``by``: The ``by`` option can take a list of columns if you want to group by more than one column to compute the summary. 
 - ``H2OFrame``: This specifies the H2OFrame that you want the group by operation to be performed on.

Python only
~~~~~~~~~~~

- ``na``: This option controls the treatment of NA values during the calculation. It can be one of:

  - ``all`` (default): Any NA values are used in the calculation as-is (which usually results in the final result being NA, too).
  - ``ignore``: NA entries are not included in calculations, but the total number of entries is taken as the total number of rows. For example, ``mean([1, 2, 3, nan], na="ignore")`` will produce ``1.5``.
  - ``rm``: NA entries are skipped during the calculations, reducing the total effective count of entries. For example, ``mean([1, 2, 3, nan], na="rm")`` will produce ``2``.

R only
~~~~~~

- ``gb.control``: In R, the ``gb.control`` option specifies how to handle NA values in the dataset as well as how to name output columns. Note that to specify a list of column names in the ``gb.control`` list, you must add the ``col.names`` argument. 
- ``na.methods``: This option controls the treatment of NA values during the calculation. It can be one of:

  - ``all`` (default): Any NA values are used in the calculation as-is (which usually results in the final result being NA, too).
  - ``ignore``: NA entries are not included in calculations, but the total number of entries is taken as the total number of rows. For example, ``mean([1, 2, 3, nan], na="ignore")`` will produce ``1.5``.
  - ``rm``: NA entries are skipped during the calculations, reducing the total effective count of entries. For example, ``mean([1, 2, 3, nan], na="rm")`` will produce ``2``.

- ``nrow``: Specify the name of the generated column.

.. note:: 
  
  If a list smaller than the number of columns groups is supplied, then the list will be padded by ``ignore``.

Aggregations
~~~~~~~~~~~~

In addition to the above parameters, any number of the following aggregations can be chained together in the ``group_by`` function: 

- ``count``: Count the number of rows in each group of a GroupBy object.
- ``max``: Calculate the maximum of each column specified in ``col`` for each group of a GroupBy object. 
- ``mean``: Calculate the mean of each column specified in ``col`` for each group of a GroupBy object. 
- ``min``: Calculate the minimum of each column specified in ``col`` for each group of a GroupBy object. 
- ``mode``: Calculate the mode of each column specified in ``col`` for each group of a GroupBy object. 
- ``sd``: Calculate the standard deviation of each column specified in ``col`` for each group of a GroupBy object. 
- ``ss``: Calculate the sum of squares of each column specified in ``col`` for each group of a GroupBy object. 
- ``sum``: Calculate the sum of each column specified in ``col`` for each group of a GroupBy object. 
- ``var``: Calculate the variance of each column specified in ``col`` for each group of a GroupBy object. 

.. note::

  If no arguments are given to the aggregation (e.g. ``max()`` in ``grouped.sum(col="X1", na="all").mean(col="X5", na="all").max()``), then it is assumed that the aggregation should apply to all columns except the GroupBy columns.

Once the aggregation operations are complete, calling the GroupBy object with a new set of aggregations will yield no effect. You must generate a new GroupBy object in order to apply a new aggregation on it. In addition, certain aggregations are only defined for numerical or categorical columns. An error will be thrown for calling aggregation on the wrong data types.

Examples
--------

The following examples in Python and R show how to find the months with the highest cancellation using ``group_by``.

.. tabs::
   .. code-tab:: python

        import h2o
        h2o.init()

        # Upload the airlines dataset
        air = h2o.import_file("https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv")
        air.dim
        [43978, 31]

        # Find number of flights by airport
        origin_flights = air.group_by("Origin")
        origin_fights.count()
        origin_fights.get_frame()
        Origin      nrow
        --------  ------
        ABE           59
        ABQ          876
        ACY           31
        ...

        # Find number of flights per month based on the origin
        cols = ["Origin","Month"]
        flights_by_origin_month = air.group_by(by=cols).count(na ="all")
        flights_by_origin_month.get_frame()
        Origin      Month    nrow
        --------  -------  ------
        ABE             1      59
        ABQ             1     846
        ABQ            10      30
        ...

        # Find months with the highest cancellation ratio
        cancellation_by_month = air.group_by(by='Month').sum('Cancelled', na="all")
        flights_by_month = air.group_by('Month').count(na="all")
        cancelled = cancellation_by_month.get_frame()['sum_Cancelled']
        flights = flights_by_month.get_frame()['nrow']
        month_count = flights_by_month.get_frame()['Month']
        ratio = cancelled/flights
        month_count.cbind(ratio)
          Month    sum_Cancelled
          -------  ---------------
                1       0.0254175
               10       0.00950475

        [2 rows x 2 columns]

        # Use group_by with multiple columns. Summarize the destination, 
        # arrival delays, and departure delays for an origin
        cols_1 = ['Origin', 'Dest', 'IsArrDelayed', 'IsDepDelayed']
        cols_2 = ["Dest", "IsArrDelayed", "IsDepDelayed"]
        air[cols_1].group_by(by='Origin').sum(cols_2, na="ignore").get_frame()
        Origin      sum_Dest    sum_IsDepDelayed    sum_IsArrDelayed
        --------  ----------  ------------------  ------------------
        ABE             5884                  30                  40
        ABQ            84505                 370                 545
        ACY             3131                   7                   9
        ALB             3646                  50                  49
        AMA              317                   6                   4
        ANC              100                   1                   0
        ...

   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import the airlines data set and display a summary.
        airlines_url <- "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv"
        airlines <- h2o.importFile(path = airlines_url)
        summary(airlines)

        # Find number of flights by airport
        origin_flights <- h2o.group_by(data = airlines, by = "Origin", nrow("Origin"), gb.control = list(na.methods = "rm"))
        origin_flights_df <- as.data.frame(origin_flights)
        origin_flights_df
            Origin nrow
        1      ABE   59
        2      ABQ  876
        3      ACY   31
        ...

        # Find number of flights per month
        flights_by_month <- h2o.group_by(data = airlines, 
                                         by = "Month", 
                                         nrow("Month"), 
                                         gb.control = list(na.methods = "rm"))
        flights_by_month_df <- as.data.frame(flights_by_month)
        flights_by_month_df
          Month   nrow
        1     1  41979
        2    10   1999

        # Find the number of flights in a given month based on the origin
        cols <- c("Origin","Month")
        flights_by_origin_month <- h2o.group_by(data = airlines, 
                                                by = cols, 
                                                nrow("Month"), 
                                                gb.control = list(na.methods = "rm"))
        flights_by_origin_month_df <- as.data.frame(flights_by_origin_month)
        flights_by_origin_month_df
            Origin Month nrow
        1      ABE     1   59
        2      ABQ     1  846
        3      ABQ    10   30
        4      ACY     1   31
        5      ALB     1   75
        ...

        # Find months with the highest cancellation ratio
        which(colnames(airlines)=="Cancelled")
        [1] 22
        cancellations_by_month <- h2o.group_by(data = airlines, 
                                               by = "Month", 
                                               sum("Cancelled"), 
                                               gb.control=list(na.methods="rm"))
        cancellation_rate <- cancellations_by_month$sum_Cancelled/flights_by_month$nrow
        rates_table <- h2o.cbind(flights_by_month$Month,cancellation_rate)
        rates_table_df <- as.data.frame(rates_table)
        rates_table_df
          Month sum_Cancelled
        1     1   0.025417471
        2    10   0.009504752

        # Use group_by with multiple columns. Summarize the destination, 
        # arrival delays, and departure delays for an origin
        cols <- c("Dest", "IsArrDelayed", "IsDepDelayed")
        origin_flights <- h2o.group_by(data = airlines[c("Origin",cols)], 
                                       by = "Origin", 
                                       sum(cols),
                                       gb.control = list(na.methods = "ignore", col.names = NULL))
        
        # Note a warning because col.names null
        res <- h2o.cbind(lapply(cols, function(x){h2o.group_by(airlines, by = "Origin", sum(x))}))[,c(1,2,4,6)]
        res
          Origin sum_Dest sum_IsArrDelayed sum_IsDepDelayed
        1    ABE     5884               40               30
        2    ABQ    84505              545              370
        3    ACY     3131                9                7
        4    ALB     3646               49               50
        5    AMA      317                4                6
        6    ANC      100                0                1

The following R code shows the options by-variable with ``gb.control``.

.. tabs::

  .. code-tab:: r R

    # Import H2O-3:
    library(h2o)
    h2o.init()

    # Import the airlines dataset:
    airlines.hex <- h2o.importFile("https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv")

    # View quantiles and histograms:
    quantile(x = airlines.hex$ArrDelay, na.rm = TRUE)
    h2o.hist(airlines.hex$ArrDelay)

    # Find the number of flights by airport:
    originFlights <- h2o.group_by(data = airlines.hex, by = "Origin", nrow("Origin"), gb.control <- list(na.methods = "rm"))
    originFlights.R <- as.data.frame(originFlights)

    # Find the number of flights per month:
    flightsByMonth <- h2o.group_by(data = airlines.hex, by = "Month", nrow("Month"), gb.control <- list(na.methods = "rm"))
    flightsByMonth.R <- as.data.frame(flightsByMonth)

    # Find months with the highest cancellation ratio:
    which(colnames(airlines.hex)=="Cancelled")
    cancellationsByMonth <- h2o.group_by(data = airlines.hex, by = "Month", sum("Cancelled"), gb.control <- list(na.methods = "rm"))
    cancellation_rate <- cancellationsByMonth$sum_Cancelled/flightsByMonth$nrow
    rates_table <- h2o.cbind(flightsByMonth$Month, cancellation_rate)
    rates_table.R <- as.data.frame(rates_table)

    # Construct test and train sets using sampling:
    airlines.split <- h2o.splitFrame(data = airlines.hex, ratio = 0.85)
    airlines.train <- airlines.split[[1]]
    airlines.test <- airlines.split[[2]]

    # Display a summary using table-like functions: 
    h2o.table(airlines.train$Cancelled)
    h2o.table(airlines.test$Cancelled)

    # Set the predictor and response variables:
    Y <- "IsDepDelayed"
    X <- c("Origin", "Dest", "DayofMonth", "Year", "UniqueCarrier", "DayOfWeek", "Month", "DepTime", "ArrTime", "Distance")

    # Define the data for the model and display the results:
    airlines.glm <- h2o.glm(training_frame = airlines.train, x = X, y = Y, family = "binomial", alpha = 0.5)

    # View the model information (training statistics, performance, important variables):
    summary(airlines.glm)

    # Predict using the GLM model:
    pred <- h2o.predict(object = airlines.glm, newdata = airlines.test)

    # Look at the summary of predictions (probability of TRUE class p1):
    summary(pred$p1)
