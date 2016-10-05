GBM
--------------
Introduction
~~~~~~~~~~~~

Gradient Boosting Machine (for Regression and Classification) is a forward learning ensemble method. The guiding heuristic is that good predictive results can be obtained through increasingly refined approximations. H2O's GBM sequentially builds regression trees on all the features of the dataset in a fully distributed way - each tree is built in parallel.

The current version of GBM is fundamentally the same as in previous
versions of H2O (same algorithmic steps, same histogramming techniques),
with the exception of the following changes:

-  Improved ability to train on categorical variables (using the
   ``nbins_cats`` parameter)
-  Minor changes in histogramming logic for some corner cases

There was some code cleanup and refactoring to support the following
features:

-  Per-row observation weights
-  Per-row offsets
-  N-fold cross-validation
-  Support for more distribution functions (such as Gamma, Poisson, and
   Tweedie)

Quick Start
~~~~~~~~~~~~
* Quick GBM using H2O Flow (Lending Club Dataset) `[Youtube] <https://www.youtube.com/watch?v=1R9iBBCxhE8>`__
* Simplest getting started R script `[Github] <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/gbm-randomforest/GBM_RandomForest_Example.R>`__
* GBM & Random Forest Video Overview `[Youtube] <https://www.youtube.com/watch?v=9wn1f-30_ZY>`__
* GBM and other algos in R (Citi Bike Dataset) `[Youtube] <https://www.youtube.com/watch?v=_ig6ZmBfhH8/>`__ `[Github] <https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/rdemo.citi.bike.small.R/>`__ 
* Prof. Trevor Hasite - Gradient Boosting Machine Learning `[Youtube] <https://www.youtube.com/watch?v=wPqtzj5VZus/>`__

Defining a GBM Model
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
   independent variable. The data can be numeric or categorical.

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

-  **ignore\_const\_cols**: Specify whether to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  `ntrees <gbm-params/ntrees.html>`__: Specify the number of trees to build.

-  `max_depth <gbm-params/max_depth.html>`__: Specify the maximum tree depth.

-  **min\_rows**: Specify the minimum number of observations for a leaf
   (``nodesize`` in R).

-  `nbins <gbm-params/nbins.html>`__: (Numerical/real/int only) Specify the number of bins for
   the histogram to build, then split at the best point.

-  `nbins_cats <gbm-params/nbins_cats.html>`__: (Categorical/enums only) Specify the maximum number
   of bins for the histogram to build, then split at the best point.
   Higher values can lead to more overfitting. The levels are ordered
   alphabetically; if there are more levels than bins, adjacent levels
   share bins. This value has a more significant impact on model fitness
   than **nbins**. Larger values may increase runtime, especially for
   deep trees and large clusters, so tuning may be required to find the
   optimal value for your configuration.

-  **seed**: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

-  **learn\_rate**: Specify the learning rate. The range is 0.0 to 1.0.

-  **learn\_rate\_annealing**:  Specifies to reduce the **learn_rate** by this factor after every tree. So for *N* trees, GBM starts with **learn_rate** and ends with **learn_rate** * **learn\_rate\_annealing**^*N*. For example, instead of using **learn_rate=0.01**, you can now try **learn_rate=0.05** and **learn\_rate\_annealing=0.99**. This method would converge much faster with almost the same accuracy. Use caution not to overfit. 

-  **distribution**: Specify the distribution (i.e., the loss function). The options are AUTO, bernoulli, multinomial, gaussian, poisson, gamma, laplace, quantile, huber, or tweedie.

       -  If the distribution is **multinomial**, the response column
          must be categorical.
       -  If the distribution is **poisson**, the response column must
          be numeric.
       -  If the distribution is **gamma**, the response column must be
          numeric.
       -  If the distribution is **tweedie**, the response column must
          be numeric.
       -  If the distribution is **gaussian**, the response column must
          be numeric.
       -  If the distribution is **huber**, the response column must
          be numeric.
       -  If the distribution is **gamma**, the response column must be
          numeric.
       -  If the distribution is **quantile**, the response column must
          be numeric.
          

