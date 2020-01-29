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

- `max_runtime_secs <data-science/algo-params/max_runtime_secs.html>`__: This argument specifies the maximum time that the AutoML process will run for, prior to training the final Stacked Ensemble models. The default is 0 (no limit), but dynamically sets to 1 hour if none of ``max_runtime_secs`` and ``max_models`` are specified by the user.

- `max_models <data-science/algo-params/max_models.html>`__: Specify the maximum number of models to build in an AutoML run, excluding the Stacked Ensemble models.  Defaults to ``NULL/None``. 


Optional Parameters
~~~~~~~~~~~~~~~~~~~

Optional Data Parameters
''''''''''''''''''''''''

- `x <data-science/algo-params/x.html>`__: A list/vector of predictor column names or indexes.  This argument only needs to be specified if the user wants to exclude columns from the set of predictors.  If all columns (other than the response) should be used in prediction, then this does not need to be set.

- `validation_frame <data-science/algo-params/validation_frame.html>`__: This argument is ignored unless ``nfolds == 0``, in which a validation frame can be specified and used for early stopping of individual models and early stopping of the grid searches (unless ``max_models`` or ``max_runtime_secs`` overrides metric-based early stopping).  By default and when ``nfolds > 1``, cross-validation metrics will be used for early stopping and thus ``validation_frame`` will be ignored.

- **leaderboard_frame**: This argument allows the user to specify a particular data frame to use to score and rank models on the leaderboard. This frame will not be used for anything besides leaderboard scoring. If a leaderboard frame is not specified by the user, then the leaderboard will use cross-validation metrics instead, or if cross-validation is turned off by setting ``nfolds = 0``, then a leaderboard frame will be generated automatically from the training frame.

- `blending_frame <data-science/algo-params/blending_frame.html>`__: Specifies a frame to be used for computing the predictions that serve as the training frame for the Stacked Ensemble models metalearner. If provided, all Stacked Ensembles produced by AutoML will be trained using Blending (a.k.a. Holdout Stacking) instead of the default Stacking method based on cross-validation.

- `fold_column <data-science/algo-params/fold_column.html>`__: Specifies a column with cross-validation fold index assignment per observation. This is used to override the default, randomized, 5-fold cross-validation scheme for individual models in the AutoML run.

- `weights_column <data-science/algo-params/weights_column.html>`__: Specifies a column with observation weights. Giving some observation a weight of zero is equivalent to excluding it from the dataset; giving an observation a relative weight of 2 is equivalent to repeating that row twice. Negative weights are not allowed.

