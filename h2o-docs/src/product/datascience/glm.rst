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

-  **model\_id**: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  **training\_frame**: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  **validation\_frame**: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  **nfolds**: Specify the number of folds for cross-validation.

-  **response\_column**: (Required) Specify the column to use as the
   independent variable.

   -  For a regression model, this column must be numeric (**Real** or
      **Int**).
   -  For a classification model, this column must be categorical
      (**Enum** or **String**). If the family is **Binomial**, the
      dataset cannot contain more than two levels.

-  **ignored\_columns**: (Optional) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column
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

-  **ignore\_const\_cols**: Enable this option to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  **family**: Specify the model type.

   -  If the family is **gaussian**, the data must be numeric (**Real** or **Int**).
   -  If the family is **binomial**, the data must be categorical 2 levels/classes or binary (**Enum** or **Int**).
   -  If the family is **multinomial**, the data can be categorical with more than two levels/classes (**Enum**).
   -  If the family is **poisson**, the data must be numeric and non-negative (**Int**).
   -  If the family is **gamma**, the data must be numeric and continuous and positive (**Real** or **Int**).
   -  If the family is **tweedie**, the data must be numeric and continuous (**Real**) and non-negative.

-  **tweedie\_variance\_power**: (Only applicable if *Tweedie* is
   specified for **Family**) Specify the Tweedie variance power.

-  **tweedie\_link\_power**: (Only applicable if *Tweedie* is specified
   for **Family**) Specify the Tweedie link power.

-  **solver**: Specify the solver to use (AUTO, IRLSM, L\_BFGS,
   COORDINATE\_DESCENT\_NAIVE, or COORDINATE\_DESCENT). IRLSM is fast on
   on problems with a small number of predictors and for lambda-search
   with L1 penalty, while
   `L\_BFGS <http://cran.r-project.org/web/packages/lbfgs/vignettes/Vignette.pdf>`__
   scales better for datasets with many columns. COORDINATE\_DESCENT is
   IRLSM with the covariance updates version of cyclical coordinate
   descent in the innermost loop. COORDINATE\_DESCENT\_NAIVE is IRLSM
   with the naive updates version of cyclical coordinate descent in the
   innermost loop. COORDINATE\_DESCENT\_NAIVE and COORDINATE\_DESCENT
   are currently experimental.

-  **alpha**: Specify the regularization distribution between L2 and L2.

-  **lambda**: Specify the regularization strength.

-  **lambda\_search**: Specify whether to enable lambda search,
   starting with lambda max. The given lambda is then interpreted as
   lambda min. 
   
-  **nlambdas**: (Applicable only if **lambda\_search** is enabled)
   Specify the number of lambdas to use in the search. The default is
   100.

-  **standardize**: Specify whether to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is enabled by default.

-  **remove\_collinear\_columns**: Specify whether to automatically remove collinear
   columns during model-building. When enabled, collinear columns will be dropped from
   the model and will have 0 coefficient in the returned model. This can only
   be set if there is no regularization (lambda=0).

-  **compute\_p\_values**: Request computation of p-values. Only
   applicable with no penalty (lambda = 0 and no beta constraints).
   Setting remove\_collinear\_columns is recommended. H2O will return an
   error if p-values are requested and there are collinear columns and
   remove\_collinear\_columns flag is not enabled.

-  **non-negative**: Specify whether to force coefficients to have non-negative values.

-  **beta\_constraints**: Specify a dataset to use beta constraints. The selected frame is used to constraint the coefficient vector to provide upper and lower bounds. The dataset must contain a names column with valid coefficient names.

