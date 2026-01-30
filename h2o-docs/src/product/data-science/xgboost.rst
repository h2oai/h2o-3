XGBoost
-------

Introduction
~~~~~~~~~~~~

XGBoost is a supervised learning algorithm that implements a process called boosting to yield accurate models. Boosting refers to the ensemble learning technique of building many models sequentially, with each new model attempting to correct for the deficiencies in the previous model. In tree boosting, each new model that is added to the ensemble is a decision tree. XGBoost provides parallel tree boosting (also known as GBDT, GBM) that solves many data science problems in a fast and accurate way. For many problems, XGBoost is one of the best gradient boosting machine (GBM) frameworks today. 

The H2O XGBoost implementation is based on two separated modules. The first module, `h2o-genmodel-ext-xgboost <https://github.com/h2oai/h2o-3/tree/master/h2o-genmodel-extensions/xgboost>`__, extends module `h2o-genmodel <https://github.com/h2oai/h2o-3/tree/master/h2o-genmodel>`__  and registers an XGBoost-specific MOJO. The module also contains all necessary XGBoost binary libraries. The module can contain multiple libraries for each platform to support different configurations (e.g., with/without GPU/OMP). H2O always tries to load the most powerful one (currently a library with GPU and OMP support). If it fails, then the loader tries the next one in a loader chain. For each platform, H2O provide an XGBoost library with minimal configuration (supports only single CPU) that serves as fallback in case all other libraries could not be loaded.

The second module, `h2o-ext-xgboost <https://github.com/h2oai/h2o-3/tree/master/h2o-extensions/xgboost>`__, contains the actual XGBoost model and model builder code, which communicates with native XGBoost libraries via the JNI API. The module also provides all necessary REST API definitions to expose the XGBoost model builder to clients.

XGBoost in H2O supports multicore, thanks to OpenMP. The multicore implementation will only be available if the system itself supports it. (It has the right version of libraries.) If the requirements are not satisfied, XGBoost will use a fallback that is single core only.

Refer to the `XGBoost in H2O Machine Learning Platform <https://www.h2o.ai/blog/xgboost-in-h2o-machine-learning-platform/>`__ blog post for an example of how to use XGBoost with the HIGGS dataset. 

MOJO Support
''''''''''''

XGBoost supports importing and exporting `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining an XGBoost Model
~~~~~~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

-  **backend**: Specify the backend type. This can be done of the following: ``"auto"`` (default), ``"gpu"``, or ``"cpu"``. By default (``"auto"``), a GPU is used if available.

-  **booster**: Specify the booster type. This can be one of the following: ``"gbtree"`` (default), ``"gblinear"``, or ``"dart"``. Note that ``"gbtree"`` and ``"dart"`` use a tree-based model while ``"gblinear"`` uses linear function. Together with ``tree_method`` this will also determine the ``updater`` XGBoost parameter:

    - For ``"gblinear"`` the ``coord_descent`` updater will be configured (``gpu_coord_descent`` for GPU backend).
    - For ``"gbtree"`` and ``"dart"`` with GPU backend only ``grow_gpu_hist`` is supported, ``tree_method`` other than ``auto`` or ``hist`` will force CPU backend
    - For other cases the ``updater`` is set automatically by XGBoost, visit the 
      `XGBoost Documentation <https://xgboost.readthedocs.io/en/latest/parameter.html#parameters-for-tree-booster>`__
      to learn more about updaters.

-  **colsample_bynode**: Specify the column subsampling rate per tree node. This method samples without replacement. Note that it is multiplicative with ``col_sample_rate`` and ``col_sample_rate_per_tree``, so setting all parameters to ``0.8``, for example, results in 51% of columns being considered at any given node to split. This value defaults to ``1.0`` and can be a value from 0.0 to 1.0.

-  `custom_metric_func <algo-params/custom_metric_func.html>`__: (Applicable only if ``eval_metric`` is not set) Specify a custom evaluation function.

- **eval_metric**: Specify the `evaluation metric <https://xgboost.readthedocs.io/en/stable/parameter.html#learning-task-parameters>`__ that will be passed to the native XGBoost backend. To use ``eval_metric`` for early stopping, you need to specify ``stopping_metric="custom"``. This option defaults to ``"None"``. 

