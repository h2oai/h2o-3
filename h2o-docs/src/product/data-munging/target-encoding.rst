Target Encoding
---------------

Target encoding is the process of replacing a categorical value with the mean of the target variable. Any non-categorical columns are automatically dropped by the target encoder model. 

**Note**: You can also use target encoding to convert categorical columns to numeric. This can help improve machine learning accuracy since algorithms tend to have a hard time dealing with high cardinality columns. The jupyter notebook, `categorical predictors with tree based model <https://github.com/h2oai/h2o-tutorials/blob/master/best-practices/categorical-predictors/gbm_drf.ipynb>`__, discusses two methods for dealing with high cardinality columns:

 -  Comparing model performance after removing high cardinality columns
 -  Parameter tuning (specifically tuning ``nbins_cats`` and ``categorical_encoding``)

Target Encoding Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~

``blending``
''''''''''''

The ``blending`` parameter defines whether the target average should be weighted based on the count of the group. It is often the case, that some groups may have a small number of records and the target average will be unreliable. To prevent this, the blended average takes a weighted average of the group’s target value and the global target value.

``data_leakage_handling``
'''''''''''''''''''''''''

To control data leakage, specify one of the following data leakage handling strategies:

- ``none`` (Python)/``None`` (R): Do not holdout anything. Using whole frame for training
- ``k_fold`` (Python)/``KFold`` (R): Encodings for a fold are generated based on out-of-fold data.
- ``leave_one_out`` (Python)/``LeaveOneOut`` (R): The current row's response value is subtracted from the pre-calculated per-level frequencies.

``f``
'''''

The smoothing value is used for blending when ``blending=True`` and to calculate lambda. Smoothing controls the rate of transition between the particular level’s posterior probability and the prior probability. For smoothing values approaching infinity, it becomes a hard threshold between the posterior and the prior probability. This value defaults to 20.

``fold_column``
'''''''''''''''

Specify the name or column index of the fold column in the data. This defaults to NULL (no fold_column).

``ignored_columns``
'''''''''''''''''''

Specify the column or columns to ignore. Note that this command is only available in the Python client and in Flow. It is not available in R.

``k``
'''''

Use ``k`` to specify the inflection point value. This value is used for blending when ``blending=True`` and to calculate lambda. This determines half of the minimal sample size for which we completely trust the estimate based on the sample in the particular level of the categorical variable. This value defaults value to 10.

``noise_level``
'''''''''''''''

If random noise should be added to the target average, the ``noise_level`` parameter can be used to specify the amount of noise to be added. This value defaults to 0.01 times the range of :math:`y` of random noise.

``response_column``
'''''''''''''''''''

Use ``response_column`` to specify the response column is the column that you are attempting to predict (y-axis). 

