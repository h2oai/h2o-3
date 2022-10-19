``autoencoder``
---------------

- available in: Deep Learning
- Hyperparameter: no

Description
~~~~~~~~~~~

The Deep Learning ``autoencoder`` is based on the standard deep neural net architecture, where the entire network is learned together (as opposed to being stacked layer-by-layer). The main difference here is that no response is required for the input and that the output layer has as many neurons as the input layer. ``autoencoder`` can also be used to compress input features (similar to PCA). Sparse autoencoders are simple extensions that can increase accuracy.

Autoencoders are useful for:

- generic dimensionality reduction (used for pre-processing for any algorithm).
- anomaly detection (used for comparing the reconstructed signal with the original to find anomalous differences).
- layer-by-layer pre-training (by using stacked autoencoders).

Related Parameters
~~~~~~~~~~~~~~~~~~

- ``pretrained_autoencoder``
- ``average_activation``
- ``sparsity_beta``

Example
~~~~~~~

.. tabs::
	.. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the MNIST bigdata dataset:
		train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")

		# set the predictors and response:
		predictors <- c(1:784)
		resp <- 785
		train[,resp] <- as.factor(train[,resp])

		# split the training data:
		data_split <- h2o.splitFrame(data = train, ratios = 0.5, seed = 1234)
		training_data <- data_split[[1]]
		valid_data <- data_split[[2]]

		# build and train the model: 
		ae_model <- h2o.deeplearning(x = predictors, 
					     training_frame = training_data[-resp], 
					     activation = "Tanh", 
					     autoencoder = TRUE, 
					     hidden = c(20), 
					     epochs = 1, 
					     reproducible = TRUE, 
					     seed = 1234, 
					     ignore_const_cols = FALSE)

		# retrieve something :)


	.. code-tab:: python

		import h2o
		from h2o.estimators import H2ODeepLearningEstimator
		h2o.init()

		# import the MNIST bigdata dataset:
		train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")

		# set the predictors and response:
		predictors = train.columns[0:783]
		response = 784
		train[response] = train[response].asfactor()

		# split the training data:
		training_data, valid_data = train.split_frame(ratios=[0.5])

		# build and train the model:
		ae_model = H2ODeepLearningEstimator(activation="tanh", ignore_const_cols=False, autoencoder=True, hidden=[20], epochs=1, reproducible=True, seed=1234)