-  **fold\_assignment**: (Applicable only if a value for **nfolds** is
   specified and **fold\_column** is not specified) Specify the
   cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random, 
   `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  **fold\_column**: Specify the column that contains the
   cross-validation fold index assignment per observation.

-  **score\_each\_iteration**: (Optional) Enable this option to score
   during each iteration of the model training.

-  **offset\_column**: Specify a column to use as the offset; the value
   cannot be the same as the value for the ``weights_column``.
   
     **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__.

-  **weights\_column**: Specify a column to use for the observation
   weights, which are used for bias correction. The specified
   ``weights_column`` must be included in the specified
   ``training_frame``. *Python only*: To use a weights column when
   passing an H2OFrame to ``x`` instead of a list of column names, the
   specified ``training_frame`` must contain the specified
   ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  **max\_iterations**: Specify the number of training iterations.

-  **link**: Specify a link function (Identity, Family\_Default, Logit,
   Log, Inverse, or Tweedie).

   -  If the family is **Gaussian**, **Identity**, **Log**, and **Inverse** are supported.
   -  If the family is **Binomial**, **Logit** is supported.
   -  If the family is **Poisson**, **Log** and **Identity** are supported.
   -  If the family is **Gamma**, **Inverse**, **Log**, and **Identity** are supported.
   -  If the family is **Tweedie**, only **Tweedie** is supported.

-  **max\_confusion\_matrix\_size**: Specify the maximum size (number of
   classes) for the confusion matrices printed in the logs.

-  **max\_hit\_ratio\_k**: (Applicable for classification only) Specify
   the maximum number (top K) of predictions to use for hit ratio
   computation. Applicable to multi-class only. To disable, enter ``0``.
   
-  **missing\_values\_handling**: Specify how to handle missing values
   (Skip or MeanImputation).

-  **keep\_cross\_validation\_predictions**: Specify whether to keep the
   cross-validation predictions.

-  **intercept**: Specify whether to include a constant term in the model. This option is enabled by default.

-  **objective\_epsilon**: Specify a threshold for convergence. If the
   objective value is less than this threshold, the model is converged.

-  **beta\_epsilon**: Specify the beta epsilon value. If the L1
   normalization of the current beta change is below this threshold,
   consider using convergence.

-  **gradient\_epsilon**: (For L-BFGS only) Specify a threshold for
   convergence. If the objective value (using the L-infinity norm) is
   less than this threshold, the model is converged.

-  **prior**: Specify prior probability for p(y==1). Use this parameter
   for logistic regression if the data has been sampled and the mean of
   response does not reflect reality. 
   
     **Note**: This is a simple method affecting only the intercept. You may want to use weights and offset for a better fit.

-  **lambda\_min\_ratio**: Specify the minimum lambda to use for lambda
   search (specified as a ratio of **lambda\_max**).

-  **max\_active\_predictors**: Specify the maximum number of active
   predictors during computation. This value is used as a stopping
   criterium to prevent expensive model building with many predictors.

-  **seed**: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations.

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

GLM auto-expands categorical variables into one-hot encoded binary variables (i.e. if variable has levels “cat”,”dog”, “mouse”, cat is encoded as 1,0,0, mouse is 0,1,0 and dog is 0,0,1). It is generally more efficient to let GLM perform auto-expansion instead of expanding data manually and it also adds the benefit of correct handling of different categorical mappings between different datasets as welll as handling of unseen categorical levels. Unlike binary numeric columns, auto-expanded variables are not standardized.

It is common to skip one of the levels during the one-hot encoding to prevent linear dependency between the variable and the intercept. H3O follows the convention of skipping the first level. This behavior can be controlled by setting use_all_factor_levels_flag (no level is going to be skipped if the flag is true). The default depends on regularization parameter - it is set to false if no regularization and to true otherwise. The reference level which is skipped is always the first level, you can change which level is the reference level by calling h2o.relevel function prior to building the model.


Lambda Search and Full Regularization Path
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If the ``lambda_search`` option is set, GLM will compute models for full regularization path similar to glmnet (see glmnet paper). Regularziation path starts at lambda max (highest lambda values which makes sense - i.e. lowest value driving all coefficients to zero) and goes down to lambda min on log scale, decreasing regularization strength at each step. The returned model will have coefficients corresponding to the “optimal” lambda value as decided during training.

It can sometimes be useful to see the coefficients for all lambda values. Or to override default lambda selection. Full regularization path can be extracted from both R and python clients (currently not from Flow). It returns coefficients (and standardized coefficients) for all computed lambda values and also explained deviances on both train and validation. Subsequently, makeGLMModel call can be used to create h2o glm model with selected coefficients.

To extract the regularization path from R or python:

- R: call h2o.getGLMFullRegularizationPath, takes the model as an argument
- Python: H2OGeneralizedLinearEstimator.getGLMRegularizationPath (static method), takes the model as an rgument


Modifying or Creating Custom GLM Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In R and python, makeGLMModel call can be used to create h2o model from given coefficients. It needs a source glm model trained on the same dataset to extract dataset information. To make custom GLM model from R or python:

- R: call h2o.makeGLMModel, takes a model and a vector of coefficients and (optional) decision threshold as parameters.
- Pyton: H2OGeneralizedLinearEstimator.makeGLMModel (static method), takes a model, dictionary containing coefficients and (optional) decision threshold as parameters.


FAQ
~~~

-  **How does the algorithm handle missing values during training?**

  Depending on the selected missing value handling policy, they are either imputed mean or the whole row is skipped. The default behavior is mean imputation. Note that categorical variables are imputed by adding extra "missing" level. Optionally, glm can skip all rows with any missing values.

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
370-384. <http://biecek.pl/MIMUW/uploads/Nelder_GLM.pdf>`__

`Niu, Feng, et al. “Hogwild!: A lock-free approach to parallelizing
stochastic gradient descent.” Advances in Neural Information Processing
Systems 24 (2011): 693-701.\*implemented algorithm on
p.5 <http://www.eecs.berkeley.edu/~brecht/papers/hogwildTR.pdf>`__

`Pearce, Jennie, and Simon Ferrier. “Evaluating the Predictive
Performance of Habitat Models Developed Using Logistic Regression.”
Ecological modeling 133.3 (2000):
225-245. <http://www.whoi.edu/cms/files/Ecological_Modelling_2000_Pearce_53557.pdf>`__

`Press, S James, and Sandra Wilson. “Choosing Between Logistic
Regression and Discriminant Analysis.” Journal of the American
Statistical Association 73.364 (April, 2012):
699–705. <http://www.statpt.com/logistic/press_1978.pdf>`__

Snee, Ronald D. “Validation of Regression Models: Methods and Examples.”
Technometrics 19.4 (1977): 415-428.

