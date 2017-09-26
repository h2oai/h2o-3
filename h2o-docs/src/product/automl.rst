AutoML: Automatic Machine Learning
==================================

In recent years, the demand for machine learning experts has outpaced the supply, despite the surge of people entering the field.  To address this gap, there have been big strides in the development of user-friendly machine learning software that can be used by non-experts.  The first steps toward simplifying machine learning involved developing simple, unified interfaces to a variety of machine learning algorithms (e.g. H2O).

Although H2O has made it easy for non-experts to experiment with machine learning, there is still a fair bit of knowledge and background in data science that is required to produce high-performing machine learning models.  Deep Neural Networks in particular are notoriously difficult for a non-expert to tune properly.  In order for machine learning software to truly be accessible to non-experts, we have designed an easy-to-use interface which automates the process of training a large selection of candidate models.  H2O's AutoML can also be a helpful tool for the advanced user, by providing a simple wrapper function that performs a large number of modeling-related tasks that would typically require many lines of code, and by freeing up their time to focus on other aspects of the data science pipeline tasks such as data-preprocessing, feature engineering and model deployment.

H2O's AutoML can be used for automating the machine learning workflow, which includes automatic training and tuning of many models within a user-specified time-limit.  The user can also use a performance metric-based stopping criterion for the AutoML process rather than a specific time constraint.  `Stacked Ensembles <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/stacked-ensembles.html>`__ will be automatically trained on the collection individual models to produce a highly predictive ensemble model which, in most cases, will be the top performing model in the AutoML Leaderboard.  Stacked ensembles are not yet available for multiclass classification problems, so in that case, only singleton models will be trained. 


AutoML Interface
----------------

The H2O AutoML interface is designed to have as few parameters as possible so that all the user needs to do is point to their dataset, identify the response column and optionally specify a time constraint, a maximum number of models constraint, and early stopping parameters. 

In both the R and Python API, AutoML uses the same data-related arguments, ``x``, ``y``, ``training_frame``, ``validation_frame``, as the other H2O algorithms.  Most of the time, all you'll need to do is specify the data arguments. You can then configure values for ``max_runtime_secs`` and/or ``max_models`` to set explicit time or number-of-model limits on your run, or you can set those values high and configure the early stopping arguments to take care of the rest.  

Required Parameters
~~~~~~~~~~~~~~~~~~~

Required Data Parameters
''''''''''''''''''''''''

- `y <data-science/algo-params/y.html>`__: This argument is the name (or index) of the response column. 

- `training_frame <data-science/algo-params/training_frame.html>`__: Specifies the training set. 

Required Stopping Parameters
''''''''''''''''''''''''''''

One of the following stopping strategies (time, model or metric-based) must be specified.  **Note:** When multiple options are set to control the stopping of the AutoML run (e.g. ``max_models`` and ``max_runtime_secs``), then whichever happens first will stop the AutoML run.

Time-based:

- `max_runtime_secs <data-science/algo-params/max_runtime_secs.html>`__: This argument controls how long the AutoML run will execute. This defaults to 3600 seconds (1 hour).

Model-based:

- **max_models**: Specify the maximum number of models to build in an AutoML run. (Does not include the Stacked Ensemble model.) 

Metric-based (used as a group):

-  `stopping_metric <data-science/algo-params/stopping_metric.html>`__: Specifies the metric to use for early stopping. Defaults to ``"AUTO"``.  The available options are:

    - ``AUTO``: This defaults to ``logloss`` for classification, ``deviance`` for regression
    - ``deviance``
    - ``logloss``
    - ``mse``
    - ``rmse``
    - ``mae``
    - ``rmsle``
    - ``auc``
    - ``lift_top_group``
    - ``misclassification``
    - ``mean_per_class_error``

-  `stopping_tolerance <data-science/algo-params/stopping_tolerance.html>`__: This option specifies the relative tolerance for the metric-based stopping to stop the AutoML run if the improvement is less than this value. This value defaults to 0.001 if the dataset is at least 1 million rows; otherwise it defaults to a bigger value determined by the size of the dataset and the non-NA-rate.  In that case, the value is computed as 1/sqrt(nrows * non-NA-rate).

- `stopping_rounds <data-science/algo-params/stopping_rounds.html>`__: This argument stops training new models in the AutoML run when the option selected for **stopping_metric** doesn't improve for the specified number of models, based on a simple moving average. Defaults to 3 and must be an non-negative integer.  To disable this feature, set it to 0. 


Optional Parameters
~~~~~~~~~~~~~~~~~~~

Optional Data Parameters
''''''''''''''''''''''''

- **x**: A list/vector of predictor column names or indexes.  This argument only needs to be specified if the user wants to exclude columns from the set of predictors.  If all columns (other than the response) should be used in prediction, then this does not need to be set.

- `validation_frame <data-science/algo-params/validation_frame.html>`__: This argument is is used for early stopping within the training process of the individual models in the AutoML run.  

- **leaderboard_frame**: This argument allows the user to specify a particular data frame to rank the models on the leaderboard. This frame will not be used for anything besides creating the leaderboard. If this option is not specified, then a ``leaderboard_frame`` will be created from the ``training_frame``.

- `fold_column <data-science/algo-params/fold_column.html>`__: Specifies a column with cross-validation fold index assignment per observation. This is used to override the default, randomized, 5-fold cross-validation scheme for individual models in the AutoML run.

- `weights_column <data-science/algo-params/weights_column.html>`__: Specifies a column with observation weights. Giving some observation a weight of zero is equivalent to excluding it from the dataset; giving an observation a relative weight of 2 is equivalent to repeating that row twice. Negative weights are not allowed.


Optional Miscellaneous Parameters
'''''''''''''''''''''''''''''''''

