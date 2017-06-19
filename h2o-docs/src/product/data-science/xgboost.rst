XGBoost
-------

This section is a work in progress.

Introduction
~~~~~~~~~~~~

XGBoost is an optimized distributed gradient boosting library designed to be highly efficient, flexible, and portable. This algorithm provides parallel tree boosting (also known as GBDT, GBM) that solves many data science problems in a fast and accurate way. For many problems, XGBoost is the one of the best gradient boosting machine (GBM) frameworks today. 

Defining an XGBoost Model
~~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate the accuracy of the model.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the independent variable. The data can be numeric or categorical.

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the cross-validation predictions.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment. 

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Specify whether to score during each iteration of the model training.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for **nfolds** is specified and **fold\_column** is not specified) Specify the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option is enabled by default.

-  `offset_column <algo-params/offset_column.html>`__: (Not applicable if the **distribution** is **multinomial**) Specify a column to use as the offset.
   
	 **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__. 

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `stopping_rounds <algo-params/stopping_rounds.html>`__: Stops training when the option selected for **stopping\_metric** doesn't improve for the specified number of training rounds, based on a simple moving average. To disable this feature, specify ``0``. The metric is computed on the validation data (if provided); otherwise, training data is used.
   
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

-  `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. Use 0 to disable.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations.

-  `distribution <algo-params/distribution.html>`__: Specify the distribution (i.e., the loss function). The options are AUTO, bernoulli, multinomial, gaussian, poisson, gamma, laplace, quantile, huber, or tweedie.

  - If the distribution is ``bernoulli``, the the response column must be 2-class categorical
  - If the distribution is ``multinomial``, the response column must be categorical.
  - If the distribution is ``poisson``, the response column must be numeric.
  - If the distribution is ``laplace``, the response column must be numeric.
  - If the distribution is ``tweedie``, the response column must be numeric.
  - If the distribution is ``gaussian``, the response column must be numeric.
  - If the distribution is ``huber``, the response column must be numeric.
  - If the distribution is ``gamma``, the response column must be numeric.
  - If the distribution is ``quantile``, the response column must be numeric.

-  `tweedie_power <algo-params/tweedie_power.html>`__: (Only applicable if *Tweedie* is specified for **distribution**) Specify the Tweedie power. The range is from 1 to 2. For a normal distribution, enter ``0``. For Poisson distribution, enter ``1``. For a gamma distribution, enter ``2``. For a compound Poisson-gamma distribution, enter a value greater than 1 but less than 2. For more information, refer to `Tweedie distribution <https://en.wikipedia.org/wiki/Tweedie_distribution>`__.

-  **quiet_mode**: Specify whether to enable quiet mode.

-  `ntrees <algo-params/ntrees.html>`__: Specify the number of trees to build.

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum tree depth.

-  `min_rows <algo-params/min_rows.html>`__: Specify the minimum number of observations for a leaf (``nodesize`` in R).

-  **min_child_weight**: Specifies the fewest allowed (weighted) observations in a leaf. This value defaults to 10.

-  `learn_rate <algo-params/learn_rate.html>`__: Specify the learning rate. The range is 0.0 to 1.0. This value defaults to 0.1.

-  **eta**: This option is the same as ``learn_rate``, except that this has a default of 0.

-  `sample_rate <algo-params/sample_rate.html>`__: Specify the row sampling rate (x-axis). The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  **subsample**: This option is the same as ``sample_rate``, except that this has a default of 0.

-  `col_sample_rate <algo-params/col_sample_rate.html>`__: Specify the column sampling rate (y-axis). The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  **colsample_bylevel**: This option is the same as ``col_sample_rate`` except that this has a default of 0.
   
-  `col_sample_rate_per_tree <algo-params/col_sample_rate_per_tree.html>`__: Specify the column sample rate per tree. This can be a value from 0.0 to 1.0. Note that it is multiplicative with ``col_sample_rate``, so setting both parameters to 0.8, for example, results in 64% of columns being considered at any given node to split.

-  **colsample_bytree**: This option is the same as ``col_sample_rate_per_tree`` except that this has a default of 0.

-  `max_abs_leafnode_pred <algo-params/max_abs_leafnode_pred.html>`__: When building a GBM classification model, this option reduces overfitting by limiting the maximum absolute value of a leaf node prediction. This option defaults to Double.MAX_VALUE.

-  **max_delta_step**: This option is the same as ``max_abs_leafnode_pred`` except that this has a default of 0.

-  `score_tree_interval <algo-params/score_tree_interval.html>`__: Score the model after every so many trees. Disabled if set to 0.

-  `min_split_improvement <algo-params/min_split_improvement.html>`__: The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range.  

-  **tree_method**: Specify the tree method. This can be one of the following: ``"auto"``, ``"exact"``, ``"approx"``, or ``"hist"``. This value defaults to ``"auto"``.

