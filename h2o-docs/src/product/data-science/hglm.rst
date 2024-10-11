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
   
     .. note:: 

      Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (``y``) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. 

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Enable this option to score during each iteration of the model training. This option defaults to ``False`` (disabled).
- score_values_handling
-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).
-  `standardize <algo-params/standardize.html>`__: Specify whether to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option defaults to ``True`` (enabled).
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

Definining an HLM
-----------------

Hierarchical linear models (HLM) is used in situations where measurements are taken with clusters of data and there are effects of the cluster that can affect the coefficient values of GLM. For instance, if we measure the students' performances from multiple schools along with other predictors like family annual incomes, students' health, school type (public, private, religious, etc.), and etc., we suspect that students from the same school will have similar performances than students from different schools. Therefore, we can denote a coefficient for predictor :math:`m \text{ as } \beta_{mj}` where :math:`j` denotes the school index in our example. :math:`\beta_{0j}` denotes the intercept associated with school :math:`j`.

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
- :math:`u_{mj}, u_{m,j}` are independent if :math:`m \neq m`.

We need to solve the following parameters: :math:`\beta_{00}, \beta_{0j}, \beta_{m0}, u_{mj}, \delta_e^2, \delta_u^2`. To do this, we use the standard linear mixed model expressed with vectors and matrices:

.. math::
	
	Y = X\beta + Z u + e \quad \text{ equation 3}

where:

- :math:`Y = \begin{bmatrix} y_{11} \\ y_{21} \\ \vdots \\ y_{n_{1}1} \\ y_{12} \\ y_{22} \\ \vdots \\ y_{n_{2}2} \\ \vdots \\ y_{1J} \\ y_{2J} \\ \vdots \\ y_{n_{J}J} \\\end{bmatrix}` is a :math:`n(= \sum^J_{j=1} n_j)` by 1 vector where :math:`n` is the number of all independent and identically distributed (i.i.d.) observations across all clusters;
- :math:`X = \begin{bmatrix} X_1 \\ X_2 \\ \vdots \\ X_J \\\end{bmatrix}` where :math:`X_j = \begin{bmatrix} 1 & x_{11j} & x_{21j} & \cdots & x_{(p-1)1j} \\ 1 & x_{12j} & x_{22j} & \cdots & x_{(p-1)2j} \\ 1 & x_{13j} & x_{23j} & \cdots & x_{(p-1)3j} \\ \vdots & \vdots & \ddots & \cdots & \vdots \\ 1 & x_{1n_{j}j} & x_{2n_{j}j} & \cdots & x_{(p-1)n_{j}j} \\\end{bmatrix} = \begin{bmatrix} x^T_{j1} \\ x^T_{j2} \\ x^T_{j3} \\ \vdots \\ x^T_{jn_j} \\\end{bmatrix}`. We are just stacking all the :math:`X_j` across all the clusters;
- :math:`\beta = \begin{bmatrix} \beta_{00} \\ \beta_{10} \\ \vdots \\ \beta_{(p-1)0} \\\end{bmatrix}` is a :math:`p` by 1 fixed coefficients vector including the intercept;
- :math:`Z = \begin{bmatrix} Z_1 & 0_{12} & 0_{13} & \cdots & 0_{1J} \\ 0_{21} & Z_2 & 0_{23} & \cdots & 0_{2J} \\ 0_{31} & 0_{32} & Z_3 & \cdots & 0_{3J} \\ \vdots & \vdots & \vdots & \ddots & \vdots \\ 0_{J1} & 0_{J2} & 0_{J3} & \cdots & Z_J \\\end{bmatrix}` where :math:`Z_J \text{ is a } n_j \times q` matrix, and :math:`0_{ij} n_i \times q` is a zero matrix. Therefore, :math:`Z` is a :math:`n \times (J * q)` matrix containing blocks of non-zero sub-matrices across its diagonal;
- :math:`u = \begin{bmatrix} u_{01} \\ u_{11} \\ u_{(q-1)1} \\ u_{02} \\ u_{12} \\ \vdots \\ u_{(q-1)2} \\ \vdots \\ u_{0J} \\ u_{1J} \\ \vdots \\ u_{(q-1)J} \\\end{bmatrix} \text{ is a } J * q` by 1 random effects vector and some coefficients may not have a random effect;
- :math:`e \sim N(0, \delta^2_e I_n), u \sim N (0, \delta^2_u I_{(J*q)}) \text{ where } I_n \text{ is an } n \times n \text{ and } I_{(J*q)} \text{ is an } (J*q) \times (J*q)` identity matrix;
- :math:`e,u` are independent;
- :math:`E \begin{bmatrix} u \\ e \\\end{bmatrix} = \begin{bmatrix} 0 \\ 0 \\\end{bmatrix} , cov \begin{bmatrix} u \\ e \\\end{bmatrix} = \begin{bmatrix} G & 0 \\ 0 & R \\\end{bmatrix} , G = \delta^2_u I_{(J*q)} , R = \delta^2_e I_{n \cdot} E \begin{bmatrix} u \\ e \\\end{bmatrix} \text{ is a size } (J * q + n) \text{ vector }, cov \begin{bmatrix} u \\ e \\\end{bmatrix} \text{ is a } (J * q + n) \times (J * q + n)` matrix. 

