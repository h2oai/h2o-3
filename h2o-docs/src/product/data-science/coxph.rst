Cox Proportional Hazards (CoxPH)
--------------------------------

**Note** CoxPH is not yet supported in Python. It is supported in R and Flow only.

Cox proportional hazards models are the most widely used approach for modeling time to event data. As the name suggests, the *hazard function*, which computes the instantaneous rate of an event occurrence and is expressed mathematically as

:math:`h(t) = \lim_{\Delta t \downarrow 0} \frac{Pr[t \le T < t + \Delta t \mid T \ge t]}{\Delta t},`

is assumed to be the product of a *baseline hazard function* and a *risk score*. Consequently, the hazard function for observation :math:`i` in a Cox proportional hazards model is defined as

:math:`h_i(t) = \lambda(t)\exp(\mathbf{x}_i^T\beta)`

where :math:`\lambda(t)` is the baseline hazard function shared by all observations and :math:`\exp(\mathbf{x}_i^T\beta)` is the risk score for observation :math:`i`, which is computed as the exponentiated linear combination of the covariate vector :math:`\mathbf{x}_i^T` using a coefficient vector :math:`\beta` common to all observations.

This combination of a non-parametric baseline hazard function and a parametric risk score results in Cox proportional hazards models being described as *semi-parametric*. In addition, a simple rearrangement of terms shows that unlike generalized linear models, an intercept (constant) term in the risk score adds no value to the model fit, due to the inclusion of a baseline hazard function.

`An R demo is available here <https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/rdemo.word2vec.craigslistjobtitles.R>`__. This uses the CoxPH algorithm along with the craigslistJobTitles.csv dataset. 

Defining a CoxPH Model
~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `start_column <algo-params/start_column.html>`__: (Optional) The name of an integer column in the **source** data set representing the start time. If supplied, the value of the **start_column** must be strictly less than the **stop_column** in each row.

-  `stop_column <algo-params/stop_column.html>`__: (Required) The name of an integer column in the **source** data set representing the stop time. 

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable. The data can be numeric or categorical.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified  ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `offset_column <algo-params/offset_column.html>`__: Specify a column to use as the offset.
   
	 **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__. 

-  **stratify_by**: A list of columns to use for stratification.

-  `ties <algo-params/ties.html>`__: The approximation method for handling ties in the partial likelihood. This can be either **efron** (default) or **breslow**). See the :ref:`coxph_model_details` section below for more information about these options.

-  **init**: (Optional) Initial values for the coefficients in the model. This value defaults to 0.

-  **lre_min**: A positive number to use as the minimum log-relative error (LRE) of subsequent log partial likelihood calculations to determine algorithmic convergence. The role this parameter plays in the stopping criteria of the model fitting algorithm is explained in the :ref:`coxph_algorithm` section below. This value defaults to 9.

-  `max_iterations <algo-params/max_iterations.html>`__: A positive integer defining the maximum number of iterations during model training. The role this parameter plays in the stopping criteria of the model-fitting algorithm is explained in the :ref:`coxph_algorithm` section below. This value defaults to 20.

-  `interactions <algo-params/interactions.html>`__: Specify a list of predictor column indices to interact. All pairwise combinations will be computed for this list. 

-  `interaction_pairs <algo-params/interaction_pairs.html>`__: (Internal only.) When defining interactions, use this option to specify a list of pairwise column interactions (interactions between two variables). Note that this is different than ``interactions``, which will compute all pairwise combinations of specified columns. This option is disabled by default.


