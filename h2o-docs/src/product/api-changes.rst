API-related changes
===================

H2O-3 does its best to keep backwards compatibility between major versions, but sometimes breaking changes are needed in order to improve code quality and to address issues. This section provides a list of current breaking changes between specific releases.

From 3.32.0.1
-------------

Modules
~~~~~~~

The deprecated ``h2o-scala`` module has been removed.

Target Encoding
~~~~~~~~~~~~~~~

The Target Encoder API has been clarified and its consistency across clients has been improved. The following parameters are now deprecated in all clients and officially replaced by their new alternative:

- ``k`` :math:`\to` ``inflection_point``
- ``f`` :math:`\to` ``smoothing``
- ``noise_level`` :math:`\to` ``noise``
- ``use_blending`` (R only) :math:`\to` ``blending``

Legacy client code using the deprecated parameters should expect a deprecation warning when using them. You are strongly encouraged to update your code to use the new naming.

``transform`` parameter updates
'''''''''''''''''''''''''''''''

In an objective of performance optimization on the backend, and of simplification of the API, the ``transform`` method used to apply target encoding was modified as follows:

- The R ``h2o.transform`` function (accepting a target encoder model as the first argument) and the Python ``H2OTargetEncoderEstimator.transform`` methods are now fully compatible: they accept the same parameters and work consistently.
- The parameters ``data_leakage_handling``, ``seed`` are now ignored on those methods: by default, ``transform`` will use the corresponding values defined when building the TargetEncoder model.
- The other regularization parameters on these ``transform`` methods (e.g. ``noise``, ``blending``, ``inflection_point``, ``smoothing``), always default to the value defined on the TargetEncoder model.
- A new ``as_training`` parameter has been introduced to simplify and enforce a correct usage of target encoding:

  - When transforming a training dataset, you should use (R) ``h2o.transform(te_model, train_dataset, as_training=TRUE)`` or (Python) ``te_model.transform(train_dataset, as_training=True)``.
  - When transforming any other dataset (validation, test, ...), you can just use (R) ``h2o.transform(te_model, train_dataset)`` or (Python) ``te_model.transform(train_dataset)``.
  - Legacy code using for example ``h2o.transform(te_model, train_dataset, data_leakage_handling="KFold")`` will now be translated internally to ``h2o.transform(te_model, train_dataset, as_training=TRUE)``.


Finally the following APIs (deprecated since 3.28) have been fully removed:

- Python: ``h2o.targetencoder`` module.
- R: ``h2o.target_encode_fit`` and ``h2o.target_encode_transform`` functions.

Parameters
~~~~~~~~~~

The ``max_hit_ratio_k`` parameter has been removed.

From 3.30.1.2
-------------

The ``max_hit_ratio_k`` parameter is deprecated in version 3.30.1.2 and will be completely removed in the next major version, 3.32.0.1.

From 3.30.1.1
-------------

The deprecated ``h2o-scala`` module has been removed.


From 3.30.0.5
-------------

The ``h2o-scala`` module is deprecated in version 3.30.0.5 and will be completely removed in the next major version, 3.30.1.1.


From 3.30.0.4
-------------

The following options are no longer supported by native `XGBoost <https://xgboost.readthedocs.io/en/latest/parameter.html>`__ and have been removed.

- ``min_sum_hessian_in_leaf``
- ``min_data_in_leaf``


From 3.28 or below to 3.30
--------------------------

Java API
~~~~~~~~

The ``hex.grid.HyperSpaceWalker`` and ``hex.grid.HyperspaceWalker.HyperSpaceIterator`` interfaces have been simplified. Users implementing those interfaces directly, for example to create a custom grid search exploration algorithm, may want to look at the default implementations in ``h2o-core/src/main/java/hex/grid/HyperSpaceWalker.java`` if they are facing any issue when compiling against the new interfaces.


From 3.26 or below to 3.28
--------------------------

Java API
~~~~~~~~

The following classes were moved:

=================================================   =========================================
  Until 3.26                                         From 3.28
=================================================   =========================================
``ai.h2o.automl.EventLog``                          ``ai.h2o.automl.events.EventLog``
``ai.h2o.automl.EventLogEntry``                     ``ai.h2o.automl.events.EventLogEntry``
``ai.h2o.automl.Leaderboard``                       ``ai.h2o.automl.leaderboard.Leaderboard``
=================================================   =========================================


From 3.22 or below to 3.24
--------------------------

Java API
~~~~~~~~

The following classes were moved and/or renamed:

=================================================   ======================================
  Until 3.22                                          From 3.24
=================================================   ======================================
``hex.StackedEnsembleModel``                        ``hex.ensemble.StackedEnsembleModel``
``hex.StackedEnsembleModel.MetalearnerAlgorithm``   ``hex.ensemble.Metalearner.Algorithm``
``ai.h2o.automl.AutoML.algo``                       ``ai.h2o.automl.Algo``
=================================================   ======================================

Some internal methods of ``StackedEnsemble`` and ``StackedEnsembleModel`` are no longer public, but this should not impact you.
