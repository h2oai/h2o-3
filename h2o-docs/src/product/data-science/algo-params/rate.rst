``rate``
---------

- Available in: Deep Learning
- Hyperparameter: yes


Description
~~~~~~~~~~~

When adaptive learning rate is disabled, the magnitude of the weight updates are determined by the user specified learning rate (potentially annealed), and are a function of the difference between the predicted value and the target value. That difference, generally called delta, is only available at the output layer. To correct the output at each hidden layer, back propagation is used. Momentum modifies back propagation by allowing prior iterations to influence the current update. Using the momentum parameter can aid in avoiding local minima and the associated instability. Too much momentum can lead to instabilities. That's why the momentum is best ramped up slowly.

This parameter is only active when adaptive learning rate is disabled.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `rate_annealing <rate_annealing.html>`__
- `rate_decay <rate_decay.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # import the mnist datasets from the bigdata folder
        train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
        test <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")

         # Turn response into a factor (we want classification)
         train[, 785] <- as.factor(train[, 785])
         test[, 785] <- as.factor(test[, 785])
         train <- h2o.assign(train, "train")
         test <- h2o.assign(test, "test")

         # Train a deep learning model
         dl_model <- h2o.deeplearning(x = c(1:784), y = 785,
                                      training_frame = train,
                                      activation = "RectifierWithDropout",
                                      adaptive_rate = F,
                                      rate = 0.01,
                                      rate_decay = 0.9,
                                      rate_annealing = 1e-6,
                                      momentum_start = 0.95, 
                                      momentum_ramp = 1e5, 
                                      momentum_stable = 0.99,
                                      nesterov_accelerated_gradient = F,
                                      input_dropout_ratio = 0.2,
                                      train_samples_per_iteration = 20000,
                                      classification_stop = -1,  # Turn off early stopping
                                      l1 = 1e-5 
                                     )

         # See the model performance
         print(h2o.performance(dl_model, test))

   .. code-tab:: python

         import h2o
         h2o.init()
         from h2o.estimators.deeplearning import H2ODeepLearningEstimator

         # Import the mnist datasets from the bigdata folder
         train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
         test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")

         # Set the predictors and response column.
         # Turn response into a factor
         predictors = list(range(0,784))
         resp = 784
         train[resp] = train[resp].asfactor()
         test[resp] = test[resp].asfactor()
         nclasses = train[resp].nlevels()[0]

         # Train a deep learnring model
         model = H2ODeepLearningEstimator(activation="RectifierWithDropout",
                                          adaptive_rate=False,
                                          rate=0.01,
                                          rate_decay=0.9,
                                          rate_annealing=1e-6,
                                          momentum_start=0.95, 
                                          momentum_ramp=1e5, 
                                          momentum_stable=0.99,
                                          nesterov_accelerated_gradient=False,
                                          input_dropout_ratio=0.2,
                                          train_samples_per_iteration=20000,
                                          classification_stop=-1,  # Turn off early stopping
                                          l1=1e-5
                                         )
         model.train (x=predictors,y=resp, training_frame=train, validation_frame=test)

         # See the model performance
         model.model_performance(valid=True)
