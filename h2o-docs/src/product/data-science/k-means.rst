K-Means Clustering
------------------

Introduction
~~~~~~~~~~~~

K-Means falls in the general category of clustering algorithms. Clustering is a form of unsupervised learning that tries to find structures in the data without using any labels or target values. Clustering partitions a set of observations into separate groupings such that an observation in a given group is more similar to another observation in the same group than to another observation in a different group.

For more information, refer to `"A Fast Clustering Algorithm to Cluster Very Large Categorical Data Sets in Data Mining" <http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.134.83&rep=rep1&type=pdf>`__ and `"Extensions to the k-Means Algorithm for Clustering Large Data Sets with Catgorical Values" <http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.15.4028&rep=rep1&type=pdf>`__ by Zhexue Huang. 

Defining a K-Means Model
~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation.

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Enable this option to keep the
   cross-validation predictions.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for **nfolds** is specified and **fold_column** is not specified) Specify the cross-validation fold assignment scheme. The available options are AUTO (which is Random), Random, `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional) Specify the column or columns to be exclude from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: (Optional) Specify whether to ignore constant training columns, since no information can be gained from them. This option is enabled by default.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Specify whether to score during each iteration of the model training.

-  `k <algo-params/k.html>`__: Specify the number of clusters (groups of data) in a dataset that are similar to one another.

-  `estimate_k <algo-params/estimate_k.html>`__: Specify whether to estimate the number of clusters (<=k) iteratively (independent of the seed) and deterministically (beginning with ``k=1,2,3...``). If enabled, for each **k** that, the estimate will go up to **max_iteration**. This option is disabled by default.

-  `user_points <algo-params/user_points.html>`__: Specify a dataframe, where each row represents an initial cluster center.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the maximum number of training iterations. The range is 0 to 1e6.

-  `standardize <algo-params/standardize.html>`__: Enable this option to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to  have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is enabled by default.

    **Note**: If standardization is enabled, each column of numeric data is centered and scaled so that its mean is zero and its standard deviation is one before the algorithm is used. At the end of the process, the cluster centers on both the standardized scale (``centers_std``) and the de-standardized scale (``centers``). To de-standardize the centers, the algorithm multiplies by the original standard deviation of the corresponding column and adds the original mean. Enabling standardization is mathematically equivalent to using ``h2o.scale`` in R with ``center`` = TRUE and ``scale`` = TRUE on the numeric columns. Therefore, there will be no discernible difference if standardization is enabled or not for K-Means, since H2O calculates unstandardized centroids.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations.

-  `init <algo-params/init.html>`__: Specify the initialization mode. The options are Random, Furthest, PlusPlus, or User.

 - Random initialization randomly samples the k-specified value of the rows of the training data as cluster centers.
 - PlusPlus initialization chooses one initial center at random and weights the random selection of subsequent centers so that points furthest from the first center are more likely to be chosen.
 - Furthest initialization chooses one initial center at random and then chooses the next center to be the point furthest away in terms of Euclidean distance.
 - User initialization requires the corresponding ``user_points`` parameter. Note that the user-specified points dataset must have the same number of columns as the training dataset.

 **Note**: If PlusPlus is specified, the initial Y matrix is chosen by the final cluster centers from the K-Means PlusPlus algorithm. 

- `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. Use 0 to disable.

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO``: Allow the algorithm to decide (default). In K-Means, the algorithm will automatically perform ``enum`` encoding.
  - ``one_hot_explicit`` or ``OneHotExplicit``: 1 column per categorical feature
  - ``one_hot_explicit``: N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ```eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.)
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.) 

Interpreting a K-Means Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, the following output displays:

-  A graph of the scoring history (number of iterations vs. average
   within the cluster's sum of squares)
-  Output (model category, validation metrics if applicable, and centers
   std)
-  Model Summary (number of clusters, number of categorical columns,
   number of iterations, avg. within sum of squares, avg. sum of
   squares, avg. between the sum of squares)
-  Scoring history (number of iterations, avg. change of standardized
   centroids, avg. within cluster sum of squares)
-  Training metrics (model name, checksum name, frame name, frame
   checksum name, description if applicable, model category, duration in
   ms, scoring time, predictions, MSE, avg. within sum of squares, avg.
   between sum of squares)
-  Centroid statistics (centroid number, size, within sum of squares)
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
