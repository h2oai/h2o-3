RuleFit
-------

Introduction
~~~~~~~~~~~~

H2O's Rulefit algorithm combines tree ensembles and linear models to take advantage of both methods: the accuracy of a tree ensemble and the interpretability of a linear model.

The general algorithm fits a tree ensemble to the data, builds a rule ensemble by traversing each tree, evaluates the rules on the data to build a rule feature set, and fits a sparse linear model (LASSO) to the rule feature set joined with the original feature set.

Defining a RuleFit Model (Beta API)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.
- `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. 

	**Note:** In Flow, if you click the **Build a model** button from the Parse cell, the training frame is entered automatically.

- `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate the accuracy of the model.
- `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternate configurations. This value defaults to -1 (time-based random number).
- `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable.

	- For a regression model, this column must be numeric (**Real** or **Int**).
	- For a classification model, this column must be categorical (**Enum** or **String**). If the family is **Binomial**, the dataset cannot contain more than two levels.

- `x <algo-params/x.html>`__: Specify a vector containing the names or indicies of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

- **algorithm**: The algorithm to use to fit a tree ensemble. Must be one of: "AUTO", "DRF", or "GBM". Defaults to "DRF".

- **min_rule_length**: Specify the minimal depth of trees to be fit. Defaults to 1.

- **max_rule_length**: Specify the maximal  depth of trees to be fit. Defaults to 10.

- **max_num_rules**: The maximum number of rules to return. Defaults to -1, which means the number of rules are selected by diminishing returns in model deviance.

- **model_type**: Specify the type of base learners in the ensemble. Must be one of: "RULES_AND_LINEAR", "RULES", or "LINEAR". Defaults to "RULES_AND_LINEAR".

- `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 

	**Python only:** To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``.

	**Note:** Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more due to the larger loss function pre-factor.

- `distribution <algo-params/distribution.html>`__: Specify the distribution (i.e. the loss function). The options are AUTO, bernoulli, multinomial, gaussian, poisson, gamma, laplace, quantile, huber, or tweedie.

	- If the distribution is ``bernoulli``, the response column must be 2-class categorical.
	- If the distribution is ``quasibinomial``, the response column must be numeric and binary.
	- If the distribution is ``multinomial``, the response column must be categorical.
	- If the distribution is ``poisson``, the response column must be numeric.
	- If the distribution is ``tweedie``, the response column must be numeric.
	- If the distribution is ``gaussian``, the response column must be numberic.
	- If the distribution is ``gamma``, the response column must be numeric.
	- If the distribution is ``fractionalbinomial``, the response column must be numeric between 0 and 1.
	- If the distribution is ``negativebinomial``, the response column must be numeric and non-negative.
	- If the distribution is ``ordinal``, the response column must be categorical with at least 3 levels. 
	- If the distribution is ``AUTO``,

		- and the response is **Enum** with cardinality = 2, then the family is automatically determined as **bernoulli**.
		- and the response is **Enum** with cardinality > 2, then the family is automatically determined as **multinomial**.
		- and the response is numeric (**Real** or **Int**), then the family is automatically determined as **gaussian**.

Interpreting a RuleFit Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The output for the RuleFit model includes:

- model parameters
- rule importances in tabular form
- training and validation metrics of the underlying linear model

Examples
~~~~~~~~

.. tabs::
	.. code-tab:: r R

		library(h2o)
		h2o.init()

		# Import the titanic dataset:
		f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
		titanic <- h2o.importFile(f)

		# Set the predictors and response; set the factors:
		response = "survived"
		predictors <- c("age", "sibsp", "parch", "fare", "sex", "pclass")
		titanic[,response] <- as.factor(titanic[,response])
		titanic[,"pclass"] <- as.factor(titanic[,"pclass"])

		# Build and train the model:
		rf_h2o = h2o.rulefit(y = response, x = predictions, training_frame = titanic, max_rule_length = 10, max_num_rules = 100, seed = 1234)

		# Retrieve the rule importance:
		print(rf_h2o@model$rule_importance)


	.. code-tab:: python

		import h2o
		h2o.init()
		from h2o.estimators import H2ORuleFitEstimators

		# Import the titanic dataset and set the column types:
		df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv", 
				     col_types={'pclass': "enum", 'survived': "enum"})

		# Set the predictors and response:
		x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
		y = "survived"

		# Build and train the model:
		rf_h2o = H2ORuleFitEstimator(max_rule_length=10, 
					     max_num_rules=100, 
					     seed = 1234, 
					     model_type="rules_and_linear")
		rf_h2o.train(training_frame=df, x=x, y=y)

		# Retrieve the rule importance:
		print(rf_h2o._model_json['output']['rule_importance'])


References
~~~~~~~~~~

FRIEDMAN, J. H., & POPESCU, B. E. (2008). Predictive learning via rule ensembles. The Annals of Applied Statistics, 2(3), 916-954.