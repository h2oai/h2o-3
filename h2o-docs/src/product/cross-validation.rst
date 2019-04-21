Cross-Validation
================

`K-fold cross-validation <https://en.wikipedia.org/wiki/Cross-validation_(statistics)#k-fold_cross-validation>`__ is used to validate a model internally, i.e., estimate the model performance without having to sacrifice a validation split. Also, you avoid statistical issues with your validation split (it might be a “lucky” split, especially for imbalanced data). Good values for K are around 5 to 10. Comparing the K validation metrics is always a good idea, to check the stability of the estimation, before “trusting” the main model.

You have to make sure, however, that the holdout sets for each of the K models are good. For i.i.d. data, the random splitting of the data into K pieces (default behavior) or modulo-based splitting is fine. For temporal or otherwise structured data with distinct “events”, you have to make sure to split the folds based on the events. For example, if you have observations (e.g., user transactions) from K cities and you want to build models on users from only K-1 cities and validate them on the remaining city (if you want to study the generalization to new cities, for example), you will need to specify the parameter “fold\_column" to be the city column. Otherwise, you will have rows (users) from all K cities randomly blended into the K folds, and all K cross-validation models will see all K cities, making the validation less useful (or totally wrong, depending on the distribution of the data). This is known as “data leakage”: https://youtu.be/NHw\_aKO5KUM?t=889

How Cross-Validation is Calculated
----------------------------------

In general, for all algos that support the nfolds parameter, H2O’s cross-validation works as follows:

For example, for ``nfolds=5``, 6 models are built. The first 5 models (cross-validation models) are built on 80% of the training data, and a different 20% is held out for each of the 5 models. Then the main model is built on 100% of the training data. This main model is the model you get back from H2O in R, Python and Flow (though the CV models are also stored and available to access later).

This main model contains training metrics and cross-validation metrics (and optionally, validation metrics if a validation frame was provided). The main model also contains pointers to the 5 cross-validation models
for further inspection.

All 5 cross-validation models contain training metrics (from the 80% training data) and validation metrics (from their 20% holdout/validation data). To compute their individual validation metrics, each of the 5 cross-validation models had to make predictions on their 20% of of rows of the original training frame, and score against the true labels of the 20% holdout.

For the main model, this is how the cross-validation metrics are computed: The 5 holdout predictions are combined into one prediction for the full training dataset (i.e., predictions for every row of the training data, but the model making the prediction for a particular row has not seen that row during training). This “holdout prediction" is then scored against the true labels, and the overall cross-validation metrics are computed.

This approach has some implications. Scoring the holdout predictions freshly can result in different metrics than taking the average of the 5 validation metrics of the cross-validation models. For example, if the sizes of the holdout folds differ a lot (e.g., when a user-given fold\_column is used), then the average should probably be replaced with a weighted average. Also, if the cross-validation models map to slightly different probability spaces, which can happen for small DL models that converge to different local minima, then the confused rank ordering of the combined predictions would lead to a significantly different AUC than the average.

Example
~~~~~~~

To gain more insights into the variance of the holdout metrics (e.g., AUCs), you can look up the cross-validation models, and inspect their validation metrics. Here’s an example:

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()
    df <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    df[,"CAPSULE"] <- as.factor(df[,"CAPSULE"])
    model_fit <- h2o.gbm(x = 3:8, y = 2, training_frame = df, nfolds = 5, seed = 1)

    # AUC of cross-validated holdout predictions
    h2o.auc(model_fit, xval = TRUE)

   .. code-block:: python

    import h2o
    h2o.init()
    from h2o.estimators.gbm import H2OGradientBoostingEstimator

    # Import the prostate dataset
    prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

    # Set the predictor names and the response column name
    response = "CAPSULE"
    predictors = prostate.names[3:8]

    # Convert the response column to a factor
    prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()

    # Train a GBM model setting nfolds to 5
    prostate_gbm = H2OGradientBoostingEstimator(nfolds = 5, seed = 1)
    prostate_gbm.train(x=predictors, y=response, training_frame=prostate)

    # AUC of cross-validated holdout predictions
    prostate_gbm.auc(xval=True)

Using Cross-Validated Predictions
---------------------------------

With cross-validated model building, H2O builds K+1 models: K cross-validated model and 1 overarching model over all of the training data.

Each cv-model produces a prediction frame pertaining to its fold. It can be saved and probed from the various clients if `keep_cross_validation_predictions <data-science/algo-params/keep_cross_validation_predictions.html>`__ parameter is set in the model constructor.

These holdout predictions have some interesting properties. First they have names like:

::

      prediction_GBM_model_1452035702801_1_cv_1

and they contain, unsurprisingly, predictions for the data held out in the fold. They also have the same number of rows as the entire input training frame with ``0``\ s filled in for all rows that are not in the hold out.

Let's look at an example.

Here is a snippet of a three-class classification dataset (last column is the response column), with a 3-fold identification column appended to the end:

