Isotonic Regression
-------------------

Introduction
~~~~~~~~~~~~

An Isotonic Regression is a method of solving univariate regression problems by fitting a free-form line to an ordered sequence of observations such that the fitted line is non-decreasing while minimizing the distance of the fitted line from the observations.

H2O's Isotonic Regression implements a pool adjacent violators algorithm which uses an approach to parallelizing isotonic regression [:ref:`1<ref5>`].

MOJO Support
''''''''''''

Isotonic Regression models can be exported as `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining an Isotonic Regression Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

All parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

-  `out_of_bounds <algo-params/out_of_bounds.html>`__: Method for handling values of the ``X`` predictor that are outside of the bounds seen in training. Must be one of: ``clip`` or ``na`` (default). The default value prints "NA" for values outside of the training range.

Common parameters
'''''''''''''''''

-  `custom_metric_func <algo-params/custom_metric_func.html>`__: Specify a custom evaluation function.

-  `fold_assignment <algo-params/fold_assignment.html>`__: (Applicable only if a value for ``nfolds`` is specified and ``fold_column`` is not specified) Specify the cross-validation fold assignment scheme. One of:

    - ``AUTO`` (default; uses ``Random``)
    - ``Random``
    - ``Modulo`` (`read more about Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__)
    - ``Stratified`` (which will stratify the folds based on the response variable for classification problems)

-  `fold_column <algo-params/fold_column.html>`__: Specify the column that contains the cross-validation fold index assignment per observation.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `keep_cross_validation_fold_assignment <algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment. This option defaults to ``False`` (not enabled).

-  `keep_cross_validation_models <algo-params/keep_cross_validation_models.html>`__: Specify whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to ``True`` (enabled).

-  `keep_cross_validation_predictions <algo-params/keep_cross_validation_predictions.html>`__: Specify whether to keep the cross-validation predictions. This option defaults to ``False`` (not enabled).

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `nfolds <algo-params/nfolds.html>`__: Specify the number of folds for cross-validation. This value defaults to ``0`` (no cross-validation).

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
   
      **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `x <algo-params/x.html>`__: Specify the name of a single predictor variable to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable.

     -  Isolation Regression will treat any input as a regression problem regardless of the type of dependent variable. For categorical variables, it will train the model on indices of the labels.

Examples
~~~~~~~~

Below are simple examples showing how to use Isotonic Regression in R and Python.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    set.seed(1234)
    N <- 100
    x <- seq(N)
    y <- sample(-50:50, N, replace=TRUE) + 50 * log1p(x)
    train <- as.h2o(data.frame(x = x, y = y))
    
    isotonic <- h2o.isotonicregression(x = "x", y = "y", training_frame = train)
    print(isotonic)

   .. code-tab:: python

    import h2o
    from h2o import H2OFrame
    from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator
    from sklearn.datasets import make_regression
    import numpy as np
    h2o.init()

    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    X = X.reshape(-1)

    train = H2OFrame(np.column_stack((y, X)), column_names=["y", "X"])
    h2o_iso_reg = H2OIsotonicRegressionEstimator()
    h2o_iso_reg.train(training_frame=train, x="X", y="y", weights_column="w")
    print(h2o_iso_reg)


References
~~~~~~~~~~

.. _ref5:

1. Kearsley, A.J., Tapia, R.A., Trosset, M.W. (1996). An Approach to Parallelizing Isotonic Regression. In: Fischer, H., Riedmüller, B., Schäffler, S. (eds) Applied Mathematics and Parallel Computing. Physica-Verlag HD. https://doi.org/10.1007/978-3-642-99789-1_10