In addition, we also consider the following alternate form:

.. math::
   
   Y = X\beta + e^*, e^* = Zu + e \quad \text{ equation 4}

where:

.. math::
   
   cov(e^*) = V = ZGZ^T + R = \delta^2_u ZZ^T + \delta^2_e I_n \quad \text{ equation 5}

We solve for :math:`\beta, u, \delta^2_u, \text{ and } \delta^2_e`.

Estimation of parameters using machine learning estimation via EM
-----------------------------------------------------------------

The EM algorithm addresses the problem of maximizing the likelihood by conceiving this as a problem with missing data.

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
- :math:`A_{rj}` is usually denoted by :math:`Z_j \text{ where } Z_j = \begin{bmatrix} z^T_{j1} \\ z^T_{j2} \\ z^T_{j3} \\ \vdots \\ z^T_{jn_j} \\\end{bmatrix}`;
   
   .. note::

      We included a term for the random intercept here. However, there are cases where we do not have a random intercept, and the last element of 1 will not be there for :math:`z_{ji}`.

- :math:`\theta_{rj}` represents the random coefficient and is a :math:`q` by 1 vector;
- :math:`r_j \text{ is an } n_j` by 1 vector of level-1 random effects assumed multivariate normal in distribution with 0 mean vector, covariance matrix :math:`\sigma^2 I_{n_{j\times nj}} \text{ where } I_{n_{j \times nj}}` is the identity matrix, :math:`n_j \text{ by } n_j`;
- :math:`j` denotes the level-2 units where :math:`j = 1,2, \cdots , J`;
- :math:`T_j` is a symmetric positive definite matrix of size :math:`n_j \text{ by } n_j`. For simplicity, all :math:`T_j` are the same. We assume that :math:`T_j` is the same for all :math:`j = 1,2, \cdots , J`. However, we can assume that the fixed coefficients are i.i.d. :math:`\sim N (0, \sigma^2_u I_{n_j \times n_j})` for simplicity initially and keep :math:`T_j` to be symmetric positive definite matrix as the iteration continues.

M step
~~~~~~

Expectation-Maximization (EM) conceives of :math:`Y_j` as the observed data with :math:`\theta_{rj}` as the missing data. Therefore, the complete data are :math:`(Y_j, \theta_{rj}), j=1, \cdots, J \text{ while } \theta_f, \sigma^2, \text{ and } T_j` are the parameters that need to be estimated. If the complete data were observed, finding the ML estimates will be simple. To estimate :math:`\theta_f`, subtract :math:`A_{rj} \theta_{rj}` from both sides of *equation 6*:

.. math::
   
   Y_j - A_{rj} \theta_{rj} = A_{fj} \theta_f + r_f \quad \text{ equation 7}

and justifying the ordinary least squares (OLS) estimate:

.. math::
   
   \hat{\theta_f} = \Big( \sum^J_{j=1} A^T_{fj} A_{fj} \Big)^{-1} \sum^J_{j=1} A^T_{fj} (Y_j - A_{rj} \theta_{rj}) \quad \text{ equation 8}

*Equation 8* can also be solved by multipying *equation 7* with :math:`A^T_{fj}` and sum across the level-2 unit :math:`j`. 

.. note::
   
   :math:`\sum^J_{j=1} A^T_{fj} r_j \sim 0` and rearrange the terms and you get *equation 8*.

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

E step
~~~~~~





References
----------

David Ruppert, M. P. Wand and R. J. Carroll, Semiparametric Regression, Chapter 4, Cambridge University Press, 2003.

Stephen w. Raudenbush, Anthony S. Bryk, Hierarchical Linear Models Applications and Data Analysis Methods, Second Edition, Sage Publications, 2002.

Rao, C. R. (1973). Linear Statistical Inference and Its Applications. New York: Wiley. 

Dempster, A. P., Laird, N. M., & Rubin, D. B. (1977). Maximum likelihood from incomplete data via the EM algorithm. Journal of the Royal Statistical Society, Seires B, 39, 1-8.