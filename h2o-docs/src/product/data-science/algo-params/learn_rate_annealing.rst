``learn_rate_annealing``
------------------------

- Available in: GBM
- Hyperparameter: yes

Description
~~~~~~~~~~~

Use this option to reduce the learning rate by this factor after every tree. When used, then for *N* trees, you would start with ``learn_rate`` and end with ``learn_rate * learn_rate_annealing^N``. 

The following provides some reference factors. (Refer to `Taylor series <https://en.wikipedia.org/wiki/Taylor_series>`__ for more information.):

- 0.99^100 = 0.366
- 0.99^1000 = 4.3e-5
- 0.999^1000 = 0.368
- 0.999^10000 = 4.5e-5

With this option, then instead of ``learn_rate=0.01``, you can try (for example) ``learn_rate=0.05`` along with ``learn_rate_annealing=0.99``. The result should converge much faster with almost the same accuracy. Note, however, that this can also result in overfitting, so use caution when specifying this option. 

The value range for this option is between 0 and 1. This option defaults to 1.0, which disables the learning rate annealing. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `learn_rate <learn_rate.html>`__


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

	# try using the `learn_rate_annealing` parameter: 
	# combine learn_rate with learn_rate_annealing
	# since we have learning_rate_annealing, we can afford to start with a bigger learning rate (.05)
	# learning rate annealing = .99 means learning_rate shrinks by 1% after every tree 
	# early stopping makes it okay to use 'more than enough' trees
	titanic.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                       learn_rate = .05, learn_rate_annealing =.99,
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

	# try using the `learn_rate_annealing` parameter: 
	# combine learn_rate with learn_rate_annealing
	# since we have learning_rate_annealing, we can afford to start with a bigger learning rate (.05)
	# learning rate annealing = .99 means learning_rate shrinks by 1% after every tree 
	# early stopping makes it okay to use 'more than enough' trees
	# initialize your estimator
	titanic_gbm = H2OGradientBoostingEstimator(ntrees = 10000, learn_rate = 0.05, learn_rate_annealing = .99,
	                                           # use early stopping once the validation AUC doesn't improve
	                                           # by at least 0.01% for 5 consecutive scoring events 
	                                           stopping_rounds = 5, stopping_metric = "auc", 
	                                           stopping_tolerance = 1e-4, seed = 1234)

	# then train the model
	titanic_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the auc for the validation data
	print(titanic_gbm.auc(valid=True))

