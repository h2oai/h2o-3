Isolation Forest
----------------

**Note**: This is a Beta version of Isolation Forest.

Introduction
~~~~~~~~~~~~

Isolation Forest is similar in principle to Random Forest and is built on the basis of decision trees. Isolation Forest, however, identifies anomalies or outliers rather than profiling normal data points. Isolation Forest isolates observations by randomly selecting a feature and then randomly selecting a split value between the maximum and minimum values of that selected feature. This split depends on how long it takes to separate the points. 

Random partitioning produces noticeably shorter paths for anomalies. When a forest of random trees collectively produces shorter path lengths for particular samples, they are highly likely to be anomalies.


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

-  `build_tree_one_node <algo-params/build_tree_one_node.html>`__: To run on a single node, check this checkbox. This is suitable for small datasets as there is no network overhead but fewer CPUs are used.

-  `mtries <algo-params/mtries.html>`__: Specify the columns to randomly select at each level. If the default value of ``-1`` is used, the number of variables is the square root of the number of columns for classification and p/3 for regression (where p is the number of predictors). The range is -1 to >=1.

-  `sample_size <algo-params/sample_size.html>`__: The number of randomly sampled observations used to train each Isolation Forest tree. If set to -1, ``sample_rate`` will be used instead. This value defaults to 256.

-  `sample_rate <algo-params/sample_rate.html>`__: Specify the row sampling rate (x-axis). (Note that this method is sample without replacement.) The range is 0.0 to 1.0, and this value defaults to 0.6320000291. Higher values may improve training accuracy. Test accuracy improves when either columns or rows are sampled. For details, refer to "Stochastic Gradient Boosting" (`Friedman, 1999 <https://statweb.stanford.edu/~jhf/ftp/stobst.pdf>`__). If set to -1 (default), then ``sample_size`` will be used instead.

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
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during Aggregator training and only keep the **T** most frequent levels.
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.)
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). This is useful in GBM/DRF, for example, when you have more levels than ``nbins_cats``, and where the top level splits now have a chance at separating the data with a split. Note that this requires a specified response column.


References
~~~~~~~~~~

`Liu, Fei Tony, Ting, Kai Ming, and Zhou, Zhi-Hua, "Isolation Forest" <https://cs.nju.edu.cn/zhouzh/zhouzh.files/publication/icdm08b.pdf>`__