-  **sample\_rate**: Specify the row sampling rate (x-axis). The range
   is 0.0 to 1.0. Higher values may improve training accuracy. Test
   accuracy improves when either columns or rows are sampled. For
   details, refer to "Stochastic Gradient Boosting" (`Friedman,
   1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  **sample\_rate\_per\_class**: When building models from imbalanced datasets, this option specifies that each tree in the ensemble should sample from the full training dataset using a per-class-specific sampling rate rather than a global sample factor (as with `sample_rate`). The range for this option is 0.0 to 1.0. If this option is specified along with **sample_rate**, then only the first option that GBM encounters will be used.

-  **col\_sample\_rate**: Specify the column sampling rate (y-axis). The
   range is 0.0 to 1.0. Higher values may improve training accuracy.
   Test accuracy improves when either columns or rows are sampled. For
   details, refer to "Stochastic Gradient Boosting" (`Friedman,
   1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).
   
-  **col\_sample_rate\_change\_per\_level**: This option specifies to change the column sampling rate as a function of the depth in the tree. For example:
	
	  level 1: **col\_sample_rate**
	
	  level 2: **col\_sample_rate** * **factor**
	
	  level 3: **col\_sample_rate** * **factor^2**
	
	  level 4: **col\_sample_rate** * **factor^3**
	
	  etc. 

-  **max\_abs\_leafnode\_pred**: When building a GBM classification model, this option reduces overfitting by limiting the maximum absolute value of a leaf node prediction. This option defaults to Double.MAX_VALUE.

-  **pred\_noise\_bandwidth**: The bandwidth (sigma) of Gaussian multiplicative noise ~N(1,sigma) for tree node predictions. If this parameter is specified with a value greater than 0, then every leaf node prediction is randomly scaled by a number drawn from a Normal distribution centered around 1 with a bandwidth given by this parameter. The default is 0 (disabled). 

-  **min\_split\_improvement**: The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range.  

-  **random\_split_points**: By default GBM bins from min...max in steps of (max-min)/N. When this option is enabled, GBM will instead sample N-1 points from min...max and use the sorted list of those for split finding.

-  **histogram_type**: By default (AUTO) GBM bins from min...max in steps of (max-min)/N. Random split points or quantile-based split points can be selected as well. RoundRobin can be specified to cycle through all histogram types (one per tree). Use this option to specify the type of histogram to use for finding optimal split points:

	- AUTO
	- UniformAdaptive
	- Random
	- QuantilesGlobal
	- RoundRobin

-  **score\_each\_iteration**: (Optional) Specify whether to score
   during each iteration of the model training.

-  **fold\_assignment**: (Applicable only if a value for **nfolds** is
   specified and **fold\_column** is not specified) Specify the
   cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random, 
   `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  **score\_tree\_interval**: Score the model after every so many trees.
   Disabled if set to 0.

-  **fold\_column**: Specify the column that contains the
   cross-validation fold index assignment per observation.

-  **offset\_column**: (Not applicable if the **distribution** is
   **multinomial**) Specify a column to use as the offset.
   
	**Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__. If the **distribution** is **Bernoulli**, the value must be less than one.

-  **weights\_column**: Specify a column to use for the observation
   weights, which are used for bias correction. The specified
   ``weights_column`` must be included in the specified
   ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `balance_classes <gbm-params/balance_classes.html>`__: Specify whether to oversample the minority classes to balance the class distribution. This option is not enabled by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **max\_after\_balance\_size** parameter.

-  **max\_confusion\_matrix\_size**: Specify the maximum size (in number
   of classes) for confusion matrices to be printed in the Logs.

-  `max_hit_ratio_k <gbm-params/max_hit_ratio_k.html>`__: Specify the maximum number (top K) of
   predictions to use for hit ratio computation. Applicable to
   multi-class only. To disable, enter 0.

-  **r2\_stopping**: Specify a threshold for the coefficient of
   determination ((r^2)) metric value. When this threshold is met or
   exceeded, H2O stops making trees.

-  **stopping\_rounds**: Stops training when the option selected for
   **stopping\_metric** doesn't improve for the specified number of
   training rounds, based on a simple moving average. To disable this
   feature, specify ``0``. The metric is computed on the validation data
   (if provided); otherwise, training data is used. When used with
   **overwrite\_with\_best\_model**, the final model is the best model
   generated for the given **stopping\_metric** option. 
   
   **Note**: If cross-validation is enabled:

    1. All cross-validation models stop training when the validation metric doesn't improve.
    2. The main model runs for the mean number of epochs.
    3. N+1 models do *not* use **overwrite\_with\_best\_model**
    4. N+1 models may be off by the number specified for **stopping\_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs).

-  **stopping\_metric**: Specify the metric to use for early stopping.
   The available options are:

   -  **AUTO**: Logloss for classification, deviance for regression
   -  **deviance**
   -  **logloss**
   -  **MSE**
   -  **AUC**
   -  **r2**
   -  **misclassification**

-  **stopping\_tolerance**: Specify the relative tolerance for the
   metric-based stopping to stop training if the improvement is less
   than this value.

-  **max\_runtime\_secs**: Maximum allowed runtime in seconds for model
   training. Use 0 to disable.

-  **build\_tree\_one\_node**: To run on a single node, check this
   checkbox. This is suitable for small datasets as there is no network
   overhead but fewer CPUs are used.

-  **quantile\_alpha**: (Only applicable if *Quantile* is specified for
   **distribution**) Specify the quantile to be used for Quantile
   Regression.

-  **tweedie\_power**: (Only applicable if *Tweedie* is specified for
   **distribution**) Specify the Tweedie power. The range is from 1 to
   2. For a normal distribution, enter ``0``. For Poisson distribution,
   enter ``1``. For a gamma distribution, enter ``2``. For a compound
   Poisson-gamma distribution, enter a value greater than 1 but less
   than 2. For more information, refer to `Tweedie
   distribution <https://en.wikipedia.org/wiki/Tweedie_distribution>`__.

