Gradient Boosting Machine (GBM)
-------------------------------

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

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable. The data can be numeric or categorical.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  `ntrees <algo-params/ntrees.html>`__: Specify the number of trees to build.

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum tree depth. Higher values will make the model more complex and can lead to overfitting. Setting this value to 0 specifies no limit. This value defaults to 5.

-  `min_rows <algo-params/min_rows.html>`__: Specify the minimum number of observations for a leaf
   (``nodesize`` in R).

-  `nbins <algo-params/nbins.html>`__: (Numerical/real/int only) Specify the number of bins for
   the histogram to build, then split at the best point.

-  `nbins_cats <algo-params/nbins_cats.html>`__: (Categorical/enums only) Specify the maximum number
   of bins for the histogram to build, then split at the best point.
   Higher values can lead to more overfitting. The levels are ordered
   alphabetically; if there are more levels than bins, adjacent levels
   share bins. This value has a more significant impact on model fitness
   than **nbins**. Larger values may increase runtime, especially for
   deep trees and large clusters, so tuning may be required to find the
   optimal value for your configuration.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

-  `learn_rate <algo-params/learn_rate.html>`__: Specify the learning rate. The range is 0.0 to 1.0.

-  `learn_rate_annealing <algo-params/learn_rate_annealing.html>`__:  Specifies to reduce the **learn_rate** by this factor after every tree. So for *N* trees, GBM starts with **learn_rate** and ends with **learn_rate** * **learn\_rate\_annealing**^*N*. For example, instead of using **learn_rate=0.01**, you can now try **learn_rate=0.05** and **learn\_rate\_annealing=0.99**. This method would converge much faster with almost the same accuracy. Use caution not to overfit. 

-  `distribution <algo-params/distribution.html>`__: Specify the distribution (i.e., the loss function). The options are AUTO, bernoulli, multinomial, gaussian, poisson, gamma, laplace, quantile, huber, or tweedie.

  - If the distribution is ``bernoulli``, the the response column must be 2-class categorical.
  - If the distribution is ``quasibinomial``, the response column must be numeric and binary. 
  - If the distribution is ``multinomial``, the response column must be categorical.
  - If the distribution is ``poisson``, the response column must be numeric.
  - If the distribution is ``laplace``, the response column must be numeric.
  - If the distribution is ``tweedie``, the response column must be numeric.
  - If the distribution is ``gaussian``, the response column must be numeric.
  - If the distribution is ``huber``, the response column must be numeric.
  - If the distribution is ``gamma``, the response column must be numeric.
  - If the distribution is ``quantile``, the response column must be numeric.

-  `sample_rate <algo-params/sample_rate.html>`__: Specify the row sampling rate (x-axis). (Note that this method is sample without replacement.) The range is 0.0 to 1.0, and this value defaults to 1. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  `sample_rate_per_class <algo-params/sample_rate_per_class.html>`__: When building models from imbalanced datasets, this option specifies that each tree in the ensemble should sample from the full training dataset using a per-class-specific sampling rate rather than a global sample factor (as with `sample_rate`). The range for this option is 0.0 to 1.0. Note that this method is sample without replacement.

-  `col_sample_rate <algo-params/col_sample_rate.html>`__: Specify the column sampling rate (y-axis). (Note that this method is sampling without replacement.) The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).
   
-  `col_sample_rate_change_per_level <algo-params/col_sample_rate_change_per_level.html>`__: This option specifies to change the column sampling rate as a function of the depth in the tree. This can be a value > 0.0 and <= 2.0 and defaults to 1. (Note that this method is sample without replacement.) For example:
	
	  level 1: **col\_sample_rate**
	
	  level 2: **col\_sample_rate** * **factor**
	
	  level 3: **col\_sample_rate** * **factor^2**
	
	  level 4: **col\_sample_rate** * **factor^3**
	
	  etc. 

-  `col_sample_rate_per_tree <algo-params/col_sample_rate_per_tree.html>`__: Specify the column sample rate per tree. This can be a value from 0.0 to 1.0 and defaults to 1. Note that it is multiplicative with ``col_sample_rate``, so setting both parameters to 0.8, for example, results in 64% of columns being considered at any given node to split. Note that this method is sample without replacement.