-  **dmatrix_type**: Specify the type of DMatrix. Valid options include the following: ``"auto"`` (default), ``"dense"``, and ``"sparse"``. Note that for ``dmatrix_type="sparse"``, NAs and 0 are treated equally.

-  **gpu_id**: If a GPU backend is available, specify Which GPU to use. This option defaults to ``0``.

-  **grow_policy**: Specify the way that new nodes are added to the tree. ``"depthwise"`` (default) splits at nodes that are closest to the root and is standard GBM; ``"lossguide"`` splits at nodes with the highest loss change and is LightGBM. Note that when the ``grow_policy=depthwise"``, then ``max_depth`` cannot be ``0`` (unlimited).

-  **max_bins**: When ``grow_policy="lossguide"`` and ``tree_method="hist"``, specify the maximum number of bins for binning continuous features. This option defaults to ``256``.

-  **max_leaves**: When ``grow_policy="lossguide"`` and ``tree_method="hist"``, specify the maximum number of leaves to include each tree. This option defaults to ``0``.

-  **normalize_type**: When ``booster="dart"``, specify whether the normalization method should be one of the following:

     -  ``tree`` (default): New trees have the same weight as each of the dropped trees :math:`\frac{1}{k + \text{learning_rate}}` .
     -  ``forest``: New trees have the same weight as the sum of the dropped trees :math:`\frac{1}{1 + \text{learning_rate}}` .

-  **nthread**: Number of parallel threads that can be used to run XGBoost. Cannot exceed H2O cluster limits (-nthreads parameter). This option defaults to ``-1`` (maximum available).

-  **one_drop**: When ``booster="dart"``, specify whether to enable one drop, which causes at least one tree to always drop during the dropout. This option defaults to ``False`` (disabled).

-  **rate_drop**: When ``booster="dart"``, specify a float value from 0 to 1 for the rate at which to drop previous trees during dropout. This option defaults to ``0.0``.

-  **reg_alpha**: Specify a value for L1 regularization. This option defaults to ``0``.

-  **reg_lambda**: Specify a value for L2 regularization. This option defaults to ``1``.

-  **sample_type**: When ``booster="dart"``, specify whether the sampling type should be one of the following:

     -  ``uniform`` (default): Dropped trees are selected uniformly.
     -  ``weighted``: Dropped trees are selected in proportion to weight.

-  **save_matrix_directory**: Directory where to save matrices passed to XGBoost library. Useful for debugging.

-  **scale_pos_weight**: Specify the multiplier that will be used for gradient calculation for observations with positive weights. This is useful for imbalanced problems. A good starting value is: sum(weight of negative observations) / sum(weight of positive observations). This defaults to ``1``.

- **score_eval_metric_only**: Disable native H2O scoring and score only the ``eval_metric`` when enabled. This can make model training faster if scoring is frequent (e.g. each iteration). This option defaults to ``False`` (disabled).

-  **skip_drop**: When ``booster="dart"``, specify a float value from 0 to 1 for the skip drop. This determines the probability of skipping the dropout procedure during a boosting iteration. If a dropout is skipped, new trees are added in the same manner as ``"gbtree"``. Note that non-zero ``skip_drop`` has higher priority than ``rate_drop`` or ``one_drop``. This option defaults to ``0.0``.

-  **tree_method**: Specify the construction tree method to use. This can be one of the following: 

     - ``auto`` (default): Allow the algorithm to choose the best method. For small to medium datasets, ``exact``  will be used. For very large datasets, ``approx`` will be used.
     - ``exact``: Use the exact greedy method.
     - ``approx``: Use an approximate greedy method. This generates a new set of bins for each iteration.
     - ``hist``: Use a fast histogram optimized approximate greedy method. In this case, only a subset of possible split values are considered.


Tree-based algorithm parameters
'''''''''''''''''''''''''''''''

-  `build_tree_one_node <algo-params/build_tree_one_node.html>`__: Specify whether to run on a single node. This is suitable for small datasets as there is no network overhead but fewer CPUs are used. Also useful when you want to use ``exact`` tree method. This option defaults to ``False`` (disabled).

-  `calibration_frame <algo-params/calibration_frame.html>`__: Specifies the frame to be used for Platt scaling.

-  **calibration_method**: Specify the calibration method to use. Must be one of: ``"auto"`` (default), ``"platt_scaling"``, ``"isotonic_regression"``.