+--------------+--------------+--------------+--------------+----------+----------+
| sepal\_len   | sepal\_wid   | petal\_len   | petal\_wid   | class    | foldId   |
+==============+==============+==============+==============+==========+==========+
| 5.1          | 3.5          | 1.4          | 0.2          | setosa   | 0        |
+--------------+--------------+--------------+--------------+----------+----------+
| 4.9          | 3.0          | 1.4          | 0.2          | setosa   | 0        |
+--------------+--------------+--------------+--------------+----------+----------+
| 4.7          | 3.2          | 1.3          | 0.2          | setosa   | 2        |
+--------------+--------------+--------------+--------------+----------+----------+
| 4.6          | 3.1          | 1.5          | 0.2          | setosa   | 1        |
+--------------+--------------+--------------+--------------+----------+----------+
| 5.0          | 3.6          | 1.4          | 0.2          | setosa   | 2        |
+--------------+--------------+--------------+--------------+----------+----------+
| 5.4          | 3.9          | 1.7          | 0.4          | setosa   | 1        |
+--------------+--------------+--------------+--------------+----------+----------+
| 4.6          | 3.4          | 1.4          | 0.3          | setosa   | 1        |
+--------------+--------------+--------------+--------------+----------+----------+
| 5.0          | 3.4          | 1.5          | 0.2          | setosa   | 0        |
+--------------+--------------+--------------+--------------+----------+----------+
| 4.4          | 2.9          | 1.4          | 0.4          | setosa   | 1        |
+--------------+--------------+--------------+--------------+----------+----------+

Each cross-validated model produces a prediction frame

::

      prediction_GBM_model_1452035702801_1_cv_1
      prediction_GBM_model_1452035702801_1_cv_2
      prediction_GBM_model_1452035702801_1_cv_3

and each one has the following shape (for example the first one):

::

      prediction_GBM_model_1452035702801_1_cv_1

+--------------+----------+--------------+-------------+
| prediction   | setosa   | versicolor   | virginica   |
+==============+==========+==============+=============+
| 1            | 0.0232   | 0.7321       | 0.2447      |
+--------------+----------+--------------+-------------+
| 2            | 0.0543   | 0.2343       | 0.7114      |
+--------------+----------+--------------+-------------+
| 0            | 0        | 0            | 0           |
+--------------+----------+--------------+-------------+
| 0            | 0        | 0            | 0           |
+--------------+----------+--------------+-------------+
| 0            | 0        | 0            | 0           |
+--------------+----------+--------------+-------------+
| 0            | 0        | 0            | 0           |
+--------------+----------+--------------+-------------+
| 0            | 0        | 0            | 0           |
+--------------+----------+--------------+-------------+
| 0            | 0.8921   | 0.0321       | 0.0758      |
+--------------+----------+--------------+-------------+
| 0            | 0        | 0            | 0           |
+--------------+----------+--------------+-------------+

The training rows receive a prediction of ``0`` (more on this below) as well as ``0`` for all class probabilities. Each of these holdout predictions has the same number of rows as the input frame.

Combining Holdout Predictions
-----------------------------

The frame of cross-validated predictions is a single-column frame, where each row is the cross-validated prediction of that row.  If you want H2O to keep these cross-validated predictions, you must set `keep_cross_validation_predictions <data-science/algo-params/keep_cross_validation_predictions.html>`__ to True.  Here's an example:

.. example-code::
   .. code-block:: r

    library(h2o)
    h2o.init()

    # H2O Cross-validated K-means example
    prostate.hex <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    fit <- h2o.kmeans(training_frame = prostate.hex,
                      k = 10,
                      x = c("AGE", "RACE", "VOL", "GLEASON"),
                      nfolds = 5,  #If you want to specify folds directly, then use "fold_column" arg
                      keep_cross_validation_predictions = TRUE)

    # This is where list of cv preds are stored (one element per fold):
    fit@model[["cross_validation_predictions"]]

    # However you most likely want a single-column frame including all cv preds
    cvpreds <- h2o.getFrame(fit@model[["cross_validation_holdout_predictions_frame_id"]][["name"]])

   .. code-block:: python

    # H2O Cross-validated K-means example
    import h2o
    h2o.init()
    from h2o.estimators.kmeans import H2OKMeansEstimator

    # Import the prostate dataset
    prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

    # Set the predictor names
    predictors = prostate.names[2:9]

    # Train a GBM model setting nfolds to 5
    prostate_kmeans = H2OKMeansEstimator(k=10, keep_cross_validation_predictions=True, nfolds = 5)
    prostate_kmeans.train(x=predictors, training_frame=prostate)

    # This is where list of cv preds are stored (one element per fold):
    prostate_kmeans.cross_validation_predictions()

    # However you most likely want a single-column frame including all cv preds
    prostate_kmeans.cross_validation_holdout_predictions()


Cross-Validation Cleanup
------------------------

When building models using cross-validation, various residuals are automatically deleted from memory when the final model has been completed. This includes the cross-validation models and its metrics, the predictions, and the fold assignments.

If you want to prevent this information from being deleted for further investigation, please refer to their corresponding activation flags:

- :ref:`keep_cross_validation_fold_assignment`
- :ref:`keep_cross_validation_models`
- :ref:`keep_cross_validation_predictions`

If the model does not complete due to timeout or manual interruption, its associated CV models and residuals are also expected to be automatically removed from memory.
