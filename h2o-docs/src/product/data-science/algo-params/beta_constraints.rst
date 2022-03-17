``beta_constraints``
--------------------

- Available in: GLM, GAM
- Hyperparameter: no

Description
~~~~~~~~~~~

Beta constraints allow you to set special conditions over the model coefficients. Currently supported constraints are upper and lower bounds and the proximal operator interface, as described in Proximal Algorithms by Boyd et. al. The proximal operator interface allows you to run GLM or GAM with a proximal penalty on a distance from a specified given solution. There are many potential uses: for example, it can be used as part of an ADMM consensus algorithm to obtain a unified solution over separate H2O clouds or in Bayesian regression approximation.

Use the ``beta_constraints`` parameter to specify a data frame or ``H2OParsedData`` object with the beta constraints, where each row corresponds to a predictor in the GLM/GAM. The selected frame is used to constrain the coefficient vector to provide upper and lower bounds. The constraints are specified as a frame with the following vectors (matched by name; all vecs can be sparse):

 - ``names``: (mandatory) Specifies the predictor names. This is required. The dataset must contain a names column with valid coefficient names.
 - ``lower_bounds``: (optional) The lower bounds of the beta. Must be less than or equal to upper_bounds.
 - ``upper_bounds``: (optional) The upper bounds of the beta. Must be greater than or equal to ``lower_bounds``.
 - ``beta_given``: (optional) Specifies the given solution. This works as a base in proximal penalty.
 - ``beta_start``: (optional) Specifies the starting solution. This is useful for warm-starting GLM/GAM. (Otherwise, the algorithm starts with all zeros.)
 - ``rho``: (mandatory if ``beta_given`` is specified; otherwise ignored): Specifies the per-column :math:`\ell_2` penalties on the distance from the given solution.
 - ``mean``: (optional) Specifies the mean override (for data standardization).
 - ``std_dev``: (optional) Specifies the standard deviation override (for data standardization).

Running GLM/GAM with Weights and Beta Constraints
'''''''''''''''''''''''''''''''''''''''''''''''''

When running GLM or GAM with weights, the expectation is that it will produce the same model as up-sampling the training dataset. This is true when standardization is turned on and without beta constraints. But if you add beta constraints, the up-sampled and weighted cases produce different GLM/GAM models with different coefficients. This occurs for a couple of reasons:

- Priors is standardized and changed when standardization is turned on for the model build. When standardization is turned on, the ``beta_given`` in ``beta_constraints`` is standardized as well. In the `code <https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/main/java/hex/glm/GLM.java#L1902>`__, you see that the ``beta_given`` is multiplied by factor ``d : _betaGiven *= d;``` where ``d = 1/sd``. In particular, be careful when using previous coefficients as priors and with standardization turned on because the penalty taken on different priors.

- When weights is turned on, the variance will differ from the variance in an up-sampled dataset. The way variance is typically calculated is:

    :math:`s2 = summation((x_i – x)^2) / N -1`

 When using weights, the weighted variance is calculated as:

    :math:`s2 = (N/ (N-1)) * (summation(w_i (x_i – x)^2) / summation(w_i))`

 So when the sum of weights equals the number of observations, you have the exact same variance; otherwise your variance will differ by a factor of approximately N/N-1, which is a relatively small difference but something you will observe in your resulting coefficients.

If you want to supply the beta constraints for a standardized model, scale your bounds and priors in ``beta_constraints`` down by the variance so that you will have ``(1/d)* betaGiven *= d;`` which equals ``betaGiven``.

**Notes**:

- ``beta_constraints`` is not supported for ``family="multinomial"`` or ``family="ordinal"``.
- P-values cannot be computed for constrained problems.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import the prostate dataset

        prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

        # Set the predictor names and the response column name
        p_y <- "CAPSULE"
        p_x <- setdiff(names(prostate), c(p_y, "ID"))
        p_n <- length(p_x)

        # Create a beta_constraints frame
        con <- data.frame(names = p_x, 
                          lower_bounds = rep(-10000, time = p_n),
                          upper_bounds = rep(10000, time = p_n),
                          beta_given = rep(1, time = p_n),
                          rho = rep(0.2, time = p_n))

        # Build a GLM with beta constraints and standardization.
        # When standardization is turned on, the beta_given is also standardized.
        glm1 <- h2o.glm(x = p_x, 
                        y = p_y, 
                        training_frame = prostate, 
                        standardize = TRUE, 
                        beta_constraints = con)

        # Build a GLM with beta constraints and without standardization
        glm2 <- h2o.glm(x = p_x, 
                        y = p_y, 
                        training_frame = prostate, 
                        standardize = FALSE, 
                        beta_constraints = con)

        # Check the coefficients for both models
        glm1@model$coefficients_table
        glm2@model$coefficients_table

   .. code-tab:: python

        import h2o
        h2o.init()
        from h2o.estimators.glm import H2OGeneralizedLinearEstimator

        # Import the prostate dataset
        prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

        # Set the predictor names and the response column name
        response = "CAPSULE"
        predictor = prostate.names[2:9]
        n = len(predictor)

        # Create a beta_constraints frame
        constraints = h2o.H2OFrame({'names':predictor,
                                    'lower_bounds': [-1000]*n,
                                    'upper_bounds': [1000]*n,
                                    'beta_given': [1]*n,
                                    'rho': [0.2]*n})

        # Build a GLM model with beta constraints and standardization
        prostate_glm1 = H2OGeneralizedLinearEstimator(standardize=True, beta_constraints=constraints)
        prostate_glm1.train(x = predictor, y = response, training_frame=prostate)

        # Build a GLM model with beta constraints and without standardization
        prostate_glm2 = H2OGeneralizedLinearEstimator(standardize=False, beta_constraints=constraints)
        prostate_glm2.train(x = predictor, y = response, training_frame=prostate)

        # Check the coefficients for both models
        coeff_table1 = prostate_glm1._model_json['output']['coefficients_table']
        coeff_table1

        coeff_table2 = prostate_glm2._model_json['output']['coefficients_table']
        coeff_table2
