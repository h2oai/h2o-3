GLM
--------------

Introduction
~~~~~~~~~~~~

Generalized Linear Models (GLM) estimate regression models for outcomes
following exponential distributions. In addition to the Gaussian (i.e.
normal) distribution, these include Poisson, binomial, and gamma
distributions. Each serves a different purpose, and depending on
distribution and link function choice, can be used either for prediction
or classification.

The GLM suite includes:

-  Gaussian regression
-  Poisson regression
-  Binomial regression (classification)
-  Multinomial classification
-  Gamma regression

Defining a GLM Model
~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the
   independent variable.

   -  For a regression model, this column must be numeric (**Real** or
      **Int**).
   -  For a classification model, this column must be categorical
      (**Enum** or **String**). If the family is **Binomial**, the
      dataset cannot contain more than two levels.

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Specify whether to keep the cross-validation predictions.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for **nfolds** is specified and **fold\_column** is not specified) Specify the cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random, `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column
   name to add it to the list of columns excluded from the model. To add
   all columns, click the **All** button. To remove a column from the
   list of ignored columns, click the X next to the column name. To
   remove all columns from the list of ignored columns, click the
   **None** button. To search for a specific column, type the column
   name in the **Search** field above the column list. To only show
   columns with a specific percentage of missing values, specify the
   percentage in the **Only show columns with more than 0% missing
   values** field. To change the selections for the hidden columns, use
   the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Enable this option to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Enable this option to score during each iteration of the model training.

-  `offset_column <algo-params/offset_column.html>`__: Specify a column to use as the offset; the value cannot be the same as the value for the ``weights_column``.
   
     **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `family <algo-params/family.html>`__: Specify the model type.

   -  If the family is **gaussian**, the data must be numeric (**Real** or **Int**).
   -  If the family is **binomial**, the data must be categorical 2 levels/classes or binary (**Enum** or **Int**).
   -  If the family is **multinomial**, the data can be categorical with more than two levels/classes (**Enum**).
   -  If the family is **poisson**, the data must be numeric and non-negative (**Int**).
   -  If the family is **gamma**, the data must be numeric and continuous and positive (**Real** or **Int**).
   -  If the family is **tweedie**, the data must be numeric and continuous (**Real**) and non-negative.
   -  If the family is **quasi_binomial**, the data must be numeric.

-  `tweedie_variance_power <algo-params/tweedie_variance_power.html>`__: (Only applicable if *Tweedie* is
   specified for **Family**) Specify the Tweedie variance power.

-  `tweedie_link_power <algo-params/tweedie_link_power.html>`__: (Only applicable if *Tweedie* is specified
   for **Family**) Specify the Tweedie link power.

-  `solver <algo-params/solver.html>`__: Specify the solver to use (AUTO, IRLSM, L\_BFGS, COORDINATE\_DESCENT\_NAIVE, or COORDINATE\_DESCENT). IRLSM is fast on problems with a small number of predictors and for lambda search with L1 penalty, while `L\_BFGS <http://cran.r-project.org/web/packages/lbfgs/vignettes/Vignette.pdf>`__ scales better for datasets with many columns. COORDINATE\_DESCENT is IRLSM with the covariance updates version of cyclical coordinate descent in the innermost loop. COORDINATE\_DESCENT\_NAIVE is IRLSM with the naive updates version of cyclical coordinate descent in the innermost loop. COORDINATE\_DESCENT\_NAIVE and COORDINATE\_DESCENT are currently experimental.

-  `alpha <algo-params/alpha.html>`__: Specify the regularization distribution between L1 and L2.

-  `lambda <algo-params/lambda.html>`__: Specify the regularization strength.

-  `lambda_search <algo-params/lambda_search.html>`__: Specify whether to enable lambda search, starting with lambda max. The given lambda is then interpreted as lambda min. 

-  `early_stopping <algo-params/early_stopping.html>`__: Specify whether to stop early when there is no more relative improvement on the training  or validation set.
   
