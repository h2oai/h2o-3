Hierarchical Generalized Linear Model (HGLM) 
============================================

HGLM fits generalized linear models with random effects, where the random effect can come from a conjugate exponential-family distribution (for example, gaussian). HGLM lets you specify both fixed and random effects, which allows for fitting correlation to random effects as well as random regression models. HGLM can be used for linear mixed models and for generalized linear mixed models with random effects for a variety of links and a variety of distributions for both the outcomes and the random effects.

Defining an HGLM model
----------------------
Parameters are optional unless specified as *required*.

Algorithm-specific parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- **em_epsilon**: (Only available for EM method) Converge if beta/ubeta/tmat/tauEVar changes less (using L-infinity norm) than EM epsilon (defaults to ``0.001``).

- **gen_syn_data**: If enabled, will add gaussian noise with variance specified in ``tau_e_var_init`` (defaults to ``False``).

- **group_column**: The column that is categorical and used to generate the groups in HGLM (defaults to ``None``).

- **initial_fixed_effects**: An array that contains the initial values of the fixed effects coefficient (defaults to ``None``).

- **initial_random_effects**: An H2OFrame ID tht contains the initial values of the random effects coefficietn. The row names should be the random coefficient names (defaults to ``None``).
	
	.. note::

		If you aren't sure wha the random coefficient names are, then build the HGLM model with ``max_iterations=0`` and check out the model output field ``random_coefficient_names``. The number of rows of this frame should be the number of level 2 units. To figure this out, build the HGLM model with ``max_iterations=0`` and check out the model output field ``group_column_names``. The number of rows should equal the length of the ``group_column_names``.

- **initial_t_matrix**: An H2OFrame ID that contains the initial values of the T matrix. It should be a positive symmetric matrix (defaults to ``None``).

- **method**: Obtains the fixed and random coefficients as well as the various variances (defaults to ``"em"``).

- `random_columns <algo-params/random_columns.html>`__: An array of random column indices to be used for ``HGLM``.

- **random_intercept**: If enabled, will allow random component to the GLM coefficients (defaults to ``True``).

- **tau_e_var_init**: Initial varience of random noise. If set, this should provide a value of > 0.0. If not set, this will be randomly set during the model building process (defaults to ``0.0``).

- **tau_u_var_init**: Initial variance of random coefficient effects. If set, should provide a value > 0.0. If not set, this will be randomly set during the model building process (defaults to ``0.0``).

GLM-family parameters
~~~~~~~~~~~~~~~~~~~~~

-  `family <algo-params/family.html>`__: Specify the model type.

   -  If the family is ``gaussian``, the response must be numeric (**Real** or **Int**).
   -  If the family is ``binomial``, the response must be categorical 2 levels/classes or binary (**Enum** or **Int**).
   -  If the family is ``fractionalbinomial``, the response must be a numeric between 0 and 1.
   -  If the family is ``multinomial``, the response can be categorical with more than two levels/classes (**Enum**).
   -  If the family is ``ordinal``, the response must be categorical with at least 3 levels.
   -  If the family is ``quasibinomial``, the response must be numeric.
   -  If the family is ``poisson``, the response must be numeric and non-negative (**Int**).
   -  If the family is ``negativebinomial``, the response must be numeric and non-negative (**Int**).
   -  If the family is ``gamma``, the response must be numeric and continuous and positive (**Real** or **Int**).
   -  If the family is ``tweedie``, the response must be numeric and continuous (**Real**) and non-negative.
   -  If the family is ``AUTO`` (default),

      - and the response is **Enum** with cardinality = 2, then the family is automatically determined as ``binomial``.
      - and the response is **Enum** with cardinality > 2, then the family is automatically determined as ``multinomial``.
      - and the response is numeric (**Real** or **Int**), then the family is automatically determined as ``gaussian``.

-  `rand_family <algo-params/rand_family.html>`__: The Random Component Family specified as an array. You must include one family for each random component. Currently only ``rand_family=["gaussisan"]`` is supported.

-  `plug_values <algo-params/plug_values.html>`__: (Applicable only if ``missing_values_handling="PlugValues"``) Specify a single row frame containing values that will be used to impute missing values of the training/validation frame.


Common parameters
~~~~~~~~~~~~~~~~~

- `custom_metric_func <algo-params/custom_metric_func.html>`__: Specify a custom evaluation function.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Enable this option to ignore constant training columns, since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the number of training iterations. This options defaults to ``-1``.

- `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. Use ``0`` (default) to disable. 

-  `missing_values_handling <algo-params/missing_values_handling.html>`__: Specify how to handle missing values. One of: ``Skip``, ``MeanImputation`` (default), or ``PlugValues``.

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `offset_column <algo-params/offset_column.html>`__: Specify a column to use as the offset; the value cannot be the same as the value for the ``weights_column``.
   
     **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (``y``) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. 

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Enable this option to score during each iteration of the model training. This option defaults to ``False`` (disabled).
- score_values_handling
-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).
-  `standardize <algo-params/standardize.html>`__: Specify whether to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option defaults to ``True`` (enabled).
-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.
-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.
-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more due to the larger loss function pre-factor.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable.

   -  For a regression model, this column must be numeric (**Real** or **Int**).
   -  For a classification model, this column must be categorical (**Enum** or **String**). If the family is ``Binomial``, the dataset cannot contain more than two levels.

Definining an HLM
-----------------

Hierarchical linear models (HLM) is used in situations where measurements are taken with clusters of data and there are effects of the cluster that can affect the coefficient values of GLM. For instance, if we measure the students' performances from multiple schools along with other predictors like family annual incomes, students' health, school type (public, private, religious, etc.), and etc., we suspect that students from the same school will have similar performances than students from different schools. Therefore, we can denote a coefficient for predictor :math:`m \text{ as } \beta_{mj}` where :math:`j` denotes the school index in our example. :math:`\beta_{0j}` denotes the intercept associated with school :math:`j`.

References
----------

David Ruppert, M. P. Wand and R. J. Carroll, Semiparametric Regression, Chapter 4, Cambridge University Press, 2003.

Stephen w. Raudenbush, Anthony S. Bryk, Hierarchical Linear Models Applications and Data Analysis Methods, Second Edition, Sage Publications, 2002.

Rao, C. R. (1973). Linear Statistical Inference and Its Applications. New York: Wiley. 

Dempster, A. P., Laird, N. M., & Rubin, D. B. (1977). Maximum likelihood from incomplete data via the EM algorithm. Journal of the Royal Statistical Society, Seires B, 39, 1-8.