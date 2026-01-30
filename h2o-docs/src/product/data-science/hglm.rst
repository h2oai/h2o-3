Hierarchical Generalized Linear Model (HGLM) 
============================================

Introduction
------------

Hierarchical linear models (HLM) are used in situations where measurements are taken with clusters of data and there are effects of the cluster that can affect the coefficient values of GLM. 

For instance, if we measure the performance of students from multiple schools along with other predictors like family annual incomes, students' health, school type (public, private, religious, etc.), and etc., we would suspect that students from the same school will have similar performances than students from different schools. Therefore, we can denote a coefficient for predictor :math:`m \text{ as } \beta_{mj}` where :math:`j` denotes the school index in our example. :math:`\beta_{0j}` denotes the intercept associated with school :math:`j`.

A level-1 HLM can be expressed as:

.. math::
   
   y_{ij} = \beta_{0j} + \sum_{m=1}^{p-1} x_{mij} \beta{mj} + \varepsilon_{ij} \quad \text{ equation 1}

The level-2 model can be expressed as:
   
.. math::
   
   \beta_{0j} = \beta_{00} + u_{0j}, \beta_{mj} = \beta_{m0} + u_{mj} \quad \text{ equation 2}

where:

- :math:`j(=[1,2,...,J])` denotes the cluster (level-2 variable) the measurement is taken from (e.g. the school index);
- :math:`i(=1,2,...,n_j)` denotes the data index taken from within cluster :math:`j`;
- :math:`\beta_{00}` is the fixed intercept;
- :math:`\beta_{0j}` is the random intercept;
- :math:`\beta_{m0}` is the fixed coefficient for predictor :math:`m`;
- The dimension of fixed effect coefficients is :math:`p` which includes the intercept;
- :math:`u_{mj}` is the random coefficient for predictor :math:`m`. For predictors without a random coefficient, :math:`u_{mj} = 0`;
- The dimension of the random effect coefficients is :math:`q` which can include the intercept. Note that :math:`q \leq p`;
- :math:`\varepsilon_{ij} \sim N(0, \delta_e^2)`;
- :math:`u_{ij} \sim N(0, \delta_u^2)`:
- :math:`\varepsilon_{ij}, u_{mj}` are independent;
- :math:`u_{mj}, u_{m^{'}j}` are independent if :math:`m \neq m^{'}`.

We need to solve the following parameters: :math:`\beta_{00}, \beta_{0j}, \beta_{m0}, u_{mj}, \delta_e^2, \delta_u^2`.

Defining an HGLM model
----------------------
Parameters are optional unless specified as *required*.

Algorithm-specific parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- **em_epsilon**: (Only available for EM method) Converge if beta/ubeta/tmat/tauEVar changes less (using L-infinity norm) than EM epsilon (defaults to ``0.001``).

- **gen_syn_data**: If enabled, it will generate synthetic HGLM data with the fixed coefficients specified in ``initial_fixed_effects`` and the random coefficients taken from ``initial_random_effects`` or the random effects are randomly generated. In particular, it will generate the folowing output: :math:`Y_j = A_{fj} \theta_f + A_{rj} \theta_{rj} + r_j`. The gaussian noise is generated with variance that's specified in ``tau_e_var_init``. If the random coefficients are to be randomly generated, they are generated with gaussian distribution with variance that's specified in ``tau_u_var_init``.

- **group_column**: Specify the level-2 variable name which is categorical and used to generate the groups in HGLM (defaults to ``None``).

- **initial_fixed_effects**: An array that contains the initial values of the fixed effects coefficient (defaults to ``None``).

- **initial_random_effects**: An H2OFrame ID that contains the initial values of the random effects coefficient. The row names should be the random coefficient names (defaults to ``None``).
	
	.. note::

		If you aren't sure what the random coefficient names are, then build the HGLM model with ``max_iterations=0`` and check out the model output field ``random_coefficient_names``. The number of rows of this frame should be the number of level 2 units. Check out the model output field ``group_column_names``. The number of rows should equal the length of the ``group_column_names``.

- **initial_t_matrix**: An H2OFrame ID that contains the initial values of the T matrix. It should be a positive symmetric matrix (defaults to ``None``).

- **method**: Obtains the fixed and random coefficients as well as the various variances (defaults to ``"em"``).

- `random_columns <algo-params/random_columns.html>`__: An array of random column names from which random effects coefficients will be generated in the model building process.

-  `rand_family <algo-params/rand_family.html>`__: Specify the distribution of the random effects. Currently only ``rand_family="gaussisan"`` is supported.

- **random_intercept**: If enabled, will generate a random intercept as part of the random effects coefficients (defaults to ``True``).