-  `nlambdas <algo-params/nlambdas.html>`__: (Applicable only if **lambda\_search** is enabled) Specify the number of lambdas to use in the search. The default is 100.

-  `standardize <algo-params/standardize.html>`__: Specify whether to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is enabled by default.

-  `missing_values_handling <algo-params/missing_values_handling.html>`__: Specify how to handle missing values (Skip or MeanImputation).

-  `compute_p_values <algo-params/compute_p_values.html>`__: Request computation of p-values. Only applicable with no penalty (lambda = 0 and no beta constraints). Setting remove\_collinear\_columns is recommended. H2O will return an error if p-values are requested and there are collinear columns and remove\_collinear\_columns flag is not enabled.

-  `remove_collinear_columns <algo-params/remove_collinear_columns.html>`__: Specify whether to automatically remove collinear columns during model-building. When enabled, collinear columns will be dropped from the model and will have 0 coefficient in the returned model. This can only be set if there is no regularization (lambda=0).

-  `intercept <algo-params/intercept.html>`__: Specify whether to include a constant term in the model. This option is enabled by default.

-  `non_negative <algo-params/non_negative.html>`__: Specify whether to force coefficients to have non-negative values.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the number of training iterations.

-  `objective_epsilon <algo-params/objective_epsilon.html>`__: Specify a threshold for convergence. If the objective value is less than this threshold, the model is converged.

-  `beta_epsilon <algo-params/beta_epsilon.html>`__: Specify the beta epsilon value. If the L1 normalization of the current beta change is below this threshold, consider using convergence.

-  `gradient_epsilon <algo-params/gradient_epsilon.html>`__: (For L-BFGS only) Specify a threshold for convergence. If the objective value (using the L-infinity norm) is less than this threshold, the model is converged.

-  `link <algo-params/link.html>`__: Specify a link function (Identity, Family_Default, Logit,
   Log, Inverse, or Tweedie).

   -  If the family is **Gaussian**, then **Identity**, **Log**, and **Inverse** are supported.
   -  If the family is **Binomial**, then **Logit** is supported.
   -  If the family is **Poisson**, then **Log** and **Identity** are supported.
   -  If the family is **Gamma**, then **Inverse**, **Log**, and **Identity** are supported.
   -  If the family is **Tweedie**, then only **Tweedie** is supported.
   -  If the family is **Multinomial**, then only **Multinomial** is supported.
   -  If the family is **Quasi-Binomial**, then only **Logit** is supported.

-  `prior <algo-params/prior.html>`__: Specify prior probability for p(y==1). Use this parameter for logistic regression if the data has been sampled and the mean of response does not reflect reality. 
   
     **Note**: This is a simple method affecting only the intercept. You may want to use weights and offset for a better fit.

-  `lambda_min_ratio <algo-params/lambda_min_ratio.html>`__: Specify the minimum lambda to use for lambda search (specified as a ratio of **lambda\_max**).

-  `beta_constraints <algo-params/beta_constraints.html>`__: Specify a dataset to use beta constraints. The selected frame is used to constraint the coefficient vector to provide upper and lower bounds. The dataset must contain a names column with valid coefficient names.

-  `max_active_predictors <algo-params/max_active_predictors.html>`__: Specify the maximum number of active
   predictors during computation. This value is used as a stopping
   criterium to prevent expensive model building with many predictors.

-  `interactions <algo-params/interactions.html>`__: Specify a list of predictor column indices to interact. All pairwise combinations will be computed for this list. 

Interpreting a GLM Model
~~~~~~~~~~~~~~~~~~~~~~~~

By default, the following output displays:

-  A graph of the normalized coefficient magnitudes
-  Output (model category, model summary, scoring history, training
   metrics, validation metrics, best lambda, threshold, residual
   deviance, null deviance, residual degrees of freedom, null degrees of
   freedom, AIC, AUC, binomial, rank)
-  Coefficients
-  Coefficient magnitudes

Handling of Categorical Variables
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

