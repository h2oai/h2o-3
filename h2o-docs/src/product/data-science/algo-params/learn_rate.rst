``learn_rate``
---------------

- Available in: GBM, XGBoost
- Hyperparameter:

Description
~~~~~~~~~~~

This option is used to specify the rate at which GBM learns when building a model. Lower learning rates are generally better, but then more trees are required (using ``ntrees``) to achieve the same level of fit as if you had used a higher learning rate. This method helps avoid overfitting. 

You can use this option along with the ``learn_rate_annealing`` option to reduce the learning rate by a specified factor for every tree. This can help speed of convergence without sacrificing too much accuracy. For faster scans, use (for example) ``learn_rate=0.5`` and ``learn_rate_annealing=0.99``. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `learn_rate_annealing <learn_rate_annealing.html>`__
- `ntrees <ntrees.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the titanic dataset:
	# This dataset is used to classify whether a passenger will survive '1' or not '0'
	# original dataset can be found at https://stat.ethz.ch/R-manual/R-devel/library/datasets/html/Titanic.html
	titanic <-  h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

	# convert response column to a factor
	titanic['survived'] <- as.factor(titanic['survived'])

	# set the predictor names and the response column name
	# predictors include all columns except 'name' and the response column ("survived")
	predictors <- setdiff(colnames(titanic), colnames(titanic)[2:3])
	response <- "survived"

	# split into train and validation
	titanic.splits <- h2o.splitFrame(data =  titanic, ratios = .8, seed = 1234)
	train <- titanic.splits[[1]]
	valid <- titanic.splits[[2]]

	# try using the `learn_rate` parameter: 
	# because we use a small learning_rate, we set ntrees to a much higer number
	# early stopping makes it okay to use 'more than enough' trees
	titanic.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                       ntrees = 10000, learn_rate = .01, 
	                       # use early stopping once the validation AUC doesn't improve by at least 0.01%
	                       # for 5 consecutive scoring events
	                       stopping_rounds = 5,
	                       stopping_tolerance = 1e-4,
	                       stopping_metric = "AUC", seed = 1234)

	# print the auc for the validation data
	print(h2o.auc(titanic.gbm, valid = TRUE))


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the titanic dataset:
	# This dataset is used to classify whether a passenger will survive '1' or not '0'
	# original dataset can be found at https://stat.ethz.ch/R-manual/R-devel/library/datasets/html/Titanic.html
	titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

	# convert response column to a factor
	titanic['survived'] = titanic['survived'].asfactor()

	# set the predictor names and the response column name
	# predictors include all columns except 'name' and the response column ("survived")
	predictors = titanic.columns
	del predictors[1:3]
	response = 'survived'

	# split into train and validation sets
	train, valid = titanic.split_frame(ratios = [.8], seed = 1234)

	# try using the `learn_rate` parameter: 
	# because we use a small learning_rate, we set ntrees to a much higer number
	# early stopping makes it okay to use 'more than enough' trees
	# initialize your estimator, then train the model
	titanic_gbm = H2OGradientBoostingEstimator(ntrees = 10000, learn_rate = 0.01,
	                                           # use early stopping once the validation AUC doesn't improve
	                                           # by at least 0.01% for 5 consecutive scoring events 
	                                           stopping_rounds = 5, stopping_metric = "AUC", 
	                                           stopping_tolerance = 1e-4, seed = 1234)
	titanic_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(titanic_gbm.auc(valid=True))
