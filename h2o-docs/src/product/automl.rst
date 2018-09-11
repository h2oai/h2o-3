AutoML: Automatic Machine Learning
==================================

In recent years, the demand for machine learning experts has outpaced the supply, despite the surge of people entering the field.  To address this gap, there have been big strides in the development of user-friendly machine learning software that can be used by non-experts.  The first steps toward simplifying machine learning involved developing simple, unified interfaces to a variety of machine learning algorithms (e.g. H2O).

Although H2O has made it easy for non-experts to experiment with machine learning, there is still a fair bit of knowledge and background in data science that is required to produce high-performing machine learning models.  Deep Neural Networks in particular are notoriously difficult for a non-expert to tune properly.  In order for machine learning software to truly be accessible to non-experts, we have designed an easy-to-use interface which automates the process of training a large selection of candidate models.  H2O's AutoML can also be a helpful tool for the advanced user, by providing a simple wrapper function that performs a large number of modeling-related tasks that would typically require many lines of code, and by freeing up their time to focus on other aspects of the data science pipeline tasks such as data-preprocessing, feature engineering and model deployment.

H2O's AutoML can be used for automating the machine learning workflow, which includes automatic training and tuning of many models within a user-specified time-limit.  `Stacked Ensembles <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/stacked-ensembles.html>`__ will be automatically trained on collections of individual models to produce highly predictive ensemble models which, in most cases, will be the top performing models in the AutoML Leaderboard.  


AutoML Interface
----------------

The H2O AutoML interface is designed to have as few parameters as possible so that all the user needs to do is point to their dataset, identify the response column and optionally specify a time constraint or limit on the number of total models trained. 

In both the R and Python API, AutoML uses the same data-related arguments, ``x``, ``y``, ``training_frame``, ``validation_frame``, as the other H2O algorithms.  Most of the time, all you'll need to do is specify the data arguments. You can then configure values for ``max_runtime_secs`` and/or ``max_models`` to set explicit time or number-of-model limits on your run.  

Required Parameters
~~~~~~~~~~~~~~~~~~~

Required Data Parameters
''''''''''''''''''''''''

- `y <data-science/algo-params/y.html>`__: This argument is the name (or index) of the response column. 

- `training_frame <data-science/algo-params/training_frame.html>`__: Specifies the training set. 

Required Stopping Parameters
''''''''''''''''''''''''''''

One of the following stopping strategies (time or number-of-model based) must be specified.  When both options are set, then the AutoML run will stop as soon as it hits one of either of these limits. 

- `max_runtime_secs <data-science/algo-params/max_runtime_secs.html>`__: This argument controls how long the AutoML will run at the most, before training the final Stacked Ensemble models. Defaults to 3600 seconds (1 hour).

- **max_models**: Specify the maximum number of models to build in an AutoML run, excluding the Stacked Ensemble models.  Defaults to ``NULL/None``. 


Optional Parameters
~~~~~~~~~~~~~~~~~~~

Optional Data Parameters
''''''''''''''''''''''''

- `x <data-science/algo-params/x.html>`__: A list/vector of predictor column names or indexes.  This argument only needs to be specified if the user wants to exclude columns from the set of predictors.  If all columns (other than the response) should be used in prediction, then this does not need to be set.

- `validation_frame <data-science/algo-params/validation_frame.html>`__: This argument is used to specify the validation frame used for early stopping of individual models and early stopping of the grid searches (unless ``max_models`` or ``max_runtime_secs`` overrides metric-based early stopping).  

- **leaderboard_frame**: This argument allows the user to specify a particular data frame use to score & rank models on the leaderboard. This frame will not be used for anything besides leaderboard scoring. If a leaderboard frame is not specified by the user, then the leaderboard will use cross-validation metrics instead (or if cross-validation is turned off by setting ``nfolds = 0``, then a leaderboard frame will be generated automatically from the validation frame (if provided) or the training frame).

- `fold_column <data-science/algo-params/fold_column.html>`__: Specifies a column with cross-validation fold index assignment per observation. This is used to override the default, randomized, 5-fold cross-validation scheme for individual models in the AutoML run.

- `weights_column <data-science/algo-params/weights_column.html>`__: Specifies a column with observation weights. Giving some observation a weight of zero is equivalent to excluding it from the dataset; giving an observation a relative weight of 2 is equivalent to repeating that row twice. Negative weights are not allowed.

-  `ignored_columns <data-science/algo-params/ignored_columns.html>`__: (Optional, Python only) Specify the column or columns (as a list/vector) to be excluded from the model.  This is the converse of the ``x`` argument.

Optional Miscellaneous Parameters
'''''''''''''''''''''''''''''''''

