
.. _adaboost:

AdaBoost
--------
**Note**: This is a beta version of the algorithm.

Introduction
~~~~~~~~~~~~

AdaBoost, short for Adaptive Boosting, is a powerful and versatile machine learning ensemble technique. It operates by combining the strengths of multiple weak or base learners, typically decision trees with limited depth, to create a strong and accurate predictive model. AdaBoost assigns higher weights to misclassified data points in each iteration, allowing subsequent weak learners to focus on those instances, progressively refining the model's performance. The final model is a weighted sum of the weak learners' predictions, resulting in a robust and flexible classifier capable of effectively handling complex datasets and improving generalization. AdaBoost's emphasis on misclassified instances and its iterative learning process make it a popular choice for classification tasks in various domains, showcasing its ability to adapt and improve predictive performance.

H2O’s implementation of AdaBoost follows the `Rojas, R. (2009), 'AdaBoost and the Super Bowl of Classifiers A Tutorial Introduction to Adaptive Boosting' <https://citeseerx.ist.psu.edu/viewdoc/download;jsessionid=BD98C2F2A8C7EEE8314FA129FBE61984?doi=10.1.1.382.9086&rep=rep1&type=pdf>`__ specification. It can be used to solve binary classification problems only.

MOJO Support
''''''''''''

AdaBoost currently does not support `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining an AdaBoost Model
~~~~~~~~~~~~~~~~~~~~~~~~~~

Algorithm-specific parameters
'''''''''''''''''''''''''''''

-  `learn_rate <algo-params/learn_rate.html>`__: Specify the learning rate. The range is 0.0 to 1.0, and the default value is ``0.5``.

-  **nlearners**: Number of AdaBoost weak learners. This option defaults to ``50``.

-  **weak_learner**: Choose a weak learner type. Must be one of: ``"AUTO"``, ``"DRF"``, ``"GBM"``, ``"GLM"``, or ``DEEP_LEARNING``. Defaults to ``"AUTO"`` (which means ``"DRF"``).

      - ``DRF``: Trains only one tree in each iteration with the following parameters: 

        .. code-block:: bash

            (mtries=1, 
             min_rows=1, 
             sample_rate=1, 
             max_depth=1)

      - ``GBM``: Trains only one tree in each iteration with the following parameters: 

        .. code-block:: bash

            (mtries=1, 
             min_rows=1, 
             sample_rate=1, 
             max_depth=1, 
             learn_rate=0.1)

      - ``GLM``: Trains a binary classifier with ``max_iterations=50``.

      - ``DEEP_LEARNING``: Trains a binary classifier with ``(epochs=10, hidden=[2])``.

-  **weak_learner_params**:  You can specify a dict/list of customized parameters for your specified ``weak_learner`` algorithm. For example, if you use a ``GBM``, you can specify ``{'ntrees': 1, 'max_depth': 10}`` in Python or ``list(ntrees = 1, max_depth = 10)`` in R. AdaBoost does not apply defaults from the ``weak_learner`` at all. The defaults of the algorithm itself will be used instead.
Common parameters
'''''''''''''''''

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Only the ordinal nature of encoding is used for splitting in the case of AdaBoost. Specify one of the following encoding schemes for handling categorical features:

      - ``auto`` or ``AUTO`` (default): Allow the algorithm to decide. For AdaBoost, the algorithm will automatically perform ``enum`` encoding.
      - ``enum`` or ``Enum``: 1 column per categorical feature.
      - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** (10) most frequent levels.
      - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels.
      - ``binary`` or ``Binary``: No more than 32 columns per categorical feature.
      - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only.
      - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.).
      - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.).

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This value defaults to ``-1`` (time-based random number).

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 

    **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified ``weights_column`` must be included in the specified ``training_frame``. By default the AdaBoost algorithm generates constant column with value ``1``

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use in building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable. The data can be only categorical binary.

Examples
~~~~~~~~

This code example demonstrates how to build and train an AdaBoost model using H2O-3 in R and Python, including data import, model configuration, and prediction generation.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the prostate dataset into H2O:
    prostate <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
    predictors <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
    response <- "CAPSULE"
    prostate[response] <- as.factor(prostate[response])

    # Build and train the model:
    adaboost_model <- h2o.adaBoost(nlearners=50,
                                   learn_rate = 0.5,
                                   weak_learner = "DRF", 
                                   x = predictors,
                                   y = response, 
                                   training_frame = prostate)

    # Generate predictions:
    h2o.predict(adaboost_model, prostate)


   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OAdaBoostEstimator
    h2o.init()
    
    # Import the prostate dataset into H2O:
    prostate = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    
    # Build and train the model:
    adaboost_model = H2OAdaBoostEstimator(nlearners=50,
                                          learn_rate = 0.8, 
                                          weak_learner = "DRF",
                                          seed=0xBEEF)
    adaboost_model.train(y = "CAPSULE", training_frame = prostate)
    
    # Generate predictions:
    pred = adaboost_model.predict(prostate)
    pred


References
~~~~~~~~~~

- Rojas, R. (2009), 'AdaBoost and the Super Bowl of Classifiers A Tutorial Introduction to Adaptive Boosting'.
- Niculescu-Mizil, Alexandru & Caruana, Rich. (2012). Obtaining Calibrated Probabilities from Boosting. 
- Y. Freund, R. Schapire, “A Decision-Theoretic Generalization of on-Line Learning and an Application to Boosting”, 1995.
 
