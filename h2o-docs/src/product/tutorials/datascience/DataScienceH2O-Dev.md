# Data Science Algorithms

>**Note**: This topic is no longer being maintained. Refer to the topics in the [Data Science](https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/data-science) folder for the most up-to-date documentation.


This document describes how to define the models and how to interpret the model, as well the algorithm itself, and provides an FAQ. 

## Commonalities 

### Quantiles


**Note**: The quantile results in Flow are computed lazily on-demand and cached. It is a fast approximation (max - min / 1024) that is very accurate for most use cases. 
If the distribution is skewed, the quantile results may not be as accurate as the results obtained using `h2o.quantile` in R or `H2OFrame.quantile` in Python.  

<a name="Kmeans"></a>
## K-Means

### Introduction

K-Means falls in the general category of clustering algorithms.

### Defining a K-Means Model

- **model_id**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates a destination key. 

- **training_frame**: (Required) Select the dataset used to build the model. 
**NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **ignored_columns**: (Optional) Click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons. 

- **ignore\_const\_cols**: (Optional) Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default. 

- **k***: Specify the number of clusters.  

- **user_points**: Specify a vector of initial cluster centers. The user-specified points must have the same number of columns as the training observations. The number of rows must equal the number of clusters. 

- **max_iterations**: Specify the maximum number of training iterations. The range is 0 to 1e6.

- **init**: Select the initialization mode. The options are Random, Furthest, PlusPlus, or User. **Note**: If PlusPlus is selected, the initial Y matrix is chosen by the final cluster centers from the K-Means PlusPlus algorithm. 

