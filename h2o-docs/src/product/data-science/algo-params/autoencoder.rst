``autoencoder``
---------------

- Available in: Deep Learning
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

- None

Example
~~~~~~~

.. tabs::
	.. code-tab:: r R

		library(h2o)
		h2o.init()

		# Import the MNIST train and test datasets:
		train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
		test <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")

		# Set the predictors and response:
		predictors = c(1:784)
		response = 785
		train[response] <- as.factor(train[response])

		# Split the training data:
		sid <- h2o.runif(train, seed=0)
		training_data <- train[sid>=0.5,]
		valid_data <- train[sid<0.5,]

		# Build and train an unsupervised autoencoder model:
		ae_model <- h2o.deeplearning(x = predictors, 
					     training_frame = training_data, 
					     activation = "Tanh", 
					     ignore_const_cols = FALSE, 
					     autoencoder = TRUE, 
					     hidden = c(20), 
					     epochs = 1, 
					     reproducible = TRUE, 
					     seed = 1234, 
					     model_id = "ae_model")

		# Evaluate the model performance:
		h2o.performance(ae_model)

		# Use that pretrained unsupervised autoencoder model to
		# initialize a supervised Deep Learning model:
		pretrained_model <- h2o.deeplearning(x = predictors, 
						     y = response, 
						     training_frame = valid_data, 
						     validation_frame = test, 
						     ignore_const_cols = FALSE, 
						     hidden = c(20), 
						     epochs = 1, 
						     reproducible = TRUE, 
						     seed = 1234, 
						     pretrained_autoencoder = "ae_model")

		# Evaluate the performance again:
		h2o.performance(pretrained_model)



	.. code-tab:: python

		import h2o
		from h2o.estimators import H2OAutoEncoderEstimator, H2ODeepLearningEstimator
		h2o.init()

		# Import the MNIST train and test datasets:
		train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
		test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")

		# Set the predictors and response:
		predictors = train.columns[0:783]
		response = train.columns[784]
		train[response] = train[response].asfactor()

		# Split the training data:
		training_data, valid_data = train.split_frame(ratios=[0.5])

		# Build and train an unsupervised autoencoder model:
		ae_model = H2OAutoEncoderEstimator(activation="tanh", 
						   ignore_const_cols=False, 
						   autoencoder=True, hidden=[20], 
						   epochs=1, 
						   reproducible=True, 
						   seed=1234, 
						   model_id="ae_model")
		ae_model.train(x=predictors, y=response, training_frame=training_data)

		# Evaluate the model performance:
		ae_model.model_performance()

		# Now, use that pretrained unsupervised autoencoder model to
		# initialize a supervised Deep Learning model:
		pretrained_model = H2ODeepLearningEstimator(ignore_const_cols=False, 
							    hidden=[20], 
							    epochs=1, 
							    reproducible=True, 
							    seed=1234, 
							    pretrained_autoencoder="ae_model")
		pretrained_model.train(x=predictors, 
				       y=response, 
				       training_frame=valid_data, 
				       validation_frame=test)

		# Evaluate the model performance again:
		pretrained_model.model_performance()



