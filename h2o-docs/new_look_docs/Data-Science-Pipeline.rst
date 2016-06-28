Data Science Pipeline
=========================

.. todo:: add a section that provides definitions and explanations of each model performance metric (currently missing from docs)
.. todo:: add cross-validation examples for Python and spark
.. todo:: add all examples for Sparkling-Water

Importing & Uploading Data
--------------------------

H2O can load data directly from:

* disk
* network file systems (NFS, S3)
* distributed file systems (HDFS)
* HTTP addresses

.. example-code::
	.. code-block:: R
	
		#Import the iris data file from the H2O package:
		irisPath = system.file("extdata", "iris.csv", package="h20")
		iris.hex = h2o.importFile(path=irisPath, destination_frame="iris.hex")
		
		#Upload data from the same machine running H2O:
		irisPath = system.file("extdata", "iris.csv", package="h2o")
		iris.hex = h2o.uploadFile(path=irisPath, destination_frame="iris.hex")
	
	.. code-block:: Python
	
		#Import data from the machine running Python to the machine running H2O: 
		df = h2o.import_file("/pathToFile/fileName")
	
		#Upload data from the same machine running H2O:
		df = h2o.upload_file("/pathToFile/fileName")
	

Working with H2OFrames
----------------------


Cross-Validation
----------------
N-fold cross-validation is used to validate a model internally, i.e., estimate the model performance without having to sacrifice a validation split. Also, you avoid statistical issues with your validation split (it might be a “lucky” split, especially for imbalanced data). Good values for N are around 5 to 10. Comparing the N validation metrics is always a good idea, to check the stability of the estimation, before “trusting” the main model.

You have to make sure, however, that the holdout sets for each of the N models are good. For i.i.d. data, the random splitting of the data into N pieces (default behavior) or modulo-based splitting is fine. For temporal or otherwise structured data with distinct “events”, you have to make sure to split the folds based on the events. For example, if you have observations (e.g., user transactions) from N cities and you want to build models on users from only N-1 cities and validate them on the remaining city (if you want to study the generalization to new cities, for example), you will need to specify the parameter “fold_column" to be the city column. Otherwise, you will have rows (users) from all N cities randomly blended into the N folds, and all N cv models will see all N cities, making the validation less useful (or totally wrong, depending on the distribution of the data).  This is known as “data leakage”: https://youtu.be/NHw_aKO5KUM?t=889.

How Cross-Validation is Calculated
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In general, for all algos that support the nfolds parameter, H2O’s cross-validation works as follows:

For example, for `nfolds=5`, 6 models are built. The first 5 models (cross-validation models) are built on 80% of the training data, and a different 20% is held out for each of the 5 models. Then the main model is built on 100% of the training data. This main model is the model you get back from H2O in R, Python and Flow.

This main model contains training metrics and cross-validation metrics (and optionally, validation metrics if a validation frame was provided). The main model also contains pointers to the 5 cross-validation models for further inspection.

All 5 cross-validation models contain training metrics (from the 80% training data) and validation metrics (from their 20% holdout/validation data). To compute their individual validation metrics, each of the 5 cross-validation models had to make predictions on their 20% of of rows of the original training frame, and score against the true labels of the 20% holdout.

For the main model, this is how the cross-validation metrics are computed: The 5 holdout predictions are combined into one prediction for the full training dataset (i.e., predictions for every row of the training data, but the model making the prediction for a particular row has not seen that row during training). This “holdout prediction” is then scored against the true labels, and the overall cross-validation metrics are computed.

This approach has some implications. Scoring the holdout predictions freshly can result in different metrics than taking the average of the 5 validation metrics of the cross-validation models. For example, if the sizes of the holdout folds differ a lot (e.g., when a user-given fold_column is used), then the average should probably be replaced with a weighted average. Also, if the cross-validation models map to slightly different probability spaces, which can happen for small DL models that converge to different local minima, then the confused rank ordering of the combined predictions would lead to a significantly different AUC than the average.

Train, Validation, and Testing
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To gain more insights into the variance of the holdout metrics (e.g., AUCs), you can look up the cross-validation models, and inspect their validation metrics.

.. example-code::
	.. code-block:: R
	
		library(h2o)
		h2o.init()
		df <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
		df$CAPSULE <- as.factor(df$CAPSULE)
		model_fit <- h2o.gbm(3:8,2,df,nfolds=5,seed=1234)

		# Default: AUC of holdout predictions
		h2o.auc(model_fit,xval=TRUE)

		# Optional: Average the holdout AUCs
		cvAUCs <- sapply(sapply(model_fit@model$cross_validation_models, `[[`, "name"), function(x) { h2o.auc(h2o.getModel(x), valid=TRUE) })
		print(cvAUCs)
		mean(cvAUCs)

	.. code-block:: Python

		#Need to add
		

Which Algorithm Solves Your Problem?
------------------------------------

Understanding Model Performace
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
For a single model



Grid Search
~~~~~~~~~~~~~~~~~~~~~~
Generate multiple models

.. todo:: move section'Grid Search (Hyperparameter Search) API' into here


Save Your Model - POJOS
-----------------------

How to download a POJO

How to create your Java file

How to use your POJO with real-time predictions