- **tau_e_var_init**: Initial variance estimate of random noise (residual noise). If set, this should provide a value of > 0.0. If not set, this will be randomly set during the model building process (defaults to ``0.0``).

- **tau_u_var_init**: Initial variance estimate of random effects. If set, should provide a value > 0.0. If not set, this will be randomly set during the model building process (defaults to ``0.0``).

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
   
     .. note:: 

      Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (``y``) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. 

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Enable this option to score during each iteration of the model training. This option defaults to ``False`` (disabled).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    .. note:: 

      Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more due to the larger loss function pre-factor.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable.

   -  For a regression model, this column must be numeric (**Real** or **Int**).
   -  For a classification model, this column must be categorical (**Enum** or **String**). If the family is ``Binomial``, the dataset cannot contain more than two levels.


Estimation of parameters using machine learning estimation via EM
-----------------------------------------------------------------

The Expectation-Maximization (EM) algorithm addresses the problem of maximizing the likelihood by conceiving this as a problem with missing data.

Model setup
~~~~~~~~~~~

Consider a combined model for each unit :math:`j`:

.. math::
   
   Y_j = A_{fj} \theta_f + A_{rj} \theta_{rj} + r_j, \theta_{rj} \sim N(0,T_j), r_j \sim N(0, \sigma^2I) \quad \text{ equation 6}

where:

- :math:`Y_j = \begin{bmatrix} x^T_{j1} \\ x^T_{j2} \\ x^T_{j3} \\ \vdots \\ x^T_{jn_j} \\\end{bmatrix}` is a known :math:`n_j \text{ by } p` matrix of level-1 predictors and :math:`x_{ji} = \begin{bmatrix} x^1_{ji} \\ x^2_{ji} \\ \vdots \\ x^{p-1}_{ji} \\ 1 \\\end{bmatrix}`;
   
   .. note::

      In general, you can place the intercept at the beginning or the end of each row of data, but we chose to put it at the end for our implementation.

- :math:`\theta_f \text{ is a } p` by 1 vector of fixed coefficients;
- :math:`A_{rj}` is usually denoted by :math:`Z_{rj} \text{ where } Z_{rj} = \begin{bmatrix} z^T_{j1} \\ z^T_{j2} \\ z^T_{j3} \\ \vdots \\ z^T_{jn_j} \\\end{bmatrix}`;
   
   .. note::

      We included a term for the random intercept here. However, there are cases where we do not have a random intercept, and the last element of 1 will not be there for :math:`z_{ji}`.

- :math:`\theta_{rj}` represents the random coefficient and is a :math:`q` by 1 vector;
- :math:`r_j \text{ is an } n_j` by 1 vector of level-1 residual noise assumed multivariate normal in distribution with 0 mean vector, covariance matrix :math:`\sigma^2 I_{n_{j}\times n_{j}} \text{ where } I_{n_{j \times nj}}` is the identity matrix, :math:`n_j \text{ by } n_j`;
- :math:`j` denotes the level-2 units where :math:`j = 1,2, \cdots , J`;
- :math:`T_j` is a symmetric positive definite matrix of size :math:`n_j \text{ by } n_j`. We assume that :math:`T_j` is the same for all :math:`j = 1,2, \cdots , J`, and it is kept to be symmetric positive definite throughout the whole model building process.

M-step
~~~~~~

EM conceives of :math:`Y_j` as the observed data with :math:`\theta_{rj}` as the missing data. Therefore, the complete data are :math:`(Y_j, \theta_{rj}), j=1, \cdots, J \text{ while } \theta_f, \sigma^2, \text{ and } T_j` are the parameters that need to be estimated. If the complete data were observed, finding the ML estimates will be simple. To estimate :math:`\theta_f`, subtract :math:`A_{rj} \theta_{rj}` from both sides of *equation 6* yielding:

.. math::
   
   Y_j - A_{rj} \theta_{rj} = A_{fj} \theta_f + r_f \quad \text{ equation 7}

Next, multiply *equation 7* with :math:`A^T_{fj}` and sum across the level-2 unit :math:`j`. Note that :math:`\sum^J_{j=1} A^T_{fj} r_j \sim 0`. Re-arrange the terms and you will get *equation 8*, which is also the ordinary least squares (OLS) estimate:

.. math::

   \hat{\theta_f} = \Big( \sum^J_{j=1} A^T_{fj} A_{fj} \Big)^{-1} \sum^J_{j=1} A^T_{fj} (Y_j - A_{rj} \theta_{rj}) \quad \text{ equation 8}

Next, ML estimators for :math:`T_j` and :math:`\sigma^2` are straightforward:

.. math::
   
   \hat{T_j} = J^{-1} \sum^J_{j=1} \theta_{rj} \theta^T_{rj} \quad \text{ equation 9}

.. math::
   
   \hat{\sigma^2} = N^{-1} \sum^J_{j=1} \hat{r^T_j} \hat{r_j} = N^{-1} \sum^J_{j=1} \big( Y_j - A_{fj} \hat{\theta_f} - A_{rj} \theta_{rj} \big)^T \big( Y_j - A_{fj} \hat{\theta_{f}} - A_{rj} \theta_{rj} \big) \quad \text{ equation 10}

where :math:`N = \sum^J_{j=1} n_j`.

.. note::
   
   This reasoning defines certain complete-data sufficent statistics (CDSS), that is, statistics that would be sufficient to estimate :math:`\theta_f, T, \text{ and } \sigma^2` if the complete data were observed. These are:

   .. math::

      \sum^J_{j=1} A^T_{fj} A_{rj} \theta_{rj}, \sum^J_{j=1} \theta_{rj} \theta^T_{rj}, \sum^J_{j=1} Y^T_j A_{rj} \theta_{rj}, \sum^J_{j=1} \theta^T_{rj} A^T_{rj} A_{rj} \theta_{rj} \quad \text{ equation 11}.

E-step
~~~~~~

While the CDSS are not observed, they can be estimated by their conditional expectations given the data :math:`Y` and parameter estimates from the previous iterations. `Dempster et al. [4] <#references>`__ showed that substituting the expected CDSS for the M-step formulas would produce new parameter estimates having a higher likelihood than the current estimates.

To find :math:`E(CDSS | Y, \theta_f, T, \sigma^2)` requires deriving the conditional distribution of the missing data :math:`\theta_r`, given :math:`Y, \theta_f, T, \sigma^2`. From *equation  6*, the joint distribution of the complete data is:

.. math::
   
   \begin{pmatrix} Y_j \\ \theta_{rj} \\\end{pmatrix} \sim N \Bigg[ \begin{pmatrix} A_{fj} \theta_{f} \\ 0 \\\end{pmatrix} , \begin{pmatrix} A_{rj}T_jA^T_{rj} + \sigma^2 & A_{rj}T_j \\ T_j A^T_{rj} & T_j \\\end{pmatrix} \Bigg] \quad \text{ equation 12}

From *equation 12*, we can obtain the conditional distribution of the missing data given the complete data as follows:

.. math::
   
   \theta_{rj} | Y, \theta_f, T_j, \sigma^2 \sim N (\theta^*_{rj}, \sigma^2 C_j^{-1}) \quad \text{ equation 13} 

with

.. math::
   
   \theta^*_{rj} = C^{-1}_j A^T_{rj} (Y_j - A_{fj} \theta_f) \quad \text{ equation 14}

   C_j = A^T_{rj} A_{rj} + \sigma^2 T^{-1}_j \quad \text{ equation 15}

The complete EM algorithm
~~~~~~~~~~~~~~~~~~~~~~~~~

The complete EM algorithm is as follows:

1. Initialization: randomly assign some small values to :math:`\theta_f, \sigma^2, T_j`;
2. Estimation: estimate the CDSS:
   
   .. math::

      E \big( \sum^J_{j=1} A^T_{fj} \theta_{rj} \theta_{rj} | Y, \theta_f, T_j, \sigma^2 \big) = \sum^J_{j=1} A^T_{fj} A_{rj} \theta^*_{rj} \\ E \big( \sum^J_{j=1} \theta_{rj} \theta^T_{rj} | Y, \theta_f, T_j, \sigma^2 \big) = \sum^J_{j=1} \theta^*_{rj} \theta^{*T}_{rj} + \sigma^2 \sum^J_{j=1} C^{-1}_j & \quad \text{ equation 17} \\ E \big( \sum^J_{j=1} r^T_j r_j \big) = \sum^J_{j=1} r^{*T}_j r^*_j + \sigma^2 \sum^J_{j=1} tr(C^{-1}_j A^T_{rj} A_{rj})

   where: :math:`r^*_j = Y_j - A_{fj} \theta_f - A_{fj} \theta^*_{rj}, \theta^*_{rj} = C^{-1}_j A^T_{rj} (Y_j - A_{fj} \theta_f), C_j = A^T_{rj} A_{rj} + \sigma^2 T^{-1} \text{ and } \theta_f, \sigma^2, T` are based on the previous iteration or from initialization;