- `nfolds <data-science/algo-params/nfolds.html>`__:  Number of folds for k-fold cross-validation of the models in the AutoML run. Defaults to 5. Use 0 to disable cross-validation; this will also disable Stacked Ensembles (thus decreasing the overall best model performance).

- `balance_classes <data-science/algo-params/balance_classes.html>`__: Specify whether to oversample the minority classes to balance the class distribution. This option is not enabled by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **max\_after\_balance\_size** parameter.

-  `class_sampling_factors <data-science/algo-params/class_sampling_factors.html>`__: Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance. Note that this requires ``balance_classes=true``.

-  `max_after_balance_size <data-science/algo-params/max_after_balance_size.html>`__: Specify the maximum relative size of the training data after balancing class counts (**balance\_classes** must be enabled). Defaults to 5.0.  (The value can be less than 1.0).

-  `stopping_metric <data-science/algo-params/stopping_metric.html>`__: Specifies the metric to use for early stopping of the grid searches and individual models. Defaults to ``"AUTO"``.  The available options are:

    - ``AUTO``: This defaults to ``logloss`` for classification, ``deviance`` for regression
    - ``deviance`` (mean residual deviance)
    - ``logloss``
    - ``MSE``
    - ``RMSE``
    - ``MAE``
    - ``RMSLE``
    - ``AUC``
    - ``lift_top_group``
    - ``misclassification``
    - ``mean_per_class_error``

-  `stopping_tolerance <data-science/algo-params/stopping_tolerance.html>`__: This option specifies the relative tolerance for the metric-based stopping criterion to stop a grid search and the training of individual models within the AutoML run. This value defaults to 0.001 if the dataset is at least 1 million rows; otherwise it defaults to a bigger value determined by the size of the dataset and the non-NA-rate.  In that case, the value is computed as 1/sqrt(nrows * non-NA-rate).

- `stopping_rounds <data-science/algo-params/stopping_rounds.html>`__: This argument is used to stop model training when the stopping metric (e.g. AUC) doesn’t improve for this specified number of training rounds, based on a simple moving average.   In the context of AutoML, this controls early stopping both within the random grid searches as well as the individual models.  Defaults to 3 and must be an non-negative integer.  To disable early stopping altogether, set this to 0. 

- `sort_metric <data-science/algo-params/sort_metric.html>`__: Specifies the metric used to sort the Leaderboard by at the end of an AutoML run. Available options include:

    - ``AUTO``: This defaults to ``AUC`` for binary classification, ``mean_per_class_error`` for multinomial classification, and ``deviance`` for regression.
    - ``deviance`` (mean residual deviance)
    - ``logloss``
    - ``MSE``
    - ``RMSE``
    - ``MAE``
    - ``RMSLE``
    - ``AUC``
    - ``mean_per_class_error``

- `seed <data-science/algo-params/seed.html>`__: Integer. Set a seed for reproducibility. AutoML can only guarantee reproducibility if ``max_models`` is used because ``max_runtime_secs`` is resource limited, meaning that if the available compute resources are not the same between runs, AutoML may be able to train more models on one run vs another.  Defaults to ``NULL/None``.

- **project_name**: Character string to identify an AutoML project. Defaults to ``NULL/None``, which means a project name will be auto-generated based on the training frame ID.  More models can be trained and added to an existing AutoML project by specifying the same project name in muliple calls to the AutoML function (as long as the same training frame is used in subsequent runs).

