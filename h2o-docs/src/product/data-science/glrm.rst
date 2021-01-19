.. _glrm:

Generalized Low Rank Models (GLRM)
----------------------------------

Introduction
~~~~~~~~~~~~

Generalized Low Rank Models (GLRM) is an algorithm for dimensionality reduction of a dataset. It is a general, parallelized optimization algorithm that applies to a variety of loss and regularization functions. Categorical columns are handled by expansion into 0/1 indicator columns for each level. With this approach, GLRM is useful for reconstructing missing values and identifying important features in heterogeneous data.

What is a Low-Rank Model?
~~~~~~~~~~~~~~~~~~~~~~~~~

Given large collections of data with numeric and categorical values, entries in the table may be noisy or even missing altogether. Low rank models facilitate the understanding of tabular data by producing a condensed vector representation for every row and column in the dataset. Specifically, given a data table A with m rows and n columns, a GLRM consists of a decomposition of A into numeric matrices X and Y. The matrix X has the same number of rows as A, but only a small, user-specified number of columns k. The matrix Y has k rows and d columns, where d is equal to the total dimension of the embedded features in A. For example, if A has 4 numeric columns and 1 categorical column with 3 distinct levels (e.g., red, blue and green), then Y will have 7 columns. When A contains only numeric features, the number of columns in A and Y are identical.

.. figure:: ../images/glrm_matrix_decomposition.png
   :alt: 

Both X and Y have practical interpretations. Each row of Y is an archetypal feature formed from the columns of A, and each row of X corresponds to a row of A projected into this reduced dimension feature space. We can approximately reconstruct A from the matrix product XY, which has rank k. The number k is chosen to be much less than both m and n: a typical value for 1 million rows and 2,000 columns of numeric data is k = 15. The smaller k is, the more compression gained from the low-rank representation.

Defining a GLRM Model
~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. NOTE: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset to calculate validation metrics.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns are used.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be exclude from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: (Optional) Specify whether to ignore constant training columns, since no information can be gained from them. This option is enabled by default.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Specify whether to score during each iteration of the model training (disabled by default).

-  **representation_name**: Specify the frame key to save the resulting X.

-  `transform <algo-params/transform.html>`__: Specify the transformation method for numeric columns in the training data: None, Standardize, Normalize, Demean, or Descale. The default is None.

-  `k <algo-params/k.html>`__: (Required) Specify the rank of matrix approximation (defaults to 1).

-  **loss**: Specify the numeric loss function: Quadratic (default), Absolute, Huber, Poisson, Hinge, or Periodic.

-  **loss_by_col**: Specify the loss function by column override: Quadratic, Absolute, Huber, Poisson, Hinge, or Periodic, Categorical, or Ordinal.

-  **loss_by_col_idx**: Specify the loss function by column index override.

-  **multi_loss**: Specify either **Categorical** (default) or **Ordinal** for the categorical loss function.

-  **period**: When ``loss=periodic``, specify the length of the period (defaults to 1).

-  **regularization_x**: Specify the regularization function for the X matrix: None (default), Quadratic, L2, L1, NonNegative, OneSparse, UnitOneSparse, or Simplex.

-  **regularization_y**: Specify the regularization function for the Y matrix: None (default), Quadratic, L2, L1, NonNegative, OneSparse, UnitOneSparse, or Simplex.

-  **gamma_x**: Specify the regularization weight on the X matrix (defaults to 0).

-  **gamma_y**: Specify the regularization weight on the Y matrix (defaults to 0).

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the maximum number of training iterations. The range is 0 to 1e6, and the value defaults to 1000.

-  **max_updates**: Specify the maximum number of updates (defaults to 2000).

-  **init_step_size**: Specify the initial step size (defaults to 1).

-  **min_step_size**: Specify the minimum step size (defaults to 0.0001).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This value defaults to -1 (time-based random number).

-  `init <algo-params/init1.html>`__: Specify the initialization mode: Random, Furthest, PlusPlus (default), or User.

