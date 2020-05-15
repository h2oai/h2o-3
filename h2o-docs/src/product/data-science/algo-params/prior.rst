``prior``
---------

- Available in: GLM, GAM
- Hyperparameter: no

Description
~~~~~~~~~~~

This option specifies the prior probability of class 1 in the response when ``family = "binomial"``.  The default value is the observation frequency of class 1. This must be a value from (0,1) exclusive range, and defaults to -1 (no prior). This parameter is useful for logistic regression if the data has been sampled and the mean of response does not reflect reality. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

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

        # convert response column to a factor
        cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

        # set the predictor names and the response column name
        predictors <- c("displacement", "power", "weight", "acceleration", "year")
        response <- "economy_20mpg"

        # split into train and validation
        cars_splits <- h2o.splitFrame(data = cars, ratios = 0.8)
        train <- cars_splits[[1]]
        valid <- cars_splits[[2]]

        # Build a GLM model and set a prior value of 0.5
        car_glm1 <- h2o.glm(x = predictors, y = response, family = 'binomial', prior=0.5,
                            training_frame = train, 
                            validation_frame = valid)

        # Build a GLM model without a prior value
        car_glm2 <- h2o.glm(x = predictors, y = response, family = 'binomial',
                            training_frame = train,
                            validation_frame = valid)

        # Check the coefficients for both models
        car_glm1@model$coefficients_table
        car_glm2@model$coefficients_table

   .. code-tab:: python

        import h2o
        from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        h2o.init()

        # import the cars dataset:
        # this dataset is used to classify whether or not a car is economical based on
        # the car's displacement, power, weight, and acceleration, and the year it was made
        cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

        # convert response column to a factor
        cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

        # set the predictor names and the response column name
        predictors = ["displacement","power","weight","acceleration","year"]
        response = "economy_20mpg"

        # split into train and validation sets
        train, valid = cars.split_frame(ratios = [.8])

        # Build a GLM model and set a prior value of 0.5
        cars_glm1 = H2OGeneralizedLinearEstimator(family = 'binomial', prior=0.5)
        cars_glm1.train(x = predictors, y = response, 
                        training_frame = train, 
                        validation_frame = valid)

        # Build a GLM model and set a prior value of 0.5
        cars_glm2 = H2OGeneralizedLinearEstimator(family = 'binomial')
        cars_glm2.train(x = predictors, y = response, 
                        training_frame = train, 
                        validation_frame = valid)

        # Check the coefficients for both models
        coeff_table1 = cars_glm1._model_json['output']['coefficients_table']
        coeff_table1

        coeff_table2 = cars_glm2._model_json['output']['coefficients_table']
        coeff_table2

