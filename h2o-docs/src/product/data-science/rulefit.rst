RuleFit
-------

Introduction
~~~~~~~~~~~~

H2O's Rulefit algorithm combines tree ensembles and linear models to take advantage of both methods: the accuracy of a tree ensemble and the interpretability of a linear model.

The general algorithm fits a tree ensemble to the data, builds a rule ensemble by traversing each tree, evaluates the rules on the data to build a rule feature set, and fits a sparse linear model (LASSO) to the rule feature set joined with the original feature set.

MOJO Support
''''''''''''

RuleFit supports importing and exporting `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining a RuleFit Model (Beta API)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

- **algorithm**: Specify the algorithm to use to fit a tree ensemble. Must be one of: ``"AUTO"``, ``"DRF"`` (default), or ``"GBM"``. 

- `lambda <algo-params/lambda.html>`__: Specify the regularization strength for LASSO regressor.

- **max_categorical_levels**: Rulefit handles categorical features by EnumLimited scheme. That means it automatically reduces categorical levels to the most prevalent ones and only keeps the ``max_categorical_levels`` most frequent levels. This option defaults to ``10``.

- **max_num_rules**: The maximum number of rules to return. This option defaults to ``-1`` which means the number of rules are selected by diminishing returns in model deviance.

- **max_rule_length**: Specify the maximal  depth of trees to be fit. This option defaults to ``3``.

- **min_rule_length**: Specify the minimal depth of trees to be fit. This option defaults to ``3``.

- **model_type**: Specify the type of base learners in the ensemble. Must be one of: ``"rules_and_linear"`` (default), ``"rules"``, or ``"linear"``. 

    - If the ``model_type`` is ``rules_and_linear``, the algorithm fits a linear model to the rule feature set joined with the original feature set.
    - If the ``model_type`` is ``rules``, the algorithm fits a linear model only to the rule feature set (no linear terms can become important).
    - If the ``model_type`` is ``linear``, the algorithm fits a linear model only to the original feature set (no rule terms can become important).

- **remove_duplicates**: Specify whether to remove rules which are identical to an earlier rule. This option defaults to ``True`` (enabled).

- **rule_generation_ntrees**: Specify the number of trees for tree ensemble. This option defaults to ``50``.

Common parameters
'''''''''''''''''

- `auc_type <algo-params/auc_type.html>`__: Set the default multinomial AUC type. Must be one of:

    - ``"AUTO"`` (default)
    - ``"NONE"``
    - ``"MACRO_OVR"``
    - ``"WEIGHTED_OVR"``
    - ``"MACRO_OVO"``
    - ``"WEIGHTED_OVO"``

- `distribution <algo-params/distribution.html>`__: Specify the distribution (i.e. the loss function). The options are:

	- ``AUTO`` (default)
	- ``bernoulli`` -- response column must be 2-class categorical
	- ``multinomial`` -- response column must be categorical
	- ``gaussian`` -- response column must be numeric
	- ``poisson`` -- response column must be numeric
	- ``gamma`` -- response column must be numeric
	- ``laplace`` -- response column must be numeric
	- ``quantile`` -- response column must be numeric
	- ``huber`` -- response column must be numeric
	- ``tweedie`` -- response column must be numeric

- `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

- `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternate configurations. This option defaults to ``-1`` (time-based random number).

- `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
	
	**Note:** In Flow, if you click the **Build a model** button from the Parse cell, the training frame is entered automatically.

- `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.

- `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 

   **Python only:** To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``.

   **Note:** Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more due to the larger loss function pre-factor.

- `x <algo-params/x.html>`__: Specify a vector containing the names or indicies of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

- `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable.

   - For a regression model, this column must be numeric (**Real** or **Int**).
   - For a classification model, this column must be categorical (**Enum** or **String**). If the family is **Binomial**, the dataset cannot contain more than two levels.

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
		coltypes <- list(by.col.name = c("pclass", "survived"), types=c("Enum", "Enum"))
		df <- h2o.importFile(f, col.types = coltypes)

		# Split the dataset into train and test
		splits <- h2o.splitFrame(data = df, ratios = 0.8, seed = 1)
		train <- splits[[1]]
		test <- splits[[2]]

		# Set the predictors and response; set the factors:
		response <- "survived"
		predictors <- c("age", "sibsp", "parch", "fare", "sex", "pclass")

		# Build and train the model:
		rfit <- h2o.rulefit(y = response,
		                    x = predictors,
		                    training_frame = train,
		                    max_rule_length = 10,
		                    max_num_rules = 100,
		                    seed = 1)

		# Retrieve the rule importance:
		print(rfit@model$rule_importance)

		# Predict on the test data:
		h2o.predict(rfit, newdata = test)


	.. code-tab:: python

		import h2o
		h2o.init()
		from h2o.estimators import H2ORuleFitEstimator

		# Import the titanic dataset and set the column types:
		f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
		df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})

		# Split the dataset into train and test
		train, test = df.split_frame(ratios=[0.8], seed=1)

		# Set the predictors and response:
		x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
		y = "survived"

		# Build and train the model:
		rfit = H2ORuleFitEstimator(max_rule_length=10, 
		                           max_num_rules=100, 
		                           seed=1)
		rfit.train(training_frame=train, x=x, y=y)

		# Retrieve the rule importance:
		print(rfit.rule_importance())

		# Predict on the test data:
		rfit.predict(test)


References
~~~~~~~~~~

`Friedman, J. H., & Popescu, B. E. (2008). Predictive learning via rule ensembles. The Annals of Applied Statistics, 2(3), 916-954.  <https://arxiv.org/abs/0811.1679>`__