GLM auto-expands categorical variables into one-hot encoded binary variables (i.e. if variable has levels "cat", "dog", "mouse", cat is encoded as 1,0,0, mouse is 0,1,0 and dog is 0,0,1). It is generally more efficient to let GLM perform auto-expansion instead of expanding data manually and it also adds the benefit of correct handling of different categorical mappings between different datasets as welll as handling of unseen categorical levels. Unlike binary numeric columns, auto-expanded variables are not standardized.

It is common to skip one of the levels during the one-hot encoding to prevent linear dependency between the variable and the intercept. H3O follows the convention of skipping the first level. This behavior can be controlled by setting use_all_factor_levels_flag (no level is going to be skipped if the flag is true). The default depends on regularization parameter - it is set to false if no regularization and to true otherwise. The reference level which is skipped is always the first level, you can change which level is the reference level by calling h2o.relevel function prior to building the model.

Regularization
~~~~~~~~~~~~~~

Regularization is used to attempt to solve problems with overfitting that can occur in GLM. Penalties can be introduced to the model building process to avoid overfitting, to reduce variance of the prediction error, and to handle correlated predictors. The two most common penalized models are ridge regression and LASSO (least absolute shrinkage and selection operator).  The elastic net combines both penalties using both the ``alpha`` and ``lambda`` options (i.e., values greater than 0 for both).

LASSO and Ridge Regression
''''''''''''''''''''''''''

LASSO represents the :math:`\ell{_1}` penalty and is an alternative regularized least squares method that penalizes the sum of the absolute coefficents :math:`||\beta||{_1} = \sum{^p_{k=1}} \beta{^2_k}`. LASSO leads to a sparse solution when the tuning parameter is sufficiently large. As the tuning parameter value :math:`\lambda` is increased, all coefficients are set to zero. Because reducing parameters to zero removes them from the model, LASSO is a good selection tool. 

Ridge regression penalizes the :math:`\ell{_2}` norm of the model coefficients :math:`||\beta||{^2_2} = \sum{^p_{k=1}} \beta{^2_k}`. It provides greater numerical stability and is easier and faster to compute than LASSO. It keeps all the predictors in the model and shrinks them proportionally. Ridge regression reduces coefficient values simultaneously as the
penalty is increased without setting any of them to zero.

Variable selection is important in numerous modern applications wiht many covariates where the :math:`\ell{_1}` penalty has proven to be successful. Therefore, if the number of variables is large or if the solution is known to be sparse, we recommend using LASSO, which will select a small number of variables for sufficiently high :math:`\lambda` that could be crucial to the inperpretability of the mode. The :math:`\ell{_2}` norm does not have this effect; it shrinks the coefficients but does not set them exactly to zero. 

The two penalites also differ in the presence of correlated predictors. The :math:`\ell{_2}` penalty shrinks coefficients for correlated columns toward each other, while the :math:`\ell{_1}` penalty tends to select only one of them and sets the other coefficients to zero. Using the elastic net argument :math:`\alpha` combines these two behaviors. 

The elastic net method selects variables and preserves the grouping effect (shrinking coefficients of correlated columns together). Moreover, while the number of predictors that can enter a LASSO model saturates at min :math:`(n,p)` (where :math:`n` is the number of observations, and :math:`p` is the number of variables in the model), the elastic net does not have this limitation and can fit models with a larger number of predictors. 

Elastic Net Penalty
'''''''''''''''''''

As indicated previously, elastic net regularization is a combination of the :math:`\ell{_1}` and :math:`\ell{_2}` penalties parametrized by the :math:`\alpha` and :math:`\lambda` arguments (similar to "Regularization Paths for Genarlized Linear Models via Coordinate Descent" by Friedman et all).

 - :math:`\alpha` controls the elastic net penalty distribution between the :math:`\ell_1` and :math:`\ell_2` norms. It can have any value in the [0,1] range or a vector of values (via grid search). If :math:`\alpha=0`, then H2O solves the GLM using ridge regression. If :math:`\alpha=1`, then LASSO penalty is used. 

 - :math:`\lambda` controls the penalty strength. The range is any positive value or a vector of values (via grid search). Note that :math:`\lambda` values are capped at :math:`\lambda_{max}`, which is the smallest :math:`\lambda` for which the solution is all zeros (except for the intercept term).

