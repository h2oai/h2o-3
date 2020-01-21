API-Related Changes
-------------------

H2O-3 does its best to keep backwards compatibility between major versions, but sometimes breaking changes are needed in order to improve code quality and to address issues. This section provides a list of current breaking changes between specific releases.

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