-  `max_abs_leafnode_pred <algo-params/max_abs_leafnode_pred.html>`__: When building a GBM classification model, this option reduces overfitting by limiting the maximum absolute value of a leaf node prediction. This option defaults to Double.MAX_VALUE.

-  `pred_noise_bandwidth <algo-params/pred_noise_bandwidth.html>`__: The bandwidth (sigma) of Gaussian multiplicative noise ~N(1,sigma) for tree node predictions. If this parameter is specified with a value greater than 0, then every leaf node prediction is randomly scaled by a number drawn from a Normal distribution centered around 1 with a bandwidth given by this parameter. The default is 0 (disabled). 

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO``: Allow the algorithm to decide (default). In GBM, the algorithm will automatically perform ``enum`` encoding.
  - ``enum`` or ``Enum``: 1 column per categorical feature
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during Aggregator training and only keep the **T** most frequent levels.
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels
  - ``binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.)
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). This is useful in GBM/DRF, for example, when you have more levels than ``nbins_cats``, and where the top level splits now have a chance at separating the data with a split. Note that this requires a specified response column.

-  `min_split_improvement <algo-params/min_split_improvement.html>`__: The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range.  

-  `histogram_type <algo-params/histogram_type.html>`__: By default (AUTO) GBM bins from min...max in steps of (max-min)/N. Random split points or quantile-based split points can be selected as well. RoundRobin can be specified to cycle through all histogram types (one per tree). Use this option to specify the type of histogram to use for finding optimal split points:

	- AUTO
	- UniformAdaptive
	- Random
	- QuantilesGlobal
	- RoundRobin

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Specify whether to score
   during each iteration of the model training.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for **nfolds** is
   specified and **fold\_column** is not specified) Specify the
   cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random, 
   `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `score_tree_interval <algo-params/score_tree_interval.html>`__: Score the model after every so many trees.
   Disabled if set to 0.

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the
   cross-validation fold index assignment per observation.

-  `offset_column <algo-params/offset_column.html>`__: (Not applicable if the **distribution** is
   **multinomial**) Specify a column to use as the offset.
   
	 **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__. 

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation
   weights, which are used for bias correction. The specified
   ``weights_column`` must be included in the specified
   ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `balance_classes <algo-params/balance_classes.html>`__: Specify whether to oversample the minority classes to balance the class distribution. This option is not enabled by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **max\_after\_balance\_size** parameter.

-  `max_hit_ratio_k <algo-params/max_hit_ratio_k.html>`__: Specify the maximum number (top K) of
   predictions to use for hit ratio computation. Applicable to
   multi-class only. To disable, enter 0.

-  **r2\_stopping**: ``r2_stopping`` is no longer supported and will be ignored if set - please use ``stopping_rounds``, ``stopping_metric``, and ``stopping_tolerance`` instead.

-  `stopping_rounds <algo-params/stopping_rounds.html>`__: Stops training when the option selected for
   **stopping\_metric** doesn't improve for the specified number of
   training rounds, based on a simple moving average. To disable this
   feature, specify ``0``. The metric is computed on the validation data
   (if provided); otherwise, training data is used.
   
   **Note**: If cross-validation is enabled:

    - All cross-validation models stop training when the validation metric doesn't improve.
    - The main model runs for the mean number of epochs.
    - N+1 models may be off by the number specified for **stopping\_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs).

-  `stopping_metric <algo-params/stopping_metric.html>`__: Specify the metric to use for early stopping.
   The available options are:

    - ``auto``: This defaults to ``logloss`` for classification, ``deviance`` for regression
    - ``deviance``
    - ``logloss``
    - ``mse``
    - ``rmse``
    - ``mae``
    - ``rmsle``
    - ``auc``
    - ``lift_top_group``
    - ``misclassification``
    - ``mean_per_class_error``

-  `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the
   metric-based stopping to stop training if the improvement is less
   than this value.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model
   training. Use 0 to disable.

-  `build_tree_one_node <algo-params/build_tree_one_node.html>`__: To run on a single node, check this
   checkbox. This is suitable for small datasets as there is no network
   overhead but fewer CPUs are used.