3. Substitution: substitute the estimated CDSS from *equation 17* into the M-step forumulas (*equations 8, 9,* and *10*);
4. Processing: feed the new estimates of :math:`\theta_f, \sigma^2, T_j` into step 2;
5. Cycling: continue steps 2, 3, and 4 until the following stopping condition is satisfied:

   - The largest change in the value of any of the parameters is sufficiently small.

Log-likelihood for HGLM
~~~~~~~~~~~~~~~~~~~~~~~

The model for level-2 unit :math:`j` can be written as:

.. math::
   
   Y_j = A_{fj} \theta_f + d_j = X_j \theta_f + d_j, \quad d_j \sim N(0,V_j)

where:

- :math:`Y_j \text{ is an } n_j` by 1 outcome vector;
- :math:`A_{fj} / X_j = \begin{bmatrix} x^T_{j1} \\ x^T_{j2} \\ x^T_{j3} \\ \vdots \\ x^T_{jn_{j}} \\\end{bmatrix}` is a known :math:`n_j \text{ by } p` matrix of level-1 predictors and :math:`x_{ji} = \begin{bmatrix} x^1_{ji} \\ x^2_{ji} \\ \vdots \\ x^{p-1}_{ji} \\ 1 \\\end{bmatrix}`;
- :math:`\theta_f \text{ is a } p` by 1 vector of fixed effects;
- :math:`d_j = A_{rj} \theta_{rj} + r_j = Z_j \theta_{rj} + r_j , A_{rj} / Z_j \text{ is } n_j \text{ by } q`;
- :math:`\theta_{rj} \sim N(0,T), \theta_{rj} \text{ is } q` by 1, :math:`T \text{ is } q \text{ by } q`;
- :math:`r_j \sim N(0, \sigma^2 I_{n_j}), I_{n_j} \text{ is } n_j \text{ by } n_j`;
- :math:`V_j = A_{rj} TA^T_{rj} + \sigma^2 I_{n_j} = Z_j TZ^T_j + \sigma^2 I_{n_j}, \text{ is } n_j \text{ by } n)j`.

For each level-2 value :math:`j`, the likelihood can be written as:

.. math::
   
   L(Y_j; \theta_f, \sigma^2, T_j) = (2 \pi)^{-n_{j} /2} |V_j |^{-1/2} \exp \{ -\frac{1}{2} d^T_j V^{-1}_j d_j\}

The log-likelihood is:

.. math::
   
   ll(Y_j; \theta_f, \sigma^2 , T_j) = -\frac{1}{2} \Big( n_j \log{(2 \pi)} + \log{(|V_j|)} + (Y_j - X_j \theta_f)^T V^{-1}_j (Y_j - X_j \theta_f) \Big)

Since we assume that random effects are i.i.d., the total log-likelihood is just the sum of the log-likelihood for each level-2 value. Let :math:`T=T_j`:

.. math::
   
   ll(Y; \theta_f, \sigma^2, T) \\

   = \sum^J_{j=1} \Big\{ - \frac{1}{2} \big( n_j \log{(2 \pi)} + \log{(|V_j|)} + (Y_j - X_j \theta_f)^T V^{-1}_j (Y_j - X_j \theta_f) \big) \Big\} =

   -\frac{1}{2} n \log{(2 \pi)} -\frac{1}{2} \Big\{ \sum^J_{j=1} \big( \log{(|V_j|)} + (Y_j - X_j \theta_f)^T V^{-1}_j (Y_j - X_j \theta_f) \big) \Big\}

:math:`|V_j|` can be calculated as:

.. math::
   
   |V_j| = \Big|Z_j TZ^T_j + \sigma^2 I_{n_j} \Big| = \Big|T^{-1} + \frac{1}{\sigma^2} Z^T_j Z_j \Big| |T| \Big| \sigma^2 I_{n_j} \Big| = \sigma^2 \Big| T^{-1} + \frac{1}{\sigma^2} Z^T_j Z_j \Big| |T|

where: :math:`V^{-1}_j = \frac{1}{\sigma^2} I_{n_j} - \frac{1}{\sigma^4} Z_j \Big( T^{-1} + \frac{1}{\sigma^2} Z^T_j Z_j \Big)^{-1} Z^T_j`

:math:`(Y_j - X_j \theta_f)^T V_j^{-1} (Y_j - X_j \theta_f)` can be calculated as:

.. math::
   
   (Y_j - X_j \theta_f)^T V_j^{-1} (Y_j - X_j \theta_f) = \frac{1}{\sigma^2} (Y_j - X_j \theta_f)^T (Y_j - X_j \theta_f) - \frac{1}{\sigma^4} (Y_j - X_j \theta_f)^T Z_j (T^{-1} + \frac{1}{\sigma^2} Z^T_j Z_j)^{-1} Z^T_j (Y_j - X_J \theta_f)