-  **huber\_alpha**: Specify the desired quantile for Huber/M-regression (the threshold between quadratic and linear loss). This value must be between 0 and 1.

-  **checkpoint**: Enter a model key associated with a
   previously-trained model. Use this option to build a new model as a
   continuation of a previously-generated model.

-  **keep\_cross\_validation\_predictions**: Enable this option to keep the
   cross-validation predictions.

-  **keep\_cross\_validation\_fold\_assignment**: Enable this option to preserve the cross-validation fold assignment. 

-  `class_sampling_factors <gbm-params/class_sampling_factors.html>`__: Specify the per-class (in
   lexicographical order) over/under-sampling ratios. By default, these
   ratios are automatically computed during training to obtain the class
   balance.

-  `max_after_balance_size <gbm-params/max_after_balance_size.html>`__: Specify the maximum relative size of
   the training data after balancing class counts (**balance\_classes**
   must be enabled). The value can be less than 1.0.

-  `nbins_top_level <gbm-params/nbins_top_level.html>`__: (For numerical/real/int columns only) Specify
   the minimum number of bins at the root level to use to build the
   histogram. This number will then be decreased by a factor of two per
   level.

Interpreting a GBM Model
~~~~~~~~~~~~~~~~~~~~~~~~

The output for GBM includes the following:

-  Model parameters (hidden)
-  A graph of the scoring history (training MSE vs number of trees)
-  A graph of the variable importances
-  Output (model category, validation metrics, initf)
-  Model summary (number of trees, min. depth, max. depth, mean depth,
   min. leaves, max. leaves, mean leaves)
-  Scoring history in tabular format
-  Training metrics (model name, model checksum name, frame name,
   description, model category, duration in ms, scoring time,
   predictions, MSE, R2)
-  Variable importances in tabular format

Leaf Node Assignment
~~~~~~~~~~~~~~~~~~~~

Trees cluster observations into leaf nodes, and this information can be
useful for feature engineering or model interpretability. Use
**h2o.predict\_leaf\_node\_assignment(model, frame)** to get an H2OFrame
with the leaf node assignments, or click the checkbox when making
predictions from Flow. Those leaf nodes represent decision rules that
can be fed to other models (i.e., GLM with lambda search and strong
rules) to obtain a limited set of the most important rules.

FAQ
~~~

-  **How does the algorithm handle missing values during training?**

  Missing values are interpreted as containing information (i.e., missing for a reason), rather than missing at random. During tree building, split decisions for every node are found by minimizing the loss function and treating missing values as a separate category that can go either left or right.

