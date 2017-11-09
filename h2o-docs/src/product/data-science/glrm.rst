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

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate the accuracy of the model.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns are used.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be exclude from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: (Optional) Specify whether to ignore constant training columns, since no information can be gained from them. This option is selected by default.

-  `score_each_iteration <algo-params/score_each_iteration.html>`__: (Optional) Specify whether to score during each iteration of the model training.

-  **loading_name**: Specify the frame key to save the resulting X.

-  `transform <algo-params/transform.html>`__: Specify the transformation method for the training data: None, Standardize, Normalize, Demean, or Descale. The default is None.

-  `k <algo-params/k.html>`__: (Required) Specify the rank of matrix approximation.

-  **loss**: Specify the numeric loss function: Quadratic, Absolute, Huber, Poisson, Hinge, or Periodic.

-  **multi_loss**: Specify either **Categorical** or **Ordinal** for the categorical loss function.

-  **period**: When ``loss=periodic``, specify the length of the period.

-  **regularization_x**: Specify the regularization function for the X matrix: None, Quadratic, L2, L1, NonNegative, OneSparse, UnitOneSparse, or Simplex.

-  **regularization_y**: Specify the regularization function for the Y matrix: None, Quadratic, L2, L1, NonNegative, OneSparse, UnitOneSparse, or Simplex.

-  **gamma_x**: Specify the regularization weight on the X matrix.

-  **gamma_y**: Specify the regularization weight on the Y matrix.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the maximum number of training iterations. The range is 0 to 1e6.

-  **max_updates**: Specify the maximum number of updates.

-  **init_step_size**: Specify the initial step size.

-  **min_step_size**: Specify the minimum step size.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations.

-  `init <algo-params/init.html>`__: Specify the initialization mode: Random, SVD, PlusPlus, or User.

-  **svd_method**: Specify the method for computing SVD during initialization: GramSVD, Power, Randomized.

       **Caution**: Randomized is currently experimental.

-  **user_y**: (Optional) Specify the initial Y value.

-  **user_x**: (Optional) Specify the initial X value.

-  **expand_user_y**: Specify whether to expand categorical columns in the user-specified initial Y value.

-  **impute_original**: Specify whether to reconstruct the original training data by reversing the data transform after projecting archetypes.

-  **recover_svd**: Specify whether to recover singular values and eigenvectors of XY.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Specify the maximum allowed runtime in seconds for model training. Use 0 to disable.

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
