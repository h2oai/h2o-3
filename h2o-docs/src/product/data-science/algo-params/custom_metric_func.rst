.. _custom_metric_func:

``custom_metric_func``
----------------------

- Available in: GBM, GLM, DRF, Deeplearning, Stacked Ensembles, XGBoost
- Hyperparameter: no

.. note::

    This option is only supported in the Python client.

.. note::

    In Deeplearning, custom metric is not supported for Auto-encoder option.

Description
~~~~~~~~~~~

Use this option to specify a custom evaluation function. A custom metric function can be used to produce adhoc scoring metrics if actuals are presented.

The Map-Reduce approach is used to calculate the metric. A dataset is broken into multiple chunks and operations are performed on each row of each chunk in the ``map`` function, combined in the ``reduce`` function, and the final value of the metric is returned in the ``metric`` function.

Map
'''

The ``map`` function defines an action that needs to be performed on each row of the data. Here is a description of each variable for the ``map`` function:

    - ``pred``: a double array storing the model prediction output.  For regresion problems, the prediction value is stored in ``pred[0]``. For classification problems, the predicted class label is stored in pred[0], the predicted probability for class i is stored in ``pred[i+1]``. 
    - ``act``: a double array containing the actual response value of the dataset.  Again, the actual response value is stored in ``act[0]``.
    - ``w``: the weight applied to each row of the metric if present; otherwise, it defaults to 1.0.
    - ``o``: refers to the offset applied to each row of the metric if applicable. Defaults to 0.0.

Reduce
''''''

The reduce function combines the results from two chunks. It keeps reducing two chunks until only one result is generated. If we have four chunks, *chunk0*, *chunk1*, *chunk2* and *chunk3* (and this is just one possible sequence of combination), ``reduce`` will work on *chunk0* and *chunk1* to generate the result of *chunk0_1*, and ``reduce`` will work on *chunk2* and *chunk3* to generate the result of *chunk2_3*.  Then, ``reduce`` will work on *chunk0_1* and *chunk2_3* to get the final result.  The variables are:

    - ``l``: a double array with ``l[0]`` containing the accumulated custom metric of one chunk and ``l[1]`` containing the number of rows that are used in the accumulation.
    - ``r``: a double array with ``r[0]`` containing the accumulated custom metric of another chunk and ``r[1]`` containing the number of rows that are used in the accumulation.

Map-Reduce Principle
''''''''''''''''''''

Here is the math formula and in the example you can see transformation into Map-Reduce principle. 

.. math::

    RMSE =  \sqrt{\frac{\sum_{i=0}^{N} \Arrowvert y(i) - \hat{y}(i) \Arrowvert ^2}{N}}

where:

    - :math:`N` is number of data points
    - :math:`y(i)` is i-th measurement
    - :math:`\hat{y}(i)` is prediction of the i-th measurement

You can deconstruct this formula into the map-reduce-value principle where:

- :math:`\Arrowvert y(i) - \hat{y}(i) \Arrowvert ^2` part is done in `map` function
- :math:`\sum_{i=0}^{N}` part (= sumError) and count N is done in ``reduce`` function with results from ``map`` phase
- :math:`\sqrt{sumError/N}` is done in ``metric`` function from results from ``reduce`` phase

See the `example <#example>`__ section for how to implement a custom RMSE metric using this formula. The names of the variables in the code should match with the variables in the formula.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `upload_custom_metric <upload_custom_metric.html>`__
- `stopping_metric <stopping_metric.html>`__

Example
~~~~~~~

.. tabs::
    .. code-tab:: python

        import h2o
        from h2o.estimtors.gbm import H2OGradientBoostingEstimator
        h2o.init()
        h2o.cluster().show_status()

        # Import the airlines dataset:
        # This dataset is used to classify whether a flight will be delayed 'YES' or not 'NO'
        # original data can be found at http://www.transtats.bts.gov/
        airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

        # Convert columns to factors:
        airlines["Year"] = airlines["Year"].asfactor()
        airlines["Month"] = airlines["Month"].asfactor()
        airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
        airlines["Cancelled"] = airlines["Cancelled"].asfactor()
        airlines['FlightNum'] = airlines['FlightNum'].asfactor()

        # Set the predictor names and the response column name:
        predictors = ["Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum"]
        response = "IsDepDelayed"

        # Split into train and validation sets:
        train, valid = airlines.split_frame(ratios=[.8], seed=1234)

        # Use a custom metric:
        # Create a custom RMSE Model metric and save as mm_rmse.py
        # Note that this references a java class java.lang.Math
        class CustomRmseFunc:

            def map(self, pred, act, w, o, model):
            '''
            Returns error calculation for a particular record.
                Parameters:
                    pred (list[float]) : Prediction probability
                        for binomial classification problems length of pred is 3:
                        pred[0] = final predicition -> 0 or 1
                        pred[1] = prediction probability for 1st class, value between 0-1
                        pred[2] = prediction probability for 2nd class, value between 0-1
                    act (list[int]): Actual value, for binomila classification problems: y(i) -> 0 or 1
                    w (float) : Weight (if weight_column is provided, w=1 otherwise)
                    o (float) : Prediction offset (if offset_column is provided, o=0 otherwise)
                    model (H2OModel) : Model the metrics are calculated against it
                Returns:
                    residual error (list[float]): Residual error for particular record and value 1 to count all records
            '''
            y = int(act[0]) # 0 or 1
            y_pred_idx = y + 1 # 1 or 2
            y_hat = pred[y_pred_idx] # value between 0-1
            err = 1 - y_hat # value between 0-1
            return [w * err * err, 1]

            def reduce(self, l, r):
            '''
            Reduce all particular records into one. First reduce pairs of records together, then reduce pairs of pairs
            together, and continue until all records are reduced into one.
            In case of RMSE sum up residual errors together and count number of all records.
                Parameters:
                    l (list[float]) : Summed up values from the left particular record/records
                    r (list[float]) : Summed up values from the right particular record/records
                Returns:
                    result list (list[float]) : Reduced error from all records and number of all records
            '''
            error = l[0] + r[0]
            n = l[1] + r[1]
            return [error, n]

            def metric(self, l):
            '''
            Calculate the final metric value. In case of RMSE it returns squared reduced error divided by number of records.
                Parameters:
                    l (list[float]) : Reduced error from all records and number of all records
                        l[0] = reduced error from all records
                        l[1] = number of all records
                Returns:
                    metric value (float) : Final metric value calculated from all records
            '''
            import java.lang.Math as math
            return math.sqrt(l[0] / l[1])

        # Upload the custom metric:
        custom_mm_func = h2o.upload_custom_metric(CustomRmseFunc, 
                                                  func_name="rmse", 
                                                  func_file="mm_rmse.py")

        # Train the model:
        model = H2OGradientBoostingEstimator(ntrees=3, 
                                             max_depth=5, 
                                             score_each_iteration=True, 
                                             custom_metric_func=custom_mm_func, 
                                             stopping_metric="custom", 
                                             stopping_tolerance=0.1, 
                                             stopping_rounds=3)
        model.train(x=predictors, 
                    y=response, 
                    training_frame=train, 
                    validation_frame=valid)

        # Get model metrics:
        perf = model.model_performance(valid=True)

        # Print custom metric name and value on validation data:
        print(perf.custom_metric_name())
        print(perf.custom_metric_value())
        
        