-  `calibrate_model <algo-params/calibrate_model.html>`__: Use Platt scaling to calculate calibrated class probabilities. This option defaults to ``False`` (disabled).

-  `col_sample_rate <algo-params/col_sample_rate.html>`__ (alias: ``colsample_bylevel``): Specify the column sampling rate (y-axis) for each split in each level. This method samples without replacement. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__). This value defaults to ``1.0``, and the range is 0.0 to 1.0. 

-  `col_sample_rate_per_tree <algo-params/col_sample_rate_per_tree.html>`__ (alias: ``colsample_bytree``): Specify the column subsampling rate per tree. This method samples without replacement. Note that it is multiplicative with ``col_sample_rate`` and ``colsample_bynode``, so setting all parameters to ``0.8``, for example, results in 51% of columns being considered at any given node to split. This value defaults to ``1.0`` and can be a value from 0.0 to 1.0. 

-  `interaction_constraints <algo-params/interaction_constraints.html>`__: Specify the feature column interactions which are allowed to interact during tree building. Use column names to define which features can interact together. 

-  `learn_rate <algo-params/learn_rate.html>`__ (alias: ``eta``): Specify the learning rate by which to shrink the feature weights. Shrinking feature weights after each boosting step makes the boosting process more conservative and prevents overfitting. The range is 0.0 to 1.0. This option defaults to ``0.3``.

-  `max_abs_leafnode_pred <algo-params/max_abs_leafnode_pred.html>`__ (alias: ``max_delta_step``): Specifies the maximum delta step allowed in each tree’s weight estimation. Setting this value to be greater than 0 can help making the update step more conservative and reduce overfitting by limiting the absolute value of a leaf node prediction. This option also helps in logistic regression when a class is extremely imbalanced. This value defaults to ``0`` (no constraint).

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum tree depth. Higher values will make the model more complex and can lead to overfitting. Setting this value to ``0`` specifies no limit. Note that a ``max_depth`` limit must be used if ``grow_policy=depthwise`` (default). This value defaults to ``6``.

-  `min_rows <algo-params/min_rows.html>`__ (alias: ``min_child_weight``): Specify the minimum number of observations for a leaf (``nodesize`` in R). This option defaults to ``1``. 

-  `min_split_improvement <algo-params/min_split_improvement.html>`__ (alias: ``gamma``): The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10 to 1e-3 range. This option defaults to ``0``.

-  `ntrees <algo-params/ntrees.html>`__ (alias: ``n_estimators``): Specify the number of trees to build. This option defaults to ``50``.

-  `sample_rate <algo-params/sample_rate.html>`__ (alias: ``subsample``): Specify the row sampling ratio of the training instance (x-axis). This method samples without replacement. For example, setting this value to ``0.5`` tells XGBoost to randomly collect half of the data instances to grow trees. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__). This option defaults to ``1``, and the range is 0.0 to 1.0. 

-  `score_tree_interval <algo-params/score_tree_interval.html>`__: Score the model after every so many trees. This option defaults to ``0`` (disabled).