Optional Miscellaneous Parameters
'''''''''''''''''''''''''''''''''

- `nfolds <data-science/algo-params/nfolds.html>`__:  Number of folds for k-fold cross-validation of the models in the AutoML run. Defaults to 5. Use 0 to disable cross-validation; this will also disable Stacked Ensembles (thus decreasing the overall best model performance).

- `balance_classes <data-science/algo-params/balance_classes.html>`__: Specify whether to oversample the minority classes to balance the class distribution. This option is not enabled by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **max\_after\_balance\_size** parameter.

- `class_sampling_factors <data-science/algo-params/class_sampling_factors.html>`__: Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance. Note that this requires ``balance_classes=true``.

- `max_after_balance_size <data-science/algo-params/max_after_balance_size.html>`__: Specify the maximum relative size of the training data after balancing class counts (**balance\_classes** must be enabled). Defaults to 5.0.  (The value can be less than 1.0).

- `max_runtime_secs_per_model <data-science/algo-params/max_runtime_secs_per_model.html>`__: Specify the max amount of time dedicated to the training of each individual model in the AutoML run. Defaults to 0 (disabled). Note that setting this parameter can affect AutoML reproducibility.

- **modeling_plan**: The list of modeling steps to be used by the AutoML engine. (They may not all get executed, depending on other constraints.)

- `monotone_constraints <data-science/algo-params/monotone_constraints.html>`__: A mapping that represents monotonic constraints. Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint. 

-  `stopping_metric <data-science/algo-params/stopping_metric.html>`__: Specify the metric to use for early stopping. Defaults to ``AUTO``. The available options are:
    
    - ``AUTO``: This defaults to ``logloss`` for classification and ``deviance`` for regression.
    - ``deviance`` (mean residual deviance)
    - ``logloss``
    - ``MSE``
    - ``RMSE``
    - ``MAE``
    - ``RMSLE``
    - ``AUC`` (area under the ROC curve)
    - ``AUCPR`` (area under the Precision-Recall curve)
    - ``lift_top_group``
    - ``misclassification``
    - ``mean_per_class_error``

- `stopping_tolerance <data-science/algo-params/stopping_tolerance.html>`__: This option specifies the relative tolerance for the metric-based stopping criterion to stop a grid search and the training of individual models within the AutoML run. This value defaults to 0.001 if the dataset is at least 1 million rows; otherwise it defaults to a bigger value determined by the size of the dataset and the non-NA-rate.  In that case, the value is computed as 1/sqrt(nrows * non-NA-rate).

- `stopping_rounds <data-science/algo-params/stopping_rounds.html>`__: This argument is used to stop model training when the stopping metric (e.g. AUC) doesn’t improve for this specified number of training rounds, based on a simple moving average.   In the context of AutoML, this controls early stopping both within the random grid searches as well as the individual models.  Defaults to 3 and must be an non-negative integer.  To disable early stopping altogether, set this to 0. 

- `sort_metric <data-science/algo-params/sort_metric.html>`__: Specifies the metric used to sort the Leaderboard by at the end of an AutoML run. Available options include:

    - ``AUTO``: This defaults to ``AUC`` for binary classification, ``mean_per_class_error`` for multinomial classification, and ``deviance`` for regression.
    - ``deviance`` (mean residual deviance)
    - ``logloss``
    - ``MSE``
    - ``RMSE``
    - ``MAE``
    - ``RMSLE``
    - ``AUC`` (area under the ROC curve)
    - ``AUCPR`` (area under the Precision-Recall curve)
    - ``mean_per_class_error``

- `seed <data-science/algo-params/seed.html>`__: Integer. Set a seed for reproducibility. AutoML can only guarantee reproducibility under certain conditions.  H2O Deep Learning models are not reproducible by default for performance reasons, so if the user requires reproducibility, then ``exclude_algos`` must contain ``"DeepLearning"``.  In addition ``max_models`` must be used because ``max_runtime_secs`` is resource limited, meaning that if the available compute resources are not the same between runs, AutoML may be able to train more models on one run vs another.  Defaults to ``NULL/None``.

- **project_name**: Character string to identify an AutoML project. Defaults to ``NULL/None``, which means a project name will be auto-generated based on the training frame ID.  More models can be trained and added to an existing AutoML project by specifying the same project name in muliple calls to the AutoML function (as long as the same training frame is used in subsequent runs).

- `exclude_algos <data-science/algo-params/exclude_algos.html>`__: A list/vector of character strings naming the algorithms to skip during the model-building phase.  An example use is ``exclude_algos = ["GLM", "DeepLearning", "DRF"]`` in Python or ``exclude_algos = c("GLM", "DeepLearning", "DRF")`` in R.  Defaults to ``None/NULL``, which means that all appropriate H2O algorithms will be used if the search stopping criteria allows and if the ``include_algos`` option is not specified. This option is mutually exclusive with ``include_algos``. The available algorithms are:

    - ``DRF`` (This includes both the Random Forest and Extremely Randomized Trees (XRT) models. Refer to the :ref:`xrt` section in the DRF chapter and the `histogram_type <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/algo-params/histogram_type.html>`__ parameter description for more information.)
    - ``GLM``
    - ``XGBoost``  (XGBoost GBM)
    - ``GBM``  (H2O GBM)
    - ``DeepLearning``  (Fully-connected multi-layer artificial neural network)
    - ``StackedEnsemble``

- `include_algos <data-science/algo-params/include_algos.html>`__: A list/vector of character strings naming the algorithms to include during the model-building phase.  An example use is ``include_algos = ["GLM", "DeepLearning", "DRF"]`` in Python or ``include_algos = c("GLM", "DeepLearning", "DRF")`` in R.  Defaults to ``None/NULL``, which means that all appropriate H2O algorithms will be used if the search stopping criteria allows and if no algorithms are specified in ``exclude_algos``. This option is mutually exclusive with ``exclude_algos``. The available algorithms are:

    - ``DRF`` (This includes both the Random Forest and Extremely Randomized Trees (XRT) models. Refer to the :ref:`xrt` section in the DRF chapter and the `histogram_type <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/algo-params/histogram_type.html>`__ parameter description for more information.)
    - ``GLM``
    - ``XGBoost``  (XGBoost GBM)
    - ``GBM``  (H2O GBM)
    - ``DeepLearning``  (Fully-connected multi-layer artificial neural network)
    - ``StackedEnsemble``

- `keep_cross_validation_predictions <data-science/algo-params/keep_cross_validation_predictions.html>`__: Specify whether to keep the predictions of the cross-validation predictions. This needs to be set to TRUE if running the same AutoML object for repeated runs because CV predictions are required to build additional Stacked Ensemble models in AutoML. This option defaults to FALSE.

- `keep_cross_validation_models <data-science/algo-params/keep_cross_validation_models.html>`__: Specify whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to FALSE.

- `keep_cross_validation_fold_assignment <data-science/algo-params/keep_cross_validation_fold_assignment.html>`__: Enable this option to preserve the cross-validation fold assignment.  Defaults to FALSE.

- **verbosity**: (Optional: Python and R only) The verbosity of the backend messages printed during training. Must be one of ``"debug", "info", "warn"``. Defaults to ``NULL/None`` (client logging disabled).

-  `export_checkpoints_dir <data-science/algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will automatically be exported.

Notes
~~~~~

If the user sets ``nfolds == 0``, then cross-validation metrics will not be available to populate the leaderboard.  In this case, we need to make sure there is a holdout frame (aka. the "leaderboard frame") to score the models on so that we can generate model performance metrics for the leaderboard.  Without cross-validation, we will also require a validation frame to be used for early stopping on the models.  Therefore, if either of these frames are not provided by the user, they will be automatically partitioned from the training data.  If either frame is missing, 10% of the training data will be used to create a missing frame (if both are missing then a total of 20% of the training data will be used to create a 10% validation and 10% leaderboard frame).


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

    # AutoML Leaderboard
    lb <- aml@leaderboard

    # Optionally edd extra model information to the leaderboard
    lb <- h2o.get_leaderboard(aml, extra_columns = "ALL")

    # Print all rows (instead of default 6 rows)
    print(lb, n = nrow(lb))

    #                                               model_id       auc   logloss     aucpr mean_per_class_error      rmse       mse training_time_ms predict_time_per_row_ms
    # 1     StackedEnsemble_AllModels_AutoML_20191213_174603 0.7898435 0.5510671 0.8046721            0.3146651 0.4320451 0.1866630              924                0.056950
    # 2  StackedEnsemble_BestOfFamily_AutoML_20191213_174603 0.7897679 0.5509061 0.8056961            0.3130590 0.4319773 0.1866044              639                0.024567
    # 3       XGBoost_grid__1_AutoML_20191213_174603_model_4 0.7846980 0.5568099 0.8031201            0.3231434 0.4347432 0.1890017             3092                0.002083
    # 4                     XGBoost_3_AutoML_20191213_174603 0.7842324 0.5577491 0.8023413            0.3179334 0.4349757 0.1892039             2878                0.002173
    # 5                     XGBoost_2_AutoML_20191213_174603 0.7835333 0.5559967 0.8031894            0.3247505 0.4346776 0.1889446             4635                0.003292
    # 6       XGBoost_grid__1_AutoML_20191213_174603_model_3 0.7825815 0.5602182 0.8007489            0.3433404 0.4359438 0.1900470             2695                0.002269
    # 7                         GBM_5_AutoML_20191213_174603 0.7821904 0.5583525 0.8002338            0.3196580 0.4355116 0.1896703              768                0.004318
    # 8                     XGBoost_1_AutoML_20191213_174603 0.7819008 0.5579435 0.8012372            0.3254461 0.4355185 0.1896764             4428                0.003039
    # 9       XGBoost_grid__1_AutoML_20191213_174603_model_1 0.7816477 0.5611116 0.7992025            0.3120154 0.4364336 0.1904743             5430                0.002557
    # 10                        GBM_2_AutoML_20191213_174603 0.7776734 0.5625137 0.7961811            0.3340557 0.4375834 0.1914793              655                0.003772
    # 11                        GBM_1_AutoML_20191213_174603 0.7772944 0.5627440 0.7982445            0.3562612 0.4377271 0.1916050              700                0.003571
    # 12                        GBM_3_AutoML_20191213_174603 0.7754882 0.5647945 0.7935850            0.3279708 0.4387224 0.1924774              635                0.003748
    # 13      XGBoost_grid__1_AutoML_20191213_174603_model_2 0.7736214 0.5781406 0.7919489            0.3411184 0.4439634 0.1971035             9722                0.003896
    # 14          GBM_grid__1_AutoML_20191213_174603_model_1 0.7726562 0.5683138 0.7916402            0.3321751 0.4400490 0.1936432              647                0.004546
    # 15                        GBM_4_AutoML_20191213_174603 0.7724798 0.5694830 0.7910775            0.3369135 0.4408733 0.1943693              800                0.004142
    # 16                        DRF_1_AutoML_20191213_174603 0.7649750 0.5801003 0.7815876            0.3360011 0.4452215 0.1982222             1399                0.007475
    # 17                        XRT_1_AutoML_20191213_174603 0.7599571 0.5851581 0.7768566            0.3389764 0.4475980 0.2003440             1426                0.004950
    # 18          GBM_grid__1_AutoML_20191213_174603_model_2 0.7480074 0.6329810 0.7588329            0.3753078 0.4622370 0.2136630              588                0.003119
    # 19 DeepLearning_grid__2_AutoML_20191213_174603_model_1 0.7398841 0.6006880 0.7479482            0.3598830 0.4552050 0.2072116            40408                0.010939
    # 20               DeepLearning_1_AutoML_20191213_174603 0.7004059 0.6316904 0.7019903            0.3953328 0.4690842 0.2200400              445                0.002288
    # 21 DeepLearning_grid__1_AutoML_20191213_174603_model_1 0.6922355 0.6715124 0.6918834            0.4098358 0.4783934 0.2288603            32546                0.003623
    # 22                        GLM_1_AutoML_20191213_174603 0.6826481 0.6385205 0.6803442            0.3972341 0.4726827 0.2234290              195                0.001312
    # 
    # [22 rows x 9 columns] 


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
    from h2o.automl import H2OAutoML, get_leaderboard

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

    # AutoML Leaderboard
    lb = aml.leaderboard

    # Optionally edd extra model information to the leaderboard
    lb = get_leaderboard(aml, extra_columns='ALL')

    # Print all rows (instead of default 10 rows)
    lb.head(rows=lb.nrows)

    # model_id                                                  auc    logloss     aucpr    mean_per_class_error      rmse       mse    training_time_ms    predict_time_per_row_ms
    # ---------------------------------------------------  --------  ---------  --------  ----------------------  --------  --------  ------------------  -------------------------
    # StackedEnsemble_AllModels_AutoML_20191213_174603     0.789844   0.551067  0.804672                0.314665  0.432045  0.186663                 924                   0.05695
    # StackedEnsemble_BestOfFamily_AutoML_20191213_174603  0.789768   0.550906  0.805696                0.313059  0.431977  0.186604                 639                   0.024567
    # XGBoost_grid__1_AutoML_20191213_174603_model_4       0.784698   0.55681   0.80312                 0.323143  0.434743  0.189002                3092                   0.002083
    # XGBoost_3_AutoML_20191213_174603                     0.784232   0.557749  0.802341                0.317933  0.434976  0.189204                2878                   0.002173
    # XGBoost_2_AutoML_20191213_174603                     0.783533   0.555997  0.803189                0.32475   0.434678  0.188945                4635                   0.003292
    # XGBoost_grid__1_AutoML_20191213_174603_model_3       0.782582   0.560218  0.800749                0.34334   0.435944  0.190047                2695                   0.002269
    # GBM_5_AutoML_20191213_174603                         0.78219    0.558353  0.800234                0.319658  0.435512  0.18967                  768                   0.004318
    # XGBoost_1_AutoML_20191213_174603                     0.781901   0.557944  0.801237                0.325446  0.435519  0.189676                4428                   0.003039
    # XGBoost_grid__1_AutoML_20191213_174603_model_1       0.781648   0.561112  0.799203                0.312015  0.436434  0.190474                5430                   0.002557
    # GBM_2_AutoML_20191213_174603                         0.777673   0.562514  0.796181                0.334056  0.437583  0.191479                 655                   0.003772
    # GBM_1_AutoML_20191213_174603                         0.777294   0.562744  0.798244                0.356261  0.437727  0.191605                 700                   0.003571
    # GBM_3_AutoML_20191213_174603                         0.775488   0.564794  0.793585                0.327971  0.438722  0.192477                 635                   0.003748
    # XGBoost_grid__1_AutoML_20191213_174603_model_2       0.773621   0.578141  0.791949                0.341118  0.443963  0.197104                9722                   0.003896
    # GBM_grid__1_AutoML_20191213_174603_model_1           0.772656   0.568314  0.79164                 0.332175  0.440049  0.193643                 647                   0.004546
    # GBM_4_AutoML_20191213_174603                         0.77248    0.569483  0.791078                0.336913  0.440873  0.194369                 800                   0.004142
    # DRF_1_AutoML_20191213_174603                         0.764975   0.5801    0.781588                0.336001  0.445222  0.198222                1399                   0.007475
    # XRT_1_AutoML_20191213_174603                         0.759957   0.585158  0.776857                0.338976  0.447598  0.200344                1426                   0.00495
    # GBM_grid__1_AutoML_20191213_174603_model_2           0.748007   0.632981  0.758833                0.375308  0.462237  0.213663                 588                   0.003119
    # DeepLearning_grid__2_AutoML_20191213_174603_model_1  0.739884   0.600688  0.747948                0.359883  0.455205  0.207212               40408                   0.010939
    # DeepLearning_1_AutoML_20191213_174603                0.700406   0.63169   0.70199                 0.395333  0.469084  0.22004                  445                   0.002288
    # DeepLearning_grid__1_AutoML_20191213_174603_model_1  0.692235   0.671512  0.691883                0.409836  0.478393  0.22886                32546                   0.003623
    # GLM_1_AutoML_20191213_174603                         0.682648   0.63852   0.680344                0.397234  0.472683  0.223429                 195                   0.001312
    # 
    # [22 rows x 9 columns]    


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

+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
|                                               model_id |      auc |  logloss |    aucpr | mean_per_class_error |     rmse |      mse | training_time_ms | predict_time_per_row_ms |
+========================================================+==========+==========+==========+======================+==========+==========+==================+=========================+
| StackedEnsemble_AllModels_AutoML_20191213_174603       | 0.789844 | 0.551067 | 0.804672 |             0.314665 | 0.432045 | 0.186663 |              924 |                0.05695  |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| StackedEnsemble_BestOfFamily_AutoML_20191213_174603    | 0.789768 | 0.550906 | 0.805696 |             0.313059 | 0.431977 | 0.186604 |              639 |                0.024567 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XGBoost_grid__1_AutoML_20191213_174603_model_4         | 0.784698 | 0.55681  | 0.80312  |             0.323143 | 0.434743 | 0.189002 |             3092 |                0.002083 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XGBoost_3_AutoML_20191213_174603                       | 0.784232 | 0.557749 | 0.802341 |             0.317933 | 0.434976 | 0.189204 |             2878 |                0.002173 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XGBoost_2_AutoML_20191213_174603                       | 0.783533 | 0.555997 | 0.803189 |             0.32475  | 0.434678 | 0.188945 |             4635 |                0.003292 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XGBoost_grid__1_AutoML_20191213_174603_model_3         | 0.782582 | 0.560218 | 0.800749 |             0.34334  | 0.435944 | 0.190047 |             2695 |                0.002269 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GBM_5_AutoML_20191213_174603                           | 0.78219  | 0.558353 | 0.800234 |             0.319658 | 0.435512 | 0.18967  |              768 |                0.004318 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XGBoost_1_AutoML_20191213_174603                       | 0.781901 | 0.557944 | 0.801237 |             0.325446 | 0.435519 | 0.189676 |             4428 |                0.003039 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XGBoost_grid__1_AutoML_20191213_174603_model_1         | 0.781648 | 0.561112 | 0.799203 |             0.312015 | 0.436434 | 0.190474 |             5430 |                0.002557 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GBM_2_AutoML_20191213_174603                           | 0.777673 | 0.562514 | 0.796181 |             0.334056 | 0.437583 | 0.191479 |              655 |                0.003772 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GBM_1_AutoML_20191213_174603                           | 0.777294 | 0.562744 | 0.798244 |             0.356261 | 0.437727 | 0.191605 |              700 |                0.003571 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GBM_3_AutoML_20191213_174603                           | 0.775488 | 0.564794 | 0.793585 |             0.327971 | 0.438722 | 0.192477 |              635 |                0.003748 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XGBoost_grid__1_AutoML_20191213_174603_model_2         | 0.773621 | 0.578141 | 0.791949 |             0.341118 | 0.443963 | 0.197104 |             9722 |                0.003896 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GBM_grid__1_AutoML_20191213_174603_model_1             | 0.772656 | 0.568314 | 0.79164  |             0.332175 | 0.440049 | 0.193643 |              647 |                0.004546 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GBM_4_AutoML_20191213_174603                           | 0.77248  | 0.569483 | 0.791078 |             0.336913 | 0.440873 | 0.194369 |              800 |                0.004142 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| DRF_1_AutoML_20191213_174603                           | 0.764975 | 0.5801   | 0.781588 |             0.336001 | 0.445222 | 0.198222 |             1399 |                0.007475 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| XRT_1_AutoML_20191213_174603                           | 0.759957 | 0.585158 | 0.776857 |             0.338976 | 0.447598 | 0.200344 |             1426 |                0.00495  |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GBM_grid__1_AutoML_20191213_174603_model_2             | 0.748007 | 0.632981 | 0.758833 |             0.375308 | 0.462237 | 0.213663 |              588 |                0.003119 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| DeepLearning_grid__2_AutoML_20191213_174603_model_1    | 0.739884 | 0.600688 | 0.747948 |             0.359883 | 0.455205 | 0.207212 |            40408 |                0.010939 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| DeepLearning_1_AutoML_20191213_174603                  | 0.700406 | 0.63169  | 0.70199  |             0.395333 | 0.469084 | 0.22004  |              445 |                0.002288 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| DeepLearning_grid__1_AutoML_20191213_174603_model_1    | 0.692235 | 0.671512 | 0.691883 |             0.409836 | 0.478393 | 0.22886  |            32546 |                0.003623 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+
| GLM_1_AutoML_20191213_174603                           | 0.682648 | 0.63852  | 0.680344 |             0.397234 | 0.472683 | 0.223429 |              195 |                0.001312 |
+--------------------------------------------------------+----------+----------+----------+----------------------+----------+----------+------------------+-------------------------+


When using Python or R clients, you can also access meta information with the following AutoML object properties:

- **event_log**: an ``H2OFrame`` with selected AutoML backend events generated during training.
- **training_info**: a dictionary exposing data that could be useful for post-analysis; for example various timings.



Experimental Features
---------------------

XGBoost
~~~~~~~

AutoML now includes `XGBoost <data-science/xgboost.html>`__ GBMs (Gradient Boosting Machines) among its set of algorithms. This feature is currently provided with the following restrictions:

- XGBoost is used only if it is available globally and if it hasn't been explicitly `disabled <data-science/xgboost.html#disabling-xgboost>`__.
- You can check if XGBoost is available by using the ``h2o.xgboost.available()`` in R or ``h2o.estimators.xgboost.H2OXGBoostEstimator.available()`` in Python.


FAQ
---

-  **Which models are trained in the AutoML process?**

  The current version of AutoML trains and cross-validates the following algorithms (in the following order):  three pre-specified XGBoost GBM (Gradient Boosting Machine) models, a fixed grid of GLMs, a default Random Forest (DRF), five pre-specified H2O GBMs, a near-default Deep Neural Net, an Extremely Randomized Forest (XRT), a random grid of XGBoost GBMs, a random grid of H2O GBMs, and a random grid of Deep Neural Nets.  In some cases, there will not be enough time to complete all the algorithms, so some may be missing from teh leaderboard.  AutoML then trains two Stacked Ensemble models (more info about the ensembles below). Particular algorithms (or groups of algorithms) can be switched off using the ``exclude_algos`` argument. This is useful if you already have some idea of the algorithms that will do well on your dataset, though sometimes this can lead to a loss of performance because having more diversity among the set of models generally increases the performance of the Stacked Ensembles. As a recommendation, if you have really wide (10k+ columns) and/or sparse data, you may consider skipping the tree-based algorithms (GBM, DRF, XGBoost).

  A list of the hyperparameters searched over for each algorithm in the AutoML process is included in the appendix below.  More `details <https://0xdata.atlassian.net/browse/PUBDEV-6003>`__ about the hyperparamter ranges for the models in addition to the hard-coded models will be added to the appendix at a later date.

  Both of the ensembles should produce better models than any individual model from the AutoML run with the exception of some rare cases.  One ensemble contains all the models, and the second ensemble contains just the best performing model from each algorithm class/family.  The "Best of Family" ensemble is optimized for production use since it only contains six (or fewer) base models.  It should be relatively fast to use (to generate predictions on new data) without much degredation in model performance when compared to the "All Models" ensemble.   

-  **How do I save AutoML runs?**

  Rather than saving an AutoML object itself, currently, the best thing to do is to save the models you want to keep, individually.  A utility for saving all of the models at once, along with a way to save the AutoML object (with leaderboard), will be added in a future release.

-   **Can we make use of GPUs with AutoML?** 

  XGBoost models in AutoML can make use of GPUs. Keep in mind that the following requirements must be met:

  - NVIDIA GPUs (GPU Cloud, DGX Station, DGX-1, or DGX-2)
  - CUDA 8

  You can monitor your GPU utilization via the ``nvidia-smi`` command. Refer to https://developer.nvidia.com/nvidia-system-management-interface for more information.

Resources
---------

- `AutoML Tutorial <https://github.com/h2oai/h2o-tutorials/tree/master/h2o-world-2017/automl>`__ (R and Python notebooks)
- Intro to AutoML + Hands-on Lab `(1 hour video) <https://www.youtube.com/watch?v=42Oo8TOl85I>`__ `(slides) <https://www.slideshare.net/0xdata/intro-to-automl-handson-lab-erin-ledell-machine-learning-scientist-h2oai>`__
- Scalable Automatic Machine Learning in H2O `(1 hour video) <https://www.youtube.com/watch?v=j6rqrEYQNdo>`__ `(slides) <https://www.slideshare.net/0xdata/scalable-automatic-machine-learning-in-h2o-89130971>`__
- `AutoML Roadmap <https://0xdata.atlassian.net/issues/?filter=21603>`__


Random Grid Search Parameters
-----------------------------

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
-  ``adaptive_rate``
-  ``activation``
-  ``rho``
-  ``epsilon``
-  ``input_dropout_ratio``
-  ``hidden``
-  ``hidden_dropout_ratios``


Additional Information
----------------------

AutoML development is tracked `here <https://0xdata.atlassian.net/issues/?filter=20700>`__. This page lists all open or in-progress AutoML JIRA tickets.