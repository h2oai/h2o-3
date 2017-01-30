``user_points``
---------------

- Available in: K-Means
- Hyperparameter: no

Description
~~~~~~~~~~~

This option allows you to specify a dataframe,  where each row represents an initial cluster center. 

**Notes**:

- The user-specified points must have the same number of columns as the training observations. 
- The number of rows must equal the number of clusters. 
- ``init=furthest`` by default. However, if a user-points file is specified and a value for ``init`` is not, then ``init`` will automatically change to ``user``. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `init <init.html>`__

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

	# specify your points
	point1 <- c(4.9,3.0,1.4,0.2)
	point2 <- c(5.6,2.5,3.9,1.1)
	point3 <- c(6.5,3.0,5.2,2.0)

	# create an H2OFrame with your points
	points <- as.h2o(t(data.frame(point1, point2, point3)))

	# take a look at the H2OFrame
	print(points)

	# try using the `user_points` parameter:
	iris_kmeans <- h2o.kmeans(x = predictors, k = 3, user_points =  points, training_frame = train, validation_frame = valid, seed = 1234)

	# print the total within cluster sum-of-square error for the validation dataset
	print(paste0("Total sum-of-square error for valid dataset: ", h2o.tot_withinss(object = iris_kmeans, valid = T)))

	
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

	# set the predictor names and the response column name
	predictors = iris.columns[:-1]

	# split into train and validation sets
	train, valid = iris.split_frame(ratios = [.8], seed = 1234)

	# specify your points
	point1 = [4.9,3.0,1.4,0.2]
	point2 = [5.6,2.5,3.9,1.1]
	point3 = [6.5,3.0,5.2,2.0]

	# create an H2OFrame with your points
	points = h2o.H2OFrame([point1, point2, point3])

	# take a look at the H2OFrame
	print(points)

	# try using the `user_points` parameter:
	# initialize the estimator then train the model
	iris_kmeans = H2OKMeansEstimator(k = 3, user_points = points, seed = 1234)
	iris_kmeans.train(x=predictors, training_frame=iris, validation_frame=valid)

	# print the total within cluster sum-of-square error for the validation dataset
	print("sum-of-square error for valid:", iris_kmeans.tot_withinss(valid = True))