The combination of the :math:`\ell_1` and :math:`\ell_2` penalties is beneficial because :math:`\ell_1` induces sparsity, while :math:`\ell_2` gives stability and encourages the grouping effect (where a group of correlated variables tend to be dropped or added into the model simultaneously). When focusing on sparsity, one possible use of the :math:`\alpha` argument involves using the :math:`\ell_1` mainly with very little :math:`\ell_2` (:math:`\alpha` almost 1) to stabilize the computation and improve convergence speed.

Regularization Parameters in GLM
''''''''''''''''''''''''''''''''

To get the best possible model, we need to find the optimal values of the regularization parameters :math:`\alpha` and
:math:`\lambda`.  To find the optimal values, H2O allows you to perform a grid search over :math:`\alpha` and a special form of grid search called "lambda search" over :math:`\lambda`.

The recommended way to find optimal regularization settings on H2O is to do a grid search over a few :math:`\alpha` values with an automatic lambda search for each :math:`\alpha`. 

- **Alpha**

 The ``alpha`` parameter controls the distribution between the :math:`\ell{_1}` (LASSO) and :math:`\ell{_2}` (ridge regression) penalties. A value of 1.0 for ``alpha`` represents LASSO, and an ``alpha`` value of 0.0 produces ridge reguression. 

- **Lambda**

 The ``lambda`` parameter controls the amount of regularization applied. If ``lambda`` is 0.0, no regularization is applied, and the ``alpha parameter is ignored. The default value for ``lambda`` is calculated by H2O using a heuristic based on the training data. If you allow H2O to calculate the value for ``lambda``, you can see the chosen value in the model output. 

Lambda Search
'''''''''''''

If the ``lambda_search`` option is set, GLM will compute models for full regularization path similar to glmnet (see glmnet paper). Regularziation path starts at lambda max (highest lambda values which makes sense - i.e. lowest value driving all coefficients to zero) and goes down to lambda min on log scale, decreasing regularization strength at each step. The returned model will have coefficients corresponding to the “optimal” lambda value as decided during training.

When looking for a sparse solution (``alpha`` > 0), lambda search can also be used to effeciently handle very wide datasets because it can filter out inactive predictors (noise) and only build models for a small subset of predictors. A common use of lambda search is to run it on a dataset with many predictors but limit the number of active predictors to a relatively small value. 

Lambda search can be configured along with the following arguments:

- ``alpha``: Regularization distribution between :math:`\ell_1` and :math:`\ell_2`.
- ``validation_frame`` and/or ``nfolds``: Used to select the best lambda based on the cross-validation performance or the validation or training data. If available, cross-validation performance takes precedence. If no validation data is available, the best lambda is selected based on training data performance and is therefore guaranteed to always be the minimal lambda computed since GLM cannot overfit on a training dataset.

 **Note**: If running lambda search with a validation dataset and cross-validation disabled, the chosen lambda value corresponds to the lambda with the lowest validation error. The validation dataset is used to select the model, and the model performance should be evaluated on another independent test dataset.

- ``lambda_min_ratio`` and ``nlambdas``: The sequence of the :math:`\lambda` values is automatically generated as an exponentially decreasing sequence. It ranges from :math:`\lambda_{max}` (the smallest :math:`\lambda` so that the solution is a model with all 0s) to :math:`\lambda_{min} =` ``lambda_min_ratio`` :math:`\times` :math:`\lambda_{max}`.

 H2O computes :math:`\lambda` models sequentially and in decreasing order, warm-starting the model (using the previous solutin as the initial prediction) for :math:`\lambda_k` with the solution for :math:`\lambda_{k-1}`. By warm-starting the models, we get better performance. Typically models for subsequent :math:`\lambda` values are close to each other, so only a few iterations per :math:`\lambda` are needed (two or three). This also achieves greater numerical stability because models with a higher penalty are easier to compute. This method starts with an easy problem and then continues to make small adjustments. 

 **Note**: ``lambda_min_ratio`` and ``nlambdas`` also specify the relative distance of any two lambdas in the sequence. This is important when applying recursive strong rules, which are only effective if the neighboring lambdas are "close" to each other. The default value for ``lambda_min_ratio`` is :math:`1e^{-4}`, and the default value for ``nlambdas`` is 100. This gives a ratio of 0.912. For best results when using strong rules, keep the ratio close to this default.

