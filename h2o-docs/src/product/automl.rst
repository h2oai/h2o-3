AutoML: Automatic Machine Learning
==================================

In recent years, the demand for machine learning experts has outpaced the supply, despite the surge of people entering the field.  To address this gap, there have been big strides in the development of user-friendly machine learning software that can be used by non-experts.  The first steps toward simplifying machine learning involved developing simple, unified interfaces to a variety of machine learning algorithms (e.g. H2O).

Although H2O has made it easy for non-experts to experiment with machine learning, there is still a fair bit of knowledge and background in data science that is required to produce high-performing machine learning models.  Deep Neural Networks in particular are notoriously difficult for a non-expert to tune properly.  In order for machine learning software to truly be accessible to non-experts, we have designed an easy-to-use interface which automates the process of training a large selection of candidate models.  H2O's AutoML can also be a helpful tool for the advanced user, by providing a simple wrapper function that performs a large number of modeling-related tasks that would typically require many lines of code, and by freeing up their time to focus on other aspects of the data science pipeline tasks such as data-preprocessing, feature engineering and model deployment.

H2O's AutoML can be used for automating the machine learning workflow, which includes automatic training and tuning of many models within a user-specified time-limit.  `Stacked Ensembles <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/stacked-ensembles.html>`__ – one based on all previously trained models, another one on the best model of each family – will be automatically trained on collections of individual models to produce highly predictive ensemble models which, in most cases, will be the top performing models in the AutoML Leaderboard.


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

    - ``DRF`` (This includes both the Random Forest and Extremely Randomized Trees (XRT) models. Refer to the :ref:`xrt` section in the DRF chapter and the `histogram_type <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/algo-params/histogram_type.html>`__ parameter description for more information.)
    - ``GLM``
    - ``XGBoost``  (XGBoost GBM)
    - ``GBM``  (H2O GBM)
    - ``DeepLearning``  (Fully-connected multi-layer artificial neural network)
    - ``StackedEnsemble``

- `keep_cross_validation_predictions <data-science/algo-params/keep_cross_validation_predictions.html>`__: Specify whether to keep the predictions of the cross-validation predictions. This needs to be set to TRUE if running the same AutoML object for repeated runs because CV predictions are required to build additional Stacked Ensemble models in AutoML. This option defaults to FALSE.

- `keep_cross_validation_models <data-science/algo-params/keep_cross_validation_models.html>`__: Specify whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to FALSE.

