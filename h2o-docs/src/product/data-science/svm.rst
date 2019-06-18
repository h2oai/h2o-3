Support Vector Machine (SVM)
----------------------------

Introduction
~~~~~~~~~~~~

In machine learning, support vector machines are supervised learning models with associated learning algorithms that analyze data used for classification and regression analysis. Given a set of training examples, each marked as belonging to one or the other of two categories, an SVM training algorithm builds a model that assigns new examples to one category or the other.

Defining an SVM Model
~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable. The data can be numeric or categorical.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  **hyper_param**: Specify the penalty parameter C of the error term. This value defaults to 1.

-  **kernel_type**: Specify the type of kernel to use. Note that currently only `gaussian` is supported.

-  **gamma**: Specify the coefficient of the kernel. This value defaults to -1.

-  **rank_ratio**: Specify the desired rank of the `Incomplete Cholesky Facorization <https://en.wikipedia.org/wiki/Incomplete_Cholesky_factorization>`__ (ICF) matrix expressed as an ration of number of input rows.

-  **positive_weight**: Specify the weight of the positive (+1) class of observations. This value defaults to 1.

-  **negative_weight**: Specify the weight of the negative (-1) class of observations. This value defaults to 1.

-  **sv_threshold**: Specify the threshold for accepting a candidate observation into the set of support vectors. This value defaults to 0.0001.

-  **fact_threshold**: Specify the convergence threshold of the `Incomplete Cholesky Facorization <https://en.wikipedia.org/wiki/Incomplete_Cholesky_factorization>`__ (ICF)

-  **feasible_threshold**: Specify the convergence threshold for primal-dual residuals in the `Interior Point Method <https://en.wikipedia.org/wiki/Interior-point_method>`__ (IPM) iteration. This value defaults to 0.001.

-  **surrogate_gap_threshold**: Specify the feasibility criterion of the surrogate duality gap (eta). This value defaults to 0.001.

-  **mu_factor**: Specify to increase the mean value by this factor. This value defaults to 10.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the maximum number of iteration of the algorithm. This value defaults to 200.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

Interpreting an SVM Model
~~~~~~~~~~~~~~~~~~~~~~~~~

SVM Algorithm
~~~~~~~~~~~~~

References
~~~~~~~~~~

Implementation of ICF based on https://static.googleusercontent.com/media/research.google.com/en//pubs/archive/34638.pdf