- ``max_active_predictors``: This limits the number of active predictors. (The actual number of non-zero predictors in the  model is going to be slightly  lower.) It is useful when obtaining a sparse solution to avoid costly computation of models with too many predictors.

Full Regularization Path
''''''''''''''''''''''''

It can sometimes be useful to see the coefficients for all lambda values or to override default lambda selection. Full regularization path can be extracted from both R and python clients (currently not from Flow). It returns coefficients (and standardized coefficients) for all computed lambda values and also the explained deviances on both train and validation. Subsequently, the makeGLMModel call can be used to create an H2O GLM model with selected coefficients.

To extract the regularization path from R or python:

- R: call h2o.getGLMFullRegularizationPath, takes the model as an argument
- Python: H2OGeneralizedLinearEstimator.getGLMRegularizationPath (static method), takes the model as an argument


Modifying or Creating Custom GLM Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In R and python, the makeGLMModel call can be used to create an H2O model from given coefficients. It needs a source GLM model trained on the same dataset to extract the dataset information. To make a custom GLM model from R or python:

- R: call h2o.makeGLMModel. This takes a model, a vector of coefficients, and (optional) decision threshold as parameters.
- Pyton: H2OGeneralizedLinearEstimator.makeGLMModel (static method) takes a model, a dictionary containing coefficients, and (optional) decision threshold as parameters.


FAQ
~~~

-  **How does the algorithm handle missing values during training?**

  Depending on the selected missing value handling policy, they are either imputed mean or the whole row is skipped. The default behavior is mean imputation. Note that categorical variables are imputed by adding an extra "missing" level. Optionally, glm can skip all rows with any missing values.

-  **How does the algorithm handle missing values during testing?** 

  Same as during training. If the missing value handling is set to skip and we are generating predictions, skipped rows will have Na (missing) prediction.

-  **What happens if the response has missing values?**

  The rows with missing response are ignored during model training and validation.

-  **What happens during prediction if the new sample has categorical
   levels not seen in training?** 
   
  The value will be filled with either 0 or a special missing level (if trained with missing values, and ``missing\_value\_handling`` was set to **MeanImputation**).

-  **Does it matter if the data is sorted?**

  No.

-  **Should data be shuffled before training?**

  No.

-  **How does the algorithm handle highly imbalanced data in a response
   column?**

  GLM does not require special handling for imbalanced data.

-  **What if there are a large number of columns?**

  IRLS will get quadratically slower with the number of columns. Try L-BFGS for datasets with more than 5-10 thousand columns.

-  **What if there are a large number of categorical factor levels?**

  GLM internally one-hot encodes the categorical factor levels; the same limitations as with a high column count will apply.

-  **When building the model, does GLM use all features or a selection
   of the best features?**

  Typically, GLM picks the best predictors, especially if lasso is used (``alpha = 1``). By default, the GLM model includes an L1 penalty and will pick only the most predictive predictors.

