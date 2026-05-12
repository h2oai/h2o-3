.. _isoforest:

Isolation Forest
----------------

Introduction
~~~~~~~~~~~~

Isolation Forest is similar in principle to Random Forest and is built on the basis of decision trees. Isolation Forest, however, identifies anomalies or outliers rather than profiling normal data points. Isolation Forest isolates observations by randomly selecting a feature and then randomly selecting a split value between the maximum and minimum values of that selected feature. This split depends on how long it takes to separate the points. 

Random partitioning produces noticeably shorter paths for anomalies. When a forest of random trees collectively produces shorter path lengths for particular samples, they are highly likely to be anomalies.

MOJO Support
''''''''''''

Isolation Forest supports importing and exporting `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Tutorials and Blogs
~~~~~~~~~~~~~~~~~~~

The following tutorials are available that describe how to use Isolation Forest to find anomalies in a dataset and how to interpret the results. 

- `Isolation Forest Deep Dive <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/isolation-forest/isolation-forest.ipynb>`__: Describes how the Isolation Forest algorithm works and how to use it.
- `Interpreting Isolation Forest Anomalies <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/isolation-forest/interpreting_isolation-forest.ipynb>`__: Describes how to understand why a particular feature is considered an anomaly.

The `Anomaly Detection with Isolation Forests using H2O <https://www.h2o.ai/blog/anomaly-detection-with-isolation-forests-using-h2o/>`__ blog provides a summary and examples of the Isolation Forest algorithm in H2O. 

Defining an Isolation Forest Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

- **contamination**: The contamination ratio is the proportion of anomalies in the input dataset. If undefined (``-1``), the predict function will not mark observations as anomalies and only anomaly score will be returned. This option defaults to ``-1``.

-  `sample_size <algo-params/sample_size.html>`__: The number of randomly sampled observations used to train each Isolation Forest tree. Only one of ``sample_size`` or ``sample_rate`` should be defined. If ``sample_rate`` is defined, ``sample_size`` will be ignored. This option defaults to ``256``.

- **validation_response_column**: Name of the response column in the validation frame. Response column should be binary and indicate not anomaly/anomaly. Experimental. 

Shared-tree algorithm parameters
''''''''''''''''''''''''''''''''

-  `build_tree_one_node <algo-params/build_tree_one_node.html>`__: Specify whether to run on a single node. This is suitable for small datasets as there is no network overhead but fewer CPUs are used. This option defaults to ``False`` (disabled).

-  `col_sample_rate_change_per_level <algo-params/col_sample_rate_change_per_level.html>`__: This option specifies to change the column sampling rate as a function of the depth in the tree. This method samples without replacement. This can be a value > 0.0 and :math:`\leq` 2.0 and defaults to ``1``. For example:

     - **level 1**: :math:`\text{col_sample_rate}`
     - **level 2**: :math:`\text{col_sample_rate} \times \text{factor}` 
     - **level 3**: :math:`\text{col_sample_rate} \times \text{factor}^2`
     - **level 4**: :math:`\text{col_sample_rate} \times \text{factor}^3`
     - etc.

-  `col_sample_rate_per_tree <algo-params/col_sample_rate_per_tree.html>`__: Specify the column sample rate per tree. This method samples without replacement.This can be a value from 0.0 to 1.0 and defaults to ``1``. 

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum tree depth. Higher values will make the model more complex and can lead to overfitting. Setting this option to ``0`` specifies no limit. This option defaults to ``8``.

-  `min_rows <algo-params/min_rows.html>`__ (Python) / **node_size** (R): Specify the minimum number of observations for a leaf. This option defaults to ``1``.

-  `mtries <algo-params/mtries.html>`__: Specify the columns to randomly select at each level. If the default value of ``-1`` is used, the number of variables is the square root of the number of columns for classification and :math:`\frac{p}{3}` for regression (where p is the number of predictors). If ``-2`` is specified, all features of IF are used. Valid values for this option are ``-2``, ``-1``, and any value :math:`\geq` 1.

-  `ntrees <algo-params/ntrees.html>`__: Specify the number of trees. This option defaults to ``50``.

-  `sample_rate <algo-params/sample_rate.html>`__: Specify the row sampling rate (x-axis). This method samples without replacement. The range is 0.0 to 1.0. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__). If set to ``-1`` (default), then ``sample_size`` will be used instead.

