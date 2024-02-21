Decision Tree
-------------

The Decision Tree algorithm creates a tree structure where each internal node represents a test on one or more attributes. Each branch emerging from a node represents the outcome of a test, and each leaf node represents a class label or a predicted value.

This implementation only supports numeric features and a binary target variable. Additionally, `it utilizes binning <#binning-for-tree-building>`__ for efficient tree building when dealing with large datasets. The splitting rule employed by the algorithm is entropy.

The Decision Tree algorithm is a powerful tool for classification and regression tasks.

Defining a Decision Tree Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

All parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

-  `max_depth <algo-params/max_depth.html>`__: Specify the maximum depth of the final decision tree. The final depth can be less than the definied ``max_depth`` if another stopped criteria is met first. This value defaults to ``20``.

-  `min_rows <algo-params/min_rows.html>`__: Specify the minimum number of observations for a leaf. This value defaults to ``10``.

Common parameters
'''''''''''''''''

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO`` (default): Allow the algorithm to decide. In DRF, the algorithm will automatically perform ``enum`` encoding
  - ``enum`` or ``Enum``: 1 column per categorical feature
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** (10) most frequent levels
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.)
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). This is useful in GBM/DRF, for example, when you have more levels than ``nbins_cats``, and where the top level splits now have a chance at separating the data with a split. Note that this requires a specified response column

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This value defaults to ``-1`` (time-based random number).

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
   
      **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable. The data can be numeric or categorical.


Decision Tree Workflow
~~~~~~~~~~~~~~~~~~~~~~

The Decision Tree algorithm follows a recursive process to build the tree structure. The basic workflow can be summarized as:

1. **Input**: The algorithm takes a dataset consisting of numerical features and a binary target variable.
2. **Binning**: The algorithm applies binning to discretize continuous features into a set of bins to optimize tree construction on large datasets. Binning is executed at each internal node prior to the selection of the splitting rule.
3. **Splitting rule selection**: The algorithm uses entropy as the splitting rule to determine the best attribute and threshold for each node.
4. **Building the tree**: The tree construction process begins with a root node and recursively divides the dataset based on the selected attribute and threshold.
5. **Stopping criteria**: The recursive division process stops when specific conditions are met. The employed criteria include:

	a. *Maximum tree depth*: The process stops when the tree reaches a predefined maximum depth (set with ``max_depth``).
	b. *Minimum number of samples*: The process stops when a node contains a minimum number of samples or instances (set with ``min_rows``).
	c. *Limit of entropy improvement*: The process stops when the improvement of entropy between the parent node and the current split reaches a specified minimum threshold (refer to the `Entropy as a Splitting Rule <#entropy-as-a-splitting-rule>`__ section for more information).

6. **Leaf node labeling**: The leaf nodes are labeled with the majority class or predicted value based on the training samples they contain at the end of the tree construction.

Binning for Tree Building
~~~~~~~~~~~~~~~~~~~~~~~~~

To handle large datasets efficiently, the Decision Tree algorithm utilizes binning as a preprocessing step at each internal node. Binning involves discretizing continuous features into a finitie number of bins. This reduces the computational complexity of finding the best attribute and threshold for each split.

The binning process can be summarized as:

1. **Input**: Inputting continuous feature values for a particular attribute.
2. **Binning algorithm**: Appling a binning algorithm to divide the feature values into a specified number of bins (this technique is equal width binning).
3. **Binning result**: Obtaining the bin boundaries for the feature values.
4. **Statistic calculation**: Calculating statistics for each bin, such as the count of samples for each class.

The binned features are then used for split point selection during tree construction. This allows for faster computation.

Entropy as a Splitting Rule
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Decision Tree algorithm employs entropy as the splitting rule to determine the best attribute and threshold for each node. Entropy measures the impurity or disorder within a set of samples. The goal is to find splits that minimize the entropy and create homogenous subsets with respect to the target variable.

The entropy of a set :math:`S` with respect to a binary target variable can be calculated using the following forumla:

.. math::
	
	\text{Entropy}(S) = -p_1 \times \log2 (p_1) - p_0 \times \log2(p_0)

where

- :math:`p_1` is the proportion of positive (or class 1) samples in :math:`S`
- :math:`p_0` is the proportion of negative (or class 0) samples in :math:`S`

The attribute and threshold combination that minimizes the weighted average of the entropies of the resulting subsets is selected as the best split point.

Examples
~~~~~~~~

.. tabs::
	.. code-tab:: r R

		library(h2o)
		h2o.init()

		# Import the prostate dataset:
		prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

		# Set the target variable:
		target_variable <- 'CAPSULE'
		prostate[target_variable] <- as.factor(prostate['CAPSULE'])

		# Split the dataset into train and test:
		splits <- h2o.splitFrame(data = prostate, ratios = 0.7, seed =1)
		train <- splits[[1]]
		test <- splits[[2]]

		# Build and train the model:
		h2o_dt <- h2o.decision_tree(y = target_variable, training_frame = train, max_depth = 5)

		# Predict on the test data:
		h2o_pred <- h2o.predict(h2o_dt, test)$predict

	.. code-tab:: python

		import h2o
		from h2o.estimators import H2ODecisionTreeEstimator
		h2o.init()

		# Import the prostatedataset:
		prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

		# Set the target variable:
		target_variable = 'CAPSULE'
		prostate[target_variable] = prostate[target_variable].asfactor()

		# Split the dataset into train and test:
		train, test = prostate.split_frame(ratios=[.7])

		# Build and train the model:
		sdt_h2o = H2ODecisionTreeEstimator(model_id="decision_tree.hex", max_depth=5)
		sdt_h2o.train(y=target_variable, training_frame=train)

		# Predict on the test data:
		pred_test = sdt_h2o.predict(test)

References
~~~~~~~~~~

T. Hastie, R. Tibshirani, J. Friedman, “The elements of Statistical Learning Data Mining, Inference and Prediction”, Chapter 9.2, Second Edition, Springer Series in Statistics, 2017.