Common parameters
'''''''''''''''''

- `auc_type <algo-params/auc_type.html>`__: Set the default multinomial AUC type. Must be one of:

     - ``"AUTO"`` (default)
     - ``"NONE"``
     - ``"MACRO_OVR"``
     - ``"WEIGHTED_OVR"``
     - ``"MACRO_OVO"``
     - ``"WEIGHTED_OVO"``

-  `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO``: (Default) Allow the algorithm to decide. In XGBoost, the algorithm will automatically perform ``one_hot_internal`` encoding. 
  - ``one_hot_internal`` or ``OneHotInternal``: On the fly N+1 new cols for categorical features with N levels.
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels.
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature.
  - ``label_encoder`` or ``LabelEncoder``: Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.).
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). This is useful, for example, when you have more levels than ``nbins_cats``, and where the top level splits now have a chance at separating the data with a split. 
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** (10) most frequent levels, and then internally do one hot encoding in the case of XGBoost.

- `checkpoint <algo-params/checkpoint.html>`__: Allows you to specify a model key associated with a previously trained model. This builds a new model as a continuation of a previously generated model. If this is not specified, then a new model will be trained instead of building on a previous model.

-  `distribution <algo-params/distribution.html>`__: Specify the distribution (i.e. the loss function). The options are:
    
     - ``AUTO`` (default)
     - ``bernoulli`` -- response column must be 2-class categorical
     - ``multinomial`` -- response column must be categorical
     - ``poisson`` -- response column must be numeric
     - ``tweedie`` -- response column must be numeric
     - ``gaussian`` -- response column must be numeric
     - ``gamma`` -- response column must be numeric

  **Note**: ``AUTO`` distribution is performed by default. In this case, the algorithm will guess the model type based on the response column type. If the response column type is numeric, ``AUTO`` defaults to ``“gaussian”``; if categorical, ``AUTO`` defaults to ``"bernoulli"`` or ``"multinomial"`` depending on the number of response categories.

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for ``nfolds`` is specified and ``fold_column`` is not specified) Specify the cross-validation fold assignment scheme. One of:

     - ``AUTO`` (default; uses ``Random``)
     - ``Random``
     - ``Modulo`` (`read more about Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__)
     - ``Stratified`` (which will stratify the folds based on the response variable for classification problems)

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

- `gainslift_bins <algo-params/gainslift_bins.html>`__: The number of bins for a Gains/Lift table. The default value is ``-1`` and makes the binning automatic. To disable this feature, set to ``0``.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment. This option defaults to ``False`` (disabled).

-  `keep_cross_validation_models <algo-params/keep_cross_validation_models.html>`__: Specify whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to ``True`` (enabled).

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the cross-validation predictions. This option defaults to ``False`` (disabled).

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option defaults to ``0`` (disabled) by default.

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `monotone_constraints <algo-params/monotone_constraints.html>`__: (Applicable when ``distribution`` is ``gaussian``, ``bernoulli``, or ``tweedie`` only) A mapping representing monotonic constraints. Use ``+1`` to enforce an increasing constraint and ``-1`` to specify a decreasing constraint. Note that constraints can only be defined for numerical columns.  A `Python demo is available <https://github.com/h2oai/h2o-3/tree/master/h2o-py/demos/H2O_tutorial_gbm_monotonicity.ipynb>`__.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation. The value can be ``0`` (default) to disable or :math:`\geq` 2. 

-  `offset_column <algo-params/offset_column.html>`__: Specify a column to use as the offset.

    **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (``y``) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. 

-  **quiet_mode**: Specify whether to enable quiet mode. This option defaults to ``True`` (enabled).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Specify whether to score during each iteration of the model training. This option defaults to ``False`` (disabled).

-  `stopping_metric <algo-params/stopping_metric.html>`__: Specify the metric to use for early stopping. The available options are:
    
    - ``AUTO`` (default): (This defaults to ``logloss`` for classification and ``deviance`` for regression)
    - ``deviance``
    - ``logloss``
    - ``MSE``
    - ``RMSE``
    - ``MAE``
    - ``RMSLE``
    - ``AUC`` (area under the ROC curve)
    - ``AUCPR`` (area under the Precision-Recall curve)
    - ``lift_top_group``
    - ``misclassification``
    - ``mean_per_class_error``

-  `stopping_rounds <algo-params/stopping_rounds.html>`__: Stops training when the option selected for ``stopping_metric`` doesn't improve for the specified number of training rounds, based on a simple moving average. This value defaults to ``0`` (disabled). The metric is computed on the validation data (if provided); otherwise, training data is used.
   
   **Note**: If cross-validation is enabled:

    - All cross-validation models stop training when the validation metric doesn't improve.
    - The main model runs for the mean number of epochs.
    - N+1 models may be off by the number specified for **stopping\_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs).

-  `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. This value defaults to ``0.001``.

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
    
     **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `tweedie_power <algo-params/tweedie_power.html>`__: (Applicable if ``distribution="tweedie"`` only) Specify the Tweedie power. For more information, refer to `Tweedie distribution <https://en.wikipedia.org/wiki/Tweedie_distribution>`__. You can tune over this option with values > 1.0 and < 2.0. This value defaults to ``1.5``. 

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.

-  **verbose**: Print scoring history to the console. For XGBoost, metrics are per tree. This option defaults to ``False`` (disabled).

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable. The data can be numeric or categorical.

"LightGBM" Emulation Mode Options
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

LightGBM mode builds trees as deep as necessary by repeatedly splitting the one leaf that gives the biggest gain instead of splitting all leaves until a maximum depth is reached. H2O does not integrate `LightGBM <https://github.com/Microsoft/LightGBM>`__. Instead, H2O provides a method for emulating the LightGBM software using a certain set of options within XGBoost. This is done by setting the following options:

::

   tree_method="hist"
   grow_policy="lossguide"

When the above are configured, then the following additional "LightGBM" options are available:

- ``max_bin``
- ``max_leaves``

XGBoost Only Options
~~~~~~~~~~~~~~~~~~~~

As opposed to light GBM models, the following options configure a true XGBoost model.

- ``tree_method``
- ``grow_policy``
- ``booster``
- ``gamma``
- ``reg_lambda``
- ``reg_alpha``
- ``dmatrix_type``
- ``backend``
- ``gpu_id``


Dart Booster Options
~~~~~~~~~~~~~~~~~~~~

The following additional parameters can be configured when ``booster=dart``: 

- ``sample_type``
- ``normalize_type``
- ``rate_drop``
- ``one_drop``
- ``skip_drop``

GPU Support
~~~~~~~~~~~

GPU support is available in H2O's XGBoost if the following requirements are met:

- NVIDIA GPUs (GPU Cloud, DGX Station, DGX-1, or DGX-2)
- CUDA 9

**Notes**:

 - You can verify that your CUDA runtime version is CUDA 9 by typing ``ls /usr/local/cuda``. If this does not point to CUDA 9, and you have CUDA 9 installed, then create a symlink that points to CUDA 9.
 - You can monitor your GPU utilization via the ``nvidia-smi`` command. Refer to https://developer.nvidia.com/nvidia-system-management-interface for more information.

Limitations
~~~~~~~~~~~

This section provides a list of XGBoost limitations - some of which will be addressed in a future release. In general, if XGBoost cannot be initialized for any reason (e.g., unsupported platform), then the algorithm is not exposed via REST API and is not available for clients. Clients can verify availability of the XGBoost by using the corresponding client API call. For example, in Python:

::

  is_xgboost_available = H2OXGBoostEstimator.available()

The list of limitations include:

  - XGBoost is not currently supported on Windows or on the new Apple M1 chip.  Please check the tickets for `Windows <https://github.com/h2oai/h2o-3/issues/7139>`__ and `Apple M1 <https://github.com/h2oai/h2o-3/issues/7180>`__ for updates. 

  - The list of supported platforms includes:
 
    +------------+-----------------+-----+-----+----------------+
    | Platform   | Minimal XGBoost | OMP | GPU | Compilation OS |
    +============+=================+=====+=====+================+
    |Linux       | yes             | yes | yes | CentOS 7       |
    +------------+-----------------+-----+-----+----------------+
    |OS X (Intel)| yes             | no  | no  | OS X 10.11     |
    +------------+-----------------+-----+-----+----------------+
    |OS X (M1)   | no              | no  | no  | NA             |
    +------------+-----------------+-----+-----+----------------+
    |Windows     | no              | no  | no  | NA             |
    +------------+-----------------+-----+-----+----------------+

    **Notes**:

    - Minimal XGBoost configuration includes support for a single CPU.
    - Testing is done on Ubuntu 16 and CentOS 7 with GCC 5. These can be considered as being supported.

  -  Because we are using native XGBoost libraries that depend on OS/platform libraries, it is possible that on older operating systems, XGBoost will not be able to find all necessary binary dependencies, and will not be initialized and available.

  -  XGBoost GPU libraries are compiled against CUDA 8, which is a necessary runtime requirement in order to utilize XGBoost GPU support.

Disabling XGBoost
~~~~~~~~~~~~~~~~~

Some environments may required disabling XGBoost. This can be done by setting ``-Dsys.ai.h2o.ext.core.toggle.XGBoost`` to ``False`` when launching the H2O jar. For example:

::

  # Disable XGBoost in the regular H2O jar
  java -Xmx10g -Dsys.ai.h2o.ext.core.toggle.XGBoost=False -jar  h2o.jar -name ni  -ip 127.0.0.1 -port 54321

  # Disable XGBoost in the Hadoop H2O driver jar
  hadoop jar h2odriver.jar -JJ "-Dsys.ai.h2o.ext.core.toggle.XGBoost=False" -nodes 1  -mapperXmx 3g  -output tmp/a39

Setting ``-Dsys.ai.h2o.ext.core.toggle.XGBoost`` to ``False`` can be done on any H2O version that supports XGBoost and removes XGBoost from the list of available algorithms. 

XGBoost Feature Interactions
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Ranks of features and feature interactions by various metrics implemented in `XGBFI <https://github.com/Far0n/xgbfi>`__ style.

Metrics
'''''''

- **Gain:** Total gain of each feature or feature interaction
- **FScore:** Amount of possible splits taken on a feature or feature interaction
- **wFScore:** Amount of possible splits taken on a feature or feature interaction weighted by the probability of the splits to take place
- **Average wFScore:** wFScore divided by FScore
- **Average Gain:** Gain divided by FScore
- **Expected Gain:** Total gain of each feature or feature interaction weighted by the probability to gather the gain
- **Average Tree Index**
- **Average Tree Depth**
- **Path:** Argument for saving the table in .xlsx format.

**Additional features:**

- Leaf Statistics
- Split Value Histograms

Usage is illustrated in the Examples section.

XGBoost Friedman and Popescu's H statistics
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

You can calculates the Friedman and Popescu's H statistics to test for the presence of an interaction between specified variables. 

H varies from 0 to 1. It will have a value of 0 if the model exhibits no interaction between specified variables and a correspondingly larger value for a stronger interaction effect between them. NaN is returned if a computation is spoiled by weak main effects and rounding errors.

This statistic can only be calculated for numerical variables. Missing values are supported.

Reference implementation: `Python <https://pypi.org/project/sklearn-gbmi/>`__ and `R <https://rdrr.io/cran/gbm/man/interact.gbm.html>`__

You can see how it used in the `Examples section <#examples>`__.

Examples
~~~~~~~~

This example demonstrates how to build an XGBoost model using H2O-3 with the Titanic dataset. It begins by importing the dataset and specifying the response variable, "survived," as a factor. The dataset is split into training and validation sets. The XGBoost model is trained with specific parameters, including the "dart" booster type. Performance metrics are evaluated on the validation set, and predictions can be generated as needed. Additionally, the model extracts feature interactions and computes Friedman and Popescu's H statistics to assess feature importance, providing insights into the model's behavior.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the iris dataset into H2O:
    titanic <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

    # Set the predictors and response; set the response as a factor:
    titanic['survived'] <- as.factor(titanic['survived'])
    predictors <- setdiff(colnames(titanic), colnames(titanic)[2:3])
    response <- "survived"

    # Split the dataset into a train and valid set:
    titanic_splits <- h2o.splitFrame(data =  titanic, ratios = 0.8, seed = 1234)
    train <- titanic_splits[[1]]
    valid <- titanic_splits[[2]]

    # Build and train the model:
    titanic_xgb <- h2o.xgboost(x = predictors, 
                               y = response, 
                               training_frame = train, 
                               validation_frame = valid, 
                               booster = "dart", 
                               normalize_type = "tree", 
                               seed = 1234)

    # Eval performance:
    perf <- h2o.performance(titanic_xgb)

    # Generate predictions on a test set (if necessary):
    pred <- h2o.predict(titanic_xgb, newdata = valid)

    # Extract feature interactions:
    feature_interactions = h2o.feature_interaction(titanic_xgb)

    # Get Friedman and Popescu's H statistics
    h <- h2o.h(titanic_xgb, train, c('fair','age'))
    print(h)


   .. code-tab:: python
   
    import h2o
    from h2o.estimators import H2OXGBoostEstimator
    h2o.init()

    # Import the titanic dataset into H2O:
    titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

    # Set the predictors and response; set the response as a factor:
    titanic["survived"] = titanic["survived"].asfactor()
    predictors = titanic.columns
    response = "survived" 

    # Split the dataset into a train and valid set: 
    train, valid = titanic.split_frame(ratios=[.8], seed=1234)

    # Build and train the model:
    titanic_xgb = H2OXGBoostEstimator(booster='dart', 
                                      normalize_type="tree", 
                                      seed=1234)
    titanic_xgb.train(x=predictors, 
                      y=response, 
                      training_frame=train, 
                      validation_frame=valid)

    # Eval performance:
    perf = titanic_xgb.model_performance()

    # Generate predictions on a test set (if necessary):
    pred = titanic_xgb.predict(valid)

    # Extract feature interactions:
    feature_interactions = titanic_xgb.feature_interaction()

    # Get Friedman and Popescu's H statistics
    h = titanic_xgb.h(train, ['fair','age'])
    print(h)

Note
''''