-  `score_tree_interval <algo-params/score_tree_interval.html>`__: Score the model after every so many trees. This value is set to ``0`` (disabled) by default.

Common parameters
'''''''''''''''''

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

     - ``auto`` or ``AUTO``: Allow the algorithm to decide (default). In Isolation Forest, the algorithm will automatically perform ``enum`` encoding.
     - ``enum`` or ``Enum``: 1 column per categorical feature.
     - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** (10) most frequent levels.
     - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels.
     - ``binary`` or ``Binary``: No more than 32 columns per categorical feature.
     - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only.
     - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.).

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will be automatically exported.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option is set to ``True`` (enabled) by default.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option is set to ``0`` (disabeld) by default.

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Enable this option to score during each iteration of the model training. This option defaults to ``False`` (default).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).

-  `stopping_metric <algo-params/stopping_metric.html>`__: Specify the metric to use for early stopping. The available options are:
    
     - ``AUTO`` (default): This defaults to ``anomaly_score`` for Isolation Forest.
     - ``anomaly_score`` 
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

-  `stopping_rounds <algo-params/stopping_rounds.html>`__: Stops training when the option selected for ``stopping_metric`` doesn't improve for the specified number of training rounds, based on a simple moving average. This value is set to ``0`` (disabled) by default. The metric is computed on the validation data (if provided); otherwise, training data is used.
   
     **Note**: If cross-validation is enabled:

     - All cross-validation models stop training when the validation metric doesn't improve.
     - The main model runs for the mean number of epochs.
     - N+1 models may be off by the number specified for ``stopping_rounds`` from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs).

-  `stopping_tolerance <algo-params/stopping_tolerance.html>`__: Specify the relative tolerance for the metric-based stopping to stop training if the improvement is less than this value. This option defaults to ``0.01``.

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
     
     **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: Id of the validation data frame.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns are used.

Anomaly Score
~~~~~~~~~~~~~

The output of Isolation Forest's algorithm depends on the ``contamination`` parameter.

With ``contamination`` parameter:
'''''''''''''''''''''''''''''''''

**Predict**:

    - ``1`` = Anomaly
    - ``0`` = Normal point

A point is marked as an anomaly if the score is greater or equal to (1-``contamination``)% quantile of the score.

.. math::
    predict = score >= Q_{score}(1-contamination)

**Score**: the normalized **mean_length**.

.. math::
    score(mean\_length) = \frac{(max\_path\_length - mean\_length)}{(max\_path\_length - min\_path\_length)}


Where :math:`min\_path\_length` and :math:`max\_path\_length` are assigned in training. It can happen that an anomalous point has a value > 1. A higher value means a “more anomalous“ point. The score is not normalized by the average path of an unsuccessful search in a binary search tree (BST).

**Mean_Length**: mean path length of the point in a forest. 

We are not using the formula (Equation (2)) from the `Isolation Forest <https://cs.nju.edu.cn/zhouzh/zhouzh.files/publication/icdm08b.pdf>`__ paper nor the estimation of the average path length of an unsuccessful search (Equation (2)).

.. math::
    mean\_length = \frac{path\_length}{ntrees}

Without ``contamination`` parameter:
''''''''''''''''''''''''''''''''''''

The **predict** column contains values from the **score** column, and the **mean_length** column is not changed.

Examples
~~~~~~~~

This example demonstrates how to build an Isolation Forest model for anomaly detection using the H2O-3 platform. It uses the prostate dataset, splits it into training and testing datasets, and trains an Isolation Forest model. After training, the model can predict anomalies and provide leaf node assignments for the testing data.

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

FAQ
~~~

- **How does the algorithm handle missing values during training?**

    When correctly imported, Isolation Forest handles missing values the same way as `DRF <drf.html#faq>`__. Missing values are interpreted as containing information (i.e., missing for a reason). Missing values are counted during splits when splitting for Feature and Value. They can either go to the left or right branch.

References
~~~~~~~~~~

`Liu, Fei Tony, Ting, Kai Ming, and Zhou, Zhi-Hua, "Isolation Forest" <https://cs.nju.edu.cn/zhouzh/zhouzh.files/publication/icdm08b.pdf>`__
