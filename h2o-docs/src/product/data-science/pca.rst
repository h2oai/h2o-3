PCA
--------------

Introduction
~~~~~~~~~~~~

Principal Components Analysis (PCA) is closely related to Principal
Components Regression. The algorithm is carried out on a set of possibly
collinear features and performs a transformation to produce a new set of
uncorrelated features.

PCA is commonly used to model without regularization or perform
dimensionality reduction. It can also be useful to carry out as a
preprocessing step before distance-based algorithms such as K-Means
since PCA guarantees that all dimensions of a manifold are orthogonal.

Defining a PCA Model
~~~~~~~~~~~~~~~~~~~~

-  **model\_id**: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  **training\_frame**: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  **validation\_frame**: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  **ignored\_columns**: (Optional) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column
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

-  **ignore\_const\_cols**: Specify whether to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  **transform**: Specify the transformation method for the training
   data: None, Standardize, Normalize, Demean, or Descale. The default
   is None.

-  **pca\_method**: Specify the algorithm to use for computing the principal components:

   -  **GramSVD**: Uses a distributed computation of the Gram matrix, followed by a local SVD using the JAMA package
   -  **Power**: Computes the SVD using the power iteration method (experimental)
   -  **Randomized**: Uses randomized subspace iteration method
   -  **GLRM**: Fits a generalized low-rank model with L2 loss function and no regularization and solves for the SVD using local matrix algebra (experimental)

-  **k**\ \*: Specify the rank of matrix approximation. The default is 1.

-  **max\_iterations**: Specify the number of training iterations. The
   value must be between 1 and 1e6 and the default is 1000.

-  **seed**: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

-  **use\_all\_factor\_levels**: Specify whether to use all factor
   levels in the possible set of predictors; if you enable this option,
   sufficient regularization is required. By default, the first factor
   level is skipped. For PCA models, this option ignores the first
   factor level of each categorical column when expanding into indicator
   columns.

-  **compute\_metrics**: Enable metrics computations on the training
   data.

-  **score\_each\_iteration**: (Optional) Specify whether to score
   during each iteration of the model training.

-  **max\_runtime\_secs**: Maximum allowed runtime in seconds for model
   training. Use 0 to disable.

Interpreting a PCA Model
~~~~~~~~~~~~~~~~~~~~~~~~

PCA output returns a table displaying the number of components specified
by the value for ``k``.

The output for PCA includes the following:

-  Model parameters (hidden)
-  Output (model category, model summary, scoring history, training
   metrics, validation metrics, iterations)
-  Archetypes
-  Standard deviation
-  Rotation
-  Importance of components (standard deviation, proportion of variance,
   cumulative proportion)

FAQ
~~~

-  **How does the algorithm handle missing values during scoring?**

  For the GramSVD and Power methods, all rows containing missing values are ignored during training. For the GLRM method, missing values are excluded from the sum over the loss function in the objective. For more information, refer to section 4 Generalized Loss Functions, equation (13), in `"Generalized Low Rank Models" <https://web.stanford.edu/~boyd/papers/pdf/glrm.pdf>`__ by Boyd et al.

-  **How does the algorithm handle missing values during testing?**

  During scoring, the test data is right-multiplied by the eigenvector matrix produced by PCA. Missing categorical values are skipped in the row product-sum. Missing numeric values propagate an entire row of NAs in the resulting projection matrix.

-  **What happens when you try to predict on a categorical level not
   seen during training?**

  New categorical levels in the test data that were not present in the training data, are skipped in the row product- sum.

-  **Does it matter if the data is sorted?**

  No, sorting data does not affect the model.

-  **Should data be shuffled before training?**

  No, shuffling data does not affect the model.

-  **What if there are a large number of columns?**

  Calculating the SVD will be slower, since computations on the Gram matrix are handled locally.

-  **What if there are a large number of categorical factor levels?**

  Each factor level (with the exception of the first, depending on whether **use\_all\_factor\_levels** is enabled) is assigned an indicator column. The indicator column is 1 if the observation corresponds to a particular factor; otherwise, it is 0. As a result, many factor levels result in a large Gram matrix and slower computation of the SVD.

-  **How are categorical columns handled during model building?**

  If the GramSVD or Power methods are used, the categorical columns are expanded into 0/1 indicator columns for each factor level. The algorithm is then performed on this expanded training frame. For GLRM, the multidimensional loss function for categorical columns is discussed in Section 6.1 of `"Generalized Low Rank Models" <https://web.stanford.edu/~boyd/papers/pdf/glrm.pdf>`__ by Boyd et al.

-  **When running PCA, is it better to create a cluster that uses many
   smaller nodes or fewer larger nodes?**

  For PCA, this is dependent on the specified ``pca_method`` parameter:

  -  For **GramSVD**, use fewer larger nodes for better performance. Forming the Gram matrix requires few intensive calculations and the main bottleneck is the JAMA library's SVD function, which is not parallelized and runs on a single machine. We do not recommend selecting GramSVD for datasets with many columns and/or categorical levels in one or more columns.
  -  For **Randomized**, use many smaller nodes for better performance, since H2O calls a few different distributed tasks in a loop, where each task does fairly simple matrix algebra computations.
  -  For **GLRM**, the number of nodes depends on whether the dataset contains many categorical columns with many levels. If this is the case, we recommend using fewer larger nodes, since computing the loss function for categoricals is an intensive task. If the majority of the data is numeric and the categorical columns have only a small number of levels (~10-20), we recommend using many small nodes in the cluster.
  -  For **Power**, we recommend using fewer larger nodes because the intensive calculations are single-threaded. However, this method is only recommended for obtaining principal component values (such as ``k << ncol(train))`` because the other methods are far more efficient.