XGBoost requires its own memory outside the H2O (Java) cluster. When running XGBoost, be sure you allow H2O-3 no more than 2/3 of the total available RAM.

FAQs
~~~~

- **How does the algorithm handle missing values?**

 Missing values are interpreted as containing information (i.e., missing for a reason), rather than missing at random. During tree building, split decisions for every node are found by minimizing the loss function and treating missing values as a separate category that can go either left or right. XGBoost will automatically learn which is the best direction to go when a value is missing. 

-  **I have a dataset with a large number of missing values (more than 40%), and I'm generating models using XGBoost and H2O Gradient Boosting. Does XGBoost handle variables with missing values differently than H2O's Gradient Boosting?**

  Missing values handling and variable importances are both slightly different between the two methods. Both treat missing values as information (i.e., they learn from them, and don't just impute with a simple constant). The variable importances are computed from the gains of their respective loss functions during tree construction. H2O uses squared error, and XGBoost uses a more complicated one based on gradient and hessian.

-  **How does H2O's XGBoost create the d-matrix?**

  H2O passes and the matrix as a float[] to the C++ backend of XGBoost, exactly like it would be done from C++ or Python.

-  **When training an H2O XGBoost model, the score is calculated intermittently. How does H2O get the score from the XGBoost model while the model is being trained?**

  H2O computes the score itself from the predictions made by XGBoost. This way, it is consistent with all other H2O models.

-  **Are there any algorithmic differences between H2O's XGBoost and regular XGBoost?**

  No, H2O calls the regular XGBoost backend.

-  **How are categorical columns handled?**

  By default, XGBoost will create N+1 new cols for categorical features with N levels (i.e., ``categorical_encoding="one_hot_internal"``). 

-  **Why does my H2O cluster on Hadoop became unresponsive when running XGBoost even when I supplied 4 times the datasize memory?**

  This is why the extramempercent option exists, and we recommend setting this to a high value, such as 120. What happens internally is that when you specify ``-node_memory 10G`` and ``-extramempercent 120``, the h2o driver will ask Hadoop for :math:`10G * (1 + 1.2) = 22G` of memory. At the same time, the h2o driver will limit the memory used by the container JVM (the h2o node) to 10G, leaving the :math:`10G*120%=12G` memory "unused." This memory can be then safely used by XGBoost outside of the JVM. Keep in mind that H2O algorithms will only have access to the JVM memory (10GB), while XGBoost will use the native memory for model training. For example:

  ::

    hadoop jar h2odriver.jar -nodes 1 -mapperXmx 20g -extramempercent 120

- **When should I define the evalutation metric instead of letting H2O choose which metrics to calculate?**

  While you don't always need to specify a custom ``eval_metric``, it is beneficial in two specific cases:

    1. When H2O does not provide a suitable built-in metric (e.g. if you want to calculate classification error for a different threshold than the one automatically determined by H2O, you can do so by specifying ``eval_metric="error@<your threshold>"``);
    2. When you have frequent scoring (e.g. ``score_each_iteration=True``, ``score_tree_interval < 10``). Using ``score_eval_metric_only=True`` allows you to keep ``score_each_iteration=True`` while still reducing training time.

  Refer to this `demo on utilizing the evalutation metric with early stopping <https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/xgboost_eval_metric_demo.ipynb>`__ for more information.

References
~~~~~~~~~~

- Chen, Tianqi and Guestrin, Carlos Guestrin. "XGBoost: A Scalable Tree Boosting System." Version 3 (2016) `http://arxiv.org/abs/1603.02754 <http://arxiv.org/abs/1603.02754>`__

- Mitchell R, Frank E. (2017) Accelerating the XGBoost algorithm using GPU computing. PeerJ Preprints 5:e2911v1 `https://doi.org/10.7287/peerj.preprints.2911v1 <https://doi.org/10.7287/peerj.preprints.2911v1>`__

`Jerome H. Friedman and Bogdan E. Popescu, 2008, "Predictive learning via rule ensembles", *Ann. Appl. Stat.* **2**:916-954. <http://projecteuclid.org/download/pdfview_1/euclid.aoas/1223908046>`__ 


