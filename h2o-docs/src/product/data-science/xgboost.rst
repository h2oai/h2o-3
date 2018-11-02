XGBoost
-------

**Note**: This section is a work in progress.

Introduction
~~~~~~~~~~~~

XGBoost is a supervised learning algorithm that implements a process called boosting to yield accurate models. Boosting refers to the ensemble learning technique of building many models sequentially, with each new model attempting to correct for the deficiencies in the previous model. In tree boosting, each new model that is added to the ensemble is a decision tree. XGBoost provides parallel tree boosting (also known as GBDT, GBM) that solves many data science problems in a fast and accurate way. For many problems, XGBoost is one of the best gradient boosting machine (GBM) frameworks today. 

The H2O XGBoost implementation is based on two separated modules. The first module, `h2o-genmodel-ext-xgboost <https://github.com/h2oai/h2o-3/tree/master/h2o-genmodel-extensions/xgboost>`__, extends module `h2o-genmodel <https://github.com/h2oai/h2o-3/tree/master/h2o-genmodel>`__  and registers an XGBoost-specific MOJO. The module also contains all necessary XGBoost binary libraries. The module can contain multiple libraries for each platform to support different configurations (e.g., with/without GPU/OMP). H2O always tries to load the most powerful one (currently a library with GPU and OMP support). If it fails, then the loader tries the next one in a loader chain. For each platform, H2O provide an XGBoost library with minimal configuration (supports only single CPU) that serves as fallback in case all other libraries could not be loaded.

The second module, `h2o-ext-xgboost <https://github.com/h2oai/h2o-3/tree/master/h2o-extensions/xgboost>`__, contains the actual XGBoost model and model builder code, which communicates with native XGBoost libraries via the JNI API. The module also provides all necessary REST API definitions to expose the XGBoost model builder to clients.

XGBoost in H2O supports multicore, thanks to OpenMP. The multicore implementation will only be available if the system itself supports it. (It has the right version of libraries.) If the requirements are not satisfied, XGBoost will use a fallback that is single core only. Multi-node support is currently available as a Beta feature.

Refer to the `XGBoost in H2O Machine Learning Platform <https://www.h2o.ai/blog/xgboost-in-h2o-machine-learning-platform/>`__ blog post for an example of how to use XGBoost with the HIGGS dataset. 

Defining an XGBoost Model
~~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate the accuracy of the model.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable. The data can be numeric or categorical.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the cross-validation predictions.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment. 

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Specify whether to score during each iteration of the model training.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for **nfolds** is specified and **fold\_column** is not specified) Specify the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option is enabled by default.

-  `offset_column <algo-params/offset_column.html>`__: Available, but not currently supported in XGBoost. 

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `stopping_rounds <algo-params/stopping_rounds.html>`__: Stops training when the option selected for **stopping\_metric** doesn't improve for the specified number of training rounds, based on a simple moving average. This value defaults to ``0`` (disabled). The metric is computed on the validation data (if provided); otherwise, training data is used.
   
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

-  `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. This value defaults to 0.001.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option defaults to 0 (disabled) by default.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to -1 (time-based random number).

-  `distribution <algo-params/distribution.html>`__: Specify the distribution (i.e., the loss function). The options are AUTO, bernoulli, multinomial, gaussian, poisson, gamma, or tweedie. 

  - If the distribution is ``bernoulli``, the the response column must be 2-class categorical
  - If the distribution is ``multinomial``, the response column must be categorical.
  - If the distribution is ``poisson``, the response column must be numeric.
  - If the distribution is ``tweedie``, the response column must be numeric.
  - If the distribution is ``gaussian``, the response column must be numeric.
  - If the distribution is ``gamma``, the response column must be numeric.

  AUTO distribution is performed by default. In this case, the algorithm will guess the model type based on the response column type. If the response column type is numeric, AUTO defaults to “gaussian”; if categorical, AUTO defaults to bernoulli or multinomial depending on the number of response categories.

-  `tweedie_power <algo-params/tweedie_power.html>`__: (Only applicable if *Tweedie* is specified for **distribution**) Specify the Tweedie power. This value defaults to 1.5, and the range is from 1 to 2. For a normal distribution, enter ``0``. For Poisson distribution, enter ``1``. For a gamma distribution, enter ``2``. For a compound Poisson-gamma distribution, enter a value greater than 1 but less than 2. For more information, refer to `Tweedie distribution <https://en.wikipedia.org/wiki/Tweedie_distribution>`__.

