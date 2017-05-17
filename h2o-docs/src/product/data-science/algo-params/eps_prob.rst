``eps_prob``
--------------------

- Available in: Naïve-Bayes
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies the threshold value for probability. If this threshold is not met, then the ``min_prob`` value is used. This option can be used, for example, if one response category has very few observations compared to the total. In this case, the conditional probability may be very low. The ``min_sdev`` and ``eps_prob`` values serve as a cutoff by setting a floor on the calculated probability.

This option is not set by default. When specified, this value must be greater than 0.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `min_prob <min_prob.html>`__

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
    min_prob <- c(0.1)
    eps_prob <- c(0.5)

    # Build the model 
    cars_naivebayes <- h2o.naiveBayes(x=predictors, y=response_col, training_frame=cars, 
                                      eps_prob=eps_prob, min_prob=min_prob, laplace=laplace)
    print(cars_naivebayes)

    # Predict on training data
    cars_naivebayes.pred <- predict(cars_naivebayes, cars)
    print(head(cars_naivebayes.pred))

    # Specify grid search parameters
    grid_space <- list()
    grid_space$laplace <- c(1,2)
    grid_space$min_prob <- c(0.1,0.2)
    grid_space$eps_prob <- c(0.5,0.6)

    # Construct the grid of naive bayes models
    cars_naivebayes_grid <- h2o.grid(x=predictors, y=response_col, training_frame=cars, 
                                     algorithm="naivebayes", grid_id="naiveBayes_grid_cars_test", 
                                     hyper_params=grid_space)
    print(cars_naivebayes_grid)

   .. code-block:: python

    import h2o
    h2o.init()
    import random
    from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator

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
    cars_nb = H2ONaiveBayesEstimator(min_prob=0.1, eps_prob=0.5, seed=1234)
    cars_nb.train(x=predictors, y=response_col, training_frame=cars)
    cars_nb.show() 
    
    # Predict on training data
    cars_pred = cars_nb.predict(cars)
    cars_pred.head()

    # Specify grid search parameters
    from h2o.grid.grid_search import H2OGridSearch
    hyper_params = {'laplace':[1,2], 'min_prob':[0.1,0.2], 'eps_prob':[0.5,0.6]}

    # Construct the grid of naive bayes models
    cars_nb2 = H2ONaiveBayesEstimator(seed = 1234)
    cars_grid = H2OGridSearch(model=cars_nb2, hyper_params=hyper_params)

    # Train using the grid
    cars_grid.train(x=predictors, y=response_col, training_frame=cars)
    cars_grid.show() 
