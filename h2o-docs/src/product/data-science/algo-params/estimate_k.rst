``estimate_k``
--------------

- Available in: K-Means
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option is used to specify whether to estimate the number of clusters (:math:`<=k`) iteratively (independent of the seed) and deterministically (beginning with :math:`k=1,2,3...`). If enabled, for each :math:`k` the estimate will go up to ``max_iterations``. 

**Notes**: 

- This option requires that at least one column includes numeric data. You will receive an error if your data has no numeric columns. 
- If this option is enabled and a ``seed`` is provided, the ``seed`` will be ignored unless you are performing cross validation. 
- This option cannot be used with ``user_points``. You will receive an error during model training if you enable this option and specify ``user_points``. 

This option is disabled by default.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `k <k.html>`__
- `max_iterations <max_iterations.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris <-h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# convert response column to a factor
	iris['class'] <-as.factor(iris['class'])

	# set the predictor names 
	predictors <-colnames(iris)[-length(iris)]

	# split into train and validation
	iris_splits <- h2o.splitFrame(data = iris, ratios = .8, seed = 1234)
	train <- iris_splits[[1]]
	valid <- iris_splits[[2]]

	# try using the `estimate_k` parameter:
	# set k to the upper limit of classes you'd like to consider
	# set standardize to False as well since the scales for each feature are very close
	iris_kmeans <- h2o.kmeans(x = predictors, k = 10, estimate_k = T, standardize = F, 
	                          training_frame = train, validation_frame=valid, seed = 1234)

	# print the model summary to see the number of clusters chosen
	summary(iris_kmeans)


	
   .. code-block:: python

	import h2o
	from h2o.estimators.kmeans import H2OKMeansEstimator
	h2o.init()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# convert response column to a factor
	iris['class'] = iris['class'].asfactor()

	# set the predictor names 
	predictors = iris.columns[:-1]

	# split into train and validation sets
	train, valid = iris.split_frame(ratios = [.8], seed = 1234)

	# try using the `estimate_k` parameter:
	# set k to the upper limit of classes you'd like to consider
	# set standardize to False as well since the scales for each feature are very close
	# initialize the estimator then train the model
	iris_kmeans = H2OKMeansEstimator(k = 10, estimate_k = True, standardize = False, seed = 1234)
	iris_kmeans.train(x = predictors, training_frame = train, validation_frame=valid)

	# print the model summary to see the number of clusters chosen
	iris_kmeans.summary()

