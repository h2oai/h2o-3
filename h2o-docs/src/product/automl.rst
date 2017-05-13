AutoML: Automatic Machine Learning
==================================

In recent years, the demand for machine learning experts has outpaced the supply, despite the surge of people entering the field.  To address this gap, there have been big strides in the development of user-friendly machine learning software that can be used by non-experts.  The first steps toward simplifying machine learning involved developing simple, unified interfaces to a variety of machine learning algorithms (e.g. H2O).

Although H2O has made it easy for non-experts to experiment with machine learning, there is still a fair bit of knowledge and background in data science that is required to produce high-performing machine learning models.  Deep Neural Networks in particular are notoriously difficult for a non-expert to tune properly.  In order for machine learning software to truly be accessible to non-experts, we have designed an easy-to-use interface which automates the process of training a large selection of candidate models.  H2O's AutoML can also be a helpful tool for the advanced user, by providing a simple wrapper function that performs a large number of modeling-related tasks that would typically require many lines of code, and by freeing up their time to focus on other aspects of the data science pipeline tasks such as data-preprocessing, feature engineering and model deployment.

H2O's AutoML can be used for automating the machine learning workflow, which includes automatic training and tuning of many models within a user-specified time-limit.  The user can also use a performance metric-based stopping criterion for the AutoML process rather than a specific time constraint.  `Stacked ensembles <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/stacked-ensembles.html>`__ will be automatically trained on the collection individual models to produce a highly predictive ensemble model which, in most cases, will be the top performing model in the AutoML Leaderboard.  Stacked ensembles are not yet available for multiclass classification problems, so in that case, only singleton models will be trained. 


AutoML Interface
----------------

The AutoML interface is designed to have as few parameters as possible so that all the user needs to do is point to their dataset, identify the response column and optionally specify a time-constraint. 

In both the R and Python API, AutoML uses the same data-related arguments, `x, y, training_frame, validation_frame`, as the other H2O algorithms.  

- The `x` argument only needs to be specified if the user wants to exclude predictor columns from their data frame.  If all columns (other than the response) should be used in prediction, this can be left blank/unspecified.
- The `validation_frame` argument is optional and will be used for early stopping within the training process of the individual models in the AutoML run.  
- The `test_frame` argument allows the user to specify a particular data frame to rank the models on the leaderboard.  This frame will not be used for anything besides creating the leaderboard.
- To control how long the AutoML run will execute, the user can specify `max_runtime_secs`, which defaults to 600 seconds (10 minutes).


Code Examples
~~~~~~~~~~~~~

Here’s an example showing basic usage of the ``h2o.automl()`` function in *R* and the ``H2OAutoML`` class in *Python*.  For demonstration purposes only, we explicitly specify the the `x` argument, even though on this dataset, that's not required.  With this dataset, the set of predictors is all columns other than the response.  Like other H2O algorithms, the default value of `x` is "all columns, excluding `y`", so that will produce the same result.

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
                      test_frame = test,
                      max_runtime_secs = 30)

    # View the AutoML Leaderboard
    lb <- aml@leaderboard
    lb

    #                                             model_id      auc  logloss
    # 1           StackedEnsemble_model_1494643945817_1709 0.780384 0.561501
    # 2 GBM_grid__95ebce3d26cd9d3997a3149454984550_model_0 0.764791 0.664823
    # 3 GBM_grid__95ebce3d26cd9d3997a3149454984550_model_2 0.758109 0.593887
    # 4                          DRF_model_1494643945817_3 0.736786 0.614430
    # 5                        XRT_model_1494643945817_461 0.735946 0.602142
    # 6 GBM_grid__95ebce3d26cd9d3997a3149454984550_model_3 0.729492 0.667036
    # 7 GBM_grid__95ebce3d26cd9d3997a3149454984550_model_1 0.727456 0.675624
    # 8 GLM_grid__95ebce3d26cd9d3997a3149454984550_model_1 0.685216 0.635137
    # 9 GLM_grid__95ebce3d26cd9d3997a3149454984550_model_0 0.685216 0.635137

    # The leader model is stored here
    aml@leader


    # If you need to generate predictions on a test set, you can make 
    # predictions directly on the `"H2OAutoML"` object, or on the leader 
    # model object directly

    #pred <- h2o.predict(aml, test)  #Not functional yet: https://0xdata.atlassian.net/browse/PUBDEV-4428

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
              test_frame = test)

    # View the AutoML Leaderboard
    lb = aml.leaderboard
    lb

    # model_id                                            auc       logloss
    # --------------------------------------------------  --------  ---------
    #           StackedEnsemble_model_1494643945817_1709  0.780384  0.561501
    # GBM_grid__95ebce3d26cd9d3997a3149454984550_model_0  0.764791  0.664823
    # GBM_grid__95ebce3d26cd9d3997a3149454984550_model_2  0.758109  0.593887
    #                          DRF_model_1494643945817_3  0.736786  0.614430
    #                        XRT_model_1494643945817_461  0.735946  0.602142
    # GBM_grid__95ebce3d26cd9d3997a3149454984550_model_3  0.729492  0.667036
    # GBM_grid__95ebce3d26cd9d3997a3149454984550_model_1  0.727456  0.675624
    # GLM_grid__95ebce3d26cd9d3997a3149454984550_model_1  0.685216  0.635137
    # GLM_grid__95ebce3d26cd9d3997a3149454984550_model_0  0.685216  0.635137


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