-  **I ran PCA on my dataset - how do I input the new parameters into a
   model?**

  After the PCA model has been built using ``h2o.prcomp``, use ``h2o.predict`` on the original data frame and the PCA model to produce the dimensionality-reduced representation. Use ``cbind`` to add the predictor column from the original data frame to the data frame produced by the output of ``h2o.predict``. At this point, you can build supervised learning models on the new data frame.

PCA Algorithm
~~~~~~~~~~~~~

Let :math:`X` be an :math:`M \times N` matrix where

-  Each row corresponds to the set of all measurements on a particular
   attribute, and

-  Each column corresponds to a set of measurements from a given
   observation or trial

The covariance matrix :math:`C_{x}` is

 :math:`C_{x}=\frac{1}{n}XX^{T}`

where :math:`n` is the number of observations, and :math:`C_{x}` is a square, symmetric :math:`m \times m` matrix, the diagonal entries of which are the variances of attributes, and the off-diagonal entries are covariances between attributes.

PCA convergence is based on the method described by Gockenbach: "The rate of convergence of the power method depends on the ratio :math:`lambda_2|/|\lambda_1`. If this is small...then the power method converges rapidly. If the ratio is close to 1, then convergence is quite slow. The power method will fail if :math:`lambda_2| = |\lambda_1`." (567).

The objective of PCA is to maximize variance while minimizing
covariance.

To accomplish this, for a new matrix :math:`C_{y}` with off diagonal entries of 0, and each successive dimension of :math:`Y` ranked according to variance, PCA finds an orthonormal matrix :math:`P` such that :math:`Y=PX` constrained by the requirement that :math:`C_{y}=\frac{1}{n}YY^{T}` be a diagonal matrix.

The rows of :math:`P` are the principal components of :math:`X`.

 :math:`C_{y}=\frac{1}{n}YY^{T}=\frac{1}{n}(PX)(PX)^{T}C_{y}=PC_{x}P^{T}`.

Because any symmetric matrix is diagonalized by an orthogonal matrix of its eigenvectors, solve matrix :math:`P` to be a matrix where each row is an eigenvector of :math:`\frac{1}{n}XX^{T}=C_{x}`

Then the principal components of :math:`X` are the eigenvectors of :math:`C_{x}`, and the :math:`i^{th}` diagonal value of :math:`C_{y}` is the variance of :math:`X` along :math:`p_{i}`.

Eigenvectors of :math:`C_{x}` are found by first finding the eigenvalues :math:`\lambda` of :math:`C_{x}`.

For each eigenvalue :math:`\lambda(C-{x}-\lambda I)x =0` where :math:`x` is the eigenvector
associated with :math:`\lambda`.

Solve for :math:`x` by Gaussian elimination.

Recovering SVD from GLRM
^^^^^^^^^^^^^^^^^^^^^^^^

GLRM gives :math:`x` and :math:`y`, where :math:`x\in\rm \Bbb I \!\Bbb R^{n * k}` and :math:`y\in\rm \Bbb I \!\Bbb R ^{k*m}`

   - :math:`n` = number of rows :math:`A`

   - :math:`m` = number of columns :math:`A`

   - :math:`k` = user-specified rank
   
   - :math:`A` = training matrix

It is assumed that the :math:`x` and :math:`y` columns are independent.

1. Perform QR decomposition of :math:`x` and :math:`y^T`:

  :math:`x = QR`
  
  :math:`y^T = ZS`, where :math:`Q^TQ = I = Z^TZ`

2. Call JAMA QR Decomposition directly on :math:`y^T` to get :math:`Z\in\rm \Bbb I \! \Bbb R`, :math:`S \in \Bbb I \! \Bbb R`

  :math:`R` from QR decomposition of :math:`x` is the upper triangular factor of Cholesky of :math:`X^TX` Gram
  
  :math:`X^TX = LL^T, X = QR`
  
  :math:`X^TX= (R^TQ^T) QR = R^TR`, since :math:`Q^TQ=I => R=L^T` (transpose lower triangular)

   **Note**: In code, :math:`X^TX \over n = LL^T`

    :math:`X^TX = (L \sqrt{n})(L\sqrt{n})^T =R^TR`

    :math:`R = L^T\sqrt{n}\in\rm \Bbb I \! \Bbb R^{k * k}` reduced QR decomposition.

    For more information, refer to the `Rectangular matrix <https://en.wikipedia.org/wiki/QR_decomposition#Rectangular_matrix>`__ section of "QR Decomposition" on Wikipedia.

  :math:`XY = QR(ZS)^T = Q(RS^T)Z^T`
  
   **Note**: :math:`(RS^T)\in \rm \Bbb I \!\Bbb R`

3. Find SVD (locally) of :math:`RS^T`

  :math:`RS^T = U \sum V^T, U^TU = I = V^TV` orthogonal
  
  :math:`XY = Q(RS^T)Z^T = (QU\sum(V^T Z^T) SVD`
  
  :math:`(QU)^T(QU) = U^T Q^TQU U^TU = I`
  
  :math:`(ZV)^T(ZV) = V^TZ^TZV = V^TV = I`

Right singular vectors: :math:`ZV \in \rm \Bbb I \!\Bbb R^{m * k}`

Singular values: :math:`\sum \in \rm \Bbb I \!\Bbb R^{k * k}` diagonal

Left singular vectors: :math:`(QU) \in \rm \Bbb I \!\Bbb R^{n * k}`

References
~~~~~~~~~~

Gockenbach, Mark S. "Finite-Dimensional Linear Algebra (Discrete
Mathematics and Its Applications)." (2010): 566-567.