-  `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO``: Allow the algorithm to decide. In XGBoost, the algorithm will automatically perform ``one_hot_internal`` encoding. (default)
  - ``enum`` or ``Enum``: 1 column per categorical feature
  - ``one_hot_internal`` or ``OneHotInternal``: On the fly N+1 new cols for categorical features with N levels
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``: Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.) 
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). This is useful, for example, when you have more levels than ``nbins_cats``, and where the top level splits now have a chance at separating the data with a split. 
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** most frequent levels.

  **Note**: This value defaults to ``label_encoder``. Similarly, if ``auto`` is specified, then the algorithm performs ``label_encoder`` encoding. 

-  **quiet_mode**: Specify whether to enable quiet mode. This option is enabled by default.

-  `ntrees <algo-params/ntrees.html>`__ (alias: ``n_estimators``): Specify the number of trees to build. This value defaults to 50.

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum tree depth. This value defaults to 6. Higher values will make the model more complex and can lead to overfitting. Setting this value to 0 specifies no limit. Note that a max_depth limit must be used if ``grow_policy=depthwise`` (default). 

-  `min_rows <algo-params/min_rows.html>`__ (alias: ``min_child_weight``): Specify the minimum number of observations for a leaf (``nodesize`` in R). This value defaults to 1. 

-  `learn_rate <algo-params/learn_rate.html>`__ (alias: ``eta``): Specify the learning rate by which to shrink the feature weights. Shrinking feature weights after each boosting step makes the boosting process more conservative and prevents overfitting. The range is 0.0 to 1.0. This value defaults to 0.3.

-  `sample_rate <algo-params/sample_rate.html>`__ (alias: ``subsample``): Specify the row sampling ratio of the training instance (x-axis). (Note that this method is sample without replacement.) For example, setting this value to 0.5 tells XGBoost to randomly collected half of the data instances to grow trees. This value defaults to 1, and the range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  `col_sample_rate <algo-params/col_sample_rate.html>`__ (alias: ``colsample_bylevel``): Specify the column sampling rate (y-axis) for each split in each level. (Note that this method is sample without replacement.) This value defaults to 1.0, and the range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__).

-  `col_sample_rate_per_tree <algo-params/col_sample_rate_per_tree.html>`__ (alias: ``colsample_bytree``: Specify the column subsampling rate per tree. (Note that this method is sample without replacement.) This value defaults to 1.0 and can be a value from 0.0 to 1.0. Note that it is multiplicative with ``col_sample_rate``, so setting both parameters to 0.8, for example, results in 64% of columns being considered at any given node to split.

-  `max_abs_leafnode_pred <algo-params/max_abs_leafnode_pred.html>`__ (alias: ``max_delta_step``): Specifies the maximum delta step allowed in each tree’s weight estimation. This value defaults to 0. Setting this value to 0 specifies no constraint. Setting this value to be greater than 0 can help making the update step more conservative and reduce overfitting by limiting the absolute value of a leafe node prediction. This option also helps in logistic regression when a class is extremely imbalanced. 

-  `score_tree_interval <algo-params/score_tree_interval.html>`__: Score the model after every so many trees. This value is set to 0 (disabled) by default.

-  `min_split_improvement <algo-params/min_split_improvement.html>`__ (alias: ``gamma``): The value of this option specifies the minimum relative improvement in squared error reduction in order for a split to happen. When properly tuned, this option can help reduce overfitting. Optimal values would be in the 1e-10...1e-3 range. This value defaults to 0.

-  **tree_method**: Specify the construction tree method to use. This can be one of the following: 

   - ``auto`` (default): Allow the algorithm to choose the best method. For small to medium dataset, ``exact``  will be used. For very large datasets, ``approx`` will be used.
   - ``exact``: Use the exact greedy method.
   - ``approx``: Use an approximate greedy method. This generates a new set of bins for each iteration.
   - ``hist``: Use a fast histogram optimized approximate greedy method. In this case, only a subset of possible split values are considered.

-  **grow_policy**: Specify the way that new nodes are added to the tree. "depthwise" (default) splits at nodes that are closest to the root; "lossguide" splits at nodes with the highest loss change. Note that when the grow policy is "depthwise", then ``max_depth`` cannot be 0 (unlimited).

-  **max_bins**: When ``grow_policy="lossguide"`` and ``tree_method="hist"``, specify the maximum number of bins for binning continuous features. This value defaults to 256.

-  **max_leaves**: When ``grow_policy="lossguide"`` and ``tree_method="hist"``, specify the maximum number of leaves to include each tree. This value defaults to 0.

-  **min_sum_hessian_in_leaf**: When ``grow_policy="lossguide"`` and ``tree_method="hist"``, specify the mininum sum of hessian in a leaf to keep splitting. This value defaults to 100.

-  **min_data_in_leaf**: When ``grow_policy="lossguide"`` and ``tree_method="hist"``, specify the mininum data in a leaf to keep splitting. This value defaults to 0.

-  **booster**: Specify the booster type. This can be one of the following: "gbtree", "gblinear", or "dart". Note that "gbtree" and "dart" use a tree-based model while "gblinear" uses linear function. This value defaults to "gbtree". More information about the ``booster`` parameter is available `here <https://github.com/dmlc/xgboost/blob/master/doc/tutorials/dart.md>`__.

-  **sample_type**: When ``booster="dart"``, specify whether the sampling type should be one of the following:

  -  ``uniform`` (default): Dropped trees are selected uniformly.
  -  ``weighted``: Dropped trees are selected in proportion to weight.

