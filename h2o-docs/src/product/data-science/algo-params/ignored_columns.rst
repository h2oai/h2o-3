``ignored_columns``
-------------------

- Available in: GBM, DRF, Deep Learning, GLM, PCA, GLRM, Naïve-Bayes, K-Means, XGBoost, Aggregator, CoxPH, Isolation Forest
- Hyperparameter: no

Description
~~~~~~~~~~~

**Note**: This command is only available in the Python client and in Flow. It is not available in R. 

There may be instances when your dataset includes information that you want to be ignored when building a model. Use the ``ignored_columns`` parameter to specify an array of column names that should be ignored. This is a strict parameter that takes into account the exact string of the column name. So, for example, if your dataset includes one column named **Type** and another column named **Types**, and you specify ``ignored_columns=["type"]``, then the algorithm will only ignore the **Type** column and will not ignore the **Types** column.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `ignore_const_cols <ignore_const_cols.html>`__
- `x <x.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the airlines dataset:
	# This dataset is used to classify whether a flight will be delayed 'YES' or not "NO"
	# original data can be found at http://www.transtats.bts.gov/
	airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

	# convert columns to factors
	airlines["Year"]= airlines["Year"].asfactor()
	airlines["Month"]= airlines["Month"].asfactor()
	airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
	airlines["Cancelled"] = airlines["Cancelled"].asfactor()
	airlines['FlightNum'] = airlines['FlightNum'].asfactor()

	# set the predictor names and the response column name
	predictors = airlines.columns[:9]
	response = "IsDepDelayed"

	# split into train and validation sets
	train, valid= airlines.split_frame(ratios = [.8], seed = 1234)

	# try using the `ignored_columns` parameter:
	# create a list of column names to ignore
	col_list = ['DepTime','CRSDepTime','ArrTime','CRSArrTime']

	# initialize the estimator and train the model
	airlines_gbm = H2OGradientBoostingEstimator(ignored_columns = col_list, seed =1234)
	airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	airlines_gbm.auc(valid=True)