-  **When running GLM, is it better to create a cluster that uses many
   smaller nodes or fewer larger nodes?**

  A rough heuristic would be:

   :math:`nodes ~=M *N^2/(p * 1e8)`

  where :math:`M` is the number of observations, :math:`N` is the number of columns (categorical columns count as a single column in this case), and :math:`p` is the number of CPU cores per node.

  For example, a dataset with 250 columns and 1M rows would optimally use about 20 nodes with 32 cores each (following the formula :math:`250^2 *1000000/(32* 1e8) = 19.5 ~= 20)`.

-  **How is variable importance calculated for GLM?**

  For GLM, the variable importance represents the coefficient magnitudes.
  
-  **How does GLM define and check for convergence during logistic regression?**

  GLM includes three convergence criteria outside of max iterations:
  	
  	- beta epsilon: beta stops changing. This is used mostly with IRLSM. 
  	- gradient epsilon: gradient is too small. Thi sis used mostly with L-BFGS.
  	- objective epsilon: relative objective improvement is too small. This is used by all solvers.

  The default values below are based on a heuristic:

   - The default for beta epsilon is 1e-4.  
   - The default for gradient epsilon is 1e-6 if there is no regularization (lambda = 0) or you are running with lambda search; 1e-4 otherwise.
   - The default for objective epsilon is 1e-6 if lambda = 0; 1e-4 otherwise.

  The default for max iterations depends on the solver type and whether you run with lambda search:
 
   - for IRLSM, the default  is 50 if no lambda search; 10* number of lambdas otherwise 
   - for LBFGS, the default is number of classes (1 if not classification) * max(20, number of predictors /4 ) if no lambda search; it is number of classes * 100 * n-lambdas with lambda search.
   
  You will receive a warning if you reach the maximum number of iterations. In some cases, GLM  can end prematurely if it can not progress forward via line search. This typically happens when running a lambda search with IRLSM solver. Note that using CoordinateDescent solver fixes the issue.

GLM Algorithm
~~~~~~~~~~~~~

Following the definitive text by P. McCullagh and J.A. Nelder (1989) on
the generalization of linear models to non-linear distributions of the
response variable Y, H2O fits GLM models based on the maximum likelihood
estimation via iteratively reweighed least squares.

Let :math:`y_{1},…,y_{n}` be n observations of the independent, random
response variable :math:`Y_{i}`.

Assume that the observations are distributed according to a function
from the exponential family and have a probability density function of
the form:

  :math:`f(y_{i})=exp[\frac{y_{i}\theta_{i} - b(\theta_{i})}{a_{i}(\phi)} + c(y_{i}; \phi)]` where :math:`\theta` and :math:`\phi` are location and scale parameters, and :math:`a_{i}(\phi)`, :math:`b_{i}(\theta{i})`, and :math:`c_{i}(y_{i}; \phi)` are known functions.

  :math:`a_{i}` is of the form :math:`a_{i}= \frac{\phi}{p_{i}}` where :math:`p_{i}` is a known prior weight.

When :math:`Y` has a pdf from the exponential family:

 :math:`E(Y_{i})=\mu_{i}=b^{\prime} var(Y_{i})=\sigma_{i}^2=b^{\prime\prime}(\theta_{i})a_{i}(\phi)`

Let :math:`g(\mu_{i})=\eta_{i}` be a monotonic, differentiable transformation of the expected value of :math:`y_{i}`. The function :math:`\eta_{i}` is the link function and follows a
linear model.

  :math:`g(\mu_{i})=\eta_{i}=\mathbf{x_{i}^{\prime}}\beta`

When inverted: :math:`\mu=g^{-1}(\mathbf{x_{i}^{\prime}}\beta)`

**Maximum Likelihood Estimation**

For an initial rough estimate of the parameters :math:`\hat{\beta}`, use the estimate to generate fitted values: :math:`\mu_{i}=g^{-1}(\hat{\eta_{i}})`

Let :math:`z` be a working dependent variable such that :math:`z_{i}=\hat{\eta_{i}}+(y_{i}-\hat{\mu_{i}})\frac{d\eta_{i}}{d\mu_{i}}`,

 where :math:`\frac{d\eta_{i}}{d\mu_{i}}` is the derivative of the link function evaluated at the trial estimate.

Calculate the iterative weights: :math:`w_{i}=\frac{p_{i}}{[b^{\prime\prime}(\theta_{i})\frac{d\eta_{i}}{d\mu_{i}}^{2}]}`

 where :math:`b^{\prime\prime}` is the second derivative of :math:`b(\theta_{i})` evaluated at the trial estimate.

Assume :math:`a_{i}(\phi)` is of the form :math:`\frac{\phi}{p_{i}}`. The weight :math:`w_{i}` is inversely proportional to the variance of the working dependent variable :math:`z_{i}` for current parameter estimates and proportionality factor :math:`\phi`.

Regress :math:`z_{i}` on the predictors :math:`x_{i}` using the weights :math:`w_{i}` to obtain new estimates of :math:`\beta`. 

  :math:`\hat{\beta}=(\mathbf{X}^{\prime}\mathbf{W}\mathbf{X})^{-1}\mathbf{X}^{\prime}\mathbf{W}\mathbf{z}`

 where :math:`\mathbf{X}` is the model matrix, :math:`\mathbf{W}` is a diagonal matrix of :math:`w_{i}`, and :math:`\mathbf{z}` is a vector of the working response variable :math:`z_{i}`.

This process is repeated until the estimates :math:`\hat{\beta}` change by less than the specified amount.

**Cost of computation**

H2O can process large data sets because it relies on parallel processes.
Large data sets are divided into smaller data sets and processed
simultaneously and the results are communicated between computers as
needed throughout the process.

In GLM, data are split by rows but not by columns, because the predicted
Y values depend on information in each of the predictor variable
vectors. If O is a complexity function, N is the number of observations
(or rows), and P is the number of predictors (or columns) then

  :math:`Runtime \propto p^3+\frac{(N*p^2)}{CPUs}`

Distribution reduces the time it takes an algorithm to process because
it decreases N.

Relative to P, the larger that (N/CPUs) becomes, the more trivial p
becomes to the overall computational cost. However, when p is greater
than (N/CPUs), O is dominated by p.

  :math:`Complexity = O(p^3 + N*p^2)`

For more information about how GLM works, refer to the `Generalized
Linear Modeling booklet <http://h2o.ai/resources>`__.

References
~~~~~~~~~~

Breslow, N E. “Generalized Linear Models: Checking Assumptions and
Strengthening Conclusions.” Statistica Applicata 8 (1996): 23-41.

`Jerome Friedman, Trevor Hastie, and Rob Tibshirani. Regularization Paths for Generalized Linear Models via Coordinate Descent. Journal of Statistical Software, 33(1), 2009. <http://core.ac.uk/download/pdf/6287975.pdf>`__

`Frome, E L. “The Analysis of Rates Using Poisson Regression Models.”
Biometrics (1983):
665-674. <http://www.csm.ornl.gov/~frome/BE/FP/FromeBiometrics83.pdf>`__

`Goldberger, Arthur S. “Best Linear Unbiased Prediction in the
Generalized Linear Regression Model.” Journal of the American
Statistical Association 57.298 (1962):
369-375. <http://people.umass.edu/~bioep740/yr2009/topics/goldberger-jasa1962-369.pdf>`__

`Guisan, Antoine, Thomas C Edwards Jr, and Trevor Hastie. “Generalized
Linear and Generalized Additive Models in Studies of Species
Distributions: Setting the Scene.” Ecological modeling 157.2 (2002):
89-100. <http://www.stanford.edu/~hastie/Papers/GuisanEtAl_EcolModel-2003.pdf>`__

`Nelder, John A, and Robert WM Wedderburn. “Generalized Linear Models.”
Journal of the Royal Statistical Society. Series A (General) (1972):
370-384. <https://docs.ufpr.br/~taconeli/CE225/Artigo.pdf>`__

`Pearce, Jennie, and Simon Ferrier. “Evaluating the Predictive
Performance of Habitat Models Developed Using Logistic Regression.”
Ecological modeling 133.3 (2000):
225-245. <http://www.whoi.edu/cms/files/Ecological_Modelling_2000_Pearce_53557.pdf>`__

`Press, S James, and Sandra Wilson. “Choosing Between Logistic
Regression and Discriminant Analysis.” Journal of the American
Statistical Association 73.364 (April, 2012):
699–705. <http://math.arizona.edu/~hzhang/math574m/LogitOrLDA.pdf>`__

Snee, Ronald D. “Validation of Regression Models: Methods and Examples.”
Technometrics 19.4 (1977): 415-428.

