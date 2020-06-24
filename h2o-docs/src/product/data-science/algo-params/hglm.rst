``HGLM``
--------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

Generalized Linear Models (GLM) estimate regression models for outcomes following exponential distributions. Hierarchical GLM (HGLM) fits generalized linear models with random effects, where the random effect can come from a conjugate exponential-family distribution (for example, Gaussian). HGLM allows you to specify both fixed and random effects, which allows fitting correlated to random effects as well as random regression models. 

HGLM produces estimates for fixed effects, random effects, variance components and their standard errors. It also produces diagnostics, such as variances and leverages. 

The ``hglm`` option allows you to build a hierarchical generalized linear model. This option is disabled by default.

**Note**: This initial release of HGLM supports only Gaussian for ``family`` and ``rand_family``.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `random_columns <random_columns.html>`__
- `rand_family <rand_family.html>`__

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
        m11H2O <- h2o.glm(x = xlist, 
                          y = yresp, 
                          family = "gaussian", 
                          rand_family = c("gaussian"), 
                          rand_link = c("identity"), 
                          training_frame = h2odata, 
                          HGLM = TRUE, 
                          random_columns = z, 
                          calc_like = TRUE)
        print(m11H2O)

   .. code-tab:: python

        import h2o
        from h2o.estimators.glm import H2OGeneralizedLinearEstimator
        h2o.init()

        # Import the semiconductor dataset
        h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/semiconductor.csv")

        # Set the response, predictor, and random columns
        y = "y"
        x = ["x1","x3","x5","x6"]
        z = 0

        # Convert the "Device" column to a factor
        h2o_data["Device"] = h2o_data["Device"].asfactor()

        # Train and view the model
        h2o_glm = H2OGeneralizedLinearEstimator(HGLM=True, 
                                                family="gaussian", 
                                                rand_family=["gaussian"], 
                                                random_columns=[z],
                                                rand_link=["identity"],
                                                calc_like=True)
        h2o_glm.train(x=x, y=y, training_frame=h2o_data)
        print(h2o_glm)