The AutoML object includes a history of all the data-processing and modeling steps that were taken, and will return a "leaderboard" of models that were trained in the process, ranked by a default metric based on the problem type (the second column of the leaderboard).  In binary classification problems, that metric is AUC, and in multiclass classification problems, the metric is mean per-class error.  In regression problems, the default sort metric is root mean squared error (RMSE).  Some additional metrics are also provided, for convenience.

Here is an example leaderboard for a binary classification task:

+----------------------------------------------------+----------+----------+
|                                           model_id |      auc |  logloss |
+====================================================+==========+==========+
| StackedEnsemble_model_1494643945817_1709           | 0.780384 | 0.561501 | 
+----------------------------------------------------+----------+----------+
| GBM_grid__95ebce3d26cd9d3997a3149454984550_model_0 | 0.764791 | 0.664823 |
+----------------------------------------------------+----------+----------+
| GBM_grid__95ebce3d26cd9d3997a3149454984550_model_2 | 0.758109 | 0.593887 |
+----------------------------------------------------+----------+----------+
| DRF_model_1494643945817_3                          | 0.736786 | 0.614430 |
+----------------------------------------------------+----------+----------+
| XRT_model_1494643945817_461                        | 0.735946 | 0.602142 |
+----------------------------------------------------+----------+----------+
| GBM_grid__95ebce3d26cd9d3997a3149454984550_model_3 | 0.729492 | 0.667036 |
+----------------------------------------------------+----------+----------+
| GBM_grid__95ebce3d26cd9d3997a3149454984550_model_1 | 0.727456 | 0.675624 |
+----------------------------------------------------+----------+----------+
| GLM_grid__95ebce3d26cd9d3997a3149454984550_model_1 | 0.685216 | 0.635137 |
+----------------------------------------------------+----------+----------+
| GLM_grid__95ebce3d26cd9d3997a3149454984550_model_0 | 0.685216 | 0.635137 |
+----------------------------------------------------+----------+----------+



FAQ
~~~

-  **How do I save AutoML runs?**

  Rather than saving an AutoML object itself, currently, the best thing to do is to save the models you want to keep, individually.  This will be improved in a future release.


-  **Why is there no Stacked Ensemble on my Leaderboard?**

  Currently, Stacked Ensembles supports binary classficiation and regression, but not multi-class classification.  So if you are missing a Stacked Ensemble, the likely cause is that you are performing multi-class classification and it's not meant to be there.


Additional Information
~~~~~~~~~~~~~~~~~~~~~~

- AutoML is currently in experimental mode ("V99" in the REST API).  This means that the API (REST, R, Python or otherwise) may be subject to breaking changes.