Cox Proportional Hazards Model Results
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Data
''''

- Number of Complete Cases: The number of observations without missing values in any of the input columns.
- Number of Non Complete Cases: The number of observations with at least one missing value in any of the input columns.
- Number of Events in Complete Cases: The number of observed events in the complete cases.

Coefficients
''''''''''''

:math:`\tt{name}`: The name given to the coefficient. If the predictor column is numeric, the corresponding coefficient has the same name. If the predictor column is categorical, the corresponding coefficients are a concatenation of the name of the column with the name of the categorical level the coefficient represents.

:math:`\tt{coef}`: The estimated coefficient value.

:math:`\tt{exp(coef)}`: The exponentiated coefficient value estimate.

:math:`\tt{se(coef)}`: The standard error of the coefficient estimate.

:math:`\tt{z}`: The z statistic, which is the ratio of the coefficient estimate to its standard error.

Model Statistics
''''''''''''''''

- Cox and Snell Generalized :math:`R^2`

  :math:`\tt{R^2} := 1 - \exp\bigg(\frac{2\big(pl(\beta^{(0)}) - pl(\hat{\beta})\big)}{n}\bigg)`

- Maximum Possible Value for Cox and Snell Generalized :math:`R^2`

  :math:`\tt{Max. R^2} := 1 - \exp\big(\frac{2 pl(\beta^{(0)})}{n}\big)`

- Likelihood Ratio Test

  :math:`2\big(pl(\hat{\beta}) - pl(\beta^{(0)})\big)`, which under the null
  hypothesis of :math:`\hat{beta} = \beta^{(0)}` follows a chi-square
  distribution with :math:`p` degrees of freedom.

Wald Test
  :math:`\big(\hat{\beta} - \beta^{(0)}\big)^T I\big(\hat{\beta}\big) \big(\hat{\beta} - \beta^{(0)}\big)`,
  which under the null hypothesis of :math:`\hat{beta} = \beta^{(0)}` follows a
  chi-square distribution with :math:`p` degrees of freedom. When there is a
  single coefficient in the model, the Wald test statistic value is that
  coefficient's z statistic.

Score (Log-Rank) Test
  :math:`U\big(\beta^{(0)}\big)^T \hat{I}\big(\beta^{0}\big)^{-1} U\big(\beta^{(0)}\big)`,
  which under the null hypothesis of :math:`\hat{beta} = \beta^{(0)}` follows a
  chi-square distribution with :math:`p` degrees of freedom.

where

  :math:`n` is the number of complete cases

  :math:`p` is the number of estimated coefficients

  :math:`pl(\beta)` is the log partial likelihood

  :math:`U(\beta)` is the derivative of the log partial likelihood

  :math:`H(\beta)` is the second derivative of the log partial likelihood

  :math:`I(\beta) = - H(\beta)` is the observed information matrix


.. _coxph_model_details:

Cox Proportional Hazards Model Details
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A Cox proportional hazards model measures time on a scale defined by the ranking of the :math:`M` distinct observed event occurrence times, :math:`t_1 < t_2 < \dots < t_M`. When no two events occur at the same time, the partial likelihood for the observations is given by

:math:`PL(\beta) = \prod_{m=1}^M\frac{\exp(w_m\mathbf{x}_m^T\beta)}{\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta)}`

where :math:`R_m` is the set of all observations at risk of an event at time :math:`t_m`. In practical terms, :math:`R_m` contains all the rows where (if supplied) the start time is less than :math:`t_m` and the stop time is greater than or equal to :math:`t_m`. When two or more events are observed at the same time, the exact partial likelihood is given by

:math:`PL(\beta) = \prod_{m=1}^M\frac{\exp(\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta)}{(\sum_{R^* : \mid R^* \mid = d_m} [\sum_{j \in R^*} w_j \exp(\mathbf{x}_j^T\beta)])^{\sum_{j \in D_m} w_j}}`

where :math:`R_m` is the risk set and :math:`D_m` is the set of observations of size :math:`d_m` with an observed event at time :math:`t_m` respectively. Due to the combinatorial nature of the denominator, this exact partial likelihood becomes prohibitively expensive to calculate, leading to the common use of Efron's and Breslow's approximations.