``seed``
'''''''''

A random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.

``training_frame``
''''''''''''''''''

Specify the dataset that you want to use when you are ready to build a Target Encoding model.

``transform``
''''''''''''''

Apply transformation to target encoded columns based on the encoding maps generated during training. Available parameters include:

- ``frame``: The H2O frame to which you are applying target encoding transformations.
- ``data_leakage_handling``: To control data leakage, specify one of the following data leakage handling strategies:

  - ``none`` (Python)/``None`` (R): Do not holdout anything. Using whole frame for training
  - ``k_fold`` (Python)/``KFold`` (R): Encodings for a fold are generated based on out-of-fold data.
  - ``leave_one_out`` (Python)/``LeaveOneOut`` (R): The current row's response value is subtracted from the pre-calculated per-level frequencies.

- ``noise``: A float value specifying the amount of random noise added to the target encoding. This helps prevent overfitting. Defaults to 0.01 * range of y.

- ``seed``: A random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.


Example
~~~~~~~

In this example, we will be trying to predict ``survived`` using the popular titanic dataset: https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv. One of the predictors is ``cabin``, a categorical column with a number of unique values. To perform target encoding on ``cabin``, we will calculate the average of ``home.dest`` and ``embarked`` per cabin. So instead of using ``cabin`` as a predictor in our model, we could use the target encoding of ``cabin``.


.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    #Import the titanic dataset
    f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
    titanic <- h2o.importFile(f)
     
    # Set response column as a factor
    response <- "survived"
    titanic[response] <- as.factor(titanic[response])
     
    # Split the dataset into train and test
    splits <- h2o.splitFrame(data = titanic, ratios = .8, seed = 1234)
    train <- splits[[1]]
    test <- splits[[2]]
     
    # Choose which columns to encode
    encoded_columns <- c("home.dest", "cabin", "embarked")
     
    # Train a TE model
    te_model <- h2o.targetencoder(x = encoded_columns,
                                  y = response, 
                                  training_frame = train,
                                  fold_column = "pclass", 
                                  data_leakage_handling = "KFold")

    # New target encoded train and test sets
    train_te <- h2o.transform(te_model, train)
    test_te <- h2o.transform(te_model, test)

   .. code-tab:: python

    library(h2o)
    h2o.init()
    from h2o.estimators import H2OTargetEncoderEstimator

    #Import the titanic dataset
    titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

    # Set response column as a factor
    titanic['survived'] = titanic['survived'].asfactor()
    response='survived'

    # Split the dataset into train and test
    train, test = titanic.split_frame(ratios = [.8], seed = 1234)

    # Choose which columns to encode
    encoded_columns = ["home.dest", "cabin", "embarked"]

    # Set target encoding parameters
    blended_avg= True
    inflection_point = 3
    smoothing = 10
    # In general, the less data you have the more regularisation you need
    noise = 0.15

    # For k_fold strategy we need to provide fold column
    data_leakage_handling = "k_fold"
    fold_column = "kfold_column"
    train[fold_column] = train.kfold_column(n_folds=5, seed=3456)

    # Train a TE model
    titanic_te = H2OTargetEncoderEstimator(fold_column=fold_column,
                                           data_leakage_handling=data_leakage_handling, blending=blended_avg, k=inflection_point, f=smoothing)

    titanic_te.train(x=encoded_columns,
                                y=response,
                                training_frame=train)

    # New target encoded train and test sets
    train_te = titanic_te.transform(frame=train, data_leakage_handling="k_fold", seed=1234, noise=noise)
    test_te = titanic_te.transform(frame=test, noise=0.0)

    gbm_with_te=H2OGradientBoostingEstimator(max_depth=6,
                                             min_rows=1,
                                             fold_column=fold_column,
                                             score_tree_interval=5,
                                             ntrees=10000,
                                             sample_rate=0.8,
                                             col_sample_rate=0.8,
                                             seed=1234,
                                             stopping_rounds=5,
                                             stopping_metric="auto",
                                             stopping_tolerance=0.001,
                                             model_id="gbm_with_te")

    # Training is based on training data with early stopping based on xval performance
    x_with_te = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin_te", "embarked_te", "home.dest_te"]
    gbm_with_te.train(x=x_with_te, y=response, training_frame=train_te)

    # To prevent overly optimistic results ( overfitting to xval metrics ) metric is computed on yet unseen test split
    my_gbm_metrics = gbm_with_te.model_performance(test_te)
    auc_with_te = my_gbm_metrics.auc()

    # auc_with_te = 0.89493

    # Train a GBM estimator
    gbm_baseline=H2OGradientBoostingEstimator(max_depth=6,
                                              min_rows=1,
                                              fold_column=fold_column,
                                              score_tree_interval=5,
                                              ntrees=10000,
                                              sample_rate=0.8,
                                              col_sample_rate=0.8,
                                              seed=1234,
                                              stopping_rounds=5,
                                              stopping_metric="auto",
                                              stopping_tolerance=0.001,
                                              model_id="gbm_baseline")

    x_baseline = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin", "embarked", "home.dest"]
    gbm_baseline.train(x=x_baseline, y=response, training_frame=train)

    # Measuring performance on a test split
    gbm_baseline_metrics = gbm_baseline.model_performance(test)
    auc_baseline = gbm_baseline_metrics.auc()

    # auc_baseline = 0.84174

    # Performance is better with target encoding being applied:
    # auc_with_te = 0.89493   >    auc_baseline = 0.84174

References
~~~~~~~~~~

-  `Target Encoding in H2O-3 Demo <https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/rdemo.target_encode.R>`__
-  `Automatic Feature Engineering Webinar <https://www.youtube.com/watch?v=VMTKcT1iHww>`__
-   Daniele Micci-Barreca. 2001. A preprocessing scheme for high-cardinality categorical attributes in classification and prediction problems. SIGKDD Explor. Newsl. 3, 1 (July 2001), 27-32.
-  `Zumel, Nina B. and John Mount. "vtreat: a data.frame Processor for Predictive Modeling." (2016). <https://arxiv.org/abs/1611.09477>`__