-  **svd_method**: Specify the method for computing SVD during initialization: GramSVD, Power, Randomized (default).

       **Caution**: Randomized is currently experimental.

-  **user_y**: (Optional) Specify the initial Y value.

-  **user_x**: (Optional) Specify the initial X value.

-  **expand_user_y**: Specify whether to expand categorical columns in the user-specified initial Y value. This value is enabled by default.

-  **impute_original**: Specify whether to reconstruct the original training data by reversing the data transform after projecting archetypes. This option is disabled by default.

-  **recover_svd**: Specify whether to recover singular values and eigenvectors of XY. This option is disabled by default.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Specify the maximum allowed runtime in seconds for model training. Set to 0 (disabled) by default.

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

Examples
~~~~~~~~

Below is a simple example showing how to build a Generalized Low Rank model.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the USArrests dataset into H2O:
    arrests <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")

    # Split the dataset into a train and valid set:
    arrests_splits <- h2o.splitFrame(data = arrests, ratios = 0.8, seed = 1234)
    train <- arrests_splits[[1]]
    valid <- arrests_splits[[2]]

    # Build and train the model:
    glrm_model = h2o.glrm(training_frame = train, 
                          k = 4, 
                          loss = "Quadratic", 
                          gamma_x = 0.5, 
                          gamma_y = 0.5,  
                          max_iterations = 700, 
                          recover_svd = TRUE, 
                          init = "SVD", 
                          transform = "STANDARDIZE")

    # Eval performance:
    arrests_perf <- h2o.performance(glrm_model)

    # Generate predictions on a validation set (if necessary):
    arrests_pred <- h2o.predict(glrm_model, newdata = valid)


   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OGeneralizedLowRankEstimator
    h2o.init()

    # Import the USArrests dataset into H2O:
    arrestsH2O = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")

    # Split the dataset into a train and valid set:
    train, valid = arrestsH2O.split_frame(ratios=[.8], seed=1234)

    # Build and train the model:
    glrm_model = H2OGeneralizedLowRankEstimator(k=4, 
                                                loss="quadratic", 
                                                gamma_x=0.5, 
                                                gamma_y=0.5, 
                                                max_iterations=700, 
                                                recover_svd=True, 
                                                init="SVD", 
                                                transform="standardize")
    glrm_model.train(training_frame=train) 


FAQ
~~~

-  **What types of data can be used with GLRM?**

   GLRM can handle mixed numeric, categorical, ordinal and Boolean data with an arbitrary number of missing values. It allows the user to apply regularization to X and Y, imposing restrictions like non-negativity appropriate to a particular data science context.

-  **What are the benefits to using low rank models?**

   -  **Memory**: Saving only the X and Y matrices can significantly reduce the amount of memory required to store a large data set. A file that is 10 GB can be compressed down to 100 MB. When we need the original data again, we can reconstruct it on the fly from X and Y with minimal loss in accuracy.
   -  **Speed**: GLRM can be used to compress data with high-dimensional, heterogeneous features into a few numeric columns. This leads to a huge speed-up in model building and prediction, especially by machine learning algorithms that scale poorly with the size of the feature space.
   -  **Feature Engineering**: The Y matrix represents the most important combination of features from the training data. These condensed features (called archetypes) can be analyzed, visualized, and incorporated into various data science applications.
   -  **Missing Data Imputation**: Reconstructing a data set from X and Y will automatically impute missing values. This imputation is accomplished by intelligently leveraging the information contained in the known values of each feature, as well as user-provided parameters such as the loss function.

References
~~~~~~~~~~

`Udell, Madeline, Corinne Horn, Reza Zadeh, and Stephen Boyd. "Generalized low rank models." arXiv preprint arXiv:1410.0342, 2014. <http://arxiv.org/abs/1410.0342>`_

`Hamner, S.R., Delp, S.L. Muscle contributions to fore-aft and vertical body mass center accelerations over a range of running speeds. Journal of Biomechanics, vol 46, pp 780-787. (2013) <http://nmbl.stanford.edu/publications/pdf/Hamner2012.pdf>`_
