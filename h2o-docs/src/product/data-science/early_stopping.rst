Early stopping
==============

All of the H2O-3 supervised learning algorithms allow for early stopping during model building and scoring. 

Early stopping in all supervised algorithms
-------------------------------------------

The following early stopping parameter is available to all supervised algorithms:

- :ref:`max_runtime_secs` (Defaults to ``0``/disabled.)

The ``max_runtime_secs`` option specifes the maximum runtime in seconds that you want to allot in order to complete the model. If this maximum runtime is exceeded before the model build is completed, then the model will fail. When performing a grid search, this option specifies the maximum runtime in seconds for the entire grid. This option can also be combined with ``max_runtime_secs`` in the model parameters. If ``max_runtime_secs`` is not set in the model parameters, then each model build is launched with a limit equal to the remainder of the grid time. On the other hand, if ``max_runtime_secs`` is set in the model parameters, then each build is launched with a limit equal to the minimum of the model time limit and the remaining time for the grid.

Early stopping in AutoML, grid search, Deep Learning, DRF, GBM, and XGBoost
---------------------------------------------------------------------------

In AutoML, grid search, Deep Learning, DRF, GBM, and XGBoost, the following additional parameters are used for early stopping:

- :ref:`stopping_rounds` - Defaults to:
	
	- AutoML: ``3``
	- Deep Learning: ``5``
	- DRF, GBM, XGBoost: ``0``/disabled

- :ref:`stopping_tolerance` - Defaults to: 
	
	- ``0.001`` 
	- In AutoML, for datasets with more than 1 million rows, this defaults to a larger valued determined by the size of the dataset and the non-NA-rate.

- :ref:`stopping_metric` - Defaults to:

	- classification models: ``"logloss"``
	- regression models: ``"deviance"``

The simplest way to turn on early stopping in these algorithms is to use a number >=1 in ``stopping_rounds``. The default values for the other two parameters will work fairly well, but a ``stopping_tolerance`` of 0 is a common alternative to the default.

Additionally, you can take :ref:`score_tree_interval` and/or :ref:`score_each_iteration` into account when using these early stopping methods. The stopping rounds applies to the number of scoring iterations H2O-3 has performed, so regular scoring iterations of a small size can help control early stopping the most (though there is a speed tradeoff to scoring more often). The default is to use H2O-3’s assessment of a reasonable ratio of training time to scoring time, which often results in inconsistent scoring gaps.

Early stopping in GLM and GAM
-----------------------------

In GLM and GAM, the following additional parameters are used for early stopping:

- :ref:`early_stopping` (Defaults to ``enabled``.)
- :ref:`max_active_predictors` (Default varies based on the ``solver``.)
- :ref:`stopping_rounds` (Defaults to ``0`` in GLM and GAM.)
- :ref:`stopping_tolerance` (Defaults to ``0.001`` in GLM and GAM.)
- :ref:`stopping_metric` (Defaults to ``"logloss"`` for classification and ``"deviance"`` for regression.)

When ``early_stopping`` is enabled, GLM and GAM will automatically stop building a model when there is no more relative improvement on the training or validation (if provided) set. This option prevents expensive model building with many predictors when no more improvements are occurring.

The ``max_active_predictors`` option limits the number of active predictors. 

.. note::
	
	The actual number of non-zero predictors in the model is going to be slightly lower. 

This is useful when obtaining a sparse solution to avoid costly computation of models with too many predictors. When using the :math:`\lambda_1` penalty with lambda search, this option will stop the search before it completes. Models built at the beginning of the lambda search have higher lambda values, consider fewer predictors, and take less time to calculate the model. Models built at the end of the lambda search have lower lambda values, incorporate more predictors, and take a longer time to calculate the model. Set the ``nlambdas`` parameter for a lambda search to specify the number of models attempted across the search. 