The final log-likelihood is:

.. math::
   
   ll(Y; \theta_f, \sigma^2, T) = - \frac{1}{2} n \log{(2 \pi)} - \frac{1}{2} \Big\{ \sum^J_{j=1} \big( \log{(|V_j|)} + \frac{1}{\sigma^2} (Y_j - X_j \theta_f)^T (Y_j - X_j \theta_f) \\ - \frac{1}{\sigma^4} (Y_j - X_j \theta_f)^T Z_j \big(T^{-1} + \frac{1}{\sigma^2} Z^T_j Z_j \big)^{-1} Z^T_j (Y_j - X_j \theta_f) \big) \Big\} \quad \quad \quad

Examples
--------

The following are simple HGLM examples in Python and R.

.. tabs::
   .. code-tab:: python

      # Initialize H2O-3 and import the HGLM estimator:
      import h2o
      h2o.init()
      from h2o.estimators import H2OHGLMEstimator as hglm

      # Import the Gaussian wintercept dataset:
      h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/hglm_test/gaussian_0GC_678R_6enum_5num_p05oise_p08T_wIntercept_standardize.gz")

      # Split the data into training and validation sets:
      train, valid = h2o_data.split_frame(ratios = [.8], seed = 1234)

      # Define the predictors and response:
      y = "response"
      x = h2o_data.names
      x.remove("response")
      x.remove("C1")

      # Set the random columns:
      random_columns = ["C10","C20","C30"]

      # Build and train the model:
      hglm_model = hglm(random_columns=random_columns, 
                        group_column = "C1", 
                        score_each_iteration=True, 
                        seed=12345, 
                        em_epsilon = 0.000005)
      hglm_model.train(x=x, y=y, training_frame=train, validation_frame=valid)

      # Grab various metrics (model metrics, scoring history coefficients, etc.):
      modelMetrics = hglm_model.training_model_metrics()
      scoring_history = hglm_model.scoring_history(as_data_frame=False)
      scoring_history_valid = hglm_model.scoring_history_valid(as_data_frame=False)
      model_summary = hglm_model.summary()
      coef = hglm_model.coef()
      coef_norm = hglm_model.coef_norm()
      coef_names = hglm_model.coef_names()
      coef_random = hglm_model.coefs_random()
      coef_random_names = hglm_model.coefs_random_names()
      coef_random_norm = hglm_model.coefs_random_norm()
      coef_random_names_norm = hglm_model.coefs_random_names_norm()
      t_mat = hglm_model.matrix_T()
      residual_var = hglm_model.residual_variance()
      mse = hglm_model.mse()
      mse_fixed = hglm_model.mean_residual_fixed()
      mse_fixed_valid = hglm_model.mean_residual_fixed(train=False)
      icc = hglm_model.icc()

   .. code-tab:: r R

      # Import the Gaussian wintercept dataset:
      h2odata <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/hglm_test/gaussian_0GC_allRC_2enum2numeric_p5oise_p08T_wIntercept_standardize.gz")

      # Set the predictors and response:
      yresp <- "response"
      predictor <- c("C2", "C3", "C4", "C5")

      # Set the random and group columns:
      random_columns <- c("C2", "C3", "C4", "C5")
      group_column <- "C1"

      # Build and train the model:
      hglm_model <- h2o.hglm(x = predictor, 
                             y = yresp, 
                             training_frame = h2odata, 
                             group_column = group_column, 
                             random_columns = random_columns, 
                             seed = 12345, 
                             max_iterations = 10, 
                             em_epsilon = 0.0000001, 
                             random_intercept = TRUE)

      # Find the coefficient:
      coeff <- h2o.coef(hglm_model)

References
----------

[1] David Ruppert, M. P. Wand and R. J. Carroll, Semiparametric Regression, Chapter 4, Cambridge University Press, 2003.

[2] Stephen w. Raudenbush, Anthony S. Bryk, Hierarchical Linear Models Applications and Data Analysis Methods, Second Edition, Sage Publications, 2002.

[3] Rao, C. R. (1973). Linear Statistical Inference and Its Applications. New York: Wiley. 

[4] Dempster, A. P., Laird, N. M., & Rubin, D. B. (1977). Maximum likelihood from incomplete data via the EM algorithm. Journal of the Royal Statistical Society, Seires B, 39, 1-8.

[5] Matrix determinant lemma: https://en.wikipedia.org/wiki/Matrix_determinant_lemma.

[6] Woodbury matrix identity: https://en.wikipedia.org/wiki/Woodbury_matrix_identity.