.. _kmeans:

K-Means Clustering
------------------

Introduction
~~~~~~~~~~~~

K-Means falls in the general category of clustering algorithms. Clustering is a form of unsupervised learning that tries to find structures in the data without using any labels or target values. Clustering partitions a set of observations into separate groupings such that an observation in a given group is more similar to another observation in the same group than to another observation in a different group.

For more information, refer to `"A Fast Clustering Algorithm to Cluster Very Large Categorical Data Sets in Data Mining" <http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.134.83&rep=rep1&type=pdf>`__ and `"Extensions to the k-Means Algorithm for Clustering Large Data Sets with Catgorical Values" <http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.15.4028&rep=rep1&type=pdf>`__ by Zhexue Huang. 

MOJO Support
''''''''''''

K-Means currently only supports exporting `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining a K-Means Model
~~~~~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*.

Algorithm-specifc parameters
''''''''''''''''''''''''''''

-  `cluster_size_constraints <algo-params/cluster_size_constraints.html>`__: An array specifying the minimum number of points that should be in each cluster. The length of the constraints array has to be the same as the number of clusters.

-  `estimate_k <algo-params/estimate_k.html>`__: Specify whether to estimate the number of clusters (:math:`\leq` ``k``) iteratively (independent of ``seed``) and deterministically (beginning with ``k=1,2,3...``). If enabled, for each ``k`` the estimate will go up to ``max_iteration``. This option defaults to ``False`` (disabled).

-  `init <algo-params/init1.html>`__: Specify the initialization mode. The options are :

      - ``Random``: initialization randomly samples the k-specified value of the rows of the training data as cluster centers.
      - ``PlusPlus``: initialization chooses one initial center at random and weights the random selection of subsequent centers so that points furthest from the first center are more likely to be chosen. If ``PlusPlus`` is specified, the initial Y matrix is chosen by the final cluster centers from the K-Means PlusPlus algorithm. 
      - ``Furthest`` (default): initialization chooses one initial center at random and then chooses the next center to be the point furthest away in terms of Euclidean distance.
      - ``User``: initialization requires the corresponding ``user_points`` parameter. Note that the user-specified points dataset must have the same number of columns as the training dataset.
      
      **Note**: ``init`` is ignored if ``estimate_k=True`` because the algorithm will determine the initial cluster centers on its own.

-  `k <algo-params/k.html>`__: Specify the number of clusters (groups of data) in a dataset that are similar to one another. This option defaults to ``1``.

-  **max_categorical_levels**: For each categorical feature, specify a limit on the number of most frequent categorical levels used for model training.

-  **parallelize_cross_validation**: Specify whether to enable parallel training of cross-validation models.

-  `user_points <algo-params/user_points.html>`__: Specify a dataframe, where each row represents an initial cluster center.

Common parameters
'''''''''''''''''

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

     - ``auto`` or ``AUTO`` (default): Allow the algorithm to decide. In K-Means, the algorithm will automatically perform ``enum`` encoding.
     - ``enum`` or ``Enum``: 1 column per categorical feature.
     - ``one_hot_explicit``: N+1 new columns for categorical features with N levels.
     - ``binary`` or ``Binary``: No more than 32 columns per categorical feature.
     - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only.
     - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.).
     - ``sort_by_response`` or ``SortByResponse``:  Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). Note that this requires a specified response column.
     - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** (10) most frequent levels.

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for ``nfolds`` is specified and ``fold_column`` is not specified) Specify the cross-validation fold assignment scheme. One of:

      - ``AUTO`` (default; uses ``Random``)
      - ``Random``
      - ``Modulo`` (`read more about Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__)
      - ``Stratified`` (which will stratify the folds based on the response variable for classification problems)

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be exclude from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment. This option defaults to ``False`` (disabled).

-  `keep_cross_validation_models <algo-params/keep_cross_validation_models.html>`__: Specify whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to ``True`` (enabled).

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the cross-validation predictions. This option defaults to ``False`` (disabled).

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the maximum number of training iterations. The range is 0 to 1e6, and the default value is ``10``.