Efron's Approximation
'''''''''''''''''''''

Of the two approximations, Efron's produces results closer to the exact combinatoric solution than Breslow's. Under this approximation, the partial likelihood and log partial likelihood are defined as

:math:`PL(\beta) = \prod_{m=1}^M \frac{\exp(\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta)}{\big[\prod_{k=1}^{d_m}(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta) - \frac{k-1}{d_m} \sum_{j \in D_m} w_j \exp(\mathbf{x}_j^T\beta))\big]^{(\sum_{j \in D_m} w_j)/d_m}}`

:math:`pl(\beta) = \sum_{m=1}^M \big[\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta - \frac{\sum_{j \in D_m} w_j}{d_m} \sum_{k=1}^{d_m} \log(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta) - \frac{k-1}{d_m} \sum_{j \in D_m} w_j \exp(\mathbf{x}_j^T\beta))\big]`

Breslow's Approximation
'''''''''''''''''''''''

Under Breslow's approximation, the partial likelihood and log partial likelihood are defined as

:math:`PL(\beta) = \prod_{m=1}^M \frac{\exp(\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta)}{(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta))^{\sum_{j \in D_m} w_j}}`

:math:`pl(\beta) = \sum_{m=1}^M \big[\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta - (\sum_{j \in D_m} w_j)\log(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta))\big]`

.. _coxph_algorithm:

Cox Proportional Hazards Model Algorithm
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

H2O uses the Newton-Raphson algorithm to maximize the partial log-likelihood, an iterative procedure defined by the steps:

To add numeric stability to the model fitting calculations, the numeric predictors and offsets are demeaned during the model fitting process.

1. Set an initial value, :math:`\beta^{(0)}`, for the coefficient vector and assume an initial log partial likelihood of :math:`- \infty`.
2. Increment iteration counter, :math:`n`, by 1.
3. Calculate the log partial likelihood, :math:`pl\big(\beta^{(n)}\big)`, at the current coefficient vector estimate.
4. Compare :math:`pl\big(\beta^{(n)}\big)` to :math:`pl\big(\beta^{(n-1)}\big)`.

  a) If :math:`pl\big(\beta^{(n)}\big) > pl\big(\beta^{(n-1)}\big)`, then accept the new coefficient vector, :math:`\beta^{(n)}`, as the current best estimate, :math:`\tilde{\beta}`, and set a new candidate coefficient vector to be :math:`\beta^{(n+1)} = \beta^{(n)} - \tt{step}`, where :math:`\tt{step} := H^{-1}(\beta^{(n)}) U(\beta^{(n)})`, which is the product of the inverse of the second derivative of :math:`pl` times the first derivative of :math:`pl` based upon the observed data.

  b) If :math:`pl\big(\beta^{(n)}\big) \le pl\big(\beta^{(n-1)}\big)`, then set :math:`\tt{step} := \tt{step} / 2` and :math:`\beta^{(n+1)} = \tilde{\beta} - \tt{step}`.

5. Repeat steps 2 - 4 until either
  
  a) :math:`n = \tt{iter\ max}` or
  
  b) the log-relative error :math:`LRE\Big(pl\big(\beta^{(n)}\big), pl\big(\beta^{(n+1)}\big)\Big) >= \tt{lre\ min}`,
     
     where
     
     :math:`LRE(x, y) = - \log_{10}\big(\frac{\mid x - y \mid}{y}\big)`, if :math:`y \ne 0`

     :math:`LRE(x, y) = - \log_{10}(\mid x \mid)`, if :math:`y = 0`


References
~~~~~~~~~~

Andersen, P. and Gill, R. (1982). Cox's regression model for counting processes, a large sample study. *Annals of Statistics* **10**, 1100-1120.

Harrell, Jr. F.E., Regression Modeling Strategies: With Applications to Linear Models, Logistic Regression, and Survival Analysis. Springer-Verlag, 2001.

Therneau, T., Grambsch, P., Modeling Survival Data: Extending the Cox Model. Springer-Verlag, 2000.
