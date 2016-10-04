K-Means
-------------

Introduction
~~~~~~~~~~~~

K-Means falls in the general category of clustering algorithms.

Defining a K-Means Model
~~~~~~~~~~~~~~~~~~~~~~~~

-  **model\_id**: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  **training\_frame**: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  **validation\_frame**: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  **ignored\_columns**: (Optional) Specify the column or columns to be exclude from the model. In Flow, click the checkbox next to a column
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

-  **ignore\_const\_cols**: (Optional) Specify whether to ignore
   constant training columns, since no information can be gained from
   them. This option is enabled by default.

-  **k**\ \*: Specify the number of clusters.

-  **estimate_k**: Specify whether to estimate the number of clusters (<=k) iteratively (independent of the seed) and deterministically (beginning with ``k=1,2,3...``). If enabled, for each **k** that, the estimate will go up to **max_iteration**. This option is disabled by default.

-  **user\_points**: Specify a vector of initial cluster centers. The
   user-specified points must have the same number of columns as the
   training observations. The number of rows must equal the number of
   clusters.

-  **max\_iterations**: Specify the maximum number of training
   iterations. The range is 0 to 1e6.

-  **init**: Specify the initialization mode. The options are Random,
   Furthest, PlusPlus, or User. **Note**: If PlusPlus is specified, the
   initial Y matrix is chosen by the final cluster centers from the
   K-Means PlusPlus algorithm.

-  **fold\_assignment**: (Applicable only if a value for **nfolds** is
   specified and **fold\_column** is not specified) Specify the
   cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random,
   `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  **fold\_column**: Specify the column that contains the
   cross-validation fold index assignment per observation.

-  **score\_each\_iteration**: (Optional) Specify whether to score
   during each iteration of the model training.

-  **standardize**: Enable this option to standardize the numeric columns to have a mean of zero and unit variance. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to  have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is enabled by default.

    **Note**: If standardization is enabled, each column of numeric data
    is centered and scaled so that its mean is zero and its standard
    deviation is one before the algorithm is used. At the end of the
    process, the cluster centers on both the standardized scale
    (``centers_std``) and the de-standardized scale (``centers``) are
    displayed. To de-standardize the centers, the algorithm multiplies
    by the original standard deviation of the corresponding column and
    adds the original mean. Enabling standardization is mathematically
    equivalent to using ``h2o.scale`` in R with ``center`` = TRUE and
    ``scale`` = TRUE on the numeric columns. Therefore, there will be no
    discernible difference if standardization is enabled or not for
    K-Means, since H2O calculates unstandardized centroids.

-  **keep\_cross\_validation\_predictions**: Enable this option to keep the
   cross-validation predictions.

-  **seed**: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

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

K-Means randomly chooses starting points and converges to a local
minimum of centroids. The number of clusters is arbitrary, and should be
thought of as a tuning parameter. The output is a matrix of the cluster
assignments and the coordinates of the cluster centers in terms of the
originally chosen attributes. Your cluster centers may differ slightly
from run to run as this problem is Non-deterministic Polynomial-time
(NP)-hard.

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

   -  **Randomization**: Choose :math:`K` clusters from the set of :math:`N` observations at random so that each observation has an equal chance of being chosen.

   -  **Plus Plus**: Choose one center :math:`m_{1}` at random.

    a. Calculate the difference between :math:`m_{1}` and each of the remaining :math:`N-1` observations :math:`x_{i}`. :math:`d(x_{i}, m_{1}) = \|(x_{i}-m_{1})\|^2`

    b. Let :math:`P(i)` be the probability of choosing :math:`x_{i}` as :math:`m_{2}`. Weight :math:`P(i)` by :math:`d(x_{i}, m_{1})` so that those :math:`x_{i}` furthest from :math:`m_{2}` have a higher probability of being selected than those :math:`x_{i}` close to :math:`m_{1}`.

    c. Choose the next center :math:`m_{2}` by drawing at random according to the weighted probability distribution.
   
    d. Repeat until :math:`K` centers have been chosen.

   -  **Furthest**: Choose one center :math:`m_{1}` at random.

    a. Calculate the difference between :math:`m_{1}` and each of the remaining :math:`N-1` observations :math:`x_{i}`. :math:`d(x_{i}, m_{1}) = ||(x_{i}-m_{1})||^2`

    b. Choose :math:`m_{2}` to be the :math:`x_{i}` that maximizes :math:`d(x_{i}, m_{1})`.

    c. Repeat until :math:`K` centers have been chosen.

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
Elements of Statistical Learning. Vol.1. N.p., Springer New York,
2001. <http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf>`__

Xiong, Hui, Junjie Wu, and Jian Chen. “K-means Clustering Versus
Validation Measures: A Data- distribution Perspective.” Systems, Man,
and Cybernetics, Part B: Cybernetics, IEEE Transactions on 39.2 (2009):
318-331.