- `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option defaults to ``0`` (disabled).

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation. This option defaults to ``0``.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Specify whether to score during each iteration of the model training. This option defaults to ``False`` (disabled).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).

-  `standardize <algo-params/standardize.html>`__: Enable this option to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to  have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option defaults to ``True`` (enabled).

    **Note**: If standardization is enabled, each column of numeric data is centered and scaled so that its mean is zero and its standard deviation is one before the algorithm is used. At the end of the process, the cluster centers on both the standardized scale (``centers_std``) and the de-standardized scale (``centers``). To de-standardize the centers, the algorithm multiplies by the original standard deviation of the corresponding column and adds the original mean. Enabling standardization is mathematically equivalent to using ``h2o.scale`` in R with ``center = TRUE`` and ``scale = TRUE`` on the numeric columns. Therefore, there will be no discernible difference if standardization is enabled or not for K-Means, since H2O calculates unstandardized centroids.

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
    
    **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset to calculate validation clustering metrics.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns are used.

Interpreting a K-Means Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, the following output displays:

-  A graph of the scoring history (number of iterations vs. within the cluster's sum of squares)
-  Output (model category, validation metrics if applicable, and centers std)
-  Model Summary Model Summary (number of clusters, number of categorical columns, number of iterations, total within sum of squares, total sum of squares, total between the sum of squares. Note that Flow also returns the number of rows.)
-  Scoring history (duration, number of iterations, number of reassigned observations, number of within cluster sum of squares)
-  Training metrics (model name, checksum name, frame name, frame checksum name, description if applicable, model category, scoring time, predictions, MSE, RMSE, total within sum of squares, total sum of squares, total between sum of squares)
-  Centroid statistics (centroid number, size, within cluster sum of squares). The centroid statistics are not available for overall cross-validation metrics.
-  Cluster means (centroid number, column)

K-Means randomly chooses starting points and converges to a local minimum of centroids. The number of clusters is arbitrary and should be thought of as a tuning parameter. The output is a matrix of the cluster assignments and the coordinates of the cluster centers in terms of the originally chosen attributes. Your cluster centers may differ slightly from run to run as this problem is Non-deterministic Polynomial-time (NP)-hard.

Estimating `k` in K-Means
~~~~~~~~~~~~~~~~~~~~~~~~~

The steps below describe the method that K-Means uses in order to estimate `k`.

1. Beginning with one cluster, run K-Means to compute the centroid.
2. Find variable with greatest range and split at the mean. 
3. Run K-Means on the two resulting clusters. 
4. Find the variable and cluster with the greatest range, and then split that cluster on the variable's mean. 
5. Run K-Means again, and so on. 
6. Continue running K-Means until a stopping criterion is met. 

H2O uses proportional reduction in error (:math:`PRE`) to determine when to stop splitting. The :math:`PRE` value is calculated based on the sum of squares within (:math:`SSW`). 

 :math:`PRE=\frac{(SSW\text{[before split]} - SSW\text{[after split]})} {SSW\text{[before split]}}`

H2O stops splitting when :math:`PRE` falls below a :math:`threshold`, which is a function of the number of variables and the number of cases as described below:

:math:`threshold` takes the smaller of these two values:

 either 0.8

  or

 :math:`\big[0.02 + \frac{10}{number\_of\_training\_rows} + \frac{2.5}{number\_of\_model\_features^{2}}\big]`

Cross-Validation Metrics
~~~~~~~~~~~~~~~~~~~~~~~~

To calculate main cross-validation metrics, the metrics from each CV model are aggregated into one. It is impossible to calculate aggregated centroid statistics because each CV model can have a different centroid size (if ``estimate_k`` is enabled), and the aggregation across all groups of centroids does not make sense. 

That is the reason why centroid statistics are NULL for overall cross-validation metrics. You can still get centroid statistics from each CV model individually. 

Constrained K-Means 
~~~~~~~~~~~~~~~~~~~

The ``cluster_size_constraints`` parameter allows the user to define an array that specifies the minimum size of each cluster during the training. The size of the array must be equal to the ``k`` parameter.

To satisfy the custom minimal cluster size, the calculation of clusters is converted to the Minimal Cost Flow problem. Instead of using the Lloyd iteration algorithm, a graph is constructed based on the distances and constraints. The goal is to go iteratively through the input edges and create an optimal spanning tree that satisfies the constraints.

More information about how to convert the standard K-means algorithm to the Minimal Cost Flow problem is described in this paper: https://pdfs.semanticscholar.org/ecad/eb93378d7911c2f7b9bd83a8af55d7fa9e06.pdf.

The result cluster size is guaranteed only on **training data** and only **during training**. Depending on the cluster assignment at the end of the training, the result centers are calculated. However, the result cluster assignment could be different when you score on the same data that was used for training because of during scoring, the resulting cluster is assigned based on the final centers and the distances from them. **No constraints are taken into account during scoring.**

If the ``nfolds`` and ``cluster_size_constraints`` parameters are set simultaneously, the sum of constraints has to be less than the number of data points in one fold.

**Minimum-cost flow problems can be efficiently solved in polynomial time (or in the worst case, in exponential time). The performance of this implementation of the Constrained K-means algorithm is slow due to many repeatable calculations that cannot be parallelized and more optimized at the H2O backend. For large dataset with large sum of constraints, the calculation can last hours. For example, a dataset with 100000 rows and five features can run several hours.**

Expected time with various sized data (OS debian 10.0 (x86-64), processor Intel© Core™ i7-7700HQ CPU @ 2.80GHz × 4, RAM 23.1 GiB):

* 10 000 rows, 5 features  ~ 0h  9m 21s
* 20 000 rows, 5 features  ~ 0h 39m 27s
* 30 000 rows, 5 features  ~ 1h 26m 43s
* 40 000 rows, 5 features  ~ 2h 13m 31s
* 50 000 rows, 5 features  ~ 4h  4m 18s

**The sum of constraints is smaller the time is faster - it uses MCF calculation until all constraints are satisfied then use standard K-means.**


Constrained K-Means with the Aggregator Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To solve Constrained K-means in a shorter time, you can use the `H2O Aggregator algorithm <aggregator.html>`__ to aggregate data to smaller sizes first and then pass this data to the Constrained K-means algorithm to calculate the final centroids to be used with scoring. The results won't be as accurate as the results of a model with the whole dataset; however, it should help solve the problem of huge datasets.

However, there are some assumptions:

* The large dataset has to consist of many similar data points. If not, the insensitive aggregation can break the structure of the dataset.
* The resulting clustering may not meet the initial constraints exactly when scoring. (This also applies to Constrained K-means models; scoring uses resulting centroids to score - no constraints defined before.)

The H2O Aggregator method is a clustering-based method for reducing a numerical/categorical dataset into a dataset with fewer rows. Aggregator maintains outliers as outliers but lumps together dense clusters into exemplars with an attached count column showing the member points.

The following demos are available for constrained KMeans with the Aggregator model:

- https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/constrained_kmeans_demo_cluto.ipynb
- https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/constrained_kmeans_demo_chicago.ipynb

FAQ
~~~

-  **How does the algorithm handle missing values during training?**

  Missing values are automatically imputed by the column mean. K-means
  also handles missing values by assuming that missing feature distance
  contributions are equal to the average of all other distance term
  contributions.

-  **How does the algorithm handle missing values during testing?**

  Missing values are automatically imputed by the column mean of the
  training data.

-  **What happens when you try to predict on a categorical level not
   seen during training?**

  An unseen categorical level in a row does not contribute to that row's
  prediction. This is because the unseen categorical level does not
  contribute to the distance comparison between clusters, and therefore
  does not factor in predicting the cluster to which that row belongs.

-  **Does it matter if the data is sorted?**

  No.

-  **Should data be shuffled before training?**

  No.

-  **What if there are a large number of columns?**

  K-Means suffers from the curse of dimensionality: all points are roughly
  at the same distance from each other in high dimensions, making the
  algorithm less and less useful.

-  **What if there are a large number of categorical factor levels?**

  This can be problematic, as categoricals are one-hot encoded on the fly,
  which can lead to the same problem as datasets with a large number of
  columns.

K-Means Algorithm
~~~~~~~~~~~~~~~~~

The number of clusters :math:`K` is user-defined and is determined a priori.

1. Choose :math:`K` initial cluster centers :math:`m_{k}` according to one of the
   following:

    - **Random**: Choose :math:`K` clusters from the set of :math:`N` observations at random so that each observation has an equal chance of being chosen.

    - **Furthest** (Default): 

      a. Choose one center :math:`m_{1}` at random.

      b. Calculate the difference between :math:`m_{1}` and each of the remaining :math:`N-1` observations :math:`x_{i}`. :math:`d(x_{i}, m_{1}) = ||(x_{i}-m_{1})||^2`

      c. Choose :math:`m_{2}` to be the :math:`x_{i}` that maximizes :math:`d(x_{i}, m_{1})`.

      d. Repeat until :math:`K` centers have been chosen.

    - **PlusPlus**: 

      a. Choose one center :math:`m_{1}` at random.

      b. Calculate the difference between :math:`m_{1}` and each of the remaining :math:`N-1` observations :math:`x_{i}`. :math:`d(x_{i}, m_{1}) = \|(x_{i}-m_{1})\|^2`

      c. Let :math:`P(i)` be the probability of choosing :math:`x_{i}` as :math:`m_{2}`. Weight :math:`P(i)` by :math:`d(x_{i}, m_{1})` so that those :math:`x_{i}` furthest from :math:`m_{2}` have a higher probability of being selected than those :math:`x_{i}` close to :math:`m_{1}`.

      d. Choose the next center :math:`m_{2}` by drawing at random according to the weighted probability distribution.
       
      e. Repeat until :math:`K` centers have been chosen. 

    - **User** initialization allows you to specify a file (using the ``user_points`` parameter) that includes a vector of initial cluster centers. 

2. Once :math:`K` initial centers have been chosen calculate the difference
   between each observation :math:`x_{i}` and each of the centers
   :math:`m_{1},...,m_{K}`, where difference is the squared Euclidean
   distance taken over :math:`p` parameters.

   .. math::

   		d(x_{i}, m_{k})=\sum_{j=1}^{p}(x_{ij}-m_{k})^2=\|(x_{i}-m_{k})\|^2

3. Assign :math:`x_{i}` to the cluster :math:`k` defined by :math:`m_{k}` that minimizes
   :math:`d(x_{i}, m_{k})`

4. When all observations :math:`x_{i}` are assigned to a cluster calculate
   the mean of the points in the cluster.

   .. math::

   	  \bar{x}(k)=\{\bar{x_{i1}},…\bar{x_{ip}}\}

5. Set the :math:`\bar{x}(k)` as the new cluster centers
   :math:`m_{k}`. Repeat steps 2 through 5 until the specified number of max
   iterations is reached or cluster assignments of the :math:`x_{i}` are
   stable.

Examples
~~~~~~~~

This example demonstrates how to build a KMeans model using H2O-3 for clustering. The model estimates the optimal number of clusters and is trained on a dataset, allowing for performance evaluation and predictions on validation data.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the iris dataset into H2O:
    iris <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

    # Set the predictors:
    predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")

    # Split the dataset into a train and valid set:
    iris_split <- h2o.splitFrame(data = iris, ratios = 0.8, seed = 1234)
    train <- iris_split[[1]]
    valid <- iris_split[[2]]

    # Build and train the model:
    iris_kmeans <- h2o.kmeans(k = 10, 
                              estimate_k = TRUE, 
                              standardize = FALSE, 
                              seed = 1234, 
                              x = predictors, 
                              training_frame = train, 
                              validation_frame = valid)

    # Eval performance:
    perf <- h2o.performance(iris_kmeans)

    # Generate predictions on a validation set (if necessary):
    pred <- h2o.predict(iris_kmeans, newdata = valid)



   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OKMeansEstimator
    h2o.init()

    # Import the iris dataset into H2O:
    iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

    # Set the predictors:
    predictors = ["sepal_len", "sepal_wid", "petal_len", "petal_wid"]

    # Split the dataset into a train and valid set:
    train, valid = iris.split_frame(ratios=[.8], seed=1234)

    # Build and train the model:
    iris_kmeans = H2OKMeansEstimator(k=10, 
                                     estimate_k=True, 
                                     standardize=False, 
                                     seed=1234)
    iris_kmeans.train(x=predictors, 
                      training_frame=train, 
                      validation_frame=valid)

    # Eval performance:
    perf = iris_kmeans.model_performance()

    #  Generate predictions on a validation set (if necessary):
    pred = iris_kmeans.predict(valid)


References
~~~~~~~~~~

`Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The
Elements of Statistical Learning. Second Edition. N.p., Springer New York,
2001. <http://statweb.stanford.edu/~tibs/ElemStatLearn/printings/ESLII_print10.pdf>`__

Xiong, Hui, Junjie Wu, and Jian Chen. “K-means Clustering Versus
Validation Measures: A Data- distribution Perspective.” Systems, Man,
and Cybernetics, Part B: Cybernetics, IEEE Transactions on 39.2 (2009):
318-331.

`Hartigan, John A. Clustering Algorithms. New York: John Wiley & Sons, Inc., N.p., 1975. <http://people.inf.elte.hu/fekete/algoritmusok_msc/klaszterezes/John%20A.%20Hartigan-Clustering%20Algorithms-John%20Wiley%20&%20Sons%20(1975).pdf>`__
