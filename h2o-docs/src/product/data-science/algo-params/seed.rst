``seed``
--------

- Available in: GBM, DRF, Deep Learning, GLM, PCA, GLRM, Naïve-Bayes, K-Means, AutoML, XGBoost, Stacked Ensembles, Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the random number generator (RNG) seed for algorithms that are dependent on randomization. When a seed is defined, the algorithm will behave deterministically. 

The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

Note that in Naïve-Bayes, this option is only used for cross-validation and when ``fold_assignment="Random"`` or ``"AUTO"``.

Related Parameters
~~~~~~~~~~~~~~~~~~

- none


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the airlines dataset:
	# This dataset is used to classify whether a flight will be delayed 'YES' or not "NO"
	# original data can be found at http://www.transtats.bts.gov/
	airlines <-  h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

	# convert columns to factors
	airlines["Year"] <- as.factor(airlines["Year"])
	airlines["Month"] <- as.factor(airlines["Month"])
	airlines["DayOfWeek"] <- as.factor(airlines["DayOfWeek"])
	airlines["Cancelled"] <- as.factor(airlines["Cancelled"])
	airlines['FlightNum'] <- as.factor(airlines['FlightNum'])

	# set the predictor names and the response column name
	predictors <- c("Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum")
	response <- "IsDepDelayed"

	# split into train and validation
	airlines.splits <- h2o.splitFrame(data =  airlines, ratios = .8, seed = 1234)
	train <- airlines.splits[[1]]
	valid <- airlines.splits[[2]]


	# try using the `seed` parameter with a stochastic parameter like `col_sample_rate`: 
	# run this model twice to see if the results are different (they will be the same)
	# build your model:
	gbm_w_seed_1 <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, col_sample_rate =.7 , 
	                        seed = 1234)
	gbm_w_seed_2 <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, col_sample_rate =.7 , 
	                        seed = 1234)

	# print the auc for the validation data for model with a seed
	print(paste('auc for the 1st model built with a seed:',
	            h2o.auc(gbm_w_seed_1, valid = TRUE)))
	print(paste('auc for the 2nd model built with a seed:',
	            h2o.auc(gbm_w_seed_2, valid = TRUE)))

	# run the same model but without a seed: 
	# run this model twice to see if the results are different (they will be different)
	# build your model:

	gbm_wo_seed_1 <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, col_sample_rate =.7)
	gbm_wo_seed_2 <- h2o.gbm(x = predictors, y = response, training_frame = train,
	                        validation_frame = valid, col_sample_rate =.7)

	# print the auc for the validation data for model with no seed
	print(paste('auc for the 1st model built WITHOUT a seed:',
	            h2o.auc(gbm_wo_seed_1, valid = TRUE)))
	print(paste('auc for the 2nd model built WITHOUT a seed:',
	            h2o.auc(gbm_wo_seed_2, valid = TRUE)))



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
	predictors = ["Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum"]
	response = "IsDepDelayed"

	# split into train and validation sets 
	train, valid= airlines.split_frame(ratios = [.8], seed = 1234)

	# try using the `seed` parameter with a stochastic parameter like `col_sample_rate`: 
	# run this model twice to see if the results are different (they will be the same)
	# build your model:
	gbm_w_seed_1 = H2OGradientBoostingEstimator(col_sample_rate = .7, seed = 1234) 
	gbm_w_seed_1.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	gbm_w_seed_2 = H2OGradientBoostingEstimator(col_sample_rate = .7, seed = 1234) 
	gbm_w_seed_2.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data for model with a seed
	print('auc for the 1st model built with a seed:', gbm_w_seed_1.auc(valid=True))
	print('auc for the 2nd model built with a seed:', gbm_w_seed_1.auc(valid=True))

	# run the same model but without a seed: 
	# run this model twice to see if the results are different (they will be different)
	# build your model:
	gbm_wo_seed_1 = H2OGradientBoostingEstimator(col_sample_rate = .7) 
	gbm_wo_seed_1.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	gbm_wo_seed_2 = H2OGradientBoostingEstimator(col_sample_rate = .7) 
	gbm_wo_seed_2.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data for model with no seed
	print('auc for the 1st model built WITHOUT a seed:', gbm_wo_seed_1.auc(valid=True))
	print('auc for the 2nd model built WITHOUT a seed:', gbm_wo_seed_2.auc(valid=True))