- **exclude_algos**: List/vector of character strings naming the algorithms to skip during the model-building phase.  An example use is ``exclude_algos = ["GLM", "DeepLearning", "DRF"]`` in Python or ``exclude_algos = c("GLM", "DeepLearning", "DRF")`` in R.  Defaults to ``None/NULL``, which means that all appropriate H2O algorithms will be used, if the search stopping criteria allow.  The algorithm names are:

    - ``GLM``
    - ``DeepLearning``
    - ``GBM``
    - ``DRF`` (This includes both the Random Forest and Extremely Randomized Trees (XRT) models. Refer to the :ref:`xrt` section in the DRF chapter and the `histogram_type <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/algo-params/histogram_type.html>`__ parameter description for more information.)
    - ``StackedEnsemble``

- **keep_cross_validation_predictions**: Specify whether to keep the predictions of the cross-validation predictions. This needs to be set to TRUE if running the same AutoML object for repeated runs because CV predictions are required to build additional Stacked Ensemble models in AutoML. This option defaults to FALSE.

- **keep_cross_validation_models**: Specify whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to FALSE.

- `keep_cross_validation_fold_assignment <data-science/algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment.  Defaults to FALSE.


Auto-Generated Frames
~~~~~~~~~~~~~~~~~~~~~

If the user doesn't specify a ``validation_frame``, then one will be created automatically by randomly partitioning the training data.  The validation frame is required for early stopping of the individual algorithms, the grid searches and the AutoML process itself.  

By default, AutoML uses cross-validation for all models, and therefore we can use cross-validation metrics to generate the leaderboard.  If the ``leaderboard_frame`` is explicitly specified by the user, then that frame will be used to generate the leaderboard metrics instead of using cross-validation metrics. 

For cross-validated AutoML, when the user specifies:

   1. **training**: The ``training_frame`` is split into training (80%) and validation (20%).  
   2. **training + leaderboard**:  The ``training_frame`` is split into training (80%) and validation (20%).  
   3. **training + validation**: Leave frames as-is.
   4. **training + validation + leaderboard**: Leave frames as-is.


If not using cross-validation (by setting ``nfolds = 0``) in AutoML, then we need to make sure there is a test frame (aka. the "leaderboard frame") to score on because cross-validation metrics will not be available.  So when the user specifies:

   1. **training**: The ``training_frame`` is split into training (80%), validation (10%) and leaderboard/test (10%).
   2. **training + leaderboard**:  The ``training_frame`` is split into training (80%) and validation (20%).  Leaderboard frame as-is.
   3. **training + validation**: The ``validation_frame`` is split into validation (50%) and leaderboard/test (50%).  Training frame as-is.
   4. **training + validation + leaderboard**: Leave frames as-is.


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
                      max_runtime_secs = 30)

    # View the AutoML Leaderboard
    lb <- aml@leaderboard
    lb

    #                                                model_id       auc   logloss
    # 1    StackedEnsemble_AllModels_0_AutoML_20180503_085035 0.7816995 0.5603380
    # 2 StackedEnsemble_BestOfFamily_0_AutoML_20180503_085035 0.7780683 0.5636519
    # 3             GBM_grid_0_AutoML_20180503_085035_model_1 0.7742967 0.5656552
    # 4             GBM_grid_0_AutoML_20180503_085035_model_0 0.7736082 0.5667454
    # 5             GBM_grid_0_AutoML_20180503_085035_model_2 0.7704520 0.5695492
    # 6             GBM_grid_0_AutoML_20180503_085035_model_3 0.7662087 0.5759679
    #  mean_per_class_error      rmse       mse
    # 1            0.3250067 0.4361930 0.1902644
    # 2            0.3261921 0.4377744 0.1916464
    # 3            0.3233579 0.4390083 0.1927283
    # 4            0.3196441 0.4394696 0.1931335
    # 5            0.3443406 0.4411033 0.1945721
    # 6            0.3348417 0.4439429 0.1970853

    # [9 rows x 6 columns] 

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
              training_frame = train)

    # View the AutoML Leaderboard
    lb = aml.leaderboard
    lb

    # model_id                                                    auc    logloss    mean_per_class_error      rmse       mse 
    # -----------------------------------------------------  --------  ---------  ----------------------  --------  -------- 
    # StackedEnsemble_AllModels_0_AutoML_20180503_084454     0.782946   0.558928                0.32715   0.4356    0.189747 
    # StackedEnsemble_BestOfFamily_0_AutoML_20180503_084454  0.780806   0.561076                0.323633  0.436574  0.190597 
    # GBM_grid_0_AutoML_20180503_084454_model_0              0.776487   0.563984                0.333979  0.438194  0.192014 
    # GBM_grid_0_AutoML_20180503_084454_model_1              0.772745   0.566795                0.340894  0.439841  0.19346  
    # GBM_grid_0_AutoML_20180503_084454_model_2              0.76977    0.569913                0.326976  0.441285  0.194732 
    # GBM_grid_0_AutoML_20180503_084454_model_3              0.762904   0.577676                0.346248  0.444726  0.197781 
    # XRT_0_AutoML_20180503_084454                           0.743111   0.603862                0.364812  0.452799  0.205027 
    # DRF_0_AutoML_20180503_084454                           0.735039   0.605574                0.359245  0.455728  0.207688 
    # GLM_grid_0_AutoML_20180503_084454_model_0              0.68048    0.639935                0.393134  0.473447  0.224152 

    # [9 rows x 6 columns]


    # The leader model is stored here
    aml.leader

    # If you need to generate predictions on a test set, you can make 
    # predictions directly on the `"H2OAutoML"` object, or on the leader 
    # model object directly

    preds = aml.predict(test)

    # or:
    preds = aml.leader.predict(test)


The code above is the quickest way to get started, however to learn more about H2O AutoML we recommend taking a look at our more in-depth `AutoML tutorial <https://github.com/h2oai/h2o-tutorials/tree/master/h2o-world-2017/automl>`__ (available in R and Python).