- **fold_assignment**: (Applicable only if a value for **nfolds** is specified and **fold_column** is not selected) Select the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, or [Modulo](https://en.wikipedia.org/wiki/Modulo_operation). 

- **fold_column**: Select the column that contains the cross-validation fold index assignment per observation. 

- **score\_each\_iteration**: (Optional) Check this checkbox to score during each iteration of the model training. 

- **standardize**: To standardize the numeric columns to have mean of zero and unit variance, check this checkbox. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is selected by default. 

 >**Note**: If standardization is enabled, each column of numeric data is centered and scaled so that its mean is zero and its standard deviation is one before the algorithm is used. At the end of the process, the cluster centers on both the standardized scale (`centers_std`) and the de-standardized scale (`centers`) are displayed. 
 >To de-standardize the centers, the algorithm multiplies by the original standard deviation of the corresponding column and adds the original mean. Enabling standardization is mathematically equivalent to using `h2o.scale` in R with `center` = TRUE and `scale` = TRUE on the numeric columns. Therefore, there will be no discernible difference if standardization is enabled or not for K-Means, since H2O calculates unstandardized centroids. 

- **keep\_cross\_validation\_predictions**: To keep the cross-validation predictions, check this checkbox. 
 
- **seed**: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

### Interpreting a K-Means Model

By default, the following output displays:

- A graph of the scoring history (number of iterations vs. average within the cluster's sum of squares) 
- Output (model category, validation metrics if applicable, and centers std)
- Model Summary (number of clusters, number of categorical columns, number of iterations, avg. within sum of squares, avg. sum of squares, avg. between the sum of squares)
- Scoring history (number of iterations, avg. change of standardized centroids, avg. within cluster sum of squares)
- Training metrics (model name, checksum name, frame name, frame checksum name, description if applicable, model category, duration in ms, scoring time, predictions, MSE, avg. within sum of squares, avg. between sum of squares)
- Centroid statistics (centroid number, size, within sum of squares)
- Cluster means (centroid number, column)

K-Means randomly chooses starting points and converges to a local minimum of centroids. The number of clusters is arbitrary, and should be thought of as a tuning parameter.
The output is a matrix of the cluster assignments and the coordinates of the cluster centers in terms of the originally chosen attributes. Your cluster centers may differ slightly from run to run as this problem is Non-deterministic Polynomial-time (NP)-hard.

### FAQ

- **How does the algorithm handle missing values during training?**
   
  Missing values are automatically imputed by the column mean.  K-means also handles missing values by assuming that missing feature distance contributions are equal to the average of all other distance term contributions.

- **How does the algorithm handle missing values during testing?**
   
  Missing values are automatically imputed by the column mean of the training data.

- **Does it matter if the data is sorted?** 
  
  No.

- **Should data be shuffled before training?**
  
  No.

- **What if there are a large number of columns?**
  
  K-Means suffers from the curse of dimensionality: all points are roughly at the same distance from each other in high dimensions, making the algorithm less and less useful.

- **What if there are a large number of categorical factor levels?**

  This can be problematic, as categoricals are one-hot encoded on the fly, which can lead to the same problem as datasets with a large number of columns.



### K-Means Algorithm

The number of clusters \(K\) is user-defined and is determined a priori. 

1. Choose \(K\) initial cluster centers \(m_{k}\) according to one of
   the following:

    - **Randomization**: Choose \(K\) clusters from the set of \(N\) observations at random so that each observation has an equal chance of being chosen.

    - **Plus Plus**  

      a. Choose one center \(m_{1}\) at random. 

      2.  Calculate the difference between \(m_{1}\) and each of the remaining \(N-1\) observations \(x_{i}\). 
  \(d(x_{i}, m_{1}) = ||(x_{i}-m_{1})||^2\)

      3. Let \(P(i)\) be the probability of choosing \(x_{i}\) as \(m_{2}\). Weight \(P(i)\) by \(d(x_{i}, m_{1})\) so that those \(x_{i}\) furthest from \(m_{2}\) have  a higher probability of being selected than those \(x_{i}\) close to \(m_{1}\).

      4. Choose the next center \(m_{2}\) by drawing at random according to the weighted probability distribution. 

      5.  Repeat until \(K\) centers have been chosen.

   - **Furthest**

       a. Choose one center \(m_{1}\) at random. 

       2. Calculate the difference between \(m_{1}\) and each of the remaining \(N-1\) observations \(x_{i}\). 
       \(d(x_{i}, m_{1}) = ||(x_{i}-m_{1})||^2\)

       3. Choose \(m_{2}\) to be the \(x_{i}\) that maximizes \(d(x_{i}, m_{1})\).

       4. Repeat until \(K\) centers have been chosen. 

2. Once \(K\) initial centers have been chosen calculate the difference between each observation \(x_{i}\) and each of the centers \(m_{1},...,m_{K}\), where difference is the squared Euclidean distance taken over \(p\) parameters.  
  
   \(d(x_{i}, m_{k})=\)
   \(\sum_{j=1}^{p}(x_{ij}-m_{k})^2=\)
 \(\lVert(x_{i}-m_{k})\rVert^2\)


3. Assign \(x_{i}\) to the cluster \(k\) defined by \(m_{k}\) that minimizes \(d(x_{i}, m_{k})\)

4. When all observations \(x_{i}\) are assigned to a cluster calculate the mean of the points in the cluster. 

	\(\bar{x}(k)=\lbrace\bar{x_{i1}},…\bar{x_{ip}}\rbrace\)

5. Set the \(\bar{x}(k)\) as the new cluster centers \(m_{k}\). Repeat steps 2 through 5 until the specified number of max iterations is reached or cluster assignments of the \(x_{i}\) are stable.



### References

[Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The Elements of Statistical Learning. Vol.1. N.p., Springer New York, 2001.](http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf)

Xiong, Hui, Junjie Wu, and Jian Chen. “K-means Clustering Versus Validation Measures: A Data- distribution Perspective.” Systems, Man, and Cybernetics, Part B: Cybernetics, IEEE Transactions on 39.2 (2009): 318-331.

---

<a name="GLM"></a>
## GLM

### Introduction

Generalized Linear Models (GLM) estimate regression models for outcomes following exponential distributions. In addition to the Gaussian (i.e. normal) distribution, these include Poisson, binomial, and gamma distributions. Each serves a different purpose, and depending on distribution and link function choice, can be used either for prediction or classification.

The GLM suite includes:

- Gaussian regression
- Poisson regression
- Binomial regression (classification)
- Multinomial classification
- Gamma regression


### Defining a GLM Model

- **model_id**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates a destination key. 

- **training_frame**: (Required) Select the dataset used to build the model. 
**NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **nfolds**: Specify the number of folds for cross-validation.

- **response_column**: (Required) Select the column to use as the independent variable.
	
	- For a regression model, this column must be numeric (**Real** or **Int**).
	- For a classification model, this column must be categorical (**Enum** or **String**).  If the family is **Binomial**, the dataset cannot contain more than two levels. 

- **ignored_columns**: (Optional) Click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons. 

- **ignore\_const\_cols**: Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default. 

- **family**: Select the model type.
	> - If the family is **gaussian**, the data must be numeric (**Real** or **Int**).
	> - If the family is **binomial**, the data must be categorical 2 levels/classes or binary (**Enum** or **Int**).
	> - If the family is **multinomial**, the data can be categorical with more than two levels/classes (**Enum**).
	> - If the family is **poisson**, the data must be numeric and non-negative (**Int**).
	> - If the family is **gamma**, the data must be numeric and continuous and positive (**Real** or **Int**).
	> - If the family is **tweedie**, the data must be numeric and continuous (**Real**) and non-negative.

- **tweedie_variance_power**: (Only applicable if *Tweedie* is selected for **Family**) Specify the Tweedie variance power. 

- **tweedie_link_power**: (Only applicable if *Tweedie* is selected for **Family**) Specify the Tweedie link power. 

- **solver**: Select the solver to use (AUTO, IRLSM, L\_BFGS, COORDINATE\_DESCENT\_NAIVE, or COORDINATE\_DESCENT). IRLSM is fast on on problems with a small number of predictors and for lambda-search with L1 penalty, while [L_BFGS](http://cran.r-project.org/web/packages/lbfgs/vignettes/Vignette.pdf) scales better for datasets with many columns.  COORDINATE\_DESCENT is IRLSM with the covariance updates version of cyclical coordinate descent in the innermost loop. COORDINATE\_DESCENT\_NAIVE is IRLSM with the naive updates version of cyclical coordinate descent in the innermost loop.

- **alpha**: Specify the regularization distribution between L2 and L2.  

- **lambda**:  Specify the regularization strength. 

- **lambda_search**: Check this checkbox to enable lambda search, starting with lambda max. The given lambda is then interpreted as lambda min.

- **nlambdas**: (Applicable only if **lambda\_search** is enabled) Specify the number of lambdas to use in the search. The default is 100. 

- **standardize**: To standardize the numeric columns to have a mean of zero and unit variance, check this checkbox. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is selected by default.

- **remove_collinear_columns**: Automatically remove collinear columns during model-building. Collinear columns will be dropped from the model and will have 0 coefficient in the returned model. Can only be set if there is no regularization (lambda=0)

- **compute_p_values**: Request computation of p-values. Only applicable with no penalty (lambda = 0 and no beta constraints). Setting remove_collinear_columns is recommended. H2O will return an error if p-values are requested and there are collinear columns and remove_collinear_columns flag is not set.

- **non-negative**: To force coefficients to have non-negative values, check this checkbox. 

- **beta_constraints**: To use beta constraints, select a dataset from the drop-down menu. The selected frame is used to constraint the coefficient vector to provide upper and lower bounds. The dataset must contain a names column with valid coefficient names. 

- **fold_assignment**: (Applicable only if a value for **nfolds** is specified and **fold_column** is not selected) Select the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, or [Modulo](https://en.wikipedia.org/wiki/Modulo_operation). 

- **fold_column**: Select the column that contains the cross-validation fold index assignment per observation. 

- **score\_each\_iteration**: (Optional) Check this checkbox to score during each iteration of the model training. 

- **offset_column**: Select a column to use as the offset; the value cannot be the same as the value for the `weights_column`. 
	>*Note*: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following [link](http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf). 

- **weights_column**: Select a column to use for the observation weights, which are used for bias correction. The specified `weights_column` must be included in the specified `training_frame`. *Python only*: To use a weights column when passing an H2OFrame to `x` instead of a list of column names, the specified `training_frame` must contain the specified `weights_column`. 
	>*Note*: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.  

- **max_iterations**: Specify the number of training iterations.   

- **link**: Select a link function (Identity, Family_Default, Logit, Log, Inverse, or Tweedie).

	> - If the family is **Gaussian**, **Identity**, **Log**, and **Inverse** are supported. 
	>  - If the family is **Binomial**, **Logit** is supported. 
	>  - If the family is **Poisson**, **Log** and **Identity** are supported. 
	>  - If the family is **Gamma**, **Inverse**, **Log**, and **Identity** are supported. 
	>  - If the family is **Tweedie**, only **Tweedie** is supported. 	 	 

- **max\_confusion\_matrix\_size**: Specify the maximum size (number of classes) for the confusion matrices printed in the logs. 

- **max\_hit\_ratio\_k**: (Applicable for classification only) Specify the maximum number (top K) of predictions to use for hit ratio computation. Applicable to multi-class only. To disable, enter `0`. 

- **keep\_cross\_validation\_predictions**: To keep the cross-validation predictions, check this checkbox. 

- **intercept**: To include a constant term in the model, check this checkbox. This option is selected by default. 

- **objective_epsilon**: Specify a threshold for convergence. If the objective value is less than this threshold, the model is converged. 

- **beta_epsilon**: Specify the beta epsilon value. If the L1 normalization of the current beta change is below this threshold, consider using convergence. 

- **gradient_epsilon**: (For L-BFGS only) Specify a threshold for convergence. If the objective value (using the L-infinity norm) is less than this threshold, the model is converged. 

- **prior**: Specify prior probability for p(y==1). Use this parameter for logistic regression if the data has been sampled and the mean of response does not reflect reality. Note: this is simple method affecting only the intercept, you may want to use weights and offset for better fit.

- **lambda\_min\_ratio**: Specify the minimum lambda to use for lambda search (specified as a ratio of **lambda\_max**). 

- **max\_active\_predictors**: Specify the maximum number of active predictors during computation. This value is used as a stopping criterium to prevent expensive model building with many predictors. 

- **missing\_values\_handling**: Specify how to handle missing values (Skip or MeanImputation). This defaults to MeanImputation. 

- **seed**: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

### Interpreting a GLM Model

By default, the following output displays:

- A graph of the normalized coefficient magnitudes
- Output (model category, model summary, scoring history, training metrics, validation metrics, best lambda, threshold, residual deviance, null deviance, residual degrees of freedom, null degrees of freedom, AIC, AUC, binomial, rank)
- Coefficients
- Coefficient magnitudes

### Handling of Categorical Variables
GLM auto-expands categorical variables into one-hot encoded binary variables (i.e. if variable has levels "cat","dog", "mouse", cat is encoded as 1,0,0, mouse is 0,1,0 and dog is 0,0,1).
It is generally more efficient to let GLM perform auto-expansion instead of expanding data manually and it also adds the benefit of correct handling of different categorical mappings between different datasets as welll as handling of unseen categorical levels.
Unlike binary numeric columns, auto-expanded variables are not standardized.

It is common to skip one of the levels during the one-hot encoding to prevent linear dependency between the variable and the intercept.
H3O follows the convention of skipping the first level.
This behavior can be controlled by setting use_all_factor_levels_flag (no level is going to be skipped if the flag is true).
The default depends on regularization parameter - it is set to false if no regularization and to true otherwise.
The reference level which is skipped is always the first level, you can change which level is the reference level by calling h2o.relevel function prior to building the model.


### Lambda Search and Full Regularization Path
If lambda_search option is set, GLM will compute models for full regularization path similar to glmnet (see glmnet paper).
Regularziation path starts at lambda max (highest lambda values which makes sense - i.e. lowest value driving all coefficients to zero) and goes down to lambda min on log scale, decreasing regularization strength at each step.
The returned model will have coefficients corresponding to the "optimal" lambda value as decided during training.

It can sometimes be useful to see the coefficients for all lambda values. Or to override default lambda selection.
Full regularization path can be extracted from both R and python clients (currently not from Flow). It returns coefficients (and standardized coefficients)
for all computed lambda values and also explained deviances on both train and validation.
Subsequently, makeGLMModel call can be used to create h2o glm model with selected coefficients.

To extract the regularization path from R or python:
 - R: call h2o.getGLMFullRegularizationPath, takes the model as an argument
 - pyton: H2OGeneralizedLinearEstimator.getGLMRegularizationPath (static method), takes the model as an rgument

### Modifying or Creating Custom GLM Model
In R and python, makeGLMModel call can be used to create h2o model from given coefficients.
It needs a source glm model trained on the same dataset to extract dataset information.
To make custom GLM model from R or python:
 - R: call h2o.makeGLMModel, takes a model and a vector of coefficients and (optional) decision threshold as parameters.
 - pyton: H2OGeneralizedLinearEstimator.makeGLMModel (static method), takes a model, dictionary containing coefficients and (optional) decision threshold as parameters.


### FAQ

- **How does the algorithm handle missing values during training?**

  Depending on the selected missing value handling policy, they are either imputed mean or the whole row is skipped.  
  The default behavior is mean imputation. Note that categorical variables are imputed by adding an extra "missing" level.   
  Optionally, glm can skip all rows with any missing values. 

- **How does the algorithm handle missing values during testing?**
  Same as during training. If the missing value handling is set to skip and we are generating predictions, skipped rows will have Na (missing) prediction.

- **What happens if the response has missing values?**

  The rows with missing response are ignored during model training and validation.

- **What happens during prediction if the new sample has categorical levels not seen in training?**
  The value will be filled with either special missing level (if trained with missing values and missing_value_handling was set to MeanImputation) or 0.

- **Does it matter if the data is sorted?** 

  No.

- **Should data be shuffled before training?**

  No.

- **How does the algorithm handle highly imbalanced data in a response column?**

  GLM does not require special handling for imbalanced data.

- **What if there are a large number of columns?**

  IRLS will get quadratically slower with the number of columns. Try L-BFGS for datasets with more than 5-10 thousand columns.

- **What if there are a large number of categorical factor levels?**

  GLM internally one-hot encodes the categorical factor levels; the same limitations as with a high column count will apply.

- **When building the model, does GLM use all features or a selection of the best features?**

  Typically, GLM picks the best predictors, especially if lasso is used (`alpha = 1`). By default, the GLM model includes an L1 penalty and will pick only the most predictive predictors. 

- **When running GLM, is it better to create a cluster that uses many smaller nodes or fewer larger nodes?** 

A rough heuristic would be: 

  nodes ~=M*N^2/(p*1e8)

where M is the number of observations, N is the number of columns (categorical columns count as a single column in this case), and p is the number of CPU cores per node. 

For example, a dataset with 250 columns and 1M rows would optimally use about 20 nodes with 32 cores each (following the formula 250^2*1000000/(32*1e8)  = 19.5 ~= 20). 

- **How is variable importance calculated for GLM?**

For GLM, the variable importance represents the coefficient magnitudes. 



### GLM Algorithm

Following the definitive text by P. McCullagh and J.A. Nelder (1989) on the generalization of linear models to non-linear distributions of the response variable Y, H2O fits GLM models based on the maximum likelihood estimation via iteratively reweighed least squares. 

Let \(y_{1},…,y_{n}\) be n observations of the independent, random response variable \(Y_{i}\).

Assume that the observations are distributed according to a function from the exponential family and have a probability density function of the form:

\(f(y_{i})=exp[\frac{y_{i}\theta_{i} - b(\theta_{i})}{a_{i}(\phi)} + c(y_{i}; \phi)]\)
where \(\theta\) and \(\phi\) are location and scale parameters,
and \(\: a_{i}(\phi), \:b_{i}(\theta_{i}),\: c_{i}(y_{i}; \phi)\) are known functions.

\(a_{i}\) is of the form \(\:a_{i}=\frac{\phi}{p_{i}}; p_{i}\) is a known prior weight.

When \(Y\) has a pdf from the exponential family: 

\(E(Y_{i})=\mu_{i}=b^{\prime}\)
\(var(Y_{i})=\sigma_{i}^2=b^{\prime\prime}(\theta_{i})a_{i}(\phi)\)

Let \(g(\mu_{i})=\eta_{i}\) be a monotonic, differentiable transformation of the expected value of \(y_{i}\). The function \(\eta_{i}\) is the link function and follows a linear model.

\(g(\mu_{i})=\eta_{i}=\mathbf{x_{i}^{\prime}}\beta\)

When inverted: 
\(\mu=g^{-1}(\mathbf{x_{i}^{\prime}}\beta)\)

**Maximum Likelihood Estimation**

For an initial rough estimate of the parameters \(\hat{\beta}\), use the estimate to generate fitted values: 
\(\mu_{i}=g^{-1}(\hat{\eta_{i}})\)

Let \(z\) be a working dependent variable such that 
\(z_{i}=\hat{\eta_{i}}+(y_{i}-\hat{\mu_{i}})\frac{d\eta_{i}}{d\mu_{i}}\),

where \(\frac{d\eta_{i}}{d\mu_{i}}\) is the derivative of the link function evaluated at the trial estimate. 

Calculate the iterative weights:
\(w_{i}=\frac{p_{i}}{[b^{\prime\prime}(\theta_{i})\frac{d\eta_{i}}{d\mu_{i}}^{2}]}\)

Where \(b^{\prime\prime}\) is the second derivative of \(b(\theta_{i})\) evaluated at the trial estimate. 


Assume \(a_{i}(\phi)\) is of the form \(\frac{\phi}{p_{i}}\). The weight \(w_{i}\) is inversely proportional to the variance of the working dependent variable \(z_{i}\) for current parameter estimates and proportionality factor \(\phi\).

Regress \(z_{i}\) on the predictors \(x_{i}\) using the weights \(w_{i}\) to obtain new estimates of \(\beta\). 
\(\hat{\beta}=(\mathbf{X}^{\prime}\mathbf{W}\mathbf{X})^{-1}\mathbf{X}^{\prime}\mathbf{W}\mathbf{z}\) 

Where \(\mathbf{X}\) is the model matrix, \(\mathbf{W}\) is a diagonal matrix of \(w_{i}\), and \(\mathbf{z}\) is a vector of the working response variable \(z_{i}\).

This process is repeated until the estimates \(\hat{\beta}\) change by less than the specified amount. 

**Cost of computation**


H2O can process large data sets because it relies on parallel processes. Large data sets are divided into smaller data sets and processed simultaneously and the results are communicated between computers as needed throughout the process. 

In GLM, data are split by rows but not by columns, because the predicted Y values depend on information in each of the predictor variable vectors. If O is a complexity function, N is the number of observations (or rows), and P is the number of predictors (or columns) then 


   &nbsp;&nbsp;&nbsp;&nbsp;\(Runtime\propto p^3+\frac{(N*p^2)}{CPUs}\)

Distribution reduces the time it takes an algorithm to process because it decreases N.
 

Relative to P, the larger that (N/CPUs) becomes, the more trivial p becomes to the overall computational cost. However, when p is greater than (N/CPUs), O is dominated by p.



   &nbsp;&nbsp;&nbsp;&nbsp;\(Complexity = O(p^3 + N*p^2)\) 

For more information about how GLM works, refer to the [Generalized Linear Modeling booklet](http://h2o.ai/resources). 


### References

Breslow, N E. “Generalized Linear Models: Checking Assumptions and Strengthening Conclusions.” Statistica Applicata 8 (1996): 23-41.

[Frome, E L. “The Analysis of Rates Using Poisson Regression Models.” Biometrics (1983): 665-674.](http://www.csm.ornl.gov/~frome/BE/FP/FromeBiometrics83.pdf)

[Goldberger, Arthur S. “Best Linear Unbiased Prediction in the Generalized Linear Regression Model.” Journal of the American Statistical Association 57.298 (1962): 369-375.](http://people.umass.edu/~bioep740/yr2009/topics/goldberger-jasa1962-369.pdf)

[Guisan, Antoine, Thomas C Edwards Jr, and Trevor Hastie. “Generalized Linear and Generalized Additive Models in Studies of Species Distributions: Setting the Scene.” Ecological modeling 157.2 (2002): 89-100.](http://www.stanford.edu/~hastie/Papers/GuisanEtAl_EcolModel-2003.pdf)

[Nelder, John A, and Robert WM Wedderburn. “Generalized Linear Models.” Journal of the Royal Statistical Society. Series A (General) (1972): 370-384.](http://biecek.pl/MIMUW/uploads/Nelder_GLM.pdf)

[Niu, Feng, et al. “Hogwild!: A lock-free approach to parallelizing stochastic gradient descent.” Advances in Neural Information Processing Systems 24 (2011): 693-701.*implemented algorithm on p.5](http://www.eecs.berkeley.edu/~brecht/papers/hogwildTR.pdf)

[Pearce, Jennie, and Simon Ferrier. “Evaluating the Predictive Performance of Habitat Models Developed Using Logistic Regression.” Ecological modeling 133.3 (2000): 225-245.](http://www.whoi.edu/cms/files/Ecological_Modelling_2000_Pearce_53557.pdf)

[Press, S James, and Sandra Wilson. “Choosing Between Logistic Regression and Discriminant Analysis.” Journal of the American Statistical Association 73.364 (April, 2012): 699–705.](http://www.statpt.com/logistic/press_1978.pdf)

Snee, Ronald D. “Validation of Regression Models: Methods and Examples.” Technometrics 19.4 (1977): 415-428.

---


<a name="DRF"></a>
## DRF

### Introduction

Distributed Random Forest (DRF) is a powerful classification and regression tool. When given a set of data, DRF generates a forest of classification (or regression) trees, rather than a single classification (or regression) tree. Each of these trees is a weak learner built on a subset of rows and columns. More trees will reduce the variance. Both classification and regression take the average prediction over all of their trees to make a final prediction, whether predicting for a class or numeric value (note: for a categorical response column, DRF maps factors  (e.g. 'dog', 'cat', 'mouse) in lexicographic order to a name lookup array with integer indices (e.g. 'cat ->0, 'dog' -> 1, 'mouse' ->2).

The current version of DRF is fundamentally the same as in previous versions of H2O (same algorithmic steps, same histogramming techniques), with the exception of the following changes: 

- Improved ability to train on categorical variables (using the `nbins_cats` parameter)
- Minor changes in histogramming logic for some corner cases
- By default, DRF builds half as many trees for binomial problems, similar to GBM: it uses a single tree to estimate class 0 (probability "p0"), and then computes the probability of class 0 as ``1.0 - p0``. For multiclass problems, a tree is used to estimate the probability of each class separately. 

There was some code cleanup and refactoring to support the following features:

- Per-row observation weights
- Per-row offsets
- N-fold cross-validation

DRF no longer has a special-cased histogram for classification or regression (class DBinomHistogram has been superseded by DRealHistogram) since it was not applicable to cases with observation weights or for cross-validation. 


### Defining a DRF Model

- **model_id**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates a destination key. 

- **training_frame**: (Required) Select the dataset used to build the model. 
**NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **nfolds**: Specify the number of folds for cross-validation. 

- **response_column**: (Required) Select the column to use as the independent variable. The data can be numeric or categorical. 

- **Ignored_columns**: (Optional) Click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons. 

- **ignore\_const\_cols**: Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default. 

- **ntrees**: Specify the number of trees.  

- **max\_depth**: Specify the maximum tree depth.

- **min\_rows**: Specify the minimum number of observations for a leaf (`nodesize` in R).  

- **nbins**: (Numerical/real/int only) Specify the number of bins for the histogram to build, then split at the best point.  

- **nbins_cats**: (Categorical/enums only) Specify the maximum number of bins for the histogram to build, then split at the best point. Higher values can lead to more overfitting.  The levels are ordered alphabetically; if there are more levels than bins, adjacent levels share bins. This value has a more significant impact on model fitness than **nbins**. Larger values may increase runtime, especially for deep trees and large clusters, so tuning may be required to find the optimal value for your configuration.

- **seed**: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

- **mtries**: Specify the columns to randomly select at each level. If the default value of `-1` is used, the number of variables is the square root of the number of columns for classification and p/3 for regression (where p is the number of predictors). The range is -1 to >=1. 

- **sample_rate**: Specify the row sampling rate (x-axis). The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" ([Friedman, 1999](https://statweb.stanford.edu/~jhf/ftp/stobst.pdf)). If this option is specified along with **sample\_rate_per\_class**, then only the first option that DRF encounters will be used.

- **col\_sample_rate**: Specify the column sampling rate (y-axis). The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" ([Friedman, 1999](https://statweb.stanford.edu/~jhf/ftp/stobst.pdf)). 

- **score\_each\_iteration**: (Optional) Check this checkbox to score during each iteration of the model training. 

- **score\_tree\_interval**: Score the model after every so many trees. Disabled if set to 0.

- **fold_assignment**: (Applicable only if a value for **nfolds** is specified and **fold_column** is not selected) Select the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, or [Modulo](https://en.wikipedia.org/wiki/Modulo_operation). 

- **fold_column**: Select the column that contains the cross-validation fold index assignment per observation. 

- **offset_column**:  Select a column to use as the offset. 
	>*Note*: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following [link](http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf). 

- **weights_column**: Select a column to use for the observation weights, which are used for bias correction. The specified `weights_column` must be included in the specified `training_frame`. *Python only*: To use a weights column when passing an H2OFrame to `x` instead of a list of column names, the specified `training_frame` must contain the specified `weights_column`. 
	>*Note*: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.  

- **balance_classes**: Oversample the minority classes to balance the class distribution. This option is not selected by default and can increase the data frame size. This option is only applicable for classification. 

- **max\_confusion\_matrix\_size**: Specify the maximum size (in number of classes) for confusion matrices to be printed in the Logs. 

- **max\_hit\_ratio\_k**: Specify the maximum number (top K) of predictions to use for hit ratio computation. Applicable to multi-class only. To disable, enter 0. 

- **r2_stopping**: Specify a threshold for the coefficient of determination (\(r^2\)) metric value. When this threshold is met or exceeded, H2O stops making trees. 

- **stopping\_rounds**: Stops training when the option selected for **stopping\_metric** doesn't improve for the specified number of training rounds, based on a simple moving  average. To disable this feature, specify `0`. The metric is computed on the validation data (if provided); otherwise, training data is used. When used with **overwrite\_with\_best\_model**, the final model is the best model generated for the given **stopping\_metric** option.   
	>**Note**: If cross-validation is enabled: 
	1. All cross-validation models stop training when the validation metric doesn't improve. 
    2. The main model runs for the mean number of epochs. 
    3. N+1 models do *not* use **overwrite\_with\_best\_model**
    4. N+1 models may be off by the number specified for **stopping\_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs). 

- **stopping\_metric**: Select the metric to use for early stopping. The available options are: 
	
    - **AUTO**: Logloss for classification; deviance for regression
    - **deviance**
    - **logloss**
    - **MSE**
    - **AUC**
    - **r2**
    - **misclassification**
    - **mean\_per\_class\_error**

- **stopping\_tolerance**: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. 

- **max\_runtime\_secs**: Maximum allowed runtime in seconds for model training. Use 0 to disable.

- **build\_tree\_one\_node**: To run on a single node, check this checkbox. This is suitable for small datasets as there is no network overhead but fewer CPUs are used. 

- **sample\_rate\_per\_class**: When building models from imbalanced datasets, this option specifies that each tree in the ensemble should sample from the full training dataset using a per-class-specific sampling rate rather than a global sample factor (as with `sample_rate`). The range for this option is 0.0 to 1.0. If this option is specified along with **sample_rate**, then only the first option that DRF encounters will be used.

- **binomial\_double\_trees**: (Binary classification only) Build twice as many trees (one per class). Enabling this option can lead to higher accuracy, while disabling can result in faster model building. This option is disabled by default. 

- **checkpoint**: Enter a model key associated with a previously-trained model. Use this option to build a new model as a continuation of a previously-generated model.

- **col\_sample_rate\_change\_per\_level**: This option specifies to change the column sampling rate as a function of the depth in the tree. For example:
	>level 1: **col\_sample_rate**
	
	>level 2: **col\_sample_rate** * **factor**
	
	>level 3: **col\_sample_rate** * **factor^2**
	
	>level 4: **col\_sample_rate** * **factor^3**
	
	>etc. 
	
- **col\_sample\_rate\_per\_tree**: Specifies the column sample rate per tree. This can be a value from 0.0 to 1.0. 
	
- **min\_split_improvement**: The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range.

- **histogram_type**: By default (AUTO) DRF bins from min...max in steps of (max-min)/N. Random split points or quantile-based split points can be selected as well. RoundRobin can be specified to cycle through all histogram types (one per tree). Use this option to specify the type of histogram to use for finding optimal split points:

  - AUTO
  - UniformAdaptive
  - Random
  - QuantilesGlobal
  - RoundRobin

  >**Note**: H2O supports extremely randomized trees via ``histogram_type="Random"``. In extremely randomized trees (Extra-Trees), randomness goes one step further in the way splits are computed. As in Random Forests, a random subset of candidate features is used, but instead of looking for the best split, thresholds (for the split) are drawn at random for each candidate feature, and the best of these randomly-generated thresholds is picked as the splitting rule. This usually allows to reduce the variance of the model a bit more, at the expense of a slightly greater increase in bias.

- **keep\_cross\_validation\_predictions**: To keep the cross-validation predictions, check this checkbox. 

- **class\_sampling\_factors**: Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance.  

- **max\_after\_balance\_size**: Specify the maximum relative size of the training data after balancing class counts (**balance\_classes** must be enabled). The value can be less than 1.0. 

- **nbins\_top\_level**: (For numerical/real/int columns only) Specify the minimum number of bins at the root level to use to build the histogram. This number will then be decreased by a factor of two per level.  


### Interpreting a DRF Model

By default, the following output displays:

- Model parameters (hidden)  
- A graph of the scoring history (number of trees vs. training MSE)
- A graph of the ROC curve (TPR vs. FPR)
- A graph of the variable importances
- Output (model category, validation metrics, initf)
- Model summary (number of trees, min. depth, max. depth, mean depth, min. leaves, max. leaves, mean leaves)
- Scoring history in tabular format
- Training metrics (model name, checksum name, frame name, frame checksum name, description, model category, duration in ms, scoring time, predictions, MSE, R2, logloss, AUC, GINI)
- Training metrics for thresholds (thresholds, F1, F2, F0Points, Accuracy, Precision, Recall, Specificity, Absolute MCC, min. per-class accuracy, TNS, FNS, FPS, TPS, IDX)
- Maximum metrics (metric, threshold, value, IDX)
- Variable importances in tabular format


### Leaf Node Assignment
Trees cluster observations into leaf nodes, and this information can be useful for feature engineering or model interpretability. Use **h2o.predict\_leaf\_node\_assignment\(model, frame\)** to get an H2OFrame with the leaf node assignments, or click the checkbox when making predictions from Flow. Those leaf nodes represent decision rules that can be fed to other models (i.e., GLM with lambda search and strong rules) to obtain a limited set of the most important rules.

### FAQ

- **How does the algorithm handle missing values during training?**

  Missing values are interpreted as containing information (i.e., missing for a reason), rather than missing at random. During tree building, split decisions for every node are found by minimizing the loss function and treating missing values as a separate category that can go either left or right.

- **How does the algorithm handle missing values during testing?**

  During scoring, missing values follow the optimal path that was determined for them during training (minimized loss function).

- **What happens if the response has missing values?**
 
  No errors will occur, but nothing will be learned from rows containing missing the response.

- **Does it matter if the data is sorted?** 

  No.

- **Should data be shuffled before training?**
  
  No.

- **How does the algorithm handle highly imbalanced data in a response column?**

 Specify `balance_classes`, `class_sampling_factors` and `max_after_balance_size` to control over/under-sampling.

- **What if there are a large number of columns?**

  DRFs are best for datasets with fewer than a few thousand columns.

- **What if there are a large number of categorical factor levels?**

  Large numbers of categoricals are handled very efficiently - there is never any one-hot encoding.

- **How is variable importance calculated for DRF?**

Variable importance is determined by calculating the relative influence of each variable: whether that variable was selected during splitting in the tree building process and how much the squared error (over all trees) improved as a result. 

- **How is column sampling implemented for DRF?**

For an example model using: 

- 100 columns
- `col_sample_rate_per_tree` is 0.602
- `mtries` is -1 or 7 (refers to the number of active predictor columns for the dataset)

For each tree, the floor is used to determine the number - for this example, (0.602*100)=60 out of the 100 - of columns that are randomly picked. For classification cases where `mtries=-1`, the square root - for this example, (100)=10 columns - are then randomly chosen for each split decision (out of the total 60). 

For regression, the floor - in this example, (100/3)=33 columns - is used for each split by default. If `mtries=7`, then 7 columns are picked for each split decision (out of the 60). 

`mtries` is configured independently of `col_sample_rate_per_tree`, but it can be limited by it. For example, if `col_sample_rate_per_tree=0.01`, then there's only one column left for each split, regardless of how large the value for `mtries` is.


### DRF Algorithm 


<iframe src="//www.slideshare.net/slideshow/embed_code/key/tASzUyJ19dtJsQ" width="425" height="355" frameborder="0" marginwidth="0" marginheight="0" scrolling="no" style="border:1px solid #CCC; border-width:1px; margin-bottom:5px; max-width: 100%;" allowfullscreen> </iframe> <div style="margin-bottom:5px"> <strong> <a href="//www.slideshare.net/0xdata/rf-brighttalk" title="Building Random Forest at Scale" target="_blank">Building Random Forest at Scale</a> </strong> from <strong><a href="//www.slideshare.net/0xdata" target="_blank">Sri Ambati</a></strong> </div>

### References

<a href="http://link.springer.com/article/10.1007%2Fs10994-006-6226-1" target="_blank">P. Geurts, D. Ernst., and L. Wehenkel, “Extremely randomized trees”, Machine Learning, 63(1), 3-42, 2006.</a>

---

<a name="NB"></a>
## Naïve Bayes

### Introduction 

Naïve Bayes (NB) is a classification algorithm that relies on strong assumptions of the independence of covariates in applying Bayes Theorem. NB models are commonly used as an alternative to decision trees for classification problems.

### Defining a Naïve Bayes Model

- **model_id**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates a destination key. 

- **training_frame**: (Required) Select the dataset used to build the model. 
**NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **response_column**: (Required) Select the column to use as the independent variable. The data must be categorical and must contain at least two unique categorical levels. 

- **ignored_columns**: (Optional) Click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons. 

- **ignore\_const\_cols**: Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default. 

- **laplace**: Specify the Laplace smoothing parameter. The value must be an integer >= 0. 

- **min\_sdev**: Specify the minimum standard deviation to use for observations without enough data. The value must be at least 1e-10.   

- **eps\_sdev**: Specify the threshold for standard deviation. The value must be positive. If this threshold is not met, the **min\_sdev** value is used.  

- **min\_prob**: Specify the minimum probability to use for observations without enough data.   

- **eps\_prob**: Specify the threshold for standard deviation. If this threshold is not met, the **min\_sdev** value is used.  

- **compute_metrics**: To compute metrics on training data, check this checkbox. The Naïve Bayes classifier assumes independence between predictor variables conditional on the response, and a Gaussian distribution of numeric predictors with mean and standard deviation computed from the training dataset. When building a Naïve Bayes classifier, every row in the training dataset that contains at least one NA will be skipped completely. If the test dataset has missing values, then those predictors are omitted in the probability calculation during prediction.

- **score\_each\_iteration**: (Optional) Check this checkbox to score during each iteration of the model training. 

- **max\_confusion\_matrix\_size**: Specify the maximum size (in number of classes) for confusion matrices to be printed in the Logs. 

- **max\_hit\_ratio\_k**: Specify the maximum number (top K) of predictions to use for hit ratio computation. Applicable to multi-class only. To disable, enter 0. 

- **max\_runtime\_secs**: Maximum allowed runtime in seconds for model training. Use 0 to disable.



### Interpreting a Naïve Bayes Model

The output from Naïve Bayes is a list of tables containing the a-priori and conditional probabilities of each class of the response. The a-priori probability is the estimated probability of a particular class before observing any of the predictors. Each conditional probability table corresponds to a predictor column. The row headers are the classes of the response and the column headers are the classes of the predictor. Thus, in the table below, the probability of survival (y) given a person is male (x) is 0.91543624.

```
        		Sex
Survived       Male     Female
     No  0.91543624 0.08456376
     Yes 0.51617440 0.48382560
```


When the predictor is numeric, Naïve Bayes assumes it is sampled from a Gaussian distribution given the class of the response. The first column contains the mean and the second column contains the standard deviation of the distribution.

By default, the following output displays:

- Output (model category, model summary, scoring history, training metrics, validation metrics)
- Y-Levels (levels of the response column)
- P-conditionals 

### FAQ

- **How does the algorithm handle missing values during training?**
  
  All rows with one or more missing values (either in the predictors or the response) will be skipped during model building. 

- **How does the algorithm handle missing values during testing?**
  
  If a predictor is missing, it will be skipped when taking the product of conditional probabilities in calculating the joint probability conditional on the response.

- **What happens if the response domain is different in the training and test datasets?**
  
  The response column in the test dataset is not used during scoring, so any response categories absent in the training data will not be predicted.

- **What happens during prediction if the new sample has categorical levels not seen in training?**
  
  The conditional probability of that predictor level will be set according to the Laplace smoothing factor. If Laplace smoothing is disabled (set to zero), the joint probability will be zero. See pgs. 13-14 of Andrew Ng’s "Generative learning algorithms" in the References section for mathematical details.

- **Does it matter if the data is sorted?**

  No. 

- **Should data be shuffled before training?**

  This does not affect model building. 

- **How does the algorithm handle highly imbalanced data in a response column?**

  Unbalanced data will not affect the model. However, if one response category has very few observations compared to the total, the conditional probability may be very low. A cutoff (`eps_prob`) and minimum value (`min_prob`) are available for the user to set a floor on the calculated probability.


- **What if there are a large number of columns?**

   More memory will be allocated on each node to store the joint frequency counts and sums.

- **What if there are a large number of categorical factor levels?**

  More memory will be allocated on each node to store the joint frequency count of each categorical predictor level with the response’s level.

- **When running PCA, is it better to create a cluster that uses many smaller nodes or fewer larger nodes?** 

For Naïve Bayes, we recommend using many smaller nodes because the distributed task doesn't require intensive computation. 


### Naïve Bayes Algorithm 

The algorithm is presented for the simplified binomial case without loss of generality.

Under the Naive Bayes assumption of independence, given a training set
for a set of discrete valued features X 
\({(X^{(i)},\ y^{(i)};\ i=1,...m)}\)

The joint likelihood of the data can be expressed as: 

\(\mathcal{L} \: (\phi(y),\: \phi_{i|y=1},\:\phi_{i|y=0})=\Pi_{i=1}^{m} p(X^{(i)},\: y^{(i)})\)

The model can be parameterized by:

\(\phi_{i|y=0}=\ p(x_{i}=1|\ y=0);\: \phi_{i|y=1}=\ p(x_{i}=1|y=1);\: \phi(y)\)

Where \(\phi_{i|y=0}=\ p(x_{i}=1|\ y=0)\) can be thought of as the fraction of the observed instances where feature \(x_{i}\) is observed, and the outcome is \(y=0, \phi_{i|y=1}=p(x_{i}=1|\ y=1)\) is the fraction of the observed instances where feature \(x_{i}\) is observed, and the outcome is \(y=1\), and so on.

The objective of the algorithm is to maximize with respect to
\(\phi_{i|y=0}, \ \phi_{i|y=1},\ and \ \phi(y)\)

Where the maximum likelihood estimates are: 

\(\phi_{j|y=1}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 1)}{\Sigma_{i=1}^{m}(y^{(i)}=1}\)

\(\phi_{j|y=0}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 0)}{\Sigma_{i=1}^{m}(y^{(i)}=0}\)

\(\phi(y)= \frac{(y^{i} = 1)}{m}\)


Once all parameters \(\phi_{j|y}\) are fitted, the model can be used to predict new examples with features \(X_{(i^*)}\). 

This is carried out by calculating: 

\(p(y=1|x)=\frac{\Pi p(x_i|y=1) p(y=1)}{\Pi p(x_i|y=1)p(y=1) \: +\: \Pi p(x_i|y=0)p(y=0)}\)

\(p(y=0|x)=\frac{\Pi p(x_i|y=0) p(y=0)}{\Pi p(x_i|y=1)p(y=1) \: +\: \Pi p(x_i|y=0)p(y=0)}\)

and predicting the class with the highest probability. 


It is possible that prediction sets contain features not originally seen in the training set. If this occurs, the maximum likelihood estimates for these features predict a probability of 0 for all cases of y. 

Laplace smoothing allows a model to predict on out of training data features by adjusting the maximum likelihood estimates to be: 


\(\phi_{j|y=1}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 1) \: + \: 1}{\Sigma_{i=1}^{m}(y^{(i)}=1 \: + \: 2}\)

\(\phi_{j|y=0}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 0) \: + \: 1}{\Sigma_{i=1}^{m}(y^{(i)}=0 \: + \: 2}\)

Note that in the general case where y takes on k values, there are k+1 modified parameter estimates, and they are added in when the denominator is k (rather than two, as shown in the two-level classifier shown here.)

Laplace smoothing should be used with care; it is generally intended to allow for predictions in rare events. As prediction data becomes increasingly distinct from training data, train new models when possible to account for a broader set of possible X values. 


### References


[Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The Elements of Statistical Learning. Vol.1. N.p., Springer New York, 2001.](http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf) 

[Ng, Andrew. "Generative Learning algorithms." (2008).](http://cs229.stanford.edu/notes/cs229-notes2.pdf)

---

<a name="PCA"></a>
## PCA

### Introduction

Principal Components Analysis (PCA) is closely related to Principal Components Regression. The algorithm is carried out on a set of possibly collinear features and performs a transformation to produce a new set of uncorrelated features.

PCA is commonly used to model without regularization or perform dimensionality reduction. It can also be useful to carry out as a preprocessing step before distance-based algorithms such as K-Means since PCA guarantees that all dimensions of a manifold are orthogonal.

### Defining a PCA Model

- **model_id**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates a destination key. 

- **training_frame**: (Required) Select the dataset used to build the model. 
**NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **ignored_columns**: (Optional) Click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

- **ignore\_const\_cols**: Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default.   

- **transform**: Select the transformation method for the training data: None, Standardize, Normalize, Demean, or Descale. The default is None. 

- **pca_method**: Select the algorithm to use for computing the principal components: 
	- *GramSVD*: Uses a distributed computation of the Gram matrix, followed by a local SVD using the JAMA package
	- *Power*: Computes the SVD using the power iteration method (experimental)
	- *Randomized*: Uses randomized subspace iteration method 
	- *GLRM*: Fits a generalized low-rank model with L2 loss function and no regularization and solves for the SVD using local matrix algebra (experimental)

- **k***: Specify the rank of matrix approximation. The default is 1.  

- **max_iterations**: Specify the number of training iterations. The value must be between 1 and 1e6 and the default is 1000.

- **seed**: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

- **use\_all\_factor\_levels**: Check this checkbox to use all factor levels in the possible set of predictors; if you enable this option, sufficient regularization is required. By default, the first factor level is skipped. For PCA models, this option ignores the first factor level of each categorical column when expanding into indicator columns. 

- **compute\_metrics**: Enable metrics computations on the training data. 

- **score\_each\_iteration**: (Optional) Check this checkbox to score during each iteration of the model training. 

- **max\_runtime\_secs**: Maximum allowed runtime in seconds for model training. Use 0 to disable.




### Interpreting a PCA Model

PCA output returns a table displaying the number of components specified by the value for `k`.

Scree and cumulative variance plots for the components are returned as well. Users can access them by clicking on the black button labeled "Scree and Variance Plots" at the top left of the results page. A scree plot shows the variance of each component, while the cumulative variance plot shows the total variance accounted for by the set of components.

The output for PCA includes the following: 

- Model parameters (hidden)
- Output (model category, model summary, scoring history, training metrics, validation metrics, iterations)
- Archetypes
- Standard deviation
- Rotation 
- Importance of components (standard deviation, proportion of variance, cumulative proportion) 



### FAQ

- **How does the algorithm handle missing values during scoring?**

For the GramSVD and Power methods, all rows containing missing values are ignored during training. For the GLRM method, missing values are excluded from the sum over the loss function in the objective. For more information, refer to section 4 Generalized Loss Functions, equation (13), in ["Generalized Low Rank Models"](https://web.stanford.edu/~boyd/papers/pdf/glrm.pdf) by Boyd et al.

  
- **How does the algorithm handle missing values during testing?**

  During scoring, the test data is right-multiplied by the eigenvector matrix produced by PCA. Missing categorical values are skipped in the row product-sum. Missing numeric values propagate an entire row of NAs in the resulting projection matrix.


- **What happens during prediction if the new sample has categorical levels not seen in training?**

  Categorical levels in the test data not present in the training data are skipped in the row product-sum.


- **Does it matter if the data is sorted?**
  
  No, sorting data does not affect the model. 
  
- **Should data be shuffled before training?**

  No, shuffling data does not affect the model. 


- **What if there are a large number of columns?**

  Calculating the SVD will be slower, since computations on the Gram matrix are handled locally. 

- **What if there are a large number of categorical factor levels?**

  Each factor level (with the exception of the first, depending on whether **use\_all\_factor\_levels** is enabled) is assigned an indicator column. The indicator column is 1 if the observation corresponds to a particular factor; otherwise, it is 0. As a result, many factor levels result in a large Gram matrix and slower computation of the SVD. 

- **How are categorical columns handled during model building?**
  
  If the GramSVD or Power methods are used, the categorical columns are expanded into 0/1 indicator columns for each factor level. The algorithm is then performed on this expanded training frame. For GLRM, the multidimensional loss function for categorical columns is discussed in Section 6.1 of ["Generalized Low Rank Models"](https://web.stanford.edu/~boyd/papers/pdf/glrm.pdf) by Boyd et al.

- **When running PCA, is it better to create a cluster that uses many smaller nodes or fewer larger nodes?** 

For PCA, this is dependent on the selected `pca_method` parameter: 

- For **GramSVD**, use fewer larger nodes for better performance. Forming the Gram matrix requires few intensive calculations and the main bottleneck is the JAMA library's SVD function, which is not parallelized and runs on a single machine. We do not recommend selecting GramSVD for datasets with many columns and/or categorical levels in one or more columns. 
- For **Randomized**, use many smaller nodes for better performance, since H2O calls a few different distributed tasks in a loop, where each task does fairly simple matrix algebra computations. 
- For **GLRM**, the number of nodes depends on whether the dataset contains many categorical columns with many levels. If this is the case, we recommend using fewer larger nodes, since computing the loss function for categoricals is an intensive task. If the majority of the data is numeric and the categorical columns have only a small number of levels (~10-20), we recommend using many small nodes in the cluster.
- For **Power**, we recommend using fewer larger nodes because the intensive calculations are single-threaded. However, this method is only recommended for obtaining principal component values (such as `k << ncol(train))` because the other methods are far more efficient. 

- **I ran PCA on my dataset - how do I input the new parameters into a model?**

After the PCA model has been built using `h2o.prcomp`, use `h2o.predict` on the original data frame and the PCA model to produce the dimensionality-reduced representation. Use `cbind` to add the predictor column from the original data frame to the data frame produced by the output of `h2o.predict`. At this point, you can build supervised learning models on the new data frame. 


### PCA Algorithm

Let \(X\) be an \(M\times N\) matrix where
 
- Each row corresponds to the set of all measurements on a particular 
   attribute, and 

- Each column corresponds to a set of measurements from a given
   observation or trial

The covariance matrix \(C_{x}\) is

\(C_{x}=\frac{1}{n}XX^{T}\)

where \(n\) is the number of observations. 

\(C_{x}\) is a square, symmetric \(m\times m\) matrix, the diagonal entries of which are the variances of attributes, and the off-diagonal entries are covariances between attributes. 

PCA convergence is based on the method described by Gockenbach: "The rate of convergence of the power method depends on the ratio \(lambda_2|/|\lambda_1\). If this is small...then the power method converges rapidly. If the ratio is close to 1, then convergence is quite slow. The power method will fail if \(lambda_2| = |\lambda_1\)." (567). 


The objective of PCA is to maximize variance while minimizing covariance. 

To accomplish this, for a new matrix \(C_{y}\) with off diagonal entries of 0, and each successive dimension of Y ranked according to variance, PCA finds an orthonormal matrix \(P\) such that \(Y=PX\) constrained by the requirement that \(C_{y}=\frac{1}{n}YY^{T}\) be a diagonal matrix. 

The rows of \(P\) are the principal components of X.

\(C_{y}=\frac{1}{n}YY^{T}\)
\(=\frac{1}{n}(PX)(PX)^{T}\)
\(C_{y}=PC_{x}P^{T}.\)

Because any symmetric matrix is diagonalized by an orthogonal matrix of its eigenvectors, solve matrix \(P\) to be a matrix where each row is an eigenvector of 
\(\frac{1}{n}XX^{T}=C_{x}\)

Then the principal components of \(X\) are the eigenvectors of \(C_{x}\), and the \(i^{th}\) diagonal value of \(C_{y}\) is the variance of \(X\) along \(p_{i}\). 

Eigenvectors of \(C_{x}\) are found by first finding the eigenvalues \(\lambda\) of \(C_{x}\).

For each eigenvalue \(\lambda\) \((C-{x}-\lambda I)x =0\) where \(x\) is the eigenvector associated with \(\lambda\). 

Solve for \(x\) by Gaussian elimination. 

#### Recovering SVD from GLRM

GLRM gives \(x\)  and \(y\), where \(x \in \rm \Bbb I \!\Bbb R ^{n * k}\) and \( y \in \rm \Bbb I \!\Bbb R ^{k*m} \)

&nbsp;&nbsp;&nbsp;- \(n\)= number of rows (A)

&nbsp;&nbsp;&nbsp;- \(m\)= number of columns (A)

&nbsp;&nbsp;&nbsp;- \(k\)= user-specified rank
&nbsp;&nbsp;&nbsp;- \(A\)= training matrix

It is assumed that the \(x\) and \(y\) columns are independent. 

First, perform QR decomposition of \(x\) and \(y^T\): 

&nbsp;&nbsp;&nbsp;\(x = QR\) 

&nbsp;&nbsp;&nbsp; \(y^T = ZS\), where \(Q^TQ = I = Z^TZ\)

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Call JAMA QR Decomposition directly on \(y^T\) to get \( Z \in \rm \Bbb I \! \Bbb R\), \( S \in \Bbb I \! \Bbb R \)

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\( R \) from QR decomposition of \( x \) is the upper triangular factor of Cholesky of \(X^TX\) Gram

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\( X^TX = LL^T, X = QR \)

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\( X^TX= (R^TQ^T) QR = R^TR \), since \(Q^TQ=I \) => \(R=L^T\) (transpose lower triangular)

**Note**: In code, \(X^TX \over n\) = \( LL^T \)

&nbsp;&nbsp;&nbsp;\( X^TX = (L \sqrt{n})(L \sqrt{n})^T =R^TR \)

&nbsp;&nbsp;&nbsp;\( R = L^T \sqrt{n} \in \rm \Bbb I \! \Bbb R^{k * k} \) reduced QR decomposition. 

For more information, refer to the [Rectangular matrix](https://en.wikipedia.org/wiki/QR_decomposition#Rectangular_matrix) section of "QR Decomposition" on Wikipedia. 

\( XY = QR(ZS)^T = Q(RS^T)Z^T \)

**Note**: \( (RS^T) \in \rm \Bbb I \!\Bbb R \)

Find SVD (locally) of \( RS^T \)

\( RS^T = U \sum V^T, U^TU = I = V^TV \) orthogonal 

\( XY = Q(RS^T)Z^T = (QU \sum (V^T Z^T) SVD \)

&nbsp;&nbsp;&nbsp;\( (QU)^T(QU) = U^T Q^TQU U^TU = I\)

&nbsp;&nbsp;&nbsp;\( (ZV)^T(ZV) = V^TZ^TZV = V^TV =I \)

Right singular vectors: \( ZV \in \rm \Bbb I \!\Bbb R^{m * k} \)

Singular values: \( \sum \in \rm \Bbb I \!\Bbb R^{k * k} \) diagonal

Left singular vectors: \( (QU) \in \rm \Bbb I \!\Bbb R^{n * k}\)



### References

Gockenbach, Mark S. "Finite-Dimensional Linear Algebra (Discrete Mathematics and Its Applications)." (2010): 566-567. 


---

<a name="GBM"></a>
## GBM

### Introduction

Gradient Boosted Regression and Gradient Boosted Classification are forward learning ensemble methods. The guiding heuristic is that good predictive results can be obtained through increasingly refined approximations. H2O's GBM sequentially builds regression trees on all the features of the dataset in a fully distributed way - each tree is built in parallel.

The current version of GBM is fundamentally the same as in previous versions of H2O (same algorithmic steps, same histogramming techniques), with the exception of the following changes: 

- Improved ability to train on categorical variables (using the `nbins_cats` parameter)
- Minor changes in histogramming logic for some corner cases

There was some code cleanup and refactoring to support the following features:

- Per-row observation weights
- Per-row offsets
- N-fold cross-validation
- Support for more distribution functions (such as Gamma, Poisson, and Tweedie)

### Defining a GBM Model

- **model_id**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates a destination key. 

- **training_frame**: (Required) Select the dataset used to build the model. 
**NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **nfolds**: Specify the number of folds for cross-validation. 

- **response_column**: (Required) Select the column to use as the independent variable. The data can be numeric or categorical. 

- **ignored_columns**: (Optional) Click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons. 

- **ignore\_const\_cols**: Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default. 

- **ntrees**: Specify the number of trees.  

- **max\_depth**: Specify the maximum tree depth.  

- **min\_rows**: Specify the minimum number of observations for a leaf (`nodesize` in R).   

- **nbins**: (Numerical/real/int only) Specify the number of bins for the histogram to build, then split at the best point.

- **max\_abs\_leafnode\_pred**: When building a GBM classification model, this option reduces overfitting by limiting the maximum absolute value of a leaf node prediction. This option defaults to Double.MAX_VALUE.

- **nbins_cats**: (Categorical/enums only) Specify the maximum number of bins for the histogram to build, then split at the best point. Higher values can lead to more overfitting.  The levels are ordered alphabetically; if there are more levels than bins, adjacent levels share bins. This value has a more significant impact on model fitness than **nbins**. Larger values may increase runtime, especially for deep trees and large clusters, so tuning may be required to find the optimal value for your configuration.  

- **seed**: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

- **learn_rate**: Specify the learning rate. The range is 0.0 to 1.0. 

- **learn\_rate\_annealing**: Specifies to reduce the **learn_rate** by this factor after every tree. So for *N* trees, GBM starts with **learn_rate** and ends with **learn_rate** * **learn\_rate\_annealing**^*N*. For example, instead of using **learn_rate=0.01**, you can now try **learn_rate=0.05** and **learn\_rate\_annealing=0.99**. This method would converge much faster with almost the same accuracy. Use caution not to overfit. 

- **distribution**: Select the loss function. The options are auto, bernoulli, multinomial, gaussian, poisson, gamma, or tweedie.  

	> - If the distribution is **multinomial**, the response column must be categorical.
	> - If the distribution is **poisson**, the response column must be numeric.
	> - If the distribution is **gamma**, the response column must be  numeric. 
	> - If the distribution is **tweedie**, the response column must be numeric. 
	> - If the distribution is **gaussian**, the response column must be numeric. 
	> - If the distribution is **laplace**, the data must be numeric and continuous (**Int**). 
	> - If the distribution is **quantile**, the data must be numeric and continuous (**Int**). 


- **sample_rate**: Specify the row sampling rate (x-axis). The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" ([Friedman, 1999](https://statweb.stanford.edu/~jhf/ftp/stobst.pdf)). If this option is specified along with **sample\_rate_per\_class**, then only the first option that GBM encounters will be used. 

- **sample\_rate_per\_class**: When building models from imbalanced datasets, this option specifies that each tree in the ensemble should sample from the full training dataset using a per-class-specific sampling rate rather than a global sample factor (as with `sample_rate`). The range for this option is 0.0 to 1.0. If this option is specified along with **sample_rate**, then only the first option that GBM encounters will be used.

- **col\_sample_rate**: Specify the column sampling rate (y-axis). The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" ([Friedman, 1999](https://statweb.stanford.edu/~jhf/ftp/stobst.pdf)). 

- **col\_sample_rate\_change\_per\_level**: This option specifies to change the column sampling rate as a function of the depth in the tree. For example:
	>level 1: **col\_sample_rate**
	
	>level 2: **col\_sample_rate** * **factor**
	
	>level 3: **col\_sample_rate** * **factor^2**
	
	>level 4: **col\_sample_rate** * **factor^3**
	
	>etc. 

- **min\_split_improvement**: The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range.  

- **histogram_type**: By default (AUTO) GBM bins from min...max in steps of (max-min)/N. Random split points or quantile-based split points can be selected as well. RoundRobin can be specified to cycle through all histogram types (one per tree). Use this option to specify the type of histogram to use for finding optimal split points:

  - AUTO
  - UniformAdaptive
  - Random
  - QuantilesGlobal
  - RoundRobin

- **score\_each\_iteration**: (Optional) Check this checkbox to score during each iteration of the model training. 

- **score\_tree\_interval**: Score the model after every so many trees. Disabled if set to 0.

- **fold_assignment**: (Applicable only if a value for **nfolds** is specified and **fold_column** is not selected) Select the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, or [Modulo](https://en.wikipedia.org/wiki/Modulo_operation).  
 
- **fold_column**: Select the column that contains the cross-validation fold index assignment per observation. 

- **offset_column**: (Not applicable if the **distribution** is **multinomial**) Select a column to use as the offset. 
	>*Note*: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following [link](http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf). If the **distribution** is **Bernoulli**, the value must be less than one.

- **weights_column**: Select a column to use for the observation weights, which are used for bias correction. The specified `weights_column` must be included in the specified `training_frame`. *Python only*: To use a weights column when passing an H2OFrame to `x` instead of a list of column names, the specified `training_frame` must contain the specified `weights_column`. 
	>*Note*: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.  

- **balance_classes**: Oversample the minority classes to balance the class distribution. This option is not selected by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **Max\_after\_balance\_size** parameter.

- **max\_confusion\_matrix\_size**: Specify the maximum size (in number of classes) for confusion matrices to be printed in the Logs. 

- **max\_hit\_ratio\_k**: Specify the maximum number (top K) of predictions to use for hit ratio computation. Applicable to multi-class only. To disable, enter 0. 


- **r2_stopping**: Specify a threshold for the coefficient of determination (\(r^2\)) metric value. When this threshold is met or exceeded, H2O stops making trees.   

- **stopping\_rounds**: Stops training when the option selected for **stopping\_metric** doesn't improve for the specified number of training rounds, based on a simple moving  average. To disable this feature, specify `0`. The metric is computed on the validation data (if provided); otherwise, training data is used. When used with **overwrite\_with\_best\_model**, the final model is the best model generated for the given **stopping\_metric** option.   
	>**Note**: If cross-validation is enabled: 
	1. All cross-validation models stop training when the validation metric doesn't improve. 
    2. The main model runs for the mean number of epochs. 
    3. N+1 models do *not* use **overwrite\_with\_best\_model**
    4. N+1 models may be off by the number specified for **stopping\_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs). 

- **stopping\_metric**: Select the metric to use for early stopping. The available options are: 
	
    - **AUTO**: Logloss for classification; deviance for regression
    - **deviance**
    - **logloss**
    - **MSE**
    - **AUC**
    - **r2**
    - **misclassification**
    - **mean\_per\_class\_error**

- **stopping\_tolerance**: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. 

- **max\_runtime\_secs**: Maximum allowed runtime in seconds for model training. Use 0 to disable.

- **build\_tree\_one\_node**: To run on a single node, check this checkbox. This is suitable for small datasets as there is no network overhead but fewer CPUs are used.

- **quantile_alpha**: (Only applicable if *Quantile* is selected for **distribution**) Specify the quantile to be used for Quantile Regression.

- **tweedie_power**: (Only applicable if *Tweedie* is selected for **distribution**) Specify the Tweedie power. The range is from 1 to 2. For a normal distribution, enter `0`. For Poisson distribution, enter `1`. For a gamma distribution, enter `2`. For a compound Poisson-gamma distribution, enter a value greater than 1 but less than 2. For more information, refer to [Tweedie distribution](https://en.wikipedia.org/wiki/Tweedie_distribution). 

- **checkpoint**: Enter a model key associated with a previously-trained model. Use this option to build a new model as a continuation of a previously-generated model.

- **keep\_cross\_validation\_models**: To keep the cross-validation models, check this checkbox. 

- **keep\_cross\_validation\_predictions**: To keep the cross-validation predictions, check this checkbox. 

- **class\_sampling\_factors**: Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance. There is no default value. 

- **max\_after\_balance\_size**: Specify the maximum relative size of the training data after balancing class counts (**balance\_classes** must be enabled). The value can be less than 1.0. 

- **nbins\_top\_level**: (For numerical/real/int columns only) Specify the minimum number of bins at the root level to use to build the histogram. This number will then be decreased by a factor of two per level.  


### Interpreting a GBM Model

The output for GBM includes the following: 

- Model parameters (hidden)
- A graph of the scoring history (training MSE vs number of trees)
- A graph of the variable importances
- Output (model category, validation metrics, initf)
- Model summary (number of trees, min. depth, max. depth, mean depth, min. leaves, max. leaves, mean leaves)
- Scoring history in tabular format
- Training metrics (model name, model checksum name, frame name, description, model category, duration in ms, scoring time, predictions, MSE, R2)
- Variable importances in tabular format

### Leaf Node Assignment
Trees cluster observations into leaf nodes, and this information can be useful for feature engineering or model interpretability. Use **h2o.predict\_leaf\_node\_assignment\(model, frame\)** to get an H2OFrame with the leaf node assignments, or click the checkbox when making predictions from Flow. Those leaf nodes represent decision rules that can be fed to other models (i.e., GLM with lambda search and strong rules) to obtain a limited set of the most important rules.

### FAQ

- **How does the algorithm handle missing values during training?**

  Missing values are interpreted as containing information (i.e., missing for a reason), rather than missing at random. During tree building, split decisions for every node are found by minimizing the loss function and treating missing values as a separate category that can go either left or right.

- **How does the algorithm handle missing values during testing?**

  During scoring, missing values follow the optimal path that was determined for them during training (minimized loss function).

- **What happens if the response has missing values?**

  No errors will occur, but nothing will be learned from rows containing missing the response.

-  **What happens when you try to predict on a categorical level not seen during training?**

  GBM converts a new categorical level to an "undefined" value in the test set, and then splits either left or right during scoring.  

- **Does it matter if the data is sorted?** 

  No.

- **Should data be shuffled before training?**

  No.

- **How does the algorithm handle highly imbalanced data in a response column?**

  You can specify `balance_classes`, `class_sampling_factors` and `max_after_balance_size` to control over/under-sampling.

- **What if there are a large number of columns?**

  DRF models are best for datasets with fewer than a few thousand columns.

- **What if there are a large number of categorical factor levels?**

  Large numbers of categoricals are handled very efficiently - there is never any one-hot encoding.

- **Given the same training set and the same GBM parameters, will GBM produce a different model with two different validation data sets, or the same model?**

  The same model will be generated. 

- **How deterministic is GBM?**

  The `nfolds` and `balance_classes` parameters use the seed directly. Otherwise, GBM is deterministic up to floating point rounding errors (out-of-order atomic addition of multiple threads during histogram building). Any observed variations in the AUC curve should be the same up to at least three to four significant digits. 

- **When fitting a random number between 0 and 1 as a single feature, the training ROC curve is consistent with `random` for low tree numbers and overfits as the number of trees is increased, as expected. However, when a random number is included as part of a set of hundreds of features, as the number of trees increases, the random number increases in feature importance. Why is this?**
 
  This is a known behavior of GBM that is similar to its behavior in R. If, for example, it takes 50 trees to learn all there is to learn from a frame without the random features, when you add a random predictor and train 1000 trees, the first 50 trees will be approximately the same. The final 950 trees are used to make sense of the random number, which will take a long time since there's no structure. The variable importance will reflect the fact that all the splits from the first 950 trees are devoted to the random feature. 

- **How is column sampling implemented for GBM?**

  For an example model using: 

  - 100 columns
  - `col_sample_rate_per_tree=0.754`
  - `col_sample_rate=0.8` (refers to available columns after per-tree sampling)

  For each tree, the floor is used to determine the number - in this example, (0.754*100)=75 out of the 100 - of columns that are randomly picked, and then the floor is used to determine the number - in this case, (0.754*0.8*100)=60 - of columns that are then randomly chosen for each split decision (out of the 75).

- **I want to score multiple models on a huge dataset. Is it possible to score these models in parallel?**

  The best way to score models in parallel is to use the in-H2O binary models. To do this, import the binary (non-POJO, previously exported) model into an H2O cluster; import the datasets into H2O as well; call the predict endpoint either from R, Python, Flow or the REST API directly; then export the predictions to file or download them from the server.

- **Are there any tutorials for GBM?**

 You can find tutorials for using GBM with R, Python, and Flow at the following location: <a href="https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/product/tutorials/gbm" target="_blank">https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/product/tutorials/gbm</a>

### GBM Algorithm 

H2O's Gradient Boosting Algorithms follow the algorithm specified by Hastie et al (2001):


Initialize \(f_{k0} = 0,\: k=1,2,…,K\)

For \(m=1\) to \(M:\)
  
   &nbsp;&nbsp;(a) Set \(p_{k}(x)=\frac{e^{f_{k}(x)}}{\sum_{l=1}^{K}e^{f_{l}(x)}},\:k=1,2,…,K\)

   &nbsp;&nbsp;(b) For \(k=1\) to \(K\):

   &nbsp;&nbsp;&nbsp;&nbsp;i. Compute \(r_{ikm}=y_{ik}-p_{k}(x_{i}),\:i=1,2,…,N.\)
	&nbsp;&nbsp;&nbsp;&nbsp;ii. Fit a regression tree to the targets \(r_{ikm},\:i=1,2,…,N\), giving terminal regions \(R_{jim},\:j=1,2,…,J_{m}.\)
   \(iii. Compute\) \(\gamma_{jkm}=\frac{K-1}{K}\:\frac{\sum_{x_{i}\in R_{jkm}}(r_{ikm})}{\sum_{x_{i}\in R_{jkm}}|r_{ikm}|(1-|r_{ikm})},\:j=1,2,…,J_{m}.\)
	\(\:iv.\:Update\:f_{km}(x)=f_{k,m-1}(x)+\sum_{j=1}^{J_{m}}\gamma_{jkm}I(x\in\:R_{jkm}).\)	      

Output \(\:\hat{f_{k}}(x)=f_{kM}(x),\:k=1,2,…,K.\) 

Be aware that the column type affects how the histogram is created and the column type depends on whether rows are excluded or assigned a weight of 0. For example:

val weight
1      1
0.5    0
5      1
3.5    0

The above vec has a real-valued type if passed as a whole, but if the zero-weighted rows are sliced away first, the integer weight is used. The resulting histogram is either kept at full `nbins` resolution or potentially shrunk to the discrete integer range, which affects the split points. 

For more information about the GBM algorithm, refer to the [Gradient Boosted Machines booklet](http://h2o.ai/resources). 


### Binning In GBM

**Is the binning range-based or percentile-based?**

It's range based, and re-binned at each tree split.
NAs always "go to the left" (smallest) bin.
There's a minimum observations required value (default 10).
There has to be at least 1 FP ULP improvement in error to split (all-constant predictors won't split).
nbins is at least 1024 at the top-level, and divides by 2 down each level until you hit the nbins parameter (default: 20).
Categoricals use a separate, more aggressive, binning range.

Re-binning means, eg, suppose your column C1 data is: {1,1,2,4,8,16,100,1000}.
Then a 20-way binning will use the range from 1 to 1000, bin by units of 50.
The first binning will be a lumpy: {1,1,2,4,8,16},{100},{47_empty_bins},{1000}.  Suppose the split peels out the {1000} bin from the rest.

Next layer in the tree for the left-split has value from 1 to 100 (not 1000!) and so re-bins in units of 5:  {1,1,2,4},{8},{},{16},{lots of empty bins}{100}
(the RH split has the single value 1000).

And so on: important dense ranges with split essentially logrithmeticaly at each layer.

**What should I do if my variables are long skewed in the tail and might have large outliers?**

You can try adding a new predictor column which is either pre-binned (e.g. as a categorical - "small", "median", and "giant" values), or a log-transform - plus keep the old column.

### References

Dietterich, Thomas G, and Eun Bae Kong. "Machine Learning Bias,
Statistical Bias, and Statistical Variance of Decision Tree
Algorithms." ML-95 255 (1995).

Elith, Jane, John R Leathwick, and Trevor Hastie. "A Working Guide to
Boosted Regression Trees." Journal of Animal Ecology 77.4 (2008): 802-813

Friedman, Jerome H. "Greedy Function Approximation: A Gradient
Boosting Machine." Annals of Statistics (2001): 1189-1232.

Friedman, Jerome, Trevor Hastie, Saharon Rosset, Robert Tibshirani,
and Ji Zhu. "Discussion of Boosting Papers." Ann. Statist 32 (2004): 
102-107

[Friedman, Jerome, Trevor Hastie, and Robert Tibshirani. "Additive
Logistic Regression: A Statistical View of Boosting (With Discussion
and a Rejoinder by the Authors)." The Annals of Statistics 28.2
(2000): 337-407](http://projecteuclid.org/DPubS?service=UI&version=1.0&verb=Display&handle=euclid.aos/1016218223)

[Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The
Elements of Statistical Learning.
Vol.1. N.p., page 339: Springer New York, 2001.](http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf)

---

<a name="DL"></a>
## Deep Learning

### Introduction

H2O’s Deep Learning is based on a multi-layer feed-forward artificial neural network that is trained with stochastic gradient descent using back-propagation. The network can contain a large number of hidden layers consisting of neurons with tanh, rectifier and maxout activation functions. Advanced features such as adaptive learning rate, rate annealing, momentum training, dropout, L1 or L2 regularization, checkpointing and grid search enable high predictive accuracy. Each compute node trains a copy of the global model parameters on its local data with multi-threading (asynchronously), and contributes periodically to the global model via model averaging across the network.

### Defining a Deep Learning Model

H2O Deep Learning models have many input parameters, many of which are only accessible via the expert mode. For most cases, use the default values. Please read the following instructions before building extensive Deep Learning models. The application of grid search and successive continuation of winning models via checkpoint restart is highly recommended, as model performance can vary greatly.

- **model_id**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates a destination key. 

- **training_frame**: (Required) Select the dataset used to build the model. 
**NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **nfolds**: Specify the number of folds for cross-validation. 
	>**Note**: Cross-validation is not supported when autoencoder is enabled.   

- **response_column**: Select the column to use as the independent variable. The data can be numeric or categorical. 

- **ignored_columns**: (Optional) Click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons. 

- **ignore\_const\_cols**: Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default. 

- **activation**: Select the activation function (Tanh, Tanh with dropout, Rectifier, Rectifier with dropout, Maxout, Maxout with dropout).
	> - **Maxout** is not supported when **autoencoder** is enabled.    

- **hidden**: Specify the hidden layer sizes (e.g., 100,100). The value must be positive.   

- **epochs**: Specify the number of times to iterate (stream) the dataset. The value can be a fraction.   

- **variable_importances**: Check this checkbox to compute variable importance. This option is not selected by default. 

- **fold_assignment**: (Applicable only if a value for **nfolds** is specified and **fold_column** is not selected) Select the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, or [Modulo](https://en.wikipedia.org/wiki/Modulo_operation).  

- **fold_column**: Select the column that contains the cross-validation fold index assignment per observation. 

- **weights_column**: Select a column to use for the observation weights, which are used for bias correction. The specified `weights_column` must be included in the specified `training_frame`. *Python only*: To use a weights column when passing an H2OFrame to `x` instead of a list of column names, the specified `training_frame` must contain the specified `weights_column`. 
	>*Note*: Weights are per-row observation weights. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.  

- **offset_column**: (Applicable for regression only) Select a column to use as the offset. 
	>*Note*: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following [link](http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf). 

- **balance_classes**: (Applicable for classification only) Oversample the minority classes to balance the class distribution. This option is not selected by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **Max\_after\_balance\_size** parameter. 

- **standardize**: If enabled, automatically standardize the data (mean 0, variance 0). If disabled, the user must provide properly scaled input data.

- **max\_confusion\_matrix\_size**: Specify the maximum size (in number of classes) for confusion matrices to be printed in the Logs. 

- **max\_hit\_ratio\_k**: Specify the maximum number (top K) of predictions to use for hit ratio computation. Applicable to multi-class only. To disable, enter 0. 

- **checkpoint**: Enter a model key associated with a previously-trained Deep Learning model. Use this option to build a new model as a continuation of a previously-generated model.
	>**Note**: Cross-validation is not supported during checkpoint restarts.  

- **use\_all\_factor\_levels**: Check this checkbox to use all factor levels in the possible set of predictors; if you enable this option, sufficient regularization is required. By default, the first factor level is skipped. For Deep Learning models, this option is useful for determining variable importances and is automatically enabled if the autoencoder is selected. 

- **train\_samples\_per\_iteration**: Specify the number of global training samples per MapReduce iteration. To specify one epoch, enter 0. To specify all available data (e.g., replicated training data), enter -1. To use the automatic values, enter -2.   

- **adaptive_rate**: Check this checkbox to enable the adaptive learning rate (ADADELTA). This option is selected by default. 

- **input\_dropout\_ratio**: Specify the input layer dropout ratio to improve generalization. Suggested values are 0.1 or 0.2.  

- **hidden\_dropout\_ratios**: (Applicable only if the activation type is **TanhWithDropout**, **RectifierWithDropout**, or **MaxoutWithDropout**) Specify the hidden layer dropout ratio to improve generalization. Specify one value per hidden layer. The range is >= 0 to <1 and the default is 0.5. 

- **l1**: Specify the L1 regularization to add stability and improve generalization; sets the value of many weights to 0. 

- **l2**: Specify the L2 regularization to add stability and improve generalization; sets the value of many weights to smaller values. 

- **loss**:  Select the loss function. The options are Automatic,  CrossEntropy, Quadratic, Huber, or Absolute and the default value is Automatic. 
	> - Use **Absolute**, **Quadratic**, or **Huber** for regression
	> - Use  **Absolute**, **Quadratic**, **Huber**, or **CrossEntropy** for classification

- **distribution**:  Select the distribution type from the drop-down list. The options are auto, bernoulli, multinomial, gaussian, poisson, gamma, laplace, quantile or tweedie.

- **quantile_alpha**: (Only applicable if *Quantile* is selected for **distribution**) Specify the quantile to be used for Quantile Regression.

- **tweedie_power**: (Only applicable if *Tweedie* is selected for **distribution**) Specify the Tweedie power. The range is from 1 to 2. For a normal distribution, enter `0`. For Poisson distribution, enter `1`. For a gamma distribution, enter `2`. For a compound Poisson-gamma distribution, enter a value greater than 1 but less than 2. For more information, refer to [Tweedie distribution](https://en.wikipedia.org/wiki/Tweedie_distribution). 

- **score_interval**: Specify the shortest time interval (in seconds) to wait between model scoring.  

- **score\_training\_samples**: Specify the number of training set samples for scoring. The value must be >= 0. To use all training samples, enter 0.  

- **score\_validation\_samples**: (Applicable only if **validation\_frame** is specified) Specify the number of validation set samples for scoring. The value must be >= 0. To use all validation samples, enter 0.  

- **score\_duty\_cycle**: Specify the maximum duty cycle fraction for scoring. A lower value results in more training and a higher value results in more scoring. 

- **stopping\_rounds**: Stops training when the option selected for **stopping\_metric** doesn't improve for the specified number of training rounds, based on a simple moving  average. To disable this feature, specify `0`. The metric is computed on the validation data (if provided); otherwise, training data is used. When used with **overwrite\_with\_best\_model**, the final model is the best model generated for the given **stopping\_metric** option.   
	>**Note**: If cross-validation is enabled: 
	1. All cross-validation models stop training when the validation metric doesn't improve. 
    2. The main model runs for the mean number of epochs. 
    3. N+1 models do *not* use **overwrite\_with\_best\_model**
    4. N+1 models may be off by the number specified for **stopping\_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs). 

- **stopping\_metric**: Select the metric to use for early stopping. The available options are: 
	
    - **AUTO**: Logloss for classification; deviance for regression
    - **deviance**
    - **logloss**
    - **MSE**
    - **AUC**
    - **r2**
    - **misclassification** 
    - **mean\_per\_class\_error**

- **stopping\_tolerance**: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. 

- **max\_runtime\_secs**: Maximum allowed runtime in seconds for model training. Use 0 to disable.

- **autoencoder**: Check this checkbox to enable the Deep Learning autoencoder. This option is not selected by default. 
	>**Note**: Cross-validation is not supported when autoencoder is enabled.   

- **keep\_cross\_validation\_predictions**: To keep the cross-validation predictions, check this checkbox. 

- **class\_sampling\_factors**: (Applicable only for classification and when **balance\_classes** is enabled) Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance.  

- **max\_after\_balance\_size**: Specify the maximum relative size of the training data after balancing class counts (**balance\_classes** must be enabled). The value can be less than 1.0. 

- **overwrite\_with\_best\_model**: Check this checkbox to overwrite the final model with the best model found during training, based on the option selected for **stopping\_metric**. This option is selected by default. 

- **target\_ratio\_comm\_to\_comp**: Specify the target ratio of communication overhead to computation. This option is only enabled for multi-node operation and if **train\_samples\_per\_iteration** equals -2 (auto-tuning).  

- **seed**: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

- **rho**: (Applicable only if **adaptive\_rate** is enabled) Specify the adaptive learning rate time decay factor.   

- **epsilon**:(Applicable only if **adaptive\_rate** is enabled) Specify the adaptive learning rate time smoothing factor to avoid dividing by zero. 

- **max_w2**: Specify the constraint for the squared sum of the incoming weights per unit (e.g., for Rectifier).  

- **initial\_weight\_distribution**: Select the initial weight distribution (Uniform Adaptive, Uniform, or Normal).   

- **regression_stop**: (Regression models only) Specify the stopping criterion for regression error (MSE) on the training data. To disable this option, enter -1.  

- **diagnostics**: Check this checkbox to compute the variable importances for input features (using the Gedeon method). For large networks, selecting this option can reduce speed. This option is selected by default. 

- **fast_mode**: Check this checkbox to enable fast mode, a minor approximation in back-propagation. This option is selected by default. 

- **force\_load\_balance**: Check this checkbox to force extra load balancing to increase training speed for small datasets and use all cores. This option is selected by default. 

- **single\_node\_mode**: Check this checkbox to force H2O to run on a single node for fine-tuning of model parameters. This option is not selected by default. 

- **shuffle\_training\_data**: Check this checkbox to shuffle the training data. This option is recommended if the training data is replicated and the value of **train\_samples\_per\_iteration** is close to the number of nodes times the number of rows. This option is not selected by default. 

- **missing\_values\_handling**: Specify how to handle missing values (Skip or MeanImputation). This defaults to MeanImputation.   

- **quiet_mode**: Check this checkbox to display less output in the standard output. This option is not selected by default. 

- **sparse**: Check this  checkbox to enable sparse data handling, which is more efficient for data with many zero values.  

- **col_major**: Check this checkbox to use a column major weight matrix for the input layer. This option can speed up forward propagation but may reduce the speed of backpropagation. This option is not selected by default. 

- **average_activation**: Specify the average activation for the sparse autoencoder.  
	> - If **Rectifier** is used, the **average\_activation** value must be positive. 

- **sparsity_beta**: (Applicable only if **autoencoder** is enabled) Specify the sparsity-based regularization optimization. For more information, refer to the following [link](http://www.mit.edu/~9.520/spring09/Classes/class11_sparsity.pdf).  
  
- **max\_categorical\_features**: Specify the maximum number of categorical features enforced via hashing. The value must be at least one. 

- **reproducible**: To force reproducibility on small data, check this checkbox. If this option is enabled, the model takes more time to generate, since it uses only one thread. 

- **export\_weights\_and\_biases**: To export the neural network weights and biases as H2O frames, check this checkbox.

- **elastic\_averaging**: To enable elastic averaging between computing nodes, which can improve distributed model convergence, check this checkbox (experimental).


- **rate**: (Applicable only if **adaptive\_rate** is disabled) Specify the learning rate. Higher values result in a less stable model, while lower values lead to slower convergence. 

- **rate\_annealing**: (Applicable only if **adaptive\_rate** is disabled) Specify the rate annealing value. The rate annealing is calculated as **rate**\(1 + **rate\_annealing** * samples). 

- **rate\_decay**: (Applicable only if **adaptive\_rate** is disabled) Specify the rate decay factor between layers. The rate decay is calculated as (N-th layer: **rate** * alpha^(N-1)). 

- **momentum\_start**: (Applicable only if **adaptive\_rate** is disabled) Specify the initial momentum at the beginning of training; we suggest 0.5. 

- **momentum\_ramp**: (Applicable only if **adaptive\_rate** is disabled) Specify the number of training samples for which the momentum increases. 

- **momentum\_stable**: (Applicable only if **adaptive\_rate** is disabled) Specify the final momentum after the ramp is over; we suggest 0.99. 

- **nesterov\_accelerated\_gradient**: (Applicable only if **adaptive\_rate** is disabled) Enables the [Nesterov Accelerated Gradient](http://premolab.ru/pub_files/pub88/qhkDNEyp8.pdf). 


- **initial\_weight\_scale**: (Applicable only if **initial\_weight\_distribution** is **Uniform** or **Normal**) Specify the scale of the distribution function. For **Uniform**, the values are drawn uniformly. For **Normal**, the values are drawn from a Normal distribution with a standard deviation.  



### Interpreting a Deep Learning Model

To view the results, click the View button. The output for the Deep Learning model includes the following information for both the training and testing sets: 

- Model parameters (hidden)
- A chart of the variable importances
- A graph of the scoring history (training MSE and validation MSE vs epochs)
- Output (model category, weights, biases)
- Status of neuron layers (layer number, units, type, dropout, L1, L2, mean rate, rate RMS, momentum, mean weight, weight RMS, mean bias, bias RMS)
- Scoring history in tabular format
- Training metrics (model name, model checksum name, frame name, frame checksum name, description, model category, duration in ms, scoring time, predictions, MSE, R2, logloss)
- Top-K Hit Ratios (for multi-class classification)
- Confusion matrix (for classification)



### FAQ

- **How does the algorithm handle missing values during training?**

  Depending on the selected missing value handling policy, they are either imputed mean or the whole row is skipped.  
  The default behavior is mean imputation. Note that categorical variables are imputed by adding an extra "missing" level.   
  Optionally, Deep Learning can skip all rows with any missing values. 

- **How does the algorithm handle missing values during testing?**

  Missing values in the test set will be mean-imputed during scoring.

- **What happens if the response has missing values?**

  No errors will occur, but nothing will be learned from rows containing missing the response.

- **Does it matter if the data is sorted?** 

  Yes, since the training set is processed in order. Depending whether `train_samples_per_iteration` is enabled, some rows will be skipped. If `shuffle_training_data` is enabled, then each thread that is processing a small subset of rows will process rows randomly, but it is not a global shuffle.

- **Should data be shuffled before training?**

  Yes, the data should be shuffled before training, especially if the dataset is sorted. 

- **How does the algorithm handle highly imbalanced data in a response column?**

  Specify `balance_classes`, `class_sampling_factors` and `max_after_balance_size` to control over/under-sampling.

- **What if there are a large number of columns?**

  The input neuron layer's size is scaled to the number of input features, so as the number of columns increases, the model complexity increases as well. 
  
- **What if there are a large number of categorical factor levels?**

  This is something to look out for. Say you have three columns: zip code (70k levels), height, and income. The resulting number of internally one-hot encoded features will be 70,002 and only 3 of them will be activated (non-zero). If the first hidden layer has 200 neurons, then the resulting weight matrix will be of size 70,002 x 200, which can take a long time to train and converge. In this case, we recommend either reducing the number of categorical factor levels upfront (e.g., using `h2o.interaction()` from R), or specifying `max_categorical_features` to use feature hashing to reduce the dimensionality.

- **How does your Deep Learning Autoencoder work? Is it deep or shallow?**

  H2O’s DL autoencoder is based on the standard deep (multi-layer) neural net architecture, where the entire network is learned together, instead of being stacked layer-by-layer.  The only difference is that no response is required in the input and that the output layer has as many neurons as the input layer. If you don’t achieve convergence, then try using the *Tanh* activation and fewer layers.  We have some example test scripts [here](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/deeplearning/), and even some that show [how stacked auto-encoders can be implemented in R](https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/deeplearning/runit_deeplearning_stacked_autoencoder_large.R). 

- **When building the model, does Deep Learning use all features or a selection of the best features?**

  For Deep Learning, all features are used, unless you manually specify that columns should be ignored. Adding an L1 penalty can make the model sparse, but it is still the full size. 

- **What is the relationship between iterations, epochs, and the `train_samples_per_iteration` parameter?**

  Epochs measures the amount of training. An iteration is one MapReduce (MR) step - essentially, one pass over the data. The `train_samples_per_iteration` parameter is the amount of data to use for training for each MR step, which can be more or less than the number of rows. 


- **When do `reduce()` calls occur, after each iteration or each epoch?**

  Neither; `reduce()` calls occur after every two `map()` calls, between threads and ultimately between nodes. There are many `reduce()` calls, much more than one per MapReduce step (also known as an "iteration"). Epochs are not related to MR iterations, unless you specify `train_samples_per_iteration` as `0` or `-1` (or to number of rows/nodes). Otherwise, one MR iteration can train with an arbitrary number of training samples (as specified by `train_samples_per_iteration`). 

- **Does each Mapper task work on a separate neural-net model that is combined during reduction, or is each Mapper manipulating a shared object that's persistent across nodes?**

   Neither; there's one model per compute node, so multiple Mappers/threads share one model, which is why H2O is not reproducible unless a small dataset is used and `force_load_balance=F` or `reproducible=T`, which effectively rebalances to a single chunk and leads to only one thread to launch a `map()`. The current behavior is simple model averaging; between-node model averaging via "Elastic Averaging" is currently [in progress](https://0xdata.atlassian.net/browse/HEXDEV-206). 

- **Is the loss function and backpropagation performed after each individual training sample, each iteration, or at the epoch level?**

  Loss function and backpropagation are performed after each training sample (mini-batch size 1 == online stochastic gradient descent). 

- **When using Hinton's dropout and specifying an input dropout ratio of ~20% and `train_samples_per_iteration` is set to 50, will each of the 50 samples have a different set of the 20% input neurons suppressed?** 

  Yes - suppression is not done at the iteration level across as samples in that iteration. The dropout mask is different for each training sample. 

- **When using dropout parameters such as `input_dropout_ratio`, what happens if you use only `Rectifier` instead of `RectifierWithDropout` in the activation parameter?**

  The amount of dropout on the input layer can be specified for all activation functions, but hidden layer dropout is only supported is set to `WithDropout`. The default hidden dropout is 50%, so you don't need to specify anything but the activation type to get good results, but you can set the hidden dropout values for each layer separately. 

- **When using the `score_validation_sampling` and `score_training_samples` parameters, is scoring done at the end of the Deep Learning run?** 

  The majority of scoring takes place after each MR iteration. After the iteration is complete, it may or may not be scored, depending on two criteria: the time since the last scoring and the time needed for scoring. 

  The maximum time between scoring (`score_interval`, default = 5 seconds) and the maximum fraction of time spent scoring (`score_duty_cycle`) independently of loss function, backpropagation, etc. 

  Of course, using more training or validation samples will increase the time for scoring, as well as scoring more frequently. For more information about how this affects runtime, refer to the [Deep Learning Performance Guide](http://h2o.ai/blog/2015/02/deep-learning-performance/).

- **How does the validation frame affect the built neuron network?**

  The validation frame is only used for scoring and does not directly affect the model. However, the validation frame can be used stopping the model early if `overwrite_with_best_model = T`, which is the default. If this parameter is enabled, the model with the lowest validation error is displayed at the end of the training. 

  By default, the validation frame is used to tune the model parameters (such as number of epochs) and will return the best model as measured by the validation metrics, depending on how often the validation metrics are computed (`score_duty_cycle`) and whether the validation frame itself was sampled. 

  Model-internal sampling of the validation frame (`score_validation_samples` and `score_validation_sampling` for optional stratification) will affect early stopping quality. If you specify a validation frame but set `score_validation_samples` to more than the number of rows in the validation frame (instead of 0, which represents the entire frame), the validation metrics received at the end of training will not be reproducible, since the model does internal sampling. 

- **Are there any best practices for building a model using checkpointing?**

 In general, to get the best possible model, we recommend building a model with `train\_samples\_per\_iteration = -2` (which is the default value for auto-tuning) and saving it. 

 To improve the initial model, start from the previous model and add iterations by building another model, setting the checkpoint to the previous model, and changing `train\_samples\_per\_iteration`, `target\_ratio\_comm\_to\_comp`, or other parameters. 

 If you don't know your model ID because it was generated by R, look it up using `h2o.ls()`. By default, Deep Learning model names start with `deeplearning_` To view the model, use `m <- h2o.getModel("my\_model\_id")` or `summary(m)`. 

 There are a few ways to manage checkpoint restarts: 

 *Option 1*: (Multi-node only) Leave `train\_samples\_per\_iteration = -2`, increase `target\_comm\_to\_comp` from 0.05 to 0.25 or 0.5, which provides more communication. This should result in a better model when using multiple nodes. **Note:** This does not affect single-node performance. 

 *Option 2*: (Single or multi-node) Set `train\_samples\_per\_iteration` to \(N\), where \(N\) is the number of training samples used for training by the entire cluster for one iteration. Each of the nodes then trains on \(N\) randomly-chosen rows for every iteration. The number defined as \(N\) depends on the dataset size and the model complexity. 

 *Option 3*: (Single or multi-node) Change regularization parameters such as `l1, l2, max\_w2, input\_droput\_ratio` or `hidden\_dropout\_ratios`. We recommend build the first mode using `RectifierWithDropout`, `input\_dropout\_ratio = 0` (if there is suspected noise in the input), and `hidden\_dropout\_ratios=c(0,0,0)` (for the ability to enable dropout regularization later). 

- **How does class balancing work?**

 The `max\_after\_balance\_size` parameter defines the maximum size of the over-sampled dataset. For example, if `max\_after\_balance\_size = 3`, the over-sampled dataset will not be greater than three times the size of the original dataset. 

 For example, if you have five classes with priors of 90%, 2.5%, 2.5%, and 2.5% (out of a total of one million rows) and you oversample to obtain a class balance using `balance\_classes = T`, the result is all four minor classes are oversampled by forty times and the total dataset will be 4.5 times as large as the original dataset (900,000 rows of each class). If `max\_after\_balance\_size = 3`, all five balance classes are reduced by 3/5 resulting in 600,000 rows each (three million total). 

 To specify the per-class over- or under-sampling factors, use `class\_sampling\_factors`. In the previous example, the default behavior with `balance\_classes` is equivalent to `c(1,40,40,40,40)`, while when `max\_after\_balance\_size = 3`, the results would be `c(3/5,40*3/5,40*3/5,40*3/5)`. 

 In all cases, the probabilities are adjusted to the pre-sampled space, so the minority classes will have lower average final probabilities than the majority class, even if they were sampled to reach class balance. 

- **How is variable importance calculated for Deep Learning?**

 For Deep Learning, variable importance is calculated using the Gedeon method. 

- **Why do my results include a negative R^2 value?**

 H2O computes the R^2 as `1 - MSE/variance`, where `MSE` is the mean squared error of the prediction, and `variance` is the (weighted) variance: `sum(w*Y*Y)/sum(w) - sum(w*Y)^2/sum(w)^2`, where `w` is the row weight (1 by default), and `Y` is the centered response.

 If the MSE is greater than the variance of the response, you will see a negative R^2 value. This indicates that the model got a really bad fit, and the results are not to be trusted. 

---

### Deep Learning Algorithm 

To compute deviance for a Deep Learning regression model, the following formula is used: 

Loss = Quadratic -> MSE==Deviance
For Absolute/Laplace or Huber -> MSE != Deviance

For more information about how the Deep Learning algorithm works, refer to the [Deep Learning booklet](http://h2o.ai/resources). 

### References

 ["Deep Learning." *Wikipedia: The free encyclopedia*. Wikimedia Foundation, Inc. 1 May 2015. Web. 4 May 2015.](http://en.wikipedia.org/wiki/Deep_learning)

 ["Artificial Neural Network." *Wikipedia: The free encyclopedia*. Wikimedia Foundation, Inc. 22 April 2015. Web. 4 May 2015.](http://en.wikipedia.org/wiki/Artificial_neural_network)

 [Zeiler, Matthew D. 'ADADELTA: An Adaptive Learning Rate Method'. Arxiv.org. N.p., 2012. Web. 4 May 2015.](http://arxiv.org/abs/1212.5701)

 [Sutskever, Ilya et al. "On the importance of initialization and momementum in deep learning." JMLR:W&CP vol. 28. (2013).](http://www.cs.toronto.edu/~fritz/absps/momentum.pdf)

 [Hinton, G.E. et. al. "Improving neural networks by preventing co-adaptation of feature detectors." University of Toronto. (2012).](http://arxiv.org/pdf/1207.0580.pdf)

 [Wager, Stefan et. al. "Dropout Training as Adaptive Regularization." Advances in Neural Information Processing Systems. (2013).](http://arxiv.org/abs/1307.1493)

 [Gedeon, TD. "Data mining of inputs: analysing magnitude and functional measures." University of New South Wales. (1997).](http://www.ncbi.nlm.nih.gov/pubmed/9327276)
    
 [Candel, Arno and Parmar, Viraj. "Deep Learning with H2O." H2O.ai, Inc. (2015).](https://leanpub.com/deeplearning)
    
  [Deep Learning Training](http://learn.h2o.ai/content/hands-on_training/deep_learning.html)
    
  [Slideshare slide decks](http://www.slideshare.net/0xdata/presentations?order=latest)
    
  [Youtube channel](https://www.youtube.com/user/0xdata)
    
  [Candel, Arno. "The Definitive Performance Tuning Guide for H2O Deep Learning." H2O.ai, Inc. (2015).](http://h2o.ai/blog/2015/02/deep-learning-performance/)

  [Niu, Feng, et al. "Hogwild!: A lock-free approach to parallelizing stochastic gradient descent." Advances in Neural Information Processing Systems 24 (2011): 693-701. (algorithm implemented is on p.5)](https://papers.nips.cc/paper/4390-hogwild-a-lock-free-approach-to-parallelizing-stochastic-gradient-descent.pdf)

  [Hawkins, Simon et al. "Outlier Detection Using Replicator Neural Networks." CSIRO Mathematical and Information Sciences](http://neuro.bstu.by/ai/To-dom/My_research/Paper-0-again/For-research/D-mining/Anomaly-D/KDD-cup-99/NN/dawak02.pdf)

## Cross-Validation

N-fold cross-validation is used to validate a model internally, i.e., estimate the model performance without having to sacrifice a validation split. Also, you avoid statistical issues with your validation split (it might be a “lucky” split, especially for imbalanced data). Good values for N are around 5 to 10. Comparing the N validation metrics is always a good idea, to check the stability of the estimation, before “trusting” the main model.

You have to make sure, however, that the holdout sets for each of the N models are good. For i.i.d. data, the random splitting of the data into N pieces (default behavior) or modulo-based splitting is fine. For temporal or otherwise structured data with distinct “events”, you have to make sure to split the folds based on the events. For example, if you have observations (e.g., user transactions) from N cities and you want to build models on users from only N-1 cities and validate them on the remaining city (if you want to study the generalization to new cities, for example), you will need to specify the parameter “fold_column" to be the city column. Otherwise, you will have rows (users) from all N cities randomly blended into the N folds, and all N cv models will see all N cities, making the validation less useful (or totally wrong, depending on the distribution of the data).  This is known as “data leakage”: https://youtu.be/NHw_aKO5KUM?t=889

### How Cross-Validation is Calculated

In general, for all algos that support the nfolds parameter, H2O’s cross-validation works as follows:

For example, for nfolds=5, 6 models are built. The first 5 models (cross-validation models) are built on 80% of the training data, and a different 20% is held out for each of the 5 models. Then the main model is built on 100% of the training data. This main model is the model you get back from H2O in R, Python and Flow.

This main model contains training metrics and cross-validation metrics (and optionally, validation metrics if a validation frame was provided). The main model also contains pointers to the 5 cross-validation models for further inspection.

All 5 cross-validation models contain training metrics (from the 80% training data) and validation metrics (from their 20% holdout/validation data). To compute their individual validation metrics, each of the 5 cross-validation models had to make predictions on their 20% of of rows of the original training frame, and score against the true labels of the 20% holdout.

For the main model, this is how the cross-validation metrics are computed: The 5 holdout predictions are combined into one prediction for the full training dataset (i.e., predictions for every row of the training data, but the model making the prediction for a particular row has not seen that row during training). This “holdout prediction" is then scored against the true labels, and the overall cross-validation metrics are computed.

This approach has some implications. Scoring the holdout predictions freshly can result in different metrics than taking the average of the 5 validation metrics of the cross-validation models. For example, if the sizes of the holdout folds differ a lot (e.g., when a user-given fold_column is used), then the average should probably be replaced with a weighted average. Also, if the cross-validation models map to slightly different probability spaces, which can happen for small DL models that converge to different local minima, then the confused rank ordering of the combined predictions would lead to a significantly different AUC than the average.

### Example in R

To gain more insights into the variance of the holdout metrics (e.g., AUCs), you can look up the cross-validation models, and inspect their validation metrics. Here’s an R code example showing the two approaches:

```
library(h2o)
h2o.init()
df <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
df$CAPSULE <- as.factor(df$CAPSULE)
model_fit <- h2o.gbm(3:8,2,df,nfolds=5,seed=1234)

# Default: AUC of holdout predictions
h2o.auc(model_fit,xval=TRUE)

# Optional: Average the holdout AUCs
cvAUCs <- sapply(sapply(model_fit@model$cross_validation_models, `[[`, "name"), function(x) { h2o.auc(h2o.getModel(x), valid=TRUE) })
print(cvAUCs)
mean(cvAUCs)
```

## Using Cross-Validated Predictions

With cross-validated model building, H2O builds N+1 models: N cross-validated model and 1 overarching model over all of the training data.

Each cv-model produces a prediction frame pertaining to its fold. It can be saved and probed from the various clients if `keep_cross_validation_predictions` parameter is set in the model constructor.

These holdout predictions have some interesting properties. First they have names like:

```
  prediction_GBM_model_1452035702801_1_cv_1
```
and they contain, unsurprisingly, predictions for the data held out in the fold. They also have the same number of rows as the entire input training frame with `0`s filled in for all rows that are not in the hold out. 

Let's look at an example. 

Here is a snippet of a three-class classification dataset (last column is the response column), with a 3-fold identification column appended to the end:


| sepal_len | sepal_wid | petal_len | petal_wid | class   | foldId |
|-----------|-----------|-----------|-----------|---------|--------|
| 5.1       | 3.5       | 1.4       | 0.2       | setosa  | 0      |
| 4.9       | 3.0       | 1.4       | 0.2       | setosa  | 0      |
| 4.7       | 3.2       | 1.3       | 0.2       | setosa  | 2      |
| 4.6       | 3.1       | 1.5       | 0.2       | setosa  | 1      |
| 5.0       | 3.6       | 1.4       | 0.2       | setosa  | 2      |
| 5.4       | 3.9       | 1.7       | 0.4       | setosa  | 1      |
| 4.6       | 3.4       | 1.4       | 0.3       | setosa  | 1      |
| 5.0       | 3.4       | 1.5       | 0.2       | setosa  | 0      |
| 4.4       | 2.9       | 1.4       | 0.4       | setosa  | 1      |


Each cross-validated model produces a prediction frame

```
  prediction_GBM_model_1452035702801_1_cv_1
  prediction_GBM_model_1452035702801_1_cv_2 
  prediction_GBM_model_1452035702801_1_cv_3
```

and each one has the following shape (for example the first one):

```
  prediction_GBM_model_1452035702801_1_cv_1
``` 

| prediction | setosa | versicolor | virginica |
|------------|--------|------------|-----------|
| 1          | 0.0232 | 0.7321     | 0.2447    |
| 2          | 0.0543 | 0.2343     | 0.7114    |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0.8921 | 0.0321     | 0.0758    |
| 0          | 0      | 0          | 0         |

The training rows receive a prediction of `0` (more on this below) as well as `0` for all class probabilities. Each of these holdout predictions has the same number of rows as the input frame.

## Combining holdout predictions

The frame of cross-validated predictions is simply the superposition of the individual predictions. [Here's an example from R](https://0xdata.atlassian.net/browse/PUBDEV-2236):

``` 
library(h2o)
h2o.init()

# H2O Cross-validated K-means example 
prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile(path = prosPath)
fit <- h2o.kmeans(training_frame = prostate.hex, 
                  k = 10, 
                  x = c("AGE", "RACE", "VOL", "GLEASON"), 
                  nfolds = 5,  #If you want to specify folds directly, then use "fold_column" arg
                  keep_cross_validation_predictions = TRUE)

# This is where cv preds are stored:
fit@model$cross_validation_predictions$name


# Compress the CV preds into a single H2O Frame:
# Each fold's preds are stored in a N x 1 col, where the row values for non-active folds are set to zero
# So we will compress this into a single 1-col H2O Frame (easier to digest)

nfolds <- fit@parameters$nfolds
predlist <- sapply(1:nfolds, function(v) h2o.getFrame(fit@model$cross_validation_predictions[[v]]$name)$predict, simplify = FALSE)
cvpred_sparse <- h2o.cbind(predlist)  # N x V Hdf with rows that are all zeros, except corresponding to the v^th fold if that rows is associated with v
pred <- apply(cvpred_sparse, 1, sum)  # These are the cross-validated predicted cluster IDs for each of the 1:N observations
```

This can be extended to other family types as well (multinomial, binomial, regression):

```
# helper function
.compress_to_cvpreds <- function(h2omodel, family) {
  # return the frame_id of the resulting 1-col Hdf of cvpreds for learner l
  V <- h2omodel@allparameters$nfolds
  if (family %in% c("bernoulli", "binomial")) {
    predlist <- sapply(1:V, function(v) h2o.getFrame(h2omodel@model$cross_validation_predictions[[v]]$name)[,3], simplify = FALSE)
  } else {
    predlist <- sapply(1:V, function(v) h2o.getFrame(h2omodel@model$cross_validation_predictions[[v]]$name)$predict, simplify = FALSE)
  }
  cvpred_sparse <- h2o.cbind(predlist)  # N x V Hdf with rows that are all zeros, except corresponding to the v^th fold if that rows is associated with v
  cvpred_col <- apply(cvpred_sparse, 1, sum)
  return(cvpred_col)
}


# Extract cross-validated predicted values (in order of original rows)
h2o.cvpreds <- function(object) {

  # Need to extract family from model object
  if (class(object) == "H2OBinomialModel") family <- "binomial"
  if (class(object) == "H2OMulticlassModel") family <- "multinomial"
  if (class(object) == "H2ORegressionModel") family <- "gaussian"
    
  cvpreds <- .compress_to_cvpreds(h2omodel = object, family = family)
  return(cvpreds)
}
```
