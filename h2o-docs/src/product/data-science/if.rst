.. _isoforest:

Isolation Forest
----------------

Introduction
~~~~~~~~~~~~

Isolation Forest is similar in principle to Random Forest and is built on the basis of decision trees. Isolation Forest, however, identifies anomalies or outliers rather than profiling normal data points. Isolation Forest isolates observations by randomly selecting a feature and then randomly selecting a split value between the maximum and minimum values of that selected feature. This split depends on how long it takes to separate the points. 

Random partitioning produces noticeably shorter paths for anomalies. When a forest of random trees collectively produces shorter path lengths for particular samples, they are highly likely to be anomalies.

Tutorials and Blogs
~~~~~~~~~~~~~~~~~~~

The following tutorials are available that describe how to use Isolation Forest to find anomalies in a dataset and how to interpret the results. 

- `Isolation Forest Deep Dive <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/isolation-forest/isolation-forest.ipynb>`__: Describes how the Isolation Forest algorithm works and how to use it.
- `Interpreting Isolation Forest Anomalies <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/isolation-forest/interpreting_isolation-forest.ipynb>`__: Describes how to understand why a particular feature is considered an anomaly.

The `Anomaly Detection with Isolation Forests using H2O <https://www.h2o.ai/blog/anomaly-detection-with-isolation-forests-using-h2o/>`__ blog provides a summary and examples of the Isolation Forest algorithm in H2O. 

Defining an Isolation Forest Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Enable this option to score during each iteration of the model training.

-  `score_tree_interval <algo-params/score_tree_interval.html>`__: Score the model after every so many trees. Disabled if set to 0.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option is enabled by default.

-  `ntrees <algo-params/ntrees.html>`__: Specify the number of trees.

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum tree depth. Higher values will make the model more complex and can lead to overfitting. Setting this value to 0 specifies no limit. This value defaults to 8.

-  `min_rows <algo-params/min_rows.html>`__: Specify the minimum number of observations for a leaf (``nodesize`` in R).

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. Use 0 to disable.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations.

-  `build_tree_one_node <algo-params/build_tree_one_node.html>`__: Specify whether to run on a single node. This is suitable for small datasets as there is no network overhead but fewer CPUs are used.

-  `mtries <algo-params/mtries.html>`__: Specify the columns to randomly select at each level. If the default value of ``-1`` is used, the number of variables is the square root of the number of columns for classification and p/3 for regression (where p is the number of predictors). If ``-2`` is specified, all features of IF are used. Valid values for this option are -2, -1, and any value >= 1.

-  `sample_size <algo-params/sample_size.html>`__: The number of randomly sampled observations used to train each Isolation Forest tree. Only one of ``sample_size`` or ``sample_rate`` should be defined. If ``sample_rate`` is defined, ``sample_size`` will be ignored. This value defaults to 256.

-  `sample_rate <algo-params/sample_rate.html>`__: Specify the row sampling rate (x-axis). (Note that this method is sample without replacement.) The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__). If set to -1 (default), then ``sample_size`` will be used instead.

-  `col_sample_rate_change_per_level <algo-params/col_sample_rate_change_per_level.html>`__: This option specifies to change the column sampling rate as a function of the depth in the tree. This can be a value > 0.0 and <= 2.0 and defaults to 1. (Note that this method is sample without replacement.) For example:

   level 1: **col\_sample_rate**
  
   level 2: **col\_sample_rate** * **factor**
  
   level 3: **col\_sample_rate** * **factor^2**
  
   level 4: **col\_sample_rate** * **factor^3**
  
   etc.

-  `col_sample_rate_per_tree <algo-params/col_sample_rate_per_tree.html>`__: Specify the column sample rate per tree. This can be a value from 0.0 to 1.0 and defaults to 1. Note that this method is sample without replacement.

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO``: Allow the algorithm to decide (default). In Isolation Forest, the algorithm will automatically perform ``enum`` encoding.
  - ``enum`` or ``Enum``: 1 column per categorical feature
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** (10) most frequent levels.
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.)

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
    
    - ``AUTO``: This defaults to ``logloss`` for classification, ``deviance`` for regression, and ``anomaly_score`` for Isolation Forest. Note that custom and custom_increasing can only be used in GBM and DRF with the Python client. Must be one of: ``AUTO``, ``anomaly_score``. Defaults to ``AUTO``.
    - ``anomaly_score`` (Isolation Forest only)
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
    - ``custom`` (Python client only)
    - ``custom_increasing`` (Python client only)

-  `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the
   metric-based stopping to stop training if the improvement is less
   than this value. This value defaults to 0.01.

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

- **contamination**: The contamination ratio is the proportion of anomalies in the input dataset. If undefined (``-1``), the predict function will not mark observations as anomalies and only anomaly score will be returned. Defaults to ``-1``.

Examples
~~~~~~~~

Below is a simple example showing how to build an Isolation Forest model. 

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import the prostate dataset
        prostate <- h2o.importFile(path = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")

        # Split dataset giving the training dataset 75% of the data
        prostate_split <- h2o.splitFrame(data = prostate, ratios = 0.75)

        # Create a training set from the 1st dataset in the split
        train <- prostate_split[[1]]

        # Create a testing set from the 2nd dataset in the split
        test <- prostate_split[[2]]

        # Build an Isolation forest model
        model <- h2o.isolationForest(training_frame = train, 
                                     sample_rate = 0.1, 
                                     max_depth = 20, 
                                     ntrees = 50)

        # Calculate score
        score <- h2o.predict(model, test)
        result_pred <- score$predict

        # Predict the leaf node assignment
        ln_pred <- h2o.predict_leaf_node_assignment(model, test)

   .. code-tab:: python

        import h2o
        from h2o.estimators import H2OIsolationForestEstimator
        h2o.init()
        
        # Import the prostate dataset
        h2o_df = h2o.import_file("https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")
        
        # Split the data giving the training dataset 75% of the data
        train,test = h2o_df.split_frame(ratios=[0.75])

        # Build an Isolation forest model
        model = H2OIsolationForestEstimator(sample_rate = 0.1, 
                                            max_depth = 20, 
                                            ntrees = 50)
        model.train(training_frame=train)

        # Calculate score
        score = model.predict(test)
        result_pred = score["predict"]

        # Predict the leaf node assignment
        ln_pred = model.predict_leaf_node_assignment(test, "Path")


References
~~~~~~~~~~

`Liu, Fei Tony, Ting, Kai Ming, and Zhou, Zhi-Hua, "Isolation Forest" <https://cs.nju.edu.cn/zhouzh/zhouzh.files/publication/icdm08b.pdf>`__