-  `quantile_alpha <algo-params/quantile_alpha.html>`__: (Only applicable if *Quantile* is specified for
   **distribution**) Specify the quantile to be used for Quantile
   Regression.

-  `tweedie_power <algo-params/tweedie_power.html>`__: (Only applicable if *Tweedie* is specified for
   **distribution**) Specify the Tweedie power. The range is from 1 to
   2. For a normal distribution, enter ``0``. For Poisson distribution,
   enter ``1``. For a gamma distribution, enter ``2``. For a compound
   Poisson-gamma distribution, enter a value greater than 1 but less
   than 2. For more information, refer to `Tweedie
   distribution <https://en.wikipedia.org/wiki/Tweedie_distribution>`__.

-  `huber_alpha <algo-params/huber_alpha.html>`__: Specify the desired quantile for Huber/M-regression (the threshold between quadratic and linear loss). This value must be between 0 and 1.

-  `checkpoint <algo-params/checkpoint.html>`__: Enter a model key associated with a
   previously trained model. Use this option to build a new model as a
   continuation of a previously generated model.

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the
   cross-validation predictions.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment. 

-  `class_sampling_factors <algo-params/class_sampling_factors.html>`__: Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance. Note that this requires ``balance_classes=true``.

-  `max_after_balance_size <algo-params/max_after_balance_size.html>`__: Specify the maximum relative size of
   the training data after balancing class counts (**balance\_classes**
   must be enabled). The value can be less than 1.0.

-  `nbins_top_level <algo-params/nbins_top_level.html>`__: (For numerical/real/int columns only) Specify
   the minimum number of bins at the root level to use to build the
   histogram. This number will then be decreased by a factor of two per
   level.

-  `calibrate_model <algo-params/calibrate_model.html>`__: Use Platt scaling to calculate calibrated class probabilities. Defaults to False.

-  `calibration_frame <algo-params/calibration_frame.html>`__: Specifies the frame to be used for Platt scaling.

-  **verbose**: Print scoring history to the console. For GBM, metrics are per tree. This value defaults to FALSE.

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

Parallel Performance in GBM
~~~~~~~~~~~~~~~~~~~~~~~~~~~

GBM's parallel performance is strongly determined by the ``max_depth``, ``nbins``, ``nbins_cats`` parameters along with the number of columns. Communication overhead grows with the number of leaf node split calculations in order to find the best column to split (and where to split). More nodes will create more communication overhead, and more nodes generally only help if the data is getting so large that the extra cores are needed to compute histograms.  In general, for datasets over 10GB, it makes sense to use 2 to 4 nodes; for datasets over 100GB, it makes sense to use over 10 nodes, and so on.  

GBM Tuning Guide
~~~~~~~~~~~~~~~~
* `R <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbmTuning.Rmd>`__
* `Python <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbmTuning.ipynb>`__
* `H2O Flow <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbmTuning.flow>`__
* `Blog <http://www.h2o.ai/blog/h2o-gbm-tuning-tutorial-for-r/>`__

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
York, 2001. <http://statweb.stanford.edu/~tibs/ElemStatLearn/>`__

`Niculescu-Mizil, Alexandru and Caruana, Rich, "Predicting Good Probabilities with Supervised Learning", Ithaca, NY, 2005. <http://www.datascienceassn.org/sites/default/files/Predicting%20good%20probabilities%20with%20supervised%20learning.pdf>`__ 

`Nee, Daniel, "Calibrating Classifier Probabilities", 2014 <http://danielnee.com/tag/platt-scaling>`__

FAQ
~~~

This section describes some common questions asked by users. The questions are broken down based on one of the types below.

.. toctree::
   :maxdepth: 2

   gbm-faq/preprocessing_steps
   gbm-faq/histograms_and_binning
   gbm-faq/missing_values
   gbm-faq/default_values
   gbm-faq/build_first_tree
   gbm-faq/splitting
   gbm-faq/cross_validation
   gbm-faq/about_the_data
   gbm-faq/reproducibility
   gbm-faq/generated_metrics
   gbm-faq/scoring
   gbm-faq/tuning_a_gbm
