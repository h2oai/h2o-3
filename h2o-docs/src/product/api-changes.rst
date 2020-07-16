API-Related Changes
-------------------

H2O-3 does its best to keep backwards compatibility between major versions, but sometimes breaking changes are needed in order to improve code quality and to address issues. This section provides a list of current breaking changes between specific releases.

From 3.32.0.1
~~~~~~~~~~~~~

The deprecated``h2o-scala`` module has been removed.

From 3.30.0.5
~~~~~~~~~~~~~

The ``h2o-scala`` module is deprecated in version 3.30.0.5 and will be completely removed in the next major version, 3.30.1.1.


From 3.30.0.4
~~~~~~~~~~~~~

The following options are no longer supported by native `XGBoost <https://xgboost.readthedocs.io/en/latest/parameter.html>`__ and have been removed.

- ``min_sum_hessian_in_leaf``
- ``min_data_in_leaf``


From 3.28 or Below to 3.30
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Java API
''''''''

``hex.grid.HyperSpaceWalker`` and ``hex.grid.HyperspaceWalker.HyperSpaceIterator`` interfaces have been simplified.
Users implementing those interfaces directly, for example to create a custom grid search exploration algorithm, may want to look at the default implementations in **h2o-core/src/main/java/hex/grid/HyperSpaceWalker.java** if they are facing any issue when compiling against the new interfaces.


From 3.26 or Below to 3.28
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Java API
''''''''

The following classes were moved:

=================================================   =========================================
  Until 3.26                                         From 3.28
=================================================   =========================================
``ai.h2o.automl.EventLog``                          ``ai.h2o.automl.events.EventLog``
``ai.h2o.automl.EventLogEntry``                     ``ai.h2o.automl.events.EventLogEntry``
``ai.h2o.automl.Leaderboard``                       ``ai.h2o.automl.leaderboard.Leaderboard``
=================================================   =========================================


From 3.22 or Below to 3.24
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Java API
''''''''

The following classes were moved and/or renamed:

=================================================   ======================================
  Until 3.22                                          From 3.24
=================================================   ======================================
``hex.StackedEnsembleModel``                        ``hex.ensemble.StackedEnsembleModel``
``hex.StackedEnsembleModel.MetalearnerAlgorithm``   ``hex.ensemble.Metalearner.Algorithm``
``ai.h2o.automl.AutoML.algo``                       ``ai.h2o.automl.Algo``
=================================================   ======================================

Some internal methods of ``StackedEnsemble`` and ``StackedEnsembleModel`` are no longer public, but this should not impact users.
