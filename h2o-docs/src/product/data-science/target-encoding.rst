Target Encoding
---------------

Target encoding is the process of replacing a categorical value with the mean of the target variable. Any non-categorical columns are automatically dropped by the target encoder model. 

**Note**: You can also use target encoding to convert categorical columns to numeric. This can help improve machine learning accuracy since algorithms tend to have a hard time dealing with high cardinality columns. The jupyter notebook, `categorical predictors with tree based model <https://github.com/h2oai/h2o-tutorials/blob/master/best-practices/categorical-predictors/gbm_drf.ipynb>`__, discusses two methods for dealing with high cardinality columns:

 -  Comparing model performance after removing high cardinality columns
 -  Parameter tuning (specifically tuning ``nbins_cats`` and ``categorical_encoding``)

Target Encoding Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset that you want to use when you are ready to build a Target Encoding model.

-  `y <algo-params/y.html>`__: (Required) Specify the target column that you are attempting to predict. The data can be numeric or categorical.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the categorical columns that will be target encoded. If ``x`` is missing, then all **categorical** columns except ``y`` are encoded.

-  `data_leakage_handling <algo-params/data_leakage_handling.html>`__: Specify one of the following data leakage handling strategies:

    - ``none``: Do not holdout anything. Using whole frame for training. This is the default value.
    - ``k_fold``: Encodings for a fold are generated based on out-of-fold data.
    - ``leave_one_out``: The current row's response value is subtracted from the pre-calculated per-level frequencies.

-  `fold_column <algo-params/fold_column.html>`__: Specify the name or column index of the fold column in the data. Applicable only if ``data_leakage_handling="k_fold"``. This defaults to None/NULL (no fold_column).

-  **keep_original_categorical_columns**: Specify if the original categorical columns that are being encoded should be included in the result frame. This value defaults to True/TRUE.

-  `blending <algo-params/inflection_point.html>`__: Specify whether the target average should be weighted based on the count of the group. It is often the case that some groups may have a small number of records and the target average will be unreliable. To prevent this, the blended average takes a weighted average of the group’s target value and the global target value. This value defaults to False/FALSE.

-  `inflection_point <algo-params/inflection_point.html>`__: Specify the inflection point value. This value is used for blending when ``blending=True`` and to calculate lambda. This determines half of the minimal sample size for which we completely trust the estimate based on the sample in the particular level of the categorical variable. This value defaults to 10.

-  `smoothing <algo-params/smoothing.html>`__: The smoothing value is used for blending when ``blending=True`` and to calculate lambda. Smoothing controls the rate of transition between the particular level’s posterior probability and the prior probability. For smoothing values approaching infinity, it becomes a hard threshold between the posterior and the prior probability. This value defaults to 20.

-  `noise <algo-params/noise.html>`__: Specify the amount of random noise that should be added to the target average in order to prevent overfitting. Set to 0 to disable noise. This value defaults to 0.01 times the range of :math:`y`.

-  `seed <algo-params/seed.html>`__: The seed for the pseudorandom number generator, mainly used to generate draws from the uniform distribution for random noise. Defaults to -1 (random seed).

Target Encoding Model Methods
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``transform``
''''''''''''''

Apply transformation to target encoded columns based on the encoding maps generated during training. Available parameters include:

