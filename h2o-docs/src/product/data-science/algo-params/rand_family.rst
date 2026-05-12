``rand_family``
---------------

- Available in: HGLM
- Hyperparameter: no

Description
~~~~~~~~~~~

Hierarchical GLM (HGLM) fits generalized linear models with random effects, where the random effect can come from a conjugate exponential-family distribution (for example, Gaussian). The ``rand_family`` option specifies the Random Family Component as an array to be used in GLM when ``HGLM=True``. 

**Note:** You must include one family for each random component. Only ``rand_family=["gaussian"]`` is currently supported.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `HGLM <hglm.html>`__
- `random_columns <random_columns.html>`__

Example
~~~~~~~

.. tabs::
  .. code-tab:: r R

      library(h2o)
      h2o.init()

      # Import the semiconductor dataset
      h2odata <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/semiconductor.csv")

      # Set the response, predictor, and random columns
      yresp <- "y"
      xlist <- c("x1", "x3", "x5", "x6")
      z <- c(1)

      # Convert the "Device" column to a factor
      h2odata$Device <- h2o.asfactor(h2odata$Device)

      # Train and view the model
      h2o_glm <- h2o.glm(x = xlist,
                         y = yresp,
                         family = "gaussian",
                         rand_family = c("gaussian"),
                         rand_link = c("identity"),
                         training_frame = h2odata,
                         HGLM = TRUE,
                         random_columns = z,
                         calc_like = TRUE)
      print(h2o_glm)

  .. code-tab:: python

      import h2o
      from h2o.estimators.glm import H2OGeneralizedLinearEstimator
      h2o.init()

      # Import the semiconductor dataset
      h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/semiconductor.csv")

      # Set the response, predictor, and random columns
      y = "y"
      x = ["x1","x3","x5","x6"]
      z = [0]

      # Convert the "Device" column to a factor
      h2o_data["Device"] = h2o_data["Device"].asfactor()

      # Train and view the model
      h2o_glm = H2OGeneralizedLinearEstimator(HGLM=True, 
                                              family="gaussian", 
                                              rand_family=["gaussian"], 
                                              random_columns=z,
                                              rand_link=["identity"],
                                              calc_like=True)
      h2o_glm.train(x=x, y=y, training_frame=h2o_data)
      print(h2o_glm)
