Principal Component Analysis (PCA)
----------------------------------

Introduction
~~~~~~~~~~~~

Principal Components Analysis (PCA) is closely related to Principal Components Regression. The algorithm is carried out on a set of possibly collinear features and performs a transformation to produce a new set of uncorrelated features.

PCA is commonly used to model without regularization or perform dimensionality reduction. It can also be useful to carry out as a preprocessing step before distance-based algorithms such as K-Means since PCA guarantees that all dimensions of a manifold are orthogonal.

MOJO Support
''''''''''''

PCA currently only supports exporting `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining a PCA Model
~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

-  `compute_metrics <algo-params/compute_metrics.html>`__: Enable metrics computations on the training data. This option defaults to ``True`` (enabled).

-  `impute_missing <algo-params/impute_missing.html>`__: Specifies whether to impute missing entries with the column mean value. This option defaults to ``False`` (disabled).

-  `k <algo-params/k.html>`__: Specify the rank of matrix approximation. This can be a value from 1 to the minimum of (total number of rows, total number of columns) in the dataset. This option defaults to ``1``.

-  `pca_impl <algo-params/pca_impl.html>`__: Specify the implementation to use for computing PCA (via SVD or EVD). Available options include:

    - ``mtj_evd_densematrix``: Eigenvalue decompositions for dense matrix using Matrix Toolkit Java (`MTJ <https://github.com/fommil/matrix-toolkits-java/>`__)
    - ``mtj_evd_symmmatrix``: Eigenvalue decompositions for symmetric matrix using Matrix Toolkit Java (`MTJ <https://github.com/fommil/matrix-toolkits-java/>`__) (default)
    - ``mtj_svd_densematrix``: Singular-value decompositions for dense matrix using Matrix Toolkit Java (`MTJ <https://github.com/fommil/matrix-toolkits-java/>`__)
    - ``jama``: Eigenvalue decompositions for dense matrix using Java Matrix (`JAMA <http://math.nist.gov/javanumerics/jama/>`__)

-  `pca_method <algo-params/pca_method.html>`__: Specify the algorithm to use for computing the principal components:

    -  ``gram_s_v_d`` (default): Uses a distributed computation of the Gram matrix, followed by a local SVD using the JAMA package
    -  ``power``: Computes the SVD using the power iteration method (experimental)
    -  ``randomized``: Uses randomized subspace iteration method
    -  ``glrm``: Fits a generalized low-rank model with L2 loss function and no regularization and solves for the SVD using  local matrix algebra (experimental)

