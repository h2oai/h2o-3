Early Stopping
~~~~~~~~~~~~~~

Most of the H2O supervised learning algorithms allow for early stopping during model building and scoring.   This lets the model find the optimal moment to stop training. Early stopping can be turned on/off in most algorithms via the ``stopping_rounds`` parameter.  Early stopping is turned on by default in Deep Learning (DNN), but must be manually turned on for the tree-based models (GBM/DRF/XGBoost) by setting ``stopping_rounds`` > 0.  In GLM and GAM, early stopping is turned on by default and can be disabled via the ``early_stopping`` parameter.


Data Scenarios
'''''''''''''' 

Early stopping can be done using only a ``training_frame`` (which is not recommended), using a ``validation_frame``, or using cross-validation data. When using cross-validation data and a ``validation_frame`` at the same time, only the cross-validation data will be used for early stopping in the final model. 

If you have provided:

- ``training_frame`` only: training data will be used to estimate the optimal stopping point;
- ``training_frame`` and ``validation_frame``: validation data will be used to estimate the optimal stopping point;
- ``training_frame``, ``validation_frame``, and ``nfolds`` > 1: cross-validation data will be used to estimate the optimal stopping point;
- ``training_frame`` and ``nfolds`` > 1: cross-validation data will be used to estimate the optimal stopping point.

**Early Stopping via Cross-Validation**

When cross-validation is turned on, the cross-validation process is used to determine the optimimal early stopping point.  Above, *"cross-validation data will be used to estimate the optimal stopping point"* is defined as follows: 

1. If a ``validation_frame`` is provided, then the cross-validation models will use the ``validation_frame`` for early stopping (or the ``training_frame``, if the ``validation_frame`` is not provided). Since each model trained in the cross-validation process will be slightly different (each is trained on a different subset of the training data), that means that each model will likely have a unique optimal early stopping point, as determined by the validation set (even though the same validation set is used, the models are slightly different and thus have unique estimates of the optimal stopping point).  However, on the final model, we do not use ``validation_frame``-based early stopping.  We simply set the ``ntrees`` in tree models (or ``epochs`` in DNNs) equal to the average of the optimal ``ntrees`` (or ``epochs``) found across the cross-validated models.
2. If a ``validation_frame`` is not provided, then each cross-validation model will use it's corresponding holdout fold as a "validation set" for early stopping. Again, the final model just uses ``ntrees`` (or ``epochs``) equal to the average of the optimal value found across all cross-validation models.


Early Stopping in AutoML, Grid Search, Deep Learning, DRF, GBM, and XGBoost
''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''' 

In AutoML, Grid Search, Deep Learning, DRF, GBM, and XGBoost, the following additional parameters are used for early stopping:

- :ref:`stopping_rounds` (Defaults to 3 in AutoML; defaults to 5 in Deep Learning; defaults to 0/disabled in DRF, GBM, XGBoost.)
- :ref:`stopping_tolerance` (Defaults to 0.001. In AutoML for datasets with more than 1 million rows, this defaults to a larger valued determined by the size of the dataset and the non-NA-rate.)
- :ref:`stopping_metric` (Defaults to "logloss" for classification and "deviance" for regression.)

The simplest way to turn on early stopping in these algorithms is to use a number >=1 in ``stopping_rounds``. The default values for the other two parameters will work fairly well, but a ``stopping_tolerance`` of 0 is a common alternative to the default.

Additionally, take :ref:`score_tree_interval` and/or :ref:`score_each_iteration` into account when using these early stopping methods. The stopping rounds applies to the number of scoring iterations H2O has performed, so regular scoring iterations of small size can help control early stopping the most (though there is a speed tradeoff to scoring more often). The default is to use H2O’s assessment of a reasonable ratio of training time to scoring time, which often results in inconsistent scoring gaps.

Early Stopping in GLM and GAM
'''''''''''''''''''''''''''''

In GLM and GAM, the following additional parameters are used for early stopping:

- :ref:`early_stopping` (Default is enabled.)
- :ref:`max_active_predictors` (Default can vary based on the solver.)
- :ref:`stopping_rounds` (Defaults to 0 in GLM and GAM.)
- :ref:`stopping_tolerance` (Defaults to 0.001 in GLM and GAM.)
- :ref:`stopping_metric` (Defaults to "logloss" for classification and "deviance" for regression.)

When ``early_stopping`` is enabled, GLM and GAM will automatically stop building a model when there is no more relative improvement on the training or validation (if provided) set. This option prevents expensive model building with many predictors when no more improvements are occurring.

The ``max_active_predictors`` option limits the number of active predictors. (Note that the actual number of non-zero predictors in the model is going to be slightly lower). This is useful when obtaining a sparse solution to avoid costly computation of models with too many predictors. When using the :math:`\lambda_1` penalty with lambda search, this option will stop the search before it completes. Models built at the beginning of the lambda search have higher lambda values, consider fewer predictors, and take less time to calculate the model. Models built at the end of the lambda search have lower lambda values, incorporate more predictors, and take a longer time to calculate the model. Set the ``nlambdas`` parameter for a lambda search to specify the number of models attempted across the search. 


Time-Based Stopping
'''''''''''''''''''

Rather than using model peformance on a holdout set to estimate the optimal stopping time, the user can choose to put a hard limit on the time that an algorithm trains via the following parameter:

- :ref:`max_runtime_secs` (Defaults to 0/disabled.)

The ``max_runtime_secs`` option specifes the maximum runtime in seconds that you want to allot in order to complete the model. If this maximum runtime is exceeded before the model build is completed, then the model will fail. When performing a grid search, this option specifies the maximum runtime in seconds for the entire grid. This option can also be combined with ``max_runtime_secs`` in the model parameters. If ``max_runtime_secs`` is not set in the model parameters, then each model build is launched with a limit equal to the remainder of the grid time. On the other hand, if ``max_runtime_secs`` is set in the model parameters, then each build is launched with a limit equal to the minimum of the model time limit and the remaining time for the grid.

In `H2O AutoML <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/automl.html>`__, there's a ``max_runtime_secs`` parameter that limits the total time to the AutoML process.  However, there's also a ``max_runtime_secs_per_model`` parameter that limits the training time for a single model.  This value gets passed down to the ``max_runtime_secs`` parameter (defined above) for each algorithm in the AutoML process.