AutoML Output
-------------

The AutoML object includes a "leaderboard" of models that were trained in the process, including the 5-fold cross-validated model performance (by default).  The number of folds used in the model evaluation process can be adjusted using the ``nfolds`` parameter.  If the user would like to score the models on a specific dataset, they can specify the ``leaderboard_frame`` argument, and then the leaderboard will show scores on that dataset instead. 

The models are ranked by a default metric based on the problem type (the second column of the leaderboard). In binary classification problems, that metric is AUC, and in multiclass classification problems, the metric is mean per-class error. In regression problems, the default sort metric is deviance.  Some additional metrics are also provided, for convenience.

Here is an example leaderboard for a binary classification task:

+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
|                                               model_id |      auc |  logloss | mean_per_class_error |     rmse |      mse |
+========================================================+==========+==========+======================+==========+==========+
| StackedEnsemble_AllModels_0_AutoML_20180503_084454     | 0.782946 | 0.558928 | 0.32715              | 0.4356   | 0.189747 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| StackedEnsemble_BestOfFamily_0_AutoML_20180503_084454  | 0.780806 | 0.561076 | 0.323633             | 0.436574 | 0.190597 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| GBM_grid_0_AutoML_20180503_084454_model_0              | 0.776487 | 0.563984 | 0.333979             | 0.438194 | 0.192014 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| GBM_grid_0_AutoML_20180503_084454_model_1              | 0.772745 | 0.566795 | 0.340894             | 0.439841 | 0.19346  |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| GBM_grid_0_AutoML_20180503_084454_model_2              | 0.76977  | 0.569913 | 0.326976             | 0.441285 | 0.194732 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| GBM_grid_0_AutoML_20180503_084454_model_3              | 0.762904 | 0.577676 | 0.346248             | 0.444726 | 0.197781 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| XRT_0_AutoML_20180503_084454                           | 0.743111 | 0.603862 | 0.364812             | 0.452799 | 0.205027 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| DRF_0_AutoML_20180503_084454                           | 0.735039 | 0.605574 | 0.359245             | 0.455728 | 0.207688 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+
| GLM_grid_0_AutoML_20180503_084454_model_0              | 0.68048  | 0.639935 | 0.393134             | 0.473447 | 0.224152 |
+--------------------------------------------------------+----------+----------+----------------------+----------+----------+


