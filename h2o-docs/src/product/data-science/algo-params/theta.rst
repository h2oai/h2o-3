``theta``
---------

- Available in: GLM, GAM
- Hyperparameter: no

Description
~~~~~~~~~~~

In GLM, negative binomial regression is a generalization of Poisson regression that loosens the restrictive assumption that the variance is equal to the mean. Instead, the variance of negative binomial regression is a function of its mean and parameter :math:`\theta`, the dispersion parameter. 

The ``theta`` parameter allows you to specify this dispersion value. This option must be > 0 and defaults to 1e-10. In addition, this option can only be used when ``family=negativebinomial``.

Refer to the :ref:`negative_binomial` topic for more inforamtion on how the ``theta`` value is used in negative binomial regression problems.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `family <family.html>`__
- `link <link.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# Import the Swedish motor insurance dataset
		h2o_df = h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt")

		# Set the predictor names and the response column
		predictors <- c["Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make"]
		response <- "Claims"

		# Train the model
		negativebinomial_fit <- h2o.glm(x = predictors, 
		                                y = response, 
		                                training_frame = h2o_df, 
		                                family = "negativebinomial", 
		                                link = "identity", 
		                                theta = 0.5)

   .. code-tab:: python

		import h2o
		from h2o.estimators.glm import H2OGeneralizedLinearEstimator
		h2o.init()

		# Import the Swedish motor insurance dataset
		h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt")

		# Set the predictor names and the response column
		predictors = ["Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make"]
		response = "Claims"

		# Train your model
		negativebinomial_fit = H2OGeneralizedLinearEstimator(family="negativebinomial", 
		                                                     link="identity",
		                                                     theta=0.5)
		negativebinomial_fit.train(x=predictors, y=response, training_frame=h2o_df)