- `seed <data-science/algo-params/seed.html>`__: Integer. Set a seed for reproducibility. AutoML can only guarantee reproducibility if ``max_models`` or early stopping is used because ``max_runtime_secs`` is resource limited, meaning that if the resources are not the same between runs, AutoML may be able to train more models on one run vs another.  Defaults to ``NULL/None``.

- **project_name**: Character string to identify an AutoML project. Defaults to ``NULL/None``, which means a project name will be auto-generated based on the training frame ID.  More models can be trained on an existing AutoML project by specifying the same project name in muliple calls to the AutoML function (as long as the same training frame is used in subsequent runs).


Auto-Generated Frames
~~~~~~~~~~~~~~~~~~~~~

If the user doesn't specify all three frames (training, validation and leaderboard), then the missing frames will be created automatically from what is provided by the user.  For reference, here are the rules for auto-generating the missing frames.

When the user specifies:

   1. **training**:  The ``training_frame`` is split into training (70%), validation (15%) and leaderboard (15%) sets.
   2. **training + validation**: The ``validation_frame`` is split into validation (50%) and leaderboard (50%) sets and the original training frame stays as-is.
   3. **training + leaderboard**: The ``training_frame`` is split into training (70%) and validation (30%) sets and the leaderboard frame stays as-is.
   4. **training + validation + leaderboard**: Leave all frames as-is.


Code Examples
~~~~~~~~~~~~~

Here’s an example showing basic usage of the ``h2o.automl()`` function in *R* and the ``H2OAutoML`` class in *Python*.  For demonstration purposes only, we explicitly specify the the `x` argument, even though on this dataset, that's not required.  With this dataset, the set of predictors is all columns other than the response.  Like other H2O algorithms, the default value of ``x`` is "all columns, excluding ``y``", so that will produce the same result.