- `keep_cross_validation_fold_assignment <data-science/algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment.  Defaults to FALSE.


Auto-Generated Frames
~~~~~~~~~~~~~~~~~~~~~

If the user doesn't specify a ``validation_frame``, then one will be created automatically by randomly partitioning the training data.  The validation frame is required by and used exclusively for early stopping of the individual algorithms, the grid searches and the AutoML process itself.  

By default, AutoML uses cross-validation for all models, and therefore we can use cross-validation metrics to generate the leaderboard.  If the ``leaderboard_frame`` is explicitly specified by the user, then that frame will be used to generate the leaderboard metrics instead of using cross-validation metrics. 

For cross-validated AutoML, when the user specifies:

   1. **training**: The ``training_frame`` is split into training (90%) and validation (10%).  
   2. **training + leaderboard**:  The ``training_frame`` is split into training (90%) and validation (10%).  
   3. **training + validation**: Leave frames as-is.
   4. **training + validation + leaderboard**: Leave frames as-is.


If not using cross-validation (by setting ``nfolds = 0``) in AutoML, then we need to make sure there is a test frame (aka. the "leaderboard frame") to score on because cross-validation metrics will not be available.  So when the user specifies:

   1. **training**: The ``training_frame`` is split into training (80%), validation (10%) and leaderboard/test (10%).
   2. **training + leaderboard**:  The ``training_frame`` is split into training (90%) and validation (10%).  Leaderboard frame as-is.
   3. **training + validation**: The ``validation_frame`` is split in half to create a new validation set and a leaderboard/test.  Leave training frame as-is.
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

    # Run AutoML for 20 base models (limited to 1 hour max runtime by default)
    aml <- h2o.automl(x = x, y = y, 
                      training_frame = train,
                      max_models = 20,
                      seed = 1)

    # View the AutoML Leaderboard
    lb <- aml@leaderboard
    print(lb, n = nrow(lb))  # Print all rows instead of default (6 rows)

    #                                                 model_id       auc   logloss mean_per_class_error      rmse       mse
    # 1     StackedEnsemble_AllModels_AutoML_20181022_221411 0.7870176 0.5541308            0.3254615 0.4333729 0.1878121
    # 2  StackedEnsemble_BestOfFamily_AutoML_20181022_221411 0.7857408 0.5553949            0.3265818 0.4340249 0.1883776
    # 3          XGBoost_grid_1_AutoML_20181022_221411_model_3 0.7825571 0.5598532            0.3326678 0.4358508 0.1899659
    # 4                       XGBoost_1_AutoML_20181022_221411 0.7810665 0.5601261            0.3312270 0.4363277 0.1903818
    # 5                       XGBoost_3_AutoML_20181022_221411 0.7808475 0.5611616            0.3240078 0.4364818 0.1905164
    # 6          XGBoost_grid_1_AutoML_20181022_221411_model_4 0.7806241 0.5606613            0.3229925 0.4365599 0.1905845
    # 7                       XGBoost_2_AutoML_20181022_221411 0.7800521 0.5613740            0.3361294 0.4366460 0.1906597
    # 8                           GBM_5_AutoML_20181022_221411 0.7798300 0.5614880            0.3267675 0.4368125 0.1908052
    # 9                           GBM_1_AutoML_20181022_221411 0.7772283 0.5628248            0.3408954 0.4376935 0.1915756
    # 10                          GBM_2_AutoML_20181022_221411 0.7751517 0.5645617            0.3356969 0.4387413 0.1924939
    # 11                          GBM_3_AutoML_20181022_221411 0.7712083 0.5688081            0.3413639 0.4407542 0.1942642
    # 12                          GBM_4_AutoML_20181022_221411 0.7700900 0.5717664            0.3614967 0.4419736 0.1953406
    # 13             GBM_grid_1_AutoML_20181022_221411_model_1 0.7661611 0.5758009            0.3390593 0.4440048 0.1971402
    # 14         XGBoost_grid_1_AutoML_20181022_221411_model_2 0.7651212 0.5864885            0.3520888 0.4475584 0.2003085
    # 15         XGBoost_grid_1_AutoML_20181022_221411_model_1 0.7526767 0.5844030            0.3591614 0.4480039 0.2007075
    # 16             GBM_grid_1_AutoML_20181022_221411_model_2 0.7491659 0.9424442            0.3629487 0.4991597 0.2491604
    # 17                          XRT_1_AutoML_20181022_221411 0.7329677 0.6034210            0.3656316 0.4564392 0.2083368
    # 18                          DRF_1_AutoML_20181022_221411 0.7329626 0.6072326            0.3671294 0.4564434 0.2083406
    # 19    DeepLearning_grid_1_AutoML_20181022_221411_model_2 0.7286182 0.6095040            0.3689510 0.4586798 0.2103872
    # 20                 DeepLearning_1_AutoML_20181022_221411 0.6881761 0.6433626            0.4173049 0.4738028 0.2244891
    # 21             GLM_grid_1_AutoML_20181022_221411_model_1 0.6853161 0.6366259            0.3936651 0.4717700 0.2225669
    # 22    DeepLearning_grid_1_AutoML_20181022_221411_model_1 0.6742703 0.6786356            0.4233795 0.4833303 0.2336081
    # 
    # [22 rows x 6 columns] 


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
    
    # Run AutoML for 20 base models (limited to 1 hour max runtime by default)
    aml = H2OAutoML(max_models=20, seed=1)
    aml.train(x=x, y=y, training_frame=train)

    # View the AutoML Leaderboard
    lb = aml.leaderboard
    lb.head(rows=lb.nrows)  # Print all rows instead of default (10 rows)

    # model_id                                                    auc    logloss    mean_per_class_error      rmse       mse
    # -----------------------------------------------------  --------  ---------  ----------------------  --------  --------
    # StackedEnsemble_AllModels_AutoML_20181022_213938     0.787952   0.553121                0.326584  0.432972  0.187465
    # StackedEnsemble_BestOfFamily_AutoML_20181022_213938  0.786665   0.554442                0.326707  0.433626  0.188031
    # XGBoost_grid_1_AutoML_20181022_213938_model_3          0.782557   0.559853                0.332668  0.435851  0.189966
    # XGBoost_1_AutoML_20181022_213938                       0.781066   0.560126                0.331227  0.436328  0.190382
    # XGBoost_3_AutoML_20181022_213938                       0.780847   0.561162                0.324008  0.436482  0.190516
    # XGBoost_grid_1_AutoML_20181022_213938_model_4          0.780624   0.560661                0.322992  0.43656   0.190585
    # XGBoost_2_AutoML_20181022_213938                       0.780052   0.561374                0.336129  0.436646  0.19066
    # GBM_5_AutoML_20181022_213938                           0.77983    0.561488                0.326767  0.436813  0.190805
    # GBM_1_AutoML_20181022_213938                           0.777228   0.562825                0.340895  0.437694  0.191576
    # GBM_2_AutoML_20181022_213938                           0.775152   0.564562                0.335697  0.438741  0.192494
    # GBM_3_AutoML_20181022_213938                           0.771208   0.568808                0.341364  0.440754  0.194264
    # GBM_4_AutoML_20181022_213938                           0.77009    0.571766                0.361497  0.441974  0.195341
    # GBM_grid_1_AutoML_20181022_213938_model_1              0.766161   0.575801                0.339059  0.444005  0.19714
    # XGBoost_grid_1_AutoML_20181022_213938_model_2          0.765121   0.586489                0.352089  0.447558  0.200309
    # GBM_grid_1_AutoML_20181022_213938_model_2              0.749166   0.942444                0.362949  0.49916   0.24916
    # XGBoost_grid_1_AutoML_20181022_213938_model_1          0.733602   0.596321                0.380896  0.454024  0.206137
    # XRT_1_AutoML_20181022_213938                           0.732968   0.603421                0.365632  0.456439  0.208337
    # DRF_1_AutoML_20181022_213938                           0.732963   0.607233                0.367129  0.456443  0.208341
    # DeepLearning_grid_1_AutoML_20181022_213938_model_2     0.729144   0.612294                0.37187   0.460569  0.212124
    # GLM_grid_1_AutoML_20181022_213938_model_1              0.685316   0.636626                0.393665  0.47177   0.222567
    # DeepLearning_1_AutoML_20181022_213938                  0.684702   0.643051                0.40708   0.474047  0.224721
    # DeepLearning_grid_1_AutoML_20181022_213938_model_1     0.67466    0.694187                0.407733  0.488307  0.238443
    # 
    # [22 rows x 6 columns]


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

+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
|                                               model_id |       auc |   logloss | mean_per_class_error |      rmse |       mse |
+========================================================+===========+===========+======================+===========+===========+
| StackedEnsemble_AllModels_AutoML_20181022_221411       | 0.7870176 | 0.5541308 |            0.3254615 | 0.4333729 | 0.1878121 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| StackedEnsemble_BestOfFamily_AutoML_20181022_221411    | 0.7857408 | 0.5553949 |            0.3265818 | 0.4340249 | 0.1883776 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XGBoost_grid_1_AutoML_20181022_221411_model_3          | 0.7825571 | 0.5598532 |            0.3326678 | 0.4358508 | 0.1899659 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XGBoost_1_AutoML_20181022_221411                       | 0.7810665 | 0.5601261 |            0.3312270 | 0.4363277 | 0.1903818 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XGBoost_3_AutoML_20181022_221411                       | 0.7808475 | 0.5611616 |            0.3240078 | 0.4364818 | 0.1905164 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XGBoost_grid_1_AutoML_20181022_221411_model_4          | 0.7806241 | 0.5606613 |            0.3229925 | 0.4365599 | 0.1905845 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XGBoost_2_AutoML_20181022_221411                       | 0.7800521 | 0.5613740 |            0.3361294 | 0.4366460 | 0.1906597 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GBM_5_AutoML_20181022_221411                           | 0.7798300 | 0.5614880 |            0.3267675 | 0.4368125 | 0.1908052 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GBM_1_AutoML_20181022_221411                           | 0.7772283 | 0.5628248 |            0.3408954 | 0.4376935 | 0.1915756 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GBM_2_AutoML_20181022_221411                           | 0.7751517 | 0.5645617 |            0.3356969 | 0.4387413 | 0.1924939 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GBM_3_AutoML_20181022_221411                           | 0.7712083 | 0.5688081 |            0.3413639 | 0.4407542 | 0.1942642 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GBM_4_AutoML_20181022_221411                           | 0.7700900 | 0.5717664 |            0.3614967 | 0.4419736 | 0.1953406 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GBM_grid_1_AutoML_20181022_221411_model_1              | 0.7661611 | 0.5758009 |            0.3390593 | 0.4440048 | 0.1971402 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XGBoost_grid_1_AutoML_20181022_221411_model_2          | 0.7651212 | 0.5864885 |            0.3520888 | 0.4475584 | 0.2003085 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XGBoost_grid_1_AutoML_20181022_221411_model_1          | 0.7526767 | 0.5844030 |            0.3591614 | 0.4480039 | 0.2007075 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GBM_grid_1_AutoML_20181022_221411_model_2              | 0.7491659 | 0.9424442 |            0.3629487 | 0.4991597 | 0.2491604 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| XRT_1_AutoML_20181022_221411                           | 0.7329677 | 0.6034210 |            0.3656316 | 0.4564392 | 0.2083368 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| DRF_1_AutoML_20181022_221411                           | 0.7329626 | 0.6072326 |            0.3671294 | 0.4564434 | 0.2083406 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| DeepLearning_grid_1_AutoML_20181022_221411_model_2     | 0.7286182 | 0.6095040 |            0.3689510 | 0.4586798 | 0.2103872 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| DeepLearning_1_AutoML_20181022_221411                  | 0.6881761 | 0.6433626 |            0.4173049 | 0.4738028 | 0.2244891 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| GLM_grid_1_AutoML_20181022_221411_model_1              | 0.6853161 | 0.6366259 |            0.3936651 | 0.4717700 | 0.2225669 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+
| DeepLearning_grid_1_AutoML_20181022_221411_model_1     | 0.6742703 | 0.6786356 |            0.4233795 | 0.4833303 | 0.2336081 |
+--------------------------------------------------------+-----------+-----------+----------------------+-----------+-----------+


Experimental Features
~~~~~~~~~~~~~~~~~~~~~

XGBoost
'''''''

AutoML now includes `XGBoost <data-science/xgboost.html>`__ GBMs (Gradient Boosting Machines) among its set of algorithms. This feature is currently provided with the following restrictions:

- XGBoost is used only if it is available globally and if it hasn't been explicitly `disabled <data-science/xgboost.html#disabling-xgboost>`__.
- XGBoost is disabled by default in AutoML when running H2O-3 in multi-node due to current `limitations <data-science/xgboost.html#limitations>`__.  XGBoost can however be enabled experimentally in multi-node by setting the environment variable ``-Dsys.ai.h2o.automl.xgboost.multinode.enabled=true`` (when launching the H2O process from the command line) for every node of the H2O cloud.
- You can check if XGBoost is available by using the ``h2o.xgboost.available()`` in R or ``h2o.estimators.xgboost.H2OXGBoostEstimator.available()`` in Python.


