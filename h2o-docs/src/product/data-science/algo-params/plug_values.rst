``plug_values``
---------------

- Available in: GLM, GAM, HGLM, ANOVAGLM, ModelSelection
- Hyperparameter: yes

Description
~~~~~~~~~~~

When ``missing_values_handling="PlugValues"``, this option is used to specify a frame containing values that will be used to impute missing values. Whereas other options mean-impute rows or skip them entirely, plug values allow you to specify values of your own choosing in the form of a single row frame that contains the desired value.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `missing_values_handling <missing_values_handling.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()
        
        # import the cars dataset:
        cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        cars$name <- NULL

        # create an H2O frame using the mean of the cars dataset
        means <- h2o.mean(cars, na.rm = TRUE, return_frame = TRUE)

        # train GLM models, configuring plug_values in the second
        glm1 <- h2o.glm(training_frame = cars, y = "cylinders")
        glm2 <- h2o.glm(training_frame = cars, 
                        y = "cylinders", 
                        missing_values_handling = "PlugValues", 
                        plug_values = means)

        # determine if the coefficients are equal
        h2o.coef(glm1)
            Intercept       economy  displacement         power        weight 
         2.8316269982  0.0043748133  0.0141242460 -0.0030047140  0.0001410077 
         acceleration          year economy_20mpg 
        -0.0146035179  0.0017987846 -0.3754994243
        
        h2o.coef(glm2)
            Intercept       economy  displacement         power        weight 
         2.8316269982  0.0043748133  0.0141242460 -0.0030047140  0.0001410077 
         acceleration          year economy_20mpg 
        -0.0146035179  0.0017987846 -0.3754994243

   .. code-tab:: python

        import h2o
        from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        from h2o import H2OFrame
        from h2o.expr import ExprNode
        h2o.init()

        # import the cars dataset:
        cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
        cars = cars.drop(0)

        # create an H2O frame using the mean of the cars dataset
        means = cars.mean()
        means = H2OFrame._expr(ExprNode("mean", cars, True, 0))

        # train a GLM
        glm_means = H2OGeneralizedLinearEstimator(seed=42)
        glm_means.train(training_frame=cars, y="cylinders")

        # configure plug_values in a second model
        glm_plugs1 = H2OGeneralizedLinearEstimator(seed=42,
                                                   missing_values_handling="PlugValues",
                                                   plug_values=means)
        glm_plugs1.train(training_frame=cars, y="cylinders")
        
        # check that the GLM coefficients are equal
        glm_means.coef() == glm_plugs1.coef()

        # modify the means to use with another GLM
        not_means = 0.1 + (means * 0.5)

        # configure plug values for the second model
        glm_plugs2 = H2OGeneralizedLinearEstimator(seed=42,
                                                   missing_values_handling="PlugValues",
                                                   plug_values=not_means)
        glm_plugs2.train(training_frame=cars, y="cylinders")

        # confirm that plug values are not being ignored
        glm_means.coef() != glm_plugs2.coef()

