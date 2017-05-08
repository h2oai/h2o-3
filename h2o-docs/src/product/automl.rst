AutoML: Automatic Machine Learning
==================================

In recent years, the demand for machine learning experts has outpaced the supply, despite the surge of people entering the field.  To address this gap, there have been big strides in the development of user-friendly machine learning software that can be used by non-experts.  The first steps toward simplifying machine learning involved developing simple, unified interfaces to a variety of machine learning algorithms, like is provided by H2O.  

Although H2O has made it easy for non-experts to experiment with machine learning, there is still a fair bit of knowledge and background in data science that is required to produce high-performing machine learning models.  Deep Neural Networks in particular are notoriously difficult for a non-expert to tune properly.  In order for machine learning software to truly be accessible to non-experts, such systems must be able to automatically perform proper data pre-processing steps and return a highly optimized machine learning model.

H2O's AutoML can be used for automating the machine learning workflow, which includes automatic training and tuning of many models within a user-specified time-limit.  The user can also specify which model performance metric that they'd like to optimize and use a metric-based stopping criterion for the AutoML process rather than a specific time constraint.  Stacked ensembles will automatically trained on subset of the individual models to produce a highly predictive ensemble model, although this can be turned off if the user prefers to return singleton models only.  Stacked ensembles are not yet available for multiclass classification problems, so in that case, only singleton models will be trained. 

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

Here’s an example showing basic usage of the ``h2o.automl()`` function in *R* and the ``H2OAutoML`` class in *Python*:

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
    aml@leaderboard

    #                                              model_id      auc
    # 1            StackedEnsemble_model_1494131714454_2450 0.781019
    # 2  GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_2 0.772083
    # 3  GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_0 0.767674
    # 4  GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_4 0.761287
    # 5  GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_3 0.754231
    # 6                         DRF_model_1494131714454_601 0.741244
    # 7                        XRT_model_1494131714454_1055 0.732022
    # 8  GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_1 0.719469
    # 9  GLM_grid__99d1689ffa54427987452fbfe0b34e14_model_1 0.685216
    # 10 GLM_grid__99d1689ffa54427987452fbfe0b34e14_model_0 0.685216



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
    lb = aml.get_leaderboard()
    lb

    #     model_id                                            auc
    # --  --------------------------------------------------  --------
    # 0   StackedEnsemble_model_1494220587649_3147            0.780276
    # 1   GBM_grid__baf3426712644306cd5c78e4156343ab_model_1  0.766559
    # 2   GBM_grid__baf3426712644306cd5c78e4156343ab_model_0  0.764055
    # 3   GBM_grid__baf3426712644306cd5c78e4156343ab_model_2  0.75778
    # 4   DRF_model_1494220587649_1417                        0.732011
    # 5   XRT_model_1494220587649_1871                        0.731159
    # 6   GBM_grid__baf3426712644306cd5c78e4156343ab_model_3  0.723212
    # 7   GLM_grid__baf3426712644306cd5c78e4156343ab_model_1  0.685216
    # 8   GLM_grid__baf3426712644306cd5c78e4156343ab_model_0  0.685216





AutoML Output
-------------

The AutoML object includes a history of all the data-processing and modeling steps that were taken, and will return a "leaderboard" of models that were trained in the process, ranked by a default metric based on the problem type.  In binary classification problems, that metric is AUC, and in multiclass classification problems, the metric is mean per-class error.  In regression problems, the metric is root mean squared error (RMSE).

An example leaderboard for a binary classification task:

+----------------------------------------------------+----------+
|                                          model_id  | auc      |
+====================================================+==========+
| StackedEnsemble_model_1494131714454_2450           | 0.781019 |
+----------------------------------------------------+----------+
| GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_2 | 0.772083 |
+----------------------------------------------------+----------+
| GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_0 | 0.767674 |
+----------------------------------------------------+----------+
| GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_4 | 0.761287 |
+----------------------------------------------------+----------+
| GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_3 | 0.754231 |
+----------------------------------------------------+----------+
| DRF_model_1494131714454_601                        | 0.741244 |
+----------------------------------------------------+----------+
| XRT_model_1494131714454_1055                       | 0.732022 |
+----------------------------------------------------+----------+
| GBM_grid__99d1689ffa54427987452fbfe0b34e14_model_1 | 0.719469 |
+----------------------------------------------------+----------+
| GLM_grid__99d1689ffa54427987452fbfe0b34e14_model_1 | 0.685216 |
+----------------------------------------------------+----------+
| GLM_grid__99d1689ffa54427987452fbfe0b34e14_model_0 | 0.685216 |
+----------------------------------------------------+----------+



FAQ
~~~

-  **How do I save AutoML runs?**

  Rather than saving an AutoML object itself, currently, the best thing to do is to save the models you want to keep, individually.  This feature will be improved in a future release.


-  **Why is there no Stacked Ensemble on my Leaderboard?**

  Currently, Stacked Ensembles supports binary classficiation and regression, but not multi-class classification.  So if you are missing a Stacked Ensemble, the likely cause is that you are performing multi-class classification and it's not meant to be there.


Additional Information
~~~~~~~~~~~~~~~~~~~~~~

- The H2OAutoML class is currently in experimental mode ("V99" in the REST API).  This means that the API (REST, R, Python or otherwise) may change.


References
~~~~~~~~~~

`Matthias Feurer, Aaron Klein, Katharina Eggensperger, Jost Springenberg, Manuel Blum, Frank Hutter. "Efficient and Robust Automated Machine Learning." Advances in Neural Information Processing Systems 28 (2015) <https://papers.nips.cc/paper/5872-efficient-and-robust-automated-machine-learning.pdf>`__