.. example-code::
   .. code-block:: r

    library(h2o)

    h2o.init()

    # Import a sample binary outcome train/test set into H2O
    train <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
    test <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

    # Identify predictors and response
    y <- "response"
    x <- setdiff(names(train), y)

    # For binary classification, response should be a factor
    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])

    aml <- h2o.automl(x = x, y = y, 
                      training_frame = train,
                      leaderboard_frame = test,
                      max_runtime_secs = 30)

    # View the AutoML Leaderboard
    lb <- aml@leaderboard
    lb

    #                                  model_id       auc    logloss
    #  StackedEnsemble_0_AutoML_20170605_212658  0.776164   0.564872
    # GBM_grid_0_AutoML_20170605_212658_model_2  0.753550   0.587546
    #              DRF_0_AutoML_20170605_212658  0.738885   0.611997
    # GBM_grid_0_AutoML_20170605_212658_model_0  0.735078   0.630062
    # GBM_grid_0_AutoML_20170605_212658_model_1  0.730645   0.674580
    #              XRT_0_AutoML_20170605_212658  0.728358   0.629296
    # GLM_grid_0_AutoML_20170605_212658_model_1  0.685216   0.635137
    # GLM_grid_0_AutoML_20170605_212658_model_0  0.685216   0.635137
    #
    # [8 rows x 3 columns]

    # The leader model is stored here
    aml@leader


    # If you need to generate predictions on a test set, you can make 
    # predictions directly on the `"H2OAutoML"` object, or on the leader 
    # model object directly

    pred <- h2o.predict(aml, test)  # predict(aml, test) also works

    # or:
    pred <- h2o.predict(aml@leader, test)



   .. code-block:: python

    import h2o
    from h2o.automl import H2OAutoML

    h2o.init()

    # Import a sample binary outcome train/test set into H2O
    train = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
    test = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # For binary classification, response should be a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()
    
    # Run AutoML for 30 seconds
    aml = H2OAutoML(max_runtime_secs = 30)
    aml.train(x = x, y = y, 
              training_frame = train, 
              leaderboard_frame = test)

    # View the AutoML Leaderboard
    lb = aml.leaderboard
    lb

    # model_id                                        auc    logloss
    # -----------------------------------------  --------  ---------
    # StackedEnsemble_0_AutoML_20170605_212658   0.776164   0.564872
    # GBM_grid_0_AutoML_20170605_212658_model_2  0.75355    0.587546
    # DRF_0_AutoML_20170605_212658               0.738885   0.611997
    # GBM_grid_0_AutoML_20170605_212658_model_0  0.735078   0.630062
    # GBM_grid_0_AutoML_20170605_212658_model_1  0.730645   0.67458
    # XRT_0_AutoML_20170605_212658               0.728358   0.629296
    # GLM_grid_0_AutoML_20170605_212658_model_1  0.685216   0.635137
    # GLM_grid_0_AutoML_20170605_212658_model_0  0.685216   0.635137
    #
    # [8 rows x 3 columns]

    # The leader model is stored here
    aml.leader


    # If you need to generate predictions on a test set, you can make 
    # predictions directly on the `"H2OAutoML"` object, or on the leader 
    # model object directly

    preds = aml.predict(test)

    # or:
    preds = aml.leader.predict(test)



AutoML Output
-------------

The AutoML object includes a "leaderboard" of models that were trained in the process, ranked by a default metric based on the problem type (the second column of the leaderboard). In binary classification problems, that metric is AUC, and in multiclass classification problems, the metric is mean per-class error. In regression problems, the default sort metric is deviance.  Some additional metrics are also provided, for convenience.

Here is an example leaderboard for a binary classification task:

+-------------------------------------------+----------+----------+
|                                  model_id |      auc |  logloss |
+===========================================+==========+==========+
| StackedEnsemble_0_AutoML_20170605_212658  | 0.776164 | 0.564872 | 
+-------------------------------------------+----------+----------+
| GBM_grid_0_AutoML_20170605_212658_model_2 | 0.75355  | 0.587546 |
+-------------------------------------------+----------+----------+
| DRF_0_AutoML_20170605_212658              | 0.738885 | 0.611997 |
+-------------------------------------------+----------+----------+
| GBM_grid_0_AutoML_20170605_212658_model_0 | 0.735078 | 0.630062 |
+-------------------------------------------+----------+----------+
| GBM_grid_0_AutoML_20170605_212658_model_1 | 0.730645 | 0.67458  |
+-------------------------------------------+----------+----------+
| XRT_0_AutoML_20170605_212658              | 0.728358 | 0.629296 |
+-------------------------------------------+----------+----------+
| GLM_grid_0_AutoML_20170605_212658_model_1 | 0.685216 | 0.635137 |
+-------------------------------------------+----------+----------+
| GLM_grid_0_AutoML_20170605_212658_model_0 | 0.685216 | 0.635137 |
+-------------------------------------------+----------+----------+

FAQ
~~~

-  **How do I save AutoML runs?**

  Rather than saving an AutoML object itself, currently, the best thing to do is to save the models you want to keep, individually.  A utility for saving all of the models at once will be added in a future release.


-  **Why is there no Stacked Ensemble on my Leaderboard?**

  Currently, Stacked Ensembles supports binary classficiation and regression, but not multi-class classification, although multi-class support is in `development <https://0xdata.atlassian.net/browse/PUBDEV-3960>`__.  So if your leaderboard is missing a Stacked Ensemble, the reason is likely that you are performing multi-class classification and it's not meant to be there.


Additional Information
~~~~~~~~~~~~~~~~~~~~~~

- AutoML development is tracked `here <https://0xdata.atlassian.net/issues/?filter=20700>`__. This page lists all open or in-progress AutoML JIRA tickets.
- AutoML is currently in experimental mode ("V99" in the REST API).  This means that, although unlikely, the API (REST, R, Python or otherwise) may be subject to breaking changes.
