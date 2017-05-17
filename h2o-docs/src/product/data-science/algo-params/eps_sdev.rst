``eps_sdev``
--------------------

- Available in: Naïve-Bayes
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the threshold value for standard deviation. If this threshold is not met, then the ``min_sdev`` value is used. 

This option is not set by default. When specified, this value must be greater than 0.


Related Parameters
~~~~~~~~~~~~~~~~~~

- `min_sdev <min_sdev.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # import the cars dataset
    cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

    # Specify model-building exercise (1:binomial, 2:multinomial)
    problem <- sample(1:2,1)

    # Specify response column based on predictor value and problem type
    predictors <- c("displacement","power","weight","acceleration","year")
    if ( problem == 1 ) { response_col <- "economy_20mpg"} else { response_col <- "cylinders" }

    # Convert the response column to a factor
    cars[,response_col] <- as.factor(cars[,response_col])

    # Specify model parameters
    laplace <- c(1)
    min_sdev <- c(0.1)
    eps_sdev <- c(0.5)

    # Build the model 
    cars_naivebayes <- h2o.naiveBayes(x=predictors, y=response_col, training_frame=cars, 
                                      eps_sdev=eps_sdev, min_sdev=min_sdev, laplace=laplace)
    print(cars_naivebayes)

    # Specify grid search parameters
    grid_space <- list()
    grid_space$laplace <- c(1,2)
    grid_space$min_sdev <- c(0.1,0.2)
    grid_space$eps_sdev <- c(0.5,0.6)

    # Specify response column based on predictor value and problem type
    predictors <- c("displacement","power","weight","acceleration","year")
    if ( problem == 1 ) { response_col <- "economy_20mpg"} else { response_col <- "cylinders" }

    # Convert the response column to a factor
    cars[,response_col] <- as.factor(cars[,response_col])

    # Construct the grid of naive bayes models
    cars_naivebayes_grid <- h2o.grid("naivebayes", grid_id="naiveBayes_grid_cars_test", 
                                     x=predictors, y=response_col, training_frame=cars, 
                                     hyper_params=grid_space)
    print(cars_naivebayes_grid)

   .. code-block:: python

    import h2o
    h2o.init()
    import random
    from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator
    from h2o.grid.grid_search import H2OGridSearch

    # import the cars dataset:
    cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

    # Specify model-building exercise (1:binomial, 2:multinomial)
    problem = random.sample(["binomial","multinomial"],1)

    # Specify response column based on predictor value and problem type
    predictors = ["displacement","power","weight","acceleration","year"]
    if problem == "binomial":
        response_col = "economy_20mpg"
    else:
        response_col = "cylinders"

    # Convert the response column to a factor
    cars[response_col] = cars[response_col].asfactor()

    # Train the model
    cars_nb = H2ONaiveBayesEstimator(min_sdev=0.1, eps_sdev=0.5, seed=1234)
    cars_nb.train(x=predictors, y=response_col, training_frame=cars)
    cars_nb.show() 
    
    # Predict on training data
    cars_pred = cars_nb.predict(cars)
    cars_pred.head()

    # Specify grid search parameters
    hyper_params = {'laplace':[1,2], 'min_sdev':[0.1,0.2], 'eps_sdev':[0.5,0.6]}

    # Construct the grid of naive bayes models
    cars_nb = H2ONaiveBayesEstimator(seed = 1234)
    cars_grid = H2OGridSearch(model=cars_nb, hyper_params=hyper_params)

    # Train using the grid
    cars_grid.train(x=predictors, y=response_col, training_frame=cars)
    cars_grid.show() 