- ``frame``: The H2O frame to which you are applying target encoding transformations.
- ``blending``: User can override the ``blending`` value defined on the model.
- ``inflection_point``: User can override the ``inflection_point`` value defined on the model.
- ``smoothing``: User can override the ``smoothing`` value defined on the model.
- ``noise``: User can override the ``noise`` value defined on the model.
- ``as_training``: User should set this parameter to True/TRUE when transforming the training dataset, and leave it to False/FALSE when applying to non-training data. Defaults to False/FALSE.


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
    seed <- 1234
    splits <- h2o.splitFrame(titanic, seed = seed, ratios = c(0.8))

    train <- splits[[1]]
    test <- splits[[2]]

    # For k_fold strategy we need to provide fold column
    train$fold <- h2o.kfold_column(train, nfolds = 5, seed = seed)

    # Choose which columns to encode
    encoded_columns <- c('home.dest', 'cabin', 'embarked')

    # Train a TE model
    target_encoder <- h2o.targetencoder(training_frame = train, 
                                        x = encoded_columns, 
                                        y = "survived", 
                                        fold_column = "fold", 
                                        data_leakage_handling = "k_fold", 
                                        blending = TRUE, 
                                        inflection_point = 3, 
                                        smoothing = 10, 
                                        noise = 0.15,     # In general, the less data you have the more regularisation you need
                                        seed = seed)

    # New target encoded train and test sets
    transformed_train <- h2o.transform(target_encoder, train, as_training=TRUE)
    transformed_test <- h2o.transform(target_encoder, test, noise=0)

    # Train a GBM (with TE) model
    ignored_columns <- c("boat", "ticket", "name", "body")
    features_with_te <- setdiff(names(transformed_train), c(response, encoded_columns, ignored_columns))

    gbm_with_te <- h2o.gbm(x = features_with_te,
                           y = response,
                           training_frame = transformed_train,
                           fold_column = "fold",
                           model_id = "gbm_with_te")

    # Measuring performance on a transformed_test split
    with_te_test_predictions <- predict(gbm_with_te, transformed_test)

    auc_with_te <- h2o.auc(h2o.performance(gbm_with_te, transformed_test))
    print(paste0("GBM AUC TEST: ", round(auc_with_te, 5)))


    # Train a baseline GBM model
    features <- setdiff(names(train), c(response,ignored_columns))

    gbm_baseline <- h2o.gbm(x = features,
                            y = response,
                            training_frame = train,
                            fold_column = "fold",
                            model_id = "gbm_baseline")

    # Measuring performance on a test split
    baseline_test_predictions <- predict(gbm_baseline, test)

    auc_baseline <- h2o.auc(h2o.performance(gbm_baseline, test))
    print(paste0("GBM AUC TEST: ", round(auc_baseline, 5)))

    # Performance is better with target encoding being applied:
    # auc_with_te = 0.8805   >    auc_baseline = 0.84105

   .. code-tab:: python

    import h2o
    h2o.init()
    from h2o.estimators import H2OTargetEncoderEstimator
    from h2o.estimators.gbm import H2OGradientBoostingEstimator

    #Import the titanic dataset
    titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

    # Set response column as a factor
    titanic['survived'] = titanic['survived'].asfactor()
    response='survived'

    # Split the dataset into train and test
    train, test = titanic.split_frame(ratios = [.8], seed = 1234)

    # Choose which columns to encode
    encoded_columns = ["home.dest", "cabin", "embarked"]

    # For k_fold strategy we need to provide fold column
    fold_column = "kfold_column"
    train[fold_column] = train.kfold_column(n_folds=5, seed=seed)

    # Train a TE model
    titanic_te = H2OTargetEncoderEstimator(fold_column=fold_column,
                                           data_leakage_handling="k_fold", 
                                           blending=True, 
                                           inflection_point=3, 
                                           smoothing=10,
                                           noise=0.15,     # In general, the less data you have the more regularization you need
                                           seed=seed)

    titanic_te.train(x=encoded_columns,
                     y=response,
                     training_frame=train)

    # New target encoded train and test sets
    train_te = titanic_te.transform(frame=train, as_training=True)
    test_te = titanic_te.transform(frame=test, noise=0)

    gbm_with_te=H2OGradientBoostingEstimator(fold_column=fold_column,
                                             model_id="gbm_with_te")

    # Training is based on training data with early stopping based on xval performance
    x_with_te = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin_te", "embarked_te", "home.dest_te"]
    gbm_with_te.train(x=x_with_te, y=response, training_frame=train_te)

    # To prevent overly optimistic results ( overfitting to xval metrics ) metric is computed on yet unseen test split
    my_gbm_metrics = gbm_with_te.model_performance(test_te)
    auc_with_te = my_gbm_metrics.auc()

    # Train a GBM estimator
    gbm_baseline=H2OGradientBoostingEstimator(fold_column=fold_column,
                                              model_id="gbm_baseline")

    x_baseline = ["pclass", "sex", "age", "sibsp", "parch", "fare", "cabin", "embarked", "home.dest"]
    gbm_baseline.train(x=x_baseline, y=response, training_frame=train)

    # Measuring performance on a test split
    gbm_baseline_metrics = gbm_baseline.model_performance(test)
    auc_baseline = gbm_baseline_metrics.auc()

    # Performance is better with target encoding being applied:
    # auc_with_te = 0.8805   >    auc_baseline = 0.0.84105

References
~~~~~~~~~~

-  `Automatic Feature Engineering Webinar <https://www.youtube.com/watch?v=VMTKcT1iHww>`__
-   Daniele Micci-Barreca. 2001. A preprocessing scheme for high-cardinality categorical attributes in classification and prediction problems. SIGKDD Explor. Newsl. 3, 1 (July 2001), 27-32.
-  `Zumel, Nina B. and John Mount. "vtreat: a data.frame Processor for Predictive Modeling." (2016). <https://arxiv.org/abs/1611.09477>`__