FAQ
~~~

-  **Which models are trained in the AutoML process?**

  The current version of AutoML trains and cross-validates a default Random Forest (DRF), an Extremely Randomized Forest (XRT), a random grid of Gradient Boosting Machines (GBMs), a random grid of Deep Neural Nets, a fixed grid of GLMs. AutoML then trains two Stacked Ensemble models. Particular algorithms (or groups of algorithms) can be switched off using the ``exclude_algos`` argument. This is useful if you already have some idea of the algorithms that will do well on your dataset. As a recommendation, if you have really wide or sparse data, you may consider skipping the tree-based algorithms (GBM, DRF).

  A list of the hyperparameters searched over for each algorithm in the AutoML process is included in the appendix below.  More details about the hyperparamter ranges for the models will be added to the appendix at a later date.

  Both of the ensembles should produce better models than any individual model from the AutoML run.  One ensemble contains all the models, and the second ensemble contains just the best performing model from each algorithm class/family.  The "Best of Family" ensemble is optimized for production use since it only contains five models.  It should be relatively fast to use (to generate predictions on new data) without much degredation in model performance when compared to the "All Models" ensemble.   

-  **How do I save AutoML runs?**

  Rather than saving an AutoML object itself, currently, the best thing to do is to save the models you want to keep, individually.  A utility for saving all of the models at once, along with a way to save the AutoML object (with leaderboard), will be added in a future release.


Resources
~~~~~~~~~

- `AutoML Tutorial <https://github.com/h2oai/h2o-tutorials/tree/master/h2o-world-2017/automl>`__ (R and Python notebooks)
- Intro to AutoML + Hands-on Lab `(1 hour video) <https://www.youtube.com/watch?v=42Oo8TOl85I>`__ `(slides) <https://www.slideshare.net/0xdata/intro-to-automl-handson-lab-erin-ledell-machine-learning-scientist-h2oai>`__
- Scalable Automatic Machine Learning in H2O `(1 hour video) <https://www.youtube.com/watch?v=j6rqrEYQNdo>`__ `(slides) <https://www.slideshare.net/0xdata/scalable-automatic-machine-learning-in-h2o-89130971>`__
- `AutoML Roadmap <https://0xdata.atlassian.net/issues/?filter=21603>`__


Appendix: Grid Search Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

AutoML performs hyperparameter search over a variety of H2O algorithms in order to deliver the best model. In AutoML, the following hyperparameters are supported by grid search.  Random Forest and Extremely Randomized Trees are not grid searched (in the current version of AutoML), so they are not included in the list below.

**GBM Hyperparameters**

-  ``score_tree_interval``
-  ``histogram_type``
-  ``ntrees``
-  ``max_depth``
-  ``min_rows``
-  ``learn_rate``
-  ``sample_rate``
-  ``col_sample_rate``
-  ``col_sample_rate_per_tree``
-  ``min_split_improvement``

**GLM Hyperparameters**

-  ``alpha``
-  ``missing_values_handling``

**Deep Learning Hyperparameters**

-  ``epochs``
-  ``adaptivate_rate``
-  ``activation``
-  ``rho``
-  ``epsilon``
-  ``input_dropout_ratio``
-  ``hidden``
-  ``hidden_dropout_ratios``


Additional Information
~~~~~~~~~~~~~~~~~~~~~~

- AutoML development is tracked `here <https://0xdata.atlassian.net/issues/?filter=20700>`__. This page lists all open or in-progress AutoML JIRA tickets.
- AutoML is currently in experimental mode ("V99" in the REST API).  This means that, although unlikely, the API (REST, R, Python or otherwise) may be subject to breaking changes.