-  **How does the algorithm handle missing values during testing?**

  During scoring, missing values follow the optimal path that was determined for them during training (minimized loss function).

-  **What happens if the response has missing values?**

  No errors will occur, but nothing will be learned from rows containing missing the response.

-  **What happens when you try to predict on a categorical level not
   seen during training?**

  GBM converts a new categorical level to an "undefined" value in the test set, and then splits either left or right during scoring. 

-  **Does it matter if the data is sorted?**

  No.

-  **Should data be shuffled before training?**

  No.

-  **How does the algorithm handle highly imbalanced data in a response
   column?**

  You can specify ``balance_classes``, ``class_sampling_factors`` and ``max_after_balance_size`` to control over/under-sampling.

-  **What if there are a large number of columns?**

  GBM models are best for datasets with fewer than a few thousand columns.

-  **What if there are a large number of categorical factor levels?**

  Large numbers of categoricals are handled very efficiently - there is never any one-hot encoding.

-  **Given the same training set and the same GBM parameters, will GBM
   produce a different model with two different validation data sets, or
   the same model?**

  Unless early stopping is turned on (it's disabled by default), then supplying two different validation sets will not change the model, resulting in the same model for both trials. However, if early stopping is turned on and two different validation sets are provided during the training process, that can lead to two different models. The use of a validation set in combination with early stopping can cause the model to stop training earlier (or later), depending on the validation set. Early stopping uses the validation set to determine when to stop building more trees. 

-  **How deterministic is GBM?**

  As long as you set the seed, GBM is deterministic up to floating point rounding errors (out-of-order atomic addition of multiple threads during histogram building). This means that if you set a seed, your results will be reproducible even if, for example, you change the number of nodes in your cluster, change the way you ingest data, or change the number of files your data lives in, among many other examples.

-  **When fitting a random number between 0 and 1 as a single feature,
   the training ROC curve is consistent with ``random`` for low tree
   numbers and overfits as the number of trees is increased, as
   expected. However, when a random number is included as part of a set
   of hundreds of features, as the number of trees increases, the random
   number increases in feature importance. Why is this?**

  This is a known behavior of GBM that is similar to its behavior in R. If, for example, it takes 50 trees to learn all there is to learn from a frame without the random features, when you add a random predictor and train 1000 trees, the first 50 trees will be approximately the same. The final 950 trees are used to make sense of the random number, which will take a long time since there's no structure. The variable importance will reflect the fact that all the splits from the first 950 trees are devoted to the random feature.

-  **How is column sampling implemented for GBM?**

  For an example model using:

   -  100 columns
   -  ``col_sample_rate_per_tree=0.754``
   -  ``col_sample_rate=0.8`` (refers to available columns after per-tree sampling)

  For each tree, the floor is used to determine the number - in this example, (0.754 * 100)=75 out of the 100 - of columns that are randomly picked, and then the floor is used to determine the number - in this case, (0.754 * 0.8 * 100)=60 - of columns that are then randomly chosen for each split decision (out of the 75).

- **I want to score multiple models on a huge dataset. Is it possible to score these models in parallel?**

 The best way to score models in parallel is to use the in-H2O binary models. To do this, import the binary (non-POJO, previously exported) model into an H2O cluster; import the datasets into H2O as well; call the predict endpoint either from R, Python, Flow or the REST API directly; then export the predictions to file or download them from the server.
 
- **Are there any tutorials for GBM?**

 You can find tutorials for using GBM with R, Python, and Flow at the following location: https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/product/tutorials/gbm. 


GBM Algorithm
~~~~~~~~~~~~~

H2O's Gradient Boosting Algorithms follow the algorithm specified by
Hastie et al (2001):

Initialize :math:`f_{k0} = 0, k=1,2,…,K`

For :math:`m=1` to :math:`M`:

1. Set :math:`p_{k}(x)=\frac{e^{f_{k}(x)}}{\sum_{l=1}^{K}e^{f_{l}(x)}},k=1,2,…,K`

2. For :math:`k=1` to :math:`K`:

	a. Compute :math:`r_{ikm}=y_{ik}-p_{k}(x_{i}),i=1,2,…,N`
	
	b. Fit a regression tree to the targets :math:`r_{ikm},i=1,2,…,N`, giving terminal regions :math:`R_{jim},j=1,2,…,J_{m}`
	
	c. Compute :math:`\gamma_{jkm}=\frac{K-1}{K} \frac{\sum_{x_{i} \in R_{jkm}}(r_{ikm})}{\sum_{x_{i} \in R_{jkm}}|r_{ikm}|(1-|r_{ikm})},j=1,2,…,J_m`.
	
	d. Update :math:`f_{km}(x)=f_{k,m-1}(x)+\sum_{j=1}^{J_m}\gamma_{jkm} I(x\in R_{jkm})`.

Output :math:`\hat{f_{k}}(x)=f_{kM}(x),k=1,2,…,K`

Be aware that the column type affects how the histogram is created and
the column type depends on whether rows are excluded or assigned a
weight of 0. For example:

val weight 1 1 0.5 0 5 1 3.5 0

The above vec has a real-valued type if passed as a whole, but if the
zero-weighted rows are sliced away first, the integer weight is used.
The resulting histogram is either kept at full ``nbins`` resolution or
potentially shrunk to the discrete integer range, which affects the
split points.

For more information about the GBM algorithm, refer to the `Gradient
Boosting Machine booklet <http://h2o.ai/resources>`__.

Binning In GBM
~~~~~~~~~~~~~~

**Is the binning range-based or percentile-based?**

It's range based, and re-binned at each tree split. NAs always "go to
the left" (smallest) bin. There's a minimum observations required value
(default 10). There has to be at least 1 FP ULP improvement in error to
split (all-constant predictors won't split). nbins is at least 1024 at
the top-level, and divides by 2 down each level until you hit the nbins
parameter (default: 20). Categoricals use a separate, more aggressive,
binning range.

Re-binning means, eg, suppose your column C1 data is:
{1,1,2,4,8,16,100,1000}. Then a 20-way binning will use the range from 1
to 1000, bin by units of 50. The first binning will be a lumpy:
{1,1,2,4,8,16},{100},{47\_empty\_bins},{1000}. Suppose the split peels
out the {1000} bin from the rest.

Next layer in the tree for the left-split has value from 1 to 100 (not
1000!) and so re-bins in units of 5: {1,1,2,4},{8},{},{16},{lots of
empty bins}{100} (the RH split has the single value 1000).

And so on: important dense ranges with split essentially logarithmically
at each layer.

**What should I do if my variables are long skewed in the tail and might
have large outliers?**

You can try adding a new predictor column which is either pre-binned
(e.g. as a categorical - "small", "median", and "giant" values), or a
log-transform - plus keep the old column.

GBM Tuning Guide
~~~~~~~~~~~~~~~~
* `R <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbmTuning.Rmd>`__
* `Python <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbmTuning.ipynb>`__
* `H2O Flow <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbmTuning.flow>`__
* `Blog <http://blog.h2o.ai/2016/06/h2o-gbm-tuning-tutorial-for-r/>`__

References
~~~~~~~~~~

Dietterich, Thomas G, and Eun Bae Kong. "Machine Learning Bias,
Statistical Bias, and Statistical Variance of Decision Tree Algorithms."
ML-95 255 (1995).

Elith, Jane, John R Leathwick, and Trevor Hastie. "A Working Guide to
Boosted Regression Trees." Journal of Animal Ecology 77.4 (2008):
802-813

Friedman, Jerome H. "Greedy Function Approximation: A Gradient Boosting
Machine." Annals of Statistics (2001): 1189-1232.

Friedman, Jerome, Trevor Hastie, Saharon Rosset, Robert Tibshirani, and
Ji Zhu. "Discussion of Boosting Papers." Ann. Statist 32 (2004): 102-107

`Friedman, Jerome, Trevor Hastie, and Robert Tibshirani. "Additive
Logistic Regression: A Statistical View of Boosting (With Discussion and
a Rejoinder by the Authors)." The Annals of Statistics 28.2 (2000):
337-407 <http://projecteuclid.org/DPubS?service=UI&version=1.0&verb=Display&handle=euclid.aos/1016218223>`__

`Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The
Elements of Statistical Learning. Vol.1. N.p., page 339: Springer New
York,
2001. <http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf>`__