-  **normalize_type**: When ``booster="dart"``, specify whether the normalization method. This can be one of the following:

  -  ``tree`` (default): New trees have the same weight as each of the dropped trees 1 / (k + learning_rate).
  -  ``forest``: New trees have the same weight as the sum of the dropped trees (1 / (1 + learning_rate).

-  **rate_drop**: When ``booster="dart"``, specify a float value from 0 to 1 for the rate at which to drop previous trees during dropout. This value defaults to 0.0.

-  **one_drop**: When ``booster="dart"``, specify whether to enable one drop, which causes at least one tree to always drop during the dropout. This value defaults to FALSE.

-  **skip_drop**: When ``booster="dart"``, specify a float value from 0 to 1 for the skip drop. This determines the probability of skipping the dropout procedure during a boosting iteration. If a dropout is skipped, new trees are added in the same manner as "gbtree". Note that non-zero ``skip_drop`` has higher priority than ``rate_drop`` or ``one_drop``. This value defaults to 0.0.

-  **reg_lambda**: Specify a value for L2 regularization. This defaults to 0.

-  **reg_alpha**: Specify a value for L1 regularization. This defaults to 0.

-  **dmatrix_type**: Specify the type of DMatrix. Valid options include the following: "auto", "dense", and "sparse". Note that for ``dmatrix_type="sparse"``, NAs and 0 are treated equally. This value defaults to "auto".

-  **backend**: Specify the backend type. This can be done of the following: "auto", "gpu", or "cpu". By default (auto), a GPU is used if available.

-  **gpu_id**: If a GPU backend is available, specify Which GPU to use. This value defaults to 0.

-  **verbose**: Print scoring history to the console. For XGBoost, metrics are per tree. This value defaults to FALSE.

"LightGBM" Emulation Mode Options
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

LightGBM mode builds trees as deep as necessary by repeatedly splitting the one leaf that gives the biggest gain instead of splitting all leaves until a maximum depth is reached. H2O does not integrate `LightGBM <https://github.com/Microsoft/LightGBM>`__. Instead, H2O provides a method for emulating the LightGBM software using a certain set of options within XGBoost. This is done by setting the following options:

::

   tree_method="hist"
   grow_policy="lossguide"

When the above are configured, then the following additional "LightGBM" options are available:

- ``max_bin``
- ``max_leaves``
- ``min_sum_hessian_in_leaf``
- ``min_data_in_leaf``

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

Limitations
~~~~~~~~~~~

This section provides a list of XGBoost limitations - some of which will be addressed in a future release. In general, if XGBoost cannot be initialized for any reason (e.g., unsupported platform), then the algorithm is not exposed via REST API and is not available for clients. Clients can verify availability of the XGBoost by using the corresponding client API call. For example, in Python:

::

  is_xgboost_available = H2OXGBoostEstimator.available()

The list of limitations include:

  1. XGBoost is not supported on Windows.

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

Disabling XGBoost
~~~~~~~~~~~~~~~~~

Some environments may required disabling XGBoost. This can be done by setting ``-Dsys.ai.h2o.ext.core.toggle.XGBoost`` to ``False`` when launching the H2O jar. For example:

::

  # Disable XGBoost in the regular H2O jar
  java -Xmx10g -Dsys.ai.h2o.ext.core.toggle.XGBoost=False -jar  h2o.jar -name ni  -ip 127.0.0.1 -port 54321

  # Disable XGBoost in the Hadoop H2O driver jar
  hadoop jar h2odriver.jar -JJ "-Dsys.ai.h2o.ext.core.toggle.XGBoost=False" -nodes 1  -mapperXmx 3g  -output tmp/a39

Setting ``-Dsys.ai.h2o.ext.core.toggle.XGBoost`` to ``False`` can be done on any H2O version that supports XGBoost and removes XGBoost from the list of available algorithms. 

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

-  **Why does my H2O cloud on Hadoop became unresponsive when running XGBoost even when I supplied 4 times the datasize memory?**

  XGBoost uses memory outside the Java heap, and when that memory is not available, Hadoop kills the h2o job and the h2o cluster becomes unresponsive. Please set ``-extramempercent`` argument to a much higher value when starting H2O. This argument configures the extra memory for internal JVM use outside of the Java heap and is a percentage of mapperXmx. For example:

  ::

    hadoop jar h2odriver.jar -nodes 1 -mapperXmx 20g -extramempercent 100

References
~~~~~~~~~~

- Chen, Tianqi and Guestrin, Carlos Guestrin. "XGBoost: A Scalable Tree Boosting System." Version 3 (2016) `http://arxiv.org/abs/1603.02754 <http://arxiv.org/abs/1603.02754>`__


- Mitchell R, Frank E. (2017) Accelerating the XGBoost algorithm using GPU computing. PeerJ Preprints 5:e2911v1 `https://doi.org/10.7287/peerj.preprints.2911v1 <https://doi.org/10.7287/peerj.preprints.2911v1>`__