FAQ
~~~

-  **Which models are trained in the AutoML process?**

  The current version of AutoML trains and cross-validates the following algorithms (in the following order):  A default Random Forest (DRF), an Extremely Randomized Forest (XRT), three pre-specified XGBoost GBM (Gradient Boosting Machine) models, five pre-specified H2O GBMs, a near-default Deep Neural Net, a random grid of XGBoost GBMs, a random grid of H2O GBMs, and lastly if there is time, a random grid of Deep Neural Nets.  AutoML then trains two Stacked Ensemble models. Particular algorithms (or groups of algorithms) can be switched off using the ``exclude_algos`` argument. This is useful if you already have some idea of the algorithms that will do well on your dataset. As a recommendation, if you have really wide or sparse data, you may consider skipping the tree-based algorithms (GBM, DRF, XGBoost).

  A list of the hyperparameters searched over for each algorithm in the AutoML process is included in the appendix below.  More `details <https://0xdata.atlassian.net/browse/PUBDEV-6003>`__ about the hyperparamter ranges for the models in addition to the hard-coded models will be added to the appendix at a later date.

  Both of the ensembles should produce better models than any individual model from the AutoML run with the exception of some rare cases.  One ensemble contains all the models, and the second ensemble contains just the best performing model from each algorithm class/family.  The "Best of Family" ensemble is optimized for production use since it only contains six (or fewer) base models.  It should be relatively fast to use (to generate predictions on new data) without much degredation in model performance when compared to the "All Models" ensemble.   