Common parameters
'''''''''''''''''

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the number of training iterations. The option must be set between 1 and 1e6 and the default value is ``1000``.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option defaults to ``0`` (disabled).

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: Specify whether to score during each iteration of the model training. This option defaults to ``False`` (disabled).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
    
    **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `transform <algo-params/transform.html>`__: Specify the transformation method for numeric columns in the training data. One of: 

    - ``none`` (default)
    - ``standardize``
    - ``normalize``
    - ``demean``
    - ``descale`` 

-  `use_all_factor_levels <algo-params/use_all_factor_levels.html>`__: Specify whether to use all factor levels in the possible set of predictors; if you enable this option, sufficient regularization is required. By default, the first factor level is skipped. For PCA models, this option ignores the first  factor level of each categorical column when expanding into indicator columns. This option defaults to ``False`` (disabled).

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset to calculate validation metrics.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns are used.

Interpreting a PCA Model
~~~~~~~~~~~~~~~~~~~~~~~~

PCA output returns a table displaying the number of components specified by the value for ``k``.

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

  Each factor level (with the exception of the first, depending on whether ``use_all_factor_levels`` is enabled) is assigned an indicator column. The indicator column is 1 if the observation corresponds to a particular factor; otherwise, it is 0. As a result, many factor levels result in a large Gram matrix and slower computation of the SVD.

-  **How are categorical columns handled during model building?**

  If the GramSVD or Power methods are used, the categorical columns are expanded into 0/1 indicator columns for each factor level. The algorithm is then performed on this expanded training frame. For GLRM, the multidimensional loss function for categorical columns is discussed in Section 6.1 of `"Generalized Low Rank Models" <https://web.stanford.edu/~boyd/papers/pdf/glrm.pdf>`__ by Boyd et al.

-  **When running PCA, is it better to create a cluster that uses many smaller nodes or fewer larger nodes?**

  For PCA, this is dependent on the specified ``pca_method`` parameter:

  -  For **GramSVD**, use fewer larger nodes for better performance. Forming the Gram matrix requires few intensive calculations and the main bottleneck is the JAMA library's SVD function, which is not parallelized and runs on a single machine. We do not recommend selecting GramSVD for datasets with many columns and/or categorical levels in one or more columns.
  -  For **Randomized**, use many smaller nodes for better performance, since H2O calls a few different distributed tasks in a loop, where each task does fairly simple matrix algebra computations.
  -  For **GLRM**, the number of nodes depends on whether the dataset contains many categorical columns with many levels. If this is the case, we recommend using fewer larger nodes, since computing the loss function for categoricals is an intensive task. If the majority of the data is numeric and the categorical columns have only a small number of levels (~10-20), we recommend using many small nodes in the cluster.
  -  For **Power**, we recommend using fewer larger nodes because the intensive calculations are single-threaded. However, this method is only recommended for obtaining principal component values (such as ``k << ncol(train))`` because the other methods are far more efficient.

-  **I ran PCA on my dataset - how do I input the new parameters into a model?**

  After the PCA model has been built using ``h2o.prcomp``, use ``h2o.predict`` on the original data frame and the PCA model to produce the dimensionality-reduced representation. Use ``cbind`` to add the predictor column from the original data frame to the data frame produced by the output of ``h2o.predict``. At this point, you can build supervised learning models on the new data frame.

- **How can I evaluate and choose the appropriate set of target dimensions for data?** 

  The set of target dimensions can be chosen by inspecting the cumulative proportion of variance explained. (For example, select the number of components that explain 95% variance in data.) This information can be displayed using ``pca_model.summary()``. You can also view the variable importances using ``@model$importance`` in R or ``varimp()`` in Python

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

PCA convergence is based on the method described by Gockenbach: "The rate of convergence of the power method depends on the ratio :math:`|\lambda_2|/|\lambda_1|`. If this is small...then the power method converges rapidly. If the ratio is close to 1, then convergence is quite slow. The power method will fail if :math:`|\lambda_2| = |\lambda_1|`." (567).

The objective of PCA is to maximize variance while minimizing
covariance.

To accomplish this, for a new matrix :math:`C_{y}` with off diagonal entries of 0, and each successive dimension of :math:`Y` ranked according to variance, PCA finds an orthonormal matrix :math:`P` such that :math:`Y=PX` constrained by the requirement that :math:`C_{y}=\frac{1}{n}YY^{T}` be a diagonal matrix.

The rows of :math:`P` are the principal components of :math:`X`.

     :math:`C_{y}=\frac{1}{n}YY^{T}=\frac{1}{n}(PX)(PX)^{T}=P(\frac{1}{n}XX^{T})P^{T}=PC_{x}P^{T}`.

Because any symmetric matrix is diagonalized by an orthogonal matrix of its eigenvectors, solve matrix :math:`P` to be a matrix where each row is an eigenvector of :math:`\frac{1}{n}XX^{T}=C_{x}`

Then the principal components of :math:`X` are the eigenvectors of :math:`C_{x}`, and the :math:`i^{th}` diagonal value of :math:`C_{y}` is the variance of :math:`X` along :math:`p_{i}`.

Eigenvectors of :math:`C_{x}` are found by first finding the eigenvalues :math:`\lambda` of :math:`C_{x}`.

For each eigenvalue :math:`(C_{x}-\lambda I)x =0` where :math:`x` is the eigenvector
associated with :math:`\lambda`.

Solve for :math:`x` by Gaussian elimination.

Recovering SVD from GLRM
''''''''''''''''''''''''

GLRM gives :math:`x` and :math:`y`, where :math:`x\in\rm \Bbb I \!\Bbb R^{n \times k}` and :math:`y\in\rm \Bbb I \!\Bbb R ^{k \times m}`

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

   **Note**: In code, :math:`\frac{X^TX}{n} = LL^T`

    :math:`X^TX = (L \sqrt{n})(L\sqrt{n})^T =R^TR`

    :math:`R = L^T\sqrt{n}\in\rm \Bbb I \! \Bbb R^{k \times k}` reduced QR decomposition.

    For more information, refer to the `Rectangular matrix <https://en.wikipedia.org/wiki/QR_decomposition#Rectangular_matrix>`__ section of "QR Decomposition" on Wikipedia.

  :math:`XY = QR(ZS)^T = Q(RS^T)Z^T`
  
   **Note**: :math:`(RS^T)\in \rm \Bbb I \!\Bbb R`

3. Find SVD (locally) of :math:`RS^T`

  :math:`RS^T = U \Sigma V^T, U^TU = I = V^TV` orthogonal
  
  :math:`XY = Q(RS^T)Z^T = (QU)\Sigma(V^T Z^T)` SVD
  
  :math:`(QU)^T(QU) = U^T Q^TQU = U^TU = I`
  
  :math:`(ZV)^T(ZV) = V^TZ^TZV = V^TV = I`

Right singular vectors: :math:`ZV \in \rm \Bbb I \!\Bbb R^{m \times k}`

Singular values: :math:`\Sigma \in \rm \Bbb I \!\Bbb R^{k \times k}` diagonal

Left singular vectors: :math:`QU \in \rm \Bbb I \!\Bbb R^{n \times k}`

Examples
~~~~~~~~

This example demonstrates how to build a Principal Component Analysis (PCA) model using H2O-3 for dimensionality reduction. The model is trained on the birds dataset, which is split into training and validation sets. The PCA is configured to retain five principal components, standardize the data, and handle missing values. After training, coefficients to reconstruct each row of the dataset using the principal components are generated when the predict function is called.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the birds dataset into H2O:
    birds <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")

    # Split the dataset into a train and valid set:
    birds_split <- h2o.splitFrame(birds, ratios = 0.8, seed = 1234)
    train <- birds_split[[1]]
    valid <- birds_split[[2]]

    # Build and train the model:
    birds_pca <- h2o.prcomp(training_frame = train, 
                            k = 5, 
                            use_all_factor_levels = TRUE, 
                            pca_method = "GLRM", 
                            transform = "STANDARDIZE", 
                            impute_missing = TRUE)

    # Generate predictions on a validation set (if necessary):
    pred <- h2o.predict(birds_pca, newdata = valid)


   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OPrincipalComponentAnalysisEstimator
    h2o.init()

    # Import the birds dataset into H2O:
    birds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")

    # Split the dataset into a train and valid set:
    train, valid = birds.split_frame(ratios = [.8], seed = 1234)

    # Build and train the model:
    birds_pca = H2OPrincipalComponentAnalysisEstimator(k = 5, 
                                                       use_all_factor_levels = True, 
                                                       pca_method = "glrm", 
                                                       transform = "standardize", 
                                                       impute_missing = True)
    birds_pca.train(training_frame = train)

    # Generate predictions on a validation set (if necessary):
    pred = birds_pca.predict(valid)


References
~~~~~~~~~~

Gockenbach, Mark S. "Finite-Dimensional Linear Algebra (Discrete
Mathematics and Its Applications)." (2010): 566-567.
