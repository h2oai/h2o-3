Quantiles
---------

**Note**: The quantile results in Flow are computed lazily on-demand and cached. It is a fast approximation (max - min / 1024) that is very accurate for most use cases. If the distribution is skewed, the quantile results may not be as accurate as the results obtained using ``h2o.quantile`` in R or ``H2OFrame.quantile`` in Python.

Early Stopping
--------------

All of the H2O supervised learning algorithms allow for early stopping during model building and scoring. 

Early Stopping in All Supervised Algorithms
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- :ref:`max_runtime_secs` (Defaults to 0/disabled.)

The ``max_runtime_secs`` option specifes the maximum runtime in seconds that you want to allot in order to complete the model. If this maximum runtime is exceeded before the model build is completed, then the model will fail. When performing a grid search, this option specifies the maximum runtime in seconds for the entire grid. This option can also be combined with ``max_runtime_secs`` in the model parameters. If ``max_runtime_secs`` is not set in the model parameters, then each model build is launched with a limit equal to the remainder of the grid time. On the other hand, if ``max_runtime_secs`` is set in the model parameters, then each build is launched with a limit equal to the minimum of the model time limit and the remaining time for the grid.

Early Stopping in AutoML, Grid Search, Deep Learning, DRF, GBM, and XGBoost
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In AutoML, Grid Search, Deep Learning, DRF, GBM, and XGBoost, the following additional parameters are used for early stopping:

- :ref:`stopping_rounds` (Defaults to 3 in AutoML; defaults to 5 in Deep Learning; defaults to 0/disabled in DRF, GBM, XGBoost.)
- :ref:`stopping_tolerance` (Defaults to 0.001. In AutoML for datasets with more than 1 million rows, this defaults to a larger valued determined by the size of the dataset and the non-NA-rate.)
- :ref:`stopping_metric` (Defaults to "logloss" for classification and "deviance" for regression.)

The simplest way to turn on early stopping in these algorithms is to use a number >=1 in ``stopping_rounds``. The default values for the other two parameters will work fairly well, but a ``stopping_tolerance`` of 0 is a common alternative to the default.

Additionally, take :ref:`score_tree_interval` and/or :ref:`score_each_iteration` into account when using these early stopping methods. The stopping rounds applies to the number of scoring iterations H2O has performed, so regular scoring iterations of small size can help control early stopping the most (though there is a speed tradeoff to scoring more often). The default is to use H2O’s assessment of a reasonable ratio of training time to scoring time, which often results in inconsistent scoring gaps.

Early Stopping in GLM
~~~~~~~~~~~~~~~~~~~~~

In GLM, the following parameters additional are used for early stopping:

- :ref:`early_stopping` (Default is enabled.)
- :ref:`max_active_predictors` (Default can vary based on the solver.)

When ``early_stopping`` is enabled, GLM will automatically stop building a model when there is no more relative improvement on the training or validation (if provided) set. This option prevents expensive model building with many predictors when no more improvements are occurring.

The ``max_active_predictors`` option limits the number of active predictors. (Note that the actual number of non-zero predictors in the model is going to be slightly lower). This is useful when obtaining a sparse solution to avoid costly computation of models with too many predictors. When using the :math:`\lambda_1` penalty with lambda search, this option will stop the search before it completes. Models built at the beginning of the lambda search have higher lambda values, consider fewer predictors, and take less time to calculate the model. Models built at the end of the lambda search have lower lambda values, incorporate more predictors, and take a longer time to calculate the model. Set the ``nlambdas`` parameter for a lambda search to specify the number of models attempted across the search. 

