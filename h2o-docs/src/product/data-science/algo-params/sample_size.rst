``sample_size``
---------------

- Available in: Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the number of randomly sampled observations used to train each Isolation Forest tree. If set to -1, ``sample_rate`` will be used instead. This value defaults to 256.

Related Parameters
~~~~~~~~~~~~~~~~~~

- none

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the ecg discord datasets:
	train <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
	test <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")

	# train using the `sample_size` parameter:
	isofor_model <- h2o.isolationForest(training_frame=train, sample_size=5, ntrees=7)

	# test the prediction
	anomaly_score <- h2o.predict(isofor_model, test)
	anomaly_score
	      predict mean_length
	1 -0.16666667    2.857143
	2 -0.16666667    2.857143
	3 -0.08333333    2.714286
	4  0.16666667    2.285714
	5  0.00000000    2.571429
	6  0.33333333    2.000000

	[23 rows x 2 columns] 

   .. code-block:: python

	import h2o
	from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
	h2o.init()

	# import the ecg discord datasets:
	train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
	test = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")

	# try using the `sample_size` parameter:
	isofor_model = H2OIsolationForestEstimator(sample_size = 5, ntrees=7) 

	# then train your model
	isofor_model.train(training_frame = train)

	perf = isofor_model.model_performance()
    perf
      ModelMetricsAnomaly: isolationforest
      ** Reported on train data. **
      
      Anomaly Score: 1.1392230576441102
      Normalized Anomaly Score: 0.3631578947368421

    test_pred = isofor_model.predict(test)
    test_pred
      predict    mean_length
    ---------  -------------
         -0.1        1.57143
         -0.1        1.57143
          0          1.42857
          0          1.42857
          0          1.42857
         -0.1        1.57143
          0.1        1.28571
          0          1.42857
          0.2        1.14286
         -0.1        1.57143

    [23 rows x 2 columns]
