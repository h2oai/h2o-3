Permutation Variable Importance
-------------------------------

Introduction
~~~~~~~~~~~~

Permutation variable importance is obtained by measuring the distance between prediction errors before and after a feature is permuted; only one feature at a time is permuted.


Implementation
~~~~~~~~~~~~~~

The model is scored on a dataset ``D``, this yields some metric value ``orig_metric`` for metric ``M``.

Permutation variable importance of a variable ``V`` is calculated by the following process:

1) Variable ``V`` is randomly shuffled using `Fisher-Yates algorithm <https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle>`__.
2) The model is scored on the dataset ``D`` with the variable ``V`` replaced by the result from step **1.** this yields some metric value ``perm_metric`` for the same metric ``M``.
3) Permutation variable importance of the variable ``V`` is then calculated as ``abs(perm_metric - orig_metric)``.

Metric ``M`` can be set by **metric** argument. If set to ``AUTO``, ``AUC`` is used for binary classification,
``logloss`` is used for multinomial classification, and ``RMSE`` is used for regression.

Parameters
~~~~~~~~~~

- **model**: A trained model for which it will be used to score the dataset.
- **frame**: The dataset to use, both train and test frame are can be reasonable choices but the interpretation differs (see `Should I Compute Importance on Training or Test Data? <https://christophm.github.io/interpretable-ml-book/feature-importance.html#feature-importance-data>`__ from the Interpretable Machine Learning by Christoph Molnar.).
- **metric**: The metric to be used to calculate the error measure. One of ``AUTO``, ``AUC``, ``MAE``, ``MSE``, ``RMSE``, ``logloss``, ``mean_per_class_error``, ``PR_AUC``. Defaults to ``AUTO``.
- **n_samples**: The number of samples to be evaluated. Use -1 to use the whole dataset. Defaults to 10 000.
- **n_repeats**: The number of repeated evaluations. Defaults to 1.
- **features**: The features to include in the permutation importance. Use None to include all.
- **seed**: The seed for the random generator. Use -1 to pick a random seed. Defaults to -1.


Output
~~~~~~

When ``n_repeats == 1``, the result is similar to the one from ``h2o.varimp()``, i.e., it contains the following columns
"Relative Importance", "Scaled Importance", and "Percentage".

When ``n_repeats > 1``, the individual columns correspond to the permutation variable importance values from individual
runs which corresponds to the "Relative Importance" and also to the distance between the original prediction error and
prediction error using a frame with a given feature permuted.

Examples
~~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)

        # start h2o
        h2o.init()

        # load data
        prostate_train <- h2o.uploadFile("smalldata/prostate/prostate.csv")

        # train a model
        gbm <- h2o.gbm(y = "CAPSULE", training_frame = prostate_train)

        # calculate importance
        permutation_varimp <- h2o.permutation_importance(gbm, prostate_train, metric = "MSE")

        # plot permutation importance (bar plot)
        h2o.permutation_importance_plot(gbm, prostate_train)

        # plot permutation importance (box plot)
        h2o.permutation_importance_plot(gbm, prostate_train, n_repeats=15)

   .. code-tab:: python

        import h2o
        from h2o.estimators import *

        # start h2o
        h2o.init()

        # load data
        prostate_train = h2o.import_file(path="smalldata/prostate/prostate.csv")
        prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()

        # train model
        gbm = H2OGradientBoostingEstimator()
        gbm.train(y="CAPSULE", training_frame=prostate_train)

        # calculate importance
        permutation_varimp = gbm.permutation_importance(prostate_train, use_pandas=True)

        # plot permutation importance (bar plot)
        gbm.permutation_importance_plot(prostate_train)

        # plot permutation importance (box plot)
        gbm.permutation_importance_plot(prostate_train, n_repeats=15)
