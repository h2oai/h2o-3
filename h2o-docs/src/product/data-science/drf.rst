DRF
--------------

Introduction
~~~~~~~~~~~~

Distributed Random Forest (DRF) is a powerful classification and regression tool. When given a set of data, DRF generates a forest of classification (or regression) trees, rather than a single classification (or regression) tree. Each of these trees is a weak
learner built on a subset of rows and columns. More trees will reduce
the variance. Both classification and regression take the average prediction over all of their trees to make a final prediction, whether predicting for a class or numeric value (note: for a categorical response column, DRF maps factors  (e.g. 'dog', 'cat', 'mouse) in lexicographic order to a name lookup array with integer indices (e.g. 'cat -> 0, 'dog' -> 1, 'mouse' -> 2).

The current version of DRF is fundamentally the same as in previous
versions of H2O (same algorithmic steps, same histogramming techniques),
with the exception of the following changes:

-  Improved ability to train on categorical variables (using the
   ``nbins_cats`` parameter)
-  Minor changes in histogramming logic for some corner cases
-  By default, DRF builds half as many trees for binomial problems, similar to GBM: it uses a single tree to estimate class 0 (probability "p0"), and then computes the probability of class 0 as :math:`1.0 - p0`.  For multiclass problems, a tree is used to estimate the probability of each class separately.

There was some code cleanup and refactoring to support the following
features:

-  Per-row observation weights
-  Per-row offsets
-  N-fold cross-validation

DRF no longer has a special-cased histogram for classification (class
DBinomHistogram has been superseded by DRealHistogram), since it was not
applicable to cases with observation weights or for cross-validation.

Defining a DRF Model
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

-  **Ignored\_columns**: (Optional) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column
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

-  **ntrees**: Specify the number of trees.

-  **max\_depth**: Specify the maximum tree depth.

-  **min\_rows**: Specify the minimum number of observations for a leaf
   (``nodesize`` in R).

-  **nbins**: (Numerical/real/int only) Specify the number of bins for
   the histogram to build, then split at the best point.

-  **nbins\_cats**: (Categorical/enums only) Specify the maximum number
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

-  **mtries**: Specify the columns to randomly select at each level. If
   the default value of ``-1`` is used, the number of variables is the
   square root of the number of columns for classification and p/3 for
   regression (where p is the number of predictors). The range is -1 to
   >=1.

-  **sample\_rate**: Specify the row sampling rate (x-axis). The range
   is 0.0 to 1.0. Higher values may improve training accuracy. Test
   accuracy improves when either columns or rows are sampled. For
   details, refer to "Stochastic Gradient Boosting" (`Friedman,
   1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  **sample\_rate\_per\_class**: When building models from imbalanced datasets, this option specifies that each tree in the ensemble should sample from the full training dataset using a per-class-specific sampling rate rather than a global sample factor (as with `sample_rate`). The range for this option is 0.0 to 1.0. If this option is specified along with **sample_rate**, then only the first option that DRF encounters will be used.

-  **col\_sample\_rate**: Specify the column sampling rate (y-axis). The
   range is 0.0 to 1.0. Higher values may improve training accuracy.
   Test accuracy improves when either columns or rows are sampled. For
   details, refer to "Stochastic Gradient Boosting" (`Friedman,
   1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  **score\_each\_iteration**: (Optional) Enable this option to score
   during each iteration of the model training.

-  **score\_tree\_interval**: Score the model after every so many trees.
   Disabled if set to 0.

-  **fold\_assignment**: (Applicable only if a value for **nfolds** is
   specified and **fold\_column** is not specified) Specify the
   cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random, 
   `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  **fold\_column**: Specify the column that contains the
   cross-validation fold index assignment per observation.

-  **offset\_column**: Specify a column to use as the offset. 

    **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__.

-  **weights\_column**: Specify a column to use for the observation
   weights, which are used for bias correction. The specified
   ``weights_column`` must be included in the specified
   ``training_frame``. 
   
    **Python only**: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
    
   | **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  **balance\_classes**: Oversample the minority classes to balance the
   class distribution. This option is not enabled by default and can
   increase the data frame size. This option is only applicable for
   classification.

-  **max\_confusion\_matrix\_size**: Specify the maximum size (in number
   of classes) for confusion matrices to be printed in the Logs.

-  **max\_hit\_ratio\_k**: Specify the maximum number (top K) of
   predictions to use for hit ratio computation. Applicable to
   multi-class only. To disable, enter 0.

-  **r2\_stopping**: Specify a threshold for the coefficient of
   determination :math:`(r^2)` metric value. When this threshold is met or
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

-  **binomial\_double\_trees**: (Binary classification only) Build twice
   as many trees (one per class). Enabling this option can lead to
   higher accuracy, while disabling can result in faster model building.
   This option is disabled by default.

-  **checkpoint**: Enter a model key associated with a
   previously-trained model. Use this option to build a new model as a
   continuation of a previously-generated model.

-  **col\_sample\_rate\_change\_per\_level**: This option specifies to change the column sampling rate as a function of the depth in the tree. For example:

	 level 1: **col\_sample_rate**
	
	 level 2: **col\_sample_rate** * **factor**
	
	 level 3: **col\_sample_rate** * **factor^2**
	
	 level 4: **col\_sample_rate** * **factor^3**
	
	 etc.

-  **col\_sample\_rate\_per\_tree**: Specify the column sample rate per tree. This can be a value from 0.0 to 1.0.

-  **min\_split\_improvement**: The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range.

- **random\_split_points**: By default DRF bins from min...max in steps of (max-min)/N. When this option is enabled, DRF will instead sample N-1 points from min...max and use the sorted list of those for split finding.

-  **histogram_type**: By default (AUTO) DRF bins from min...max in steps of (max-min)/N. Random split points or quantile-based split points can be selected as well. RoundRobin can be specified to cycle through all histogram types (one per tree). Use this option to specify the type of histogram to use for finding optimal split points:

	- AUTO
	- UniformAdaptive
	- Random
	- QuantilesGlobal
	- RoundRobin

    **Note**: H2O supports extremely randomized trees via ``histogram_type="Random"``. In extremely randomized trees (Extra-Trees), randomness goes one step further in the way splits are computed. As in Random Forests, a random subset of candidate features is used, but instead of looking for the best split, thresholds (for the split) are drawn at random for each candidate feature, and the best of these randomly-generated thresholds is picked as the splitting rule. This usually allows to reduce the variance of the model a bit more, at the expense of a slightly greater increase in bias.

-  **keep\_cross\_validation\_predictions**: Enable this option to keep the
   cross-validation prediction.

-  **keep\_cross\_validation\_fold\_assignment**: Enable this option to preserve the cross-validation fold assignment.

-  **class\_sampling\_factors**: Specify the per-class (in
   lexicographical order) over/under-sampling ratios. By default, these
   ratios are automatically computed during training to obtain the class
   balance.

-  **max\_after\_balance\_size**: Specify the maximum relative size of
   the training data after balancing class counts (**balance\_classes**
   must be enabled). The value can be less than 1.0.

-  **nbins\_top\_level**: (For numerical/real/int columns only) Specify
   the minimum number of bins at the root level to use to build the
   histogram. This number will then be decreased by a factor of two per
   level.

Interpreting a DRF Model
~~~~~~~~~~~~~~~~~~~~~~~~

By default, the following output displays:

-  Model parameters (hidden)
-  A graph of the scoring history (number of trees vs. training MSE)
-  A graph of the ROC curve (TPR vs. FPR)
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

  DRF converts a new categorical level to a NA value in the test set, and then splits left on the NA value during scoring. The algorithm splits left on NA values because, during training, Na values are grouped with the outliers in the left-most bin.

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

-  **How is variable importance calculated for DRF?**

 Variable importance is determined by calculating the relative influence of each variable: whether that variable was selected during splitting in the tree building process and how much the squared error (over all trees) improved as a result.

-  **How is column sampling implemented for DRF?**

  For an example model using:

   - 100 columns
   - ``col_sample_rate_per_tree`` is 0.602
   - ``mtries`` is -1 or 7 (refers to the number of active predictor columns for the dataset)

  For each tree, the floor is used to determine the number - for this example, (0.602\*100)=60 out of the 100 ()of columns that are randomly picked. For classification cases where ``mtries=-1``, the square root ()for this example, (100)=10 columns) are then randomly chosen for each split decision (out of the total 60).

  For regression, the floor ()in this example, (100/3)=33 columns) is used for each split by default. If ``mtries=7``, then 7 columns are picked for each split decision (out of the 60).

  ``mtries`` is configured independently of ``col_sample_rate_per_tree``, but it can be limited by it. For example, if ``col_sample_rate_per_tree=0.01``, then there's only one column left for each split, regardless of how large the value for ``mtries`` is.

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

`P. Geurts, D. Ernst., and L. Wehenkel, “Extremely randomized trees”, Machine Learning, 63(1), 3-42, 2006. <http://link.springer.com/article/10.1007%2Fs10994-006-6226-1>`_
