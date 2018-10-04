Cross Validation
^^^^^^^^^^^^^^^^

- **Which parameters are used for or with cross validation?**

 	- ``nfolds``
 	- ``keep_cross_validation_models``
 	- ``keep_cross_validation_predictions``
 	- ``keep_cross_validation_fold_assignment``
 	- ``fold_assignment``
 	- ``fold_column``


- **If a user activates cross validation in one of the algorithms (** ``h2o.randomForest()`` **,** ``h2o.gbm()`` **, etc), will H2O output estimates of model performance on only the holdout sets.**

	No, H2O will build nfolds+1 models in total, the 'main' model on 100% of training data and nfolds 'cross-validation’ models that use disjoint holdout 'validation' sets (obtained from the training data) to estimate the generalization of the main model. The main model contains a cross-validation metrics object that is computed from the combined holdout predictions (obtain by setting xval to true in h2o.performance), as well as a table containing the statistics of various metrics across all nfolds cross-validation models (e.g., the mean and stddev of the logloss, rmse, etc.). You can also get the performance of the main model on the ``training_frame`` dataset if you specify ``train = TRUE`` (R) or ``train = True`` (python) when you ask for a model performance metric. If you provide a ``validation_frame`` during cross-validation, then you can get the performance of the main model on that by specifying ``valid = TRUE`` (R) or ``valid = True`` (python) when you ask for a model performance metric.

- **Can H2O automatically feed back the implications of the cross-validation results to improve the algorithm during training, as well as tune some of the model's hyperparamters?**

	Yes, H2O can use cross-validation for parameter tuning if early stopping is enabled (stopping_rounds>0). In that case, cross-validation is used to automatically tune the optimal number of epochs for Deep Learning or the number of trees for DRF/GBM. The main model will use the mean number of epochs across all cross-validation models.

- **If a** ``validation_frame`` **isn't specified, does supplying the** ``nfolds`` **parameter activate cross-validation scoring on the** ``training_frame`` **dataset's holdouts?**

	True (if ``nfolds > 1`` )

- **Does the model only train on the training data?**

	The model only ever trains on training data, but can use validation data (if provided) to tune parameters related to early stopping (epochs, number of trees). If no validation data is provided, we will tune based off training data.

- **Does supplying the** ``validation_frame`` **parameter activate scoring on the** ``validation_frame`` **dataset instead of the** ``training_frame`` **dataset?**

	No, the models always score on the training frame (unless explicitly turned off - only available in Deep Learning), but if a validation frame is provided, then the model will score on that as well (and can use it for parameter tuning such as early stopping). It’s always a good idea to provide a validation set. If you don’t want to 'sacrifice' data, use cross-validation instead. Then, you can still provide a validation frame, but you don’t have to (and it isn’t used for parameter tuning either, just for metrics reporting).

- **If the** ``nfolds`` **parameter is not specified, while** ``validation_frame`` **and** ``training_frame`` **are , then would cross validation be activate, and some default value for** ``nfolds`` **parameter will be applied?**

	No, when a training frame and validation frame are supplied without the ``nfolds`` parameter, then training is done on the ``training_frame`` and validation is done on the ``validation_frame`` (CV will only ever activate unless ``nfolds > 1`` )

- **Is early stopping (** ``stopping_rounds > 0`` **) based on the** ``validation_frame`` **dataset, if provided, and otherwise based on** ``the training_frame`` **dataset .**

	Yes.