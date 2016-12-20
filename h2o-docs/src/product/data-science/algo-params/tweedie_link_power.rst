``tweedie_link_power``
----------------------

- Available in: GLM
- Hyperparameter: yes

Description
~~~~~~~~~~~

Tweedie distributions are a family of distributions that include gamma, normal, Poisson and their combinations. This distribution is especially useful for modeling positive continuous variables with exact zeros. When ``family=tweedie``, the ``tweedie_link_power`` option can be used to specify the power for the tweedie link function. The link functions :math:`g(\cdot)` are of the form :math:`g(\eta) = \eta^{link.power}`.

This option defaults to 1. 

The following describes the values that can be specified for this option:

- A value of 0 specifies a logarithm link (log-link) function. This is typically used for a count of occurrences in a fixed amount of time/space and is defined as **X**:math:`\beta = ln(\mu)`
- A value of 1 - vpow (1 minus the variance power) specifies a canonical link function. 
- A value of 1 specifies an identity link function. This is typically used for linear-response data and is defined as **X**:math:`\beta = \mu`
- A value of 2 specifies an inverse link function. This is defined as **X**:math:`\beta = \mu^{-2}`

The following table shows the acceptable relationships between family functions, tweedie variance powers, and tweedie link powers.

+------------------+------------------------+--------------------+
| Family Function  | Tweedie Variance Power | Tweedie Link Power |
+==================+========================+====================+
| Poisson          | 1                      | 0, 1-vpow, 1       |
+------------------+------------------------+--------------------+
| Gamma            | 2                      | 0, 1-vpow, 2       |
+------------------+------------------------+--------------------+
| Inverse-Gaussian | 3                      | 1, 1-vpow          |
+------------------+------------------------+--------------------+

Related Parameters
~~~~~~~~~~~~~~~~~~

- `family <family.html>`__
- `link <link.html>`__
- `tweedie_variance_power <tweedie_variance_power.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the auto dataset:
	# this dataset looks at features of motor insurance policies and predicts the aggregate claim loss
	# the original dataset can be found at https://cran.r-project.org/web/packages/HDtweedie/HDtweedie.pdf
	auto <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")

	# set the predictor names and the response column name
	predictors <- colnames(auto)[-1]
	# The  response is aggregate claim loss (in $1000s)
	response <- "y"

	# split into train and validation sets
	auto.splits <- h2o.splitFrame(data =  auto, ratios = .8)
	train <- auto.splits[[1]]
	valid <- auto.splits[[2]]

	# try using the `tweedie_link_power` parameter:
	# train your model, where you specify tweedie_link_power
	auto_glm <- h2o.glm(x = predictors, y = response, training_frame = train,
	                    validation_frame = valid,
	                    family = 'tweedie',
	                    tweedie_link_power = 1)

	# print the mse for validation set
	print(h2o.mse(auto_glm, valid=TRUE))

	# look at several values of `tweedie_link_power`
	# use the tweedie_variance_power (vp) with the tweedie_link_power to create the canonical link function
	vp_list = list(0, 1, 1.1, 1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2,
	           2.1, 2.2,2.3,2.4,2.5,2.6,2.7,2.8,2.9,3, 5, 7)

	# create a dataframe with the tweedie_variance_power, tweedie_link_power, and corresponding mse
	model_results <-lapply(vp_list, function(vp) {  
	  auto_glm_2 <- h2o.glm(x = predictors, y = response, training_frame = train,
	                       validation_frame = valid,
	                       family = 'tweedie', tweedie_variance_power = vp,
	                       tweedie_link_power = 1.0 - vp)
	  temp_df <- data.frame(vp, 1.0 - vp, h2o.mse(auto_glm_2, valid = TRUE))
	  names(temp_df) <- c("variance_power","link_power","mse")
	  return(temp_df)})   
	results = do.call('rbind',model_results)

	# print results
	results[order(results$mse),]

   .. code-block:: python

	import pandas as pd
	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	# import the auto dataset:
	# this dataset looks at features of motor insurance policies and predicts the aggregate claim loss
	# the original dataset can be found at https://cran.r-project.org/web/packages/HDtweedie/HDtweedie.pdf
	auto = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")

	# set the predictor names and the response column name
	predictors = auto.names
	predictors.remove('y')
	# The  response is aggregate claim loss (in $1000s)
	response = "y"

	# split into train and validation sets
	train, valid = auto.split_frame(ratios = [.8])

	# try using the `tweedie_link_power` parameter:
	# initialize the estimator then train the model
	auto_glm = H2OGeneralizedLinearEstimator(family = 'tweedie', tweedie_link_power = 1)
	auto_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the mse for the validation data
	print(auto_glm.mse(valid=True))

	# look at several values of `tweedie_link_power`
	# use the tweedie_variance_power (vp) with the tweedie_link_power to create the canonical link function
	vp_list = [0, 1, 1.1, 1.2,1.3,1.4,1.5,1.6,1.7,1.8,1.9,2,
	       2.1, 2.2,2.3,2.4,2.5,2.6,2.7,2.8,2.9,3, 5, 7]

	# loop though the values and append values to the list 'results'
	results = []
	for vp in vp_list:
	    auto_glm_2 = H2OGeneralizedLinearEstimator(family = 'tweedie',
	                                               tweedie_variance_power = vp,
	                                               tweedie_link_power = 1.0 - vp)
	    auto_glm_2.train(x = predictors, y = response, training_frame = train, validation_frame = valid)
	    results.append((vp, 1-vp, auto_glm_2.mse(valid=True)))
	    
	# create a pandas dataframe that has the tweedie_variance_power,tweedie_link_power, and corresponding mse
	pd.DataFrame(sorted(results, key=lambda triple: triple[2]), columns=['variance_power', 'link_power', 'mse'])