Distributed Random Forest (DRF)
-------------------------------

Introduction
~~~~~~~~~~~~

Distributed Random Forest (DRF) is a powerful classification and regression tool. When given a set of data, DRF generates a forest of classification or regression trees, rather than a single classification or regression tree. Each of these trees is a weak learner built on a subset of rows and columns. More trees will reduce the variance. Both classification and regression take the average prediction over all of their trees to make a final prediction, whether predicting for a class or numeric value. (Note: For a categorical response column, DRF maps factors  (e.g. 'dog', 'cat', 'mouse) in lexicographic order to a name lookup array with integer indices (e.g. 'cat -> 0, 'dog' -> 1, 'mouse' -> 2.)

The current version of DRF is fundamentally the same as in previous versions of H2O (same algorithmic steps, same histogramming techniques), with the exception of the following changes:

-  Improved ability to train on categorical variables (using the ``nbins_cats`` parameter)
-  Minor changes in histogramming logic for some corner cases
-  By default, DRF builds half as many trees for binomial problems, similar to GBM: it uses a single tree to estimate class 0 (probability "p0"), and then computes the probability of class 0 as :math:`1.0 - p0`.  For multiclass problems, a tree is used to estimate the probability of each class separately.

There was some code cleanup and refactoring to support the following features:

-  Per-row observation weights
-  N-fold cross-validation

DRF no longer has a special-cased histogram for classification (class DBinomHistogram has been superseded by DRealHistogram) since it was not applicable to cases with observation weights or for cross-validation.

.. _xrt:

Extremely Randomized Trees
~~~~~~~~~~~~~~~~~~~~~~~~~~

In random forests, a random subset of candidate features is used to determine the most discriminative thresholds that are picked as the splitting rule. In extremely randomized trees (XRT), randomness goes one step further in the way that splits are computed. As in random forests, a random subset of candidate features is used, but instead of looking for the most discriminative thresholds, thresholds are drawn at random for each candidate feature, and the best of these randomly generated thresholds is picked as the splitting rule. This usually allows to reduce the variance of the model a bit more, at the expense of a slightly greater increase in bias.

H2O supports extremely randomized trees (XRT) via ``histogram_type="Random"``. When this is specified, the algorithm will sample N-1 points from min...max and use the sorted list of those to find the best split. The cut points are random rather than uniform. For example, to generate 4 bins for some feature ranging from 0-100, 3 random numbers would be generated in this range (13.2, 89.12, 45.0). The sorted list of these random numbers forms the histogram bin boundaries e.g. (0-13.2, 13.2-45.0, 45.0-89.12, 89.12-100).

Defining a DRF Model
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

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the cross-validation prediction.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Enable this option to score
   during each iteration of the model training.

-  `score_tree_interval <algo-params/score_tree_interval.html>`__: Score the model after every so many trees.
   Disabled if set to 0.

-  `fold_assignment <algo-params/fold_assignment.html>`_-: (Applicable only if a value for **nfolds** is
   specified and **fold\_column** is not specified) Specify the
   cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random, 
   `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the
   cross-validation fold index assignment per observation.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column
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

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation
   weights, which are used for bias correction. The specified
   ``weights_column`` must be included in the specified
   ``training_frame``. 
   
    **Python only**: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
    
   | **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `balance_classes <algo-params/balance_classes.html>`__: Oversample the minority classes to balance the
   class distribution. This option is not enabled by default and can
   increase the data frame size. This option is only applicable for
   classification.

-  `class_sampling_factors <algo-params/class_sampling_factors.html>`__: Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance. Note that this requires ``balance_classes=true``.

-  `max_after_balance_size <algo-params/max_after_balance_size.html>`__: Specify the maximum relative size of
   the training data after balancing class counts (**balance\_classes**
   must be enabled). The value can be less than 1.0.

-  `max_hit_ratio_k <algo-params/max_hit_ratio_k.html>`__: Specify the maximum number (top K) of
   predictions to use for hit ratio computation. Applicable to
   multi-class only. To disable, enter 0.

-  `ntrees <algo-params/ntrees.html>`__: Specify the number of trees.

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum tree depth. Higher values will make the model more complex and can lead to overfitting. Setting this value to 0 specifies no limit. This value defaults to 20. 

-  `min_rows <algo-params/min_rows.html>`__: Specify the minimum number of observations for a leaf
   (``nodesize`` in R).

-  `nbins <algo-params/nbins.html>`__: (Numerical/real/int only) Specify the number of bins for
   the histogram to build, then split at the best point.

-  `nbins_top_level <algo-params/nbins_top_level.html>`__: (For numerical/real/int columns only) Specify
   the minimum number of bins at the root level to use to build the
   histogram. This number will then be decreased by a factor of two per
   level.

-  `nbins_cats <algo-params/nbins_cats.html>`__: (Categorical/enums only) Specify the maximum number
   of bins for the histogram to build, then split at the best point.
   Higher values can lead to more overfitting. The levels are ordered
   alphabetically; if there are more levels than bins, adjacent levels
   share bins. This value has a more significant impact on model fitness
   than **nbins**. Larger values may increase runtime, especially for
   deep trees and large clusters, so tuning may be required to find the
   optimal value for your configuration.

-  **r2\_stopping**: ``r2_stopping`` is no longer supported and will be ignored if set - please use ``stopping_rounds``, ``stopping_metric``, and ``stopping_tolerance`` instead.

-  `stopping_rounds <algo-params/stopping_rounds.html>`_-: Stops training when the option selected for
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

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

-  `build_tree_one_node <algo-params/build_tree_one_node.html>`__: To run on a single node, check this
   checkbox. This is suitable for small datasets as there is no network
   overhead but fewer CPUs are used.

-  `mtries <algo-params/mtries.html>`__: Specify the columns to randomly select at each level. If
   the default value of ``-1`` is used, the number of variables is the
   square root of the number of columns for classification and p/3 for
   regression (where p is the number of predictors). The range is -1 to
   >=1.

-  `sample_rate <algo-params/sample_rate.html>`__: Specify the row sampling rate (x-axis). (Note that this method is sample without replacement.) The range is 0.0 to 1.0, and this value defaults to 0.6320000291. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  `sample_rate_per_class <algo-params/sample_rate_per_class.html>`__: When building models from imbalanced datasets, this option specifies that each tree in the ensemble should sample from the full training dataset using a per-class-specific sampling rate rather than a global sample factor (as with `sample_rate`). The range for this option is 0.0 to 1.0. Note that this method is sample without replacement.

-  `binomial_double_trees <algo-params/binomial_double_trees.html>`__: (Binary classification only) Build twice
   as many trees (one per class). Enabling this option can lead to
   higher accuracy, while disabling can result in faster model building.
   This option is disabled by default.

-  `checkpoint <algo-params/checkpoint.html>`__: Enter a model key associated with a
   previously trained model. Use this option to build a new model as a
   continuation of a previously generated model.

-  `col_sample_rate_change_per_level <algo-params/col_sample_rate_change_per_level.html>`__: This option specifies to change the column sampling rate as a function of the depth in the tree. This can be a value > 0.0 and <= 2.0 and defaults to 1. (Note that this method is sample without replacement.) For example:

   level 1: **col\_sample_rate**
  
   level 2: **col\_sample_rate** * **factor**
  
   level 3: **col\_sample_rate** * **factor^2**
  
   level 4: **col\_sample_rate** * **factor^3**
  
   etc.

-  `col_sample_rate_per_tree <algo-params/col_sample_rate_per_tree.html>`__: Specify the column sample rate per tree. This can be a value from 0.0 to 1.0 and defaults to 1. Note that this method is sample without replacement.

-  `min_split_improvement <algo-params/min_split_improvement.html>`__: The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range.

-  `histogram_type <algo-params/histogram_type.html>`__: By default (AUTO) DRF bins from min...max in steps of (max-min)/N. Random split points or quantile-based split points can be selected as well. RoundRobin can be specified to cycle through all histogram types (one per tree). Use this option to specify the type of histogram to use for finding optimal split points:

	- AUTO
	- UniformAdaptive
	- Random
	- QuantilesGlobal
	- RoundRobin

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO``: Allow the algorithm to decide (default). In DRF, the algorithm will automatically perform ``enum`` encoding.
  - ``enum`` or ``Enum``: 1 column per categorical feature
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during Aggregator training and only keep the **T** most frequent levels.
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.)
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). This is useful in GBM/DRF, for example, when you have more levels than ``nbins_cats``, and where the top level splits now have a chance at separating the data with a split. Note that this requires a specified response column.

-  `calibrate_model <algo-params/calibrate_model.html>`__: Use Platt scaling to calculate calibrated class probabilities. Defaults to False.

-  `calibration_frame <algo-params/calibration_frame.html>`__: Specifies the frame to be used for Platt scaling.

-  **verbose**: Print scoring history to the console. For DRF, metrics are per tree. This value defaults to FALSE.

Interpreting a DRF Model
~~~~~~~~~~~~~~~~~~~~~~~~

By default, the following output displays:

-  Model parameters (hidden)
-  A graph of the scoring history (number of trees vs. training MSE)
-  A graph of the `ROC curve <https://en.wikipedia.org/wiki/Receiver_operating_characteristic>`__ (TPR vs. FPR)
-  A graph of the variable importances
-  Output (model category, validation metrics, initf)
-  Model summary (number of trees, min. depth, max. depth, mean depth,
   min. leaves, max. leaves, mean leaves)
-  Scoring history in tabular format
-  Training metrics (model name, checksum name, frame name, frame
   checksum name, description, model category, duration in ms, scoring
   time, predictions, MSE, R2, logloss, AUC, GINI)
-  Training metrics for thresholds (thresholds, F1, F2, F0Points,
   Accuracy, Precision, Recall, Specificity, Absolute MCC, min.
   per-class accuracy, TNS, FNS, FPS, TPS, IDX)
-  Maximum metrics (metric, threshold, value, IDX)
-  Variable importances in tabular format

Leaf Node Assignment
~~~~~~~~~~~~~~~~~~~~

Trees cluster observations into leaf nodes, and this information can be
useful for feature engineering or model interpretability. Use
**h2o.predict\_leaf\_node\_assignment(** *model*, *frame* **)** to get an H2OFrame
with the leaf node assignments, or click the **Compute Leafe Node Assignment** checkbox when making
predictions from Flow. Those leaf nodes represent decision rules that
can be fed to other models (i.e., GLM with lambda search and strong
rules) to obtain a limited set of the most important rules. 

FAQ
~~~

-  **How does the algorithm handle missing values during training?**

  Missing values are interpreted as containing information (i.e., missing for a reason), rather than missing at random. During tree building, split decisions for every node are found by minimizing the loss function and treating missing values as a separate category that can go either left or right.

  **Note**: Unlike in GLM, in DRF numerical values are handled the same way as categorical values. Missing values are not imputed with the mean, as is done by default in GLM.

-  **How does the algorithm handle missing values during testing?**

  During scoring, missing values follow the optimal path that was determined for them during training (minimized loss function).

-  **What happens if the response has missing values?**

  No errors will occur, but nothing will be learned from rows containing missing values in the response column.

-  **What happens when you try to predict on a categorical level not
   seen during training?**

  Unseen categorical levels are turned into NAs, and thus follow the same behavior as an NA. If there are no NAs in the training data, then unseen categorical levels in the test data follow the majority direction (the direction with the most observations). If there are NAs in the training data, then unseen categorical levels in the test data follow the direction that is optimal for the NAs of the training data.

-  **Does it matter if the data is sorted?**

  No.

-  **Should data be shuffled before training?**

  No.

-  **How does the algorithm handle highly imbalanced data in a response
   column?**

  Specify ``balance_classes``, ``class_sampling_factors`` and ``max_after_balance_size`` to control over/under-sampling.

-  **What if there are a large number of columns?**

  DRFs are best for datasets with fewer than a few thousand columns.

-  **What if there are a large number of categorical factor levels?**

  Large numbers of categoricals are handled very efficiently - there is never any one-hot encoding.

- **Does the algo stop splitting when all the possible splits lead to worse error measures?**

 It does if you use ``min_split_improvement`` (min_split_improvement turned ON by default (0.00001).) When properly tuned, this option can help reduce overfitting. 

- **When does the algo stop splitting on an internal node?**

 A single tree will stop splitting when there are no more splits that satisfy the minimum rows parameter, if it reaches ``max_depth``, or if there are no splits that satisfy the ``min_split_improvement`` parameter.

- **How does DRF decide which feature to split on?**

 It splits on the column and level that results in the greatest reduction in residual sum of the squares (RSS) in the subtree at that point. It considers all fields available from the algorithm. Note that any use of column sampling and row sampling will cause each decision to not consider all data points, and that this is on purpose to generate more robust trees. To find the best level, the histogram binning process is used to quickly compute the potential MSE of each possible split. The number of bins is controlled via ``nbins_cats`` for categoricals, the pair of ``nbins`` (the number of bins for the histogram to build, then split at the best point), and ``nbins_top_level`` (the minimum number of bins at the root level to use to build the histogram). This number will then be decreased by a factor of two per level. 

 For ``nbins_top_level``, higher = more precise, but potentially more prone to overfitting. Higher also takes more memory and possibly longer to run.

- **What is the difference between nbins and nbins_top_level?**

 ``nbins`` and ``nbins_top_level`` are both for numerics (real and integer). ``nbins_top_level`` is the number of bins DRF uses at the top of each tree. It then divides by 2 at each ensuing level to find a new number. ``nbins`` controls when DRF stops dividing by 2.

-  **How is variable importance calculated for DRF?**

 Variable importance is determined by calculating the relative influence of each variable: whether that variable was selected during splitting in the tree building process and how much the squared error (over all trees) improved as a result.

-  **How is column sampling implemented for DRF?**

  For an example model using:

  - 100 columns
  - ``col_sample_rate_per_tree`` is 0.602
  - ``mtries`` is -1 or 7 (refers to the number of active predictor columns for the dataset)

  For each tree, the floor is used to determine the number of columns that are randomly picked (for this example, (0.602*100)=60 out of the 100 columns). 

  For classification cases where ``mtries=-1``, the square root is randomly chosen for each split decision (out of the total 60 - for this example, (:math:`\sqrt{100}` = 10 columns).

  For regression, the floor  is used for each split by default (in this example, (100/3)=33 columns). If ``mtries=7``, then 7 columns are picked for each split decision (out of the 60).

  ``mtries`` is configured independently of ``col_sample_rate_per_tree``, but it can be limited by it. For example, if ``col_sample_rate_per_tree=0.01``, then there’s only one column left for each split, regardless of how large the value for ``mtries`` is.

-  **Why does performance appear slower in DRF than in GBM?**

  With DRF, depth and size of trees can result in speed tradeoffs.

  By default, DRF will go to depth 20, which can lead to up to 1+2+4+8+…+2^19 ~ 1M nodes to be split, and for every one of them, mtries=sqrt(4600)=67 columns need to be considered for splitting. This results in a total work of finding up to 1M*67 ~ 67M split points per tree. Usually, many of the leaves don’t go to depth 20, so the actual number is less. (You can inspect the model to see that value.)

  By default, GBM will go to depth 5, so only 1+2+4+8+16 = 31 nodes to be split, and for every one of them, all 4600 columns need to be considered. This results in a total work of finding up to 31*4600 ~ 143k split points (often all are needed) per tree.

  This is why the shallow depth of GBM is one of the reasons it’s great for wide (for tree purposes) datasets. To make DRF faster, consider decreasing max_depth and/or mtries and/or ntrees.

  For both algorithms, finding one split requires a pass over one column and all rows. Assume a dataset with 250k rows and 500 columns. GBM can take minutes minutes, while DRF may take hours. This is because:

  - Assuming the above, GBM needs to pass over up to 31\*500\*250k = 4 billion numbers per tree, and assuming 50 trees, that’s up to (typically equal to) 200 billion numbers in 11 minutes, or 300M per second, which is pretty fast.

  - DRF needs to pass over up to 1M\*22\*250k = 5500 billion numbers per tree, and assuming 50 trees, that’s up to 275 trillion numbers, which can take a few hours


DRF Algorithm
~~~~~~~~~~~~~

.. image:: http://image.slidesharecdn.com/rfbrighttalk-140522173736-phpapp02/95/building-random-forest-at-scale-1-638.jpg?cb=1400782751.png
   :width: 425px
   :height: 355px
   :target: http://www.slideshare.net/0xdata/rf-brighttalk


`Building Random Forest at Scale <http://www.slideshare.net/0xdata/rf-brighttalk>`_ from Sri Ambati

References
~~~~~~~~~~

`P. Geurts, D. Ernst., and L. Wehenkel, "Extremely randomized trees", Machine Learning, 63(1), 3-42, 2006. <http://link.springer.com/article/10.1007%2Fs10994-006-6226-1>`_

`Niculescu-Mizil, Alexandru and Caruana, Rich, "Predicting Good Probabilities with Supervised Learning", Ithaca, NY, 2005. <http://www.datascienceassn.org/sites/default/files/Predicting%20good%20probabilities%20with%20supervised%20learning.pdf>`__ 

`Nee, Daniel, "Calibrating Classifier Probabilities", 2014 <http://danielnee.com/tag/platt-scaling>`__