-  **How do I save AutoML runs?**

  Rather than saving an AutoML object itself, currently, the best thing to do is to save the models you want to keep, individually.  A utility for saving all of the models at once, along with a way to save the AutoML object (with leaderboard), will be added in a future release.

-  **Why don't I see XGBoost models when using AutoML in a multi-node H2O cluster?**

  XGBoost is turned off by default for multi-node H2O clusters.


Resources
~~~~~~~~~

- `AutoML Tutorial <https://github.com/h2oai/h2o-tutorials/tree/master/h2o-world-2017/automl>`__ (R and Python notebooks)
- Intro to AutoML + Hands-on Lab `(1 hour video) <https://www.youtube.com/watch?v=42Oo8TOl85I>`__ `(slides) <https://www.slideshare.net/0xdata/intro-to-automl-handson-lab-erin-ledell-machine-learning-scientist-h2oai>`__
- Scalable Automatic Machine Learning in H2O `(1 hour video) <https://www.youtube.com/watch?v=j6rqrEYQNdo>`__ `(slides) <https://www.slideshare.net/0xdata/scalable-automatic-machine-learning-in-h2o-89130971>`__
- `AutoML Roadmap <https://0xdata.atlassian.net/issues/?filter=21603>`__


Appendix: Random Grid Search Parameters
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

AutoML performs hyperparameter search over a variety of H2O algorithms in order to deliver the best model. In AutoML, the following hyperparameters are supported by grid search.  Random Forest and Extremely Randomized Trees are not grid searched (in the current version of AutoML), so they are not included in the list below.


**GLM Hyperparameters**

-  ``alpha``
-  ``missing_values_handling``


**XGBoost Hyperparameters**

-  ``ntrees``
-  ``max_depth``
-  ``min_rows``
-  ``min_sum_hessian_in_leaf``
-  ``sample_rate``
-  ``col_sample_rate``
-  ``col_sample_rate_per_tree``
-  ``booster``
-  ``reg_lambda``
-  ``reg_alpha``

**GBM Hyperparameters**

-  ``histogram_type``
-  ``ntrees``
-  ``max_depth``
-  ``min_rows``
-  ``learn_rate``
-  ``sample_rate``
-  ``col_sample_rate``
-  ``col_sample_rate_per_tree``
-  ``min_split_improvement``


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
