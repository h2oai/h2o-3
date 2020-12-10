``auc_type``
----------------

- Available in: Random Forest, GBM, Deep Learning, XGBoost, GLM, GAM, Naive Bayes, Stack Ensembles
- Hyperparameter: no

Description
~~~~~~~~~~~

To calculate AUC or AUCPR for multinomial classification, this parameter has to be set. By default, this option is disabled due to expensive CPU and memory usage. This functionality is available only for multinomial classification problems with a maximum of 50 domains. 

This parameter is important when the `early_stopping <early_stopping.html>`__ parameter is set to ``AUC`` and also for sorting model by metric in `Grid search <../../grid-search.html>`__ using ``sort_by`` parameter.

More information about Multinomial AUC/AUCPR metric is on `Performance and prediction <../../performance-and-prediction.html>`__ page. 


- If the AUC type is ``WEIGHTED_OVR``, the weighted average One vs. Rest AUC or AUCPR will be the default value for AUC and AUCPR metrics.
- If the default AUC type is ``WEIGHTED_OVO``, the weighted average One vs. One AUC or AUCPR will be the default value for AUC and AUCPR metrics.
- If the default AUC type is ``MACRO_OVR``, the macro average One vs. Rest AUC or AUCPR will be the default value for AUC and AUCPR metrics.
- If the default AUC type is ``MACRO_OVO``, the macro average One vs. One AUC or AUCPR will be the default value for AUC and AUCPR metrics.
- If the default AUC type is ``NONE``, the metric is not calculated and the None value is returned instead.
- If the default AUC type is ``AUTO``, the auto option is ``NONE`` by default.


**NOTE**: ``auc_type`` is available ONLY for multinomial distribution and is ``NONE`` by default.


Related Parameters
~~~~~~~~~~~~~~~~~~

- `distribution <distribution.html>`__
- `early_stopping <early_stopping.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the cars dataset:
		# this dataset is used to classify whether or not a car is economical based on
		# the car's displacement, power, weight, and acceleration, and the year it was made
		cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")


		# set the predictor names and the response column name
		predictors <- c("displacement","power", "weight", "acceleration", "year")
		response <- "cylinders"
        cars[response] <- h2o.asfactor(cars[response])

		# split into train and validation sets
		cars_splits <- h2o.splitFrame(data =  cars, ratios = 0.8, seed = 1234)
		train <- cars_splits[[1]]
		valid <- cars_splits[[2]]

		# try using the distribution parameter:
		# train a GBM
		car_gbm <- h2o.gbm(x=predictors, 
		                   y=response, 
		                   training_frame=train,
		                   validation_frame=valid,
		                   distribution="multinomial",
                           auc_type="MACRO_OVR", 
		                   seed=1234)

		# print the AUC for your validation data
		print(h2o.auc(car_gbm, valid = TRUE))
		# print the AUCPR for your validation data
		print(h2o.aucpr(car_gbm, valid = TRUE))
        # print the whole AUC table
        print(cars_gbm.multinomial_auc_table())
        # print the whole AUCPR table
        print(cars_gbm.multinomial_aucpr_table())

   .. code-tab:: python

		import h2o
		from h2o.estimators.gbm import H2OGradientBoostingEstimator
		h2o.init()

		# import the cars dataset:
		# this dataset is used to classify whether or not a car is economical based on
		# the car's displacement, power, weight, and acceleration, and the year it was made
		cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# set the predictor names and the response column name
		predictors = ["displacement","power","weight","acceleration","year"]
		response = "cylinders"

        cars[response] = cars[response].asfactor()

		# split into train and validation sets
		train, valid = cars.split_frame(ratios = [.8], seed = 1234)

		# try using the distribution parameter:
		# Initialize and train a GBM
		cars_gbm = H2OGradientBoostingEstimator(distribution="multinomial", seed=1234, auc_type="MACRO_OVR")
		cars_gbm.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

		# print the AUC for the validation data
		print(cars_gbm.auc(valid=True))
		# print the AUCPR for the validation data
		print(cars_gbm.auc(valid=True))
        # print the whole AUC table
        print(cars_gbm.multinomial_auc_table())
        # print the whole AUCPR table
        print(cars_gbm.multinomial_aucpr_table())