-  **max_bin**: When ``tree_method="hist"``, specify the maximum number of bins. This value defaults to 255.

-  **num_leaves**: When ``tree_method="hist"``, specify the maximum number of leaves to include each tree. This value defaults to 255.

-  **min_sum_hessian_in_leaf**: When ``tree_method="hist"``, specify the mininum sum of hessian in a leaf to keep splitting. This value defaults to 100.

-  **min_data_in_leaf**: When ``tree_method="hist"``, specify the mininum data in a leaf to keep splitting. This value defaults to 0.

-  **grow_policy**: Specify a grow policy of "depthwise" or "lossguide". "depthwise" is standard GBM; "lossguide" is LightGBM. This value defaults to "depthwise".

-  **booster**: Specify the booster type. This can be one of the following: ``"gbtree"``, ``"gblinear"``, or ``"dart"``. This value defaults to ``"gbtree"``.

-  **sample_type**: When ``booster=dart``, specify whether the sampling type should be "uniform" or "weighted". This value defaults to "uniform".

-  **normalize_type**: When ``booster=dart``, specify whether the normalize type should be "tree" or "forest". This value defaults to "tree".

-  **rate_drop**: When ``booster=dart``, specify a float value from 0 to 1 for the rate drop. This value defaults to 0.

-  **one_drop**: When ``booster=dart``, specify whether to enable one drop. This value defaults to FALSE.

-  **skip_drop**: When ``booster=dart``, specify whether a float value from 0 to 1 for the skip drop. This value defaults to 0.

-  **gamma**: This is the same as ``min_split_improvement`` except that this has a default of 0.

-  **reg_lamda**: Specify a value for L2 regularization. This defaults to 1.0.

-  **reg_alpha**: Specify a value for L1 regularization. This defaults to 0.

-  **dmatrix_type**: Specify the type of DMatrix. Valid options include the following: ``"auto"``, ``"dense"``, and ``"sparse"``. Note that for ``dmatrix_type=sparse``, NAs and 0 are treated equally. This value defaults to ``"auto"``.

-  **backend**: Specify the backend type. This can be done of the following: ``"auto"``, ``"gpu"``, or ``"cpu"``. By default (auto), a GPU is used if available.

-  **gpu_id**: If a GPU backend is available, specify Which GPU to use. This value defaults to 0.


Light GBM
~~~~~~~~~

The following options are used to configure a light GBM:

- ``max_bin``
- ``num_leaves``
- ``min_sum_hessian_in_leaf``
- ``min_data_in_leaf``

Dart
~~~~

The following additional parameters can be configured when ``booster=dart``: 

- ``sample_type``
- ``normalize_type``
- ``rate_drop``
- ``one_drop``
- ``skip_drop``


XGBoost Only
~~~~~~~~~~~~

As opposed to Light GBM models, the following options configure a true XGBoost model.

- ``tree_method``
- ``grow_policy``
- ``booster``
- ``gamma``
- ``reg_lambda``
- ``reg_alpha``
- ``dmatrix_type``
- ``backend``
- ``gpu_id``

Limitations
~~~~~~~~~~~

This section provides a list of XGBoost limitations - some of which will be addressed in a future release. In general, if XGBoost cannot be initialized for any reason (e.g., unsupported platform), then the algorithm is not exposed via REST API and is not available for clients. Clients can verify availability of the XGBoost by using the corresponding client API call. For example, in Python:

```python
is_xgboost_available = H2OXGBoostEstimator.available()
```

The list of limitations include:

  1. Right now XGBoost is initialized only for single-node H2O clustersl however multi-node XGBoost support is coming soon.

  2. The list of supported platforms includes:
 
    +----------+-----------------+-----+-----+-----------------------+
    | Platform | Minimal XGBoost | OMP | GPU | Compilation OS        |
    +==========+=================+=====+=====+=======================+
    |Linux     | yes             | yes | yes | Ubuntu 14.04, g++ 4.7 |
    +----------+-----------------+-----+-----+-----------------------+
    |OS X      | yes             | no  | no  | OS X 10.11            |
    +----------+-----------------+-----+-----+-----------------------+
    |Windows   | no              | no  | no  | NA                    |
    +----------+-----------------+-----+-----+-----------------------+

    **Note**: Minimal XGBoost configuration includes support for a single CPU.

  3. Because we are using native XGBoost libraries that depend on OS/platform libraries, it is possible that on older operating systems, XGBoost will not be able to find all necessary binary dependencies, and will not be initialized and available.

  4. XGBoost GPU libraries are compiled against CUDA 8, which is a necessary runtime requirement in order to utilize XGBoost GPU support.


References
~~~~~~~~~~

`Chen, Tianqi and Guestrin, Carlos Guestrin. "XGBoost: A Scalable Tree Boosting System." Version 3 (2016) <http://arxiv.org/abs/1603.02754>`__

