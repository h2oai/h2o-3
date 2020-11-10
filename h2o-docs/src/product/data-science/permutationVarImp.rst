Permutation Variable Importance
-----------------------------------

Introduction
~~~~~~~~~~~~~~~~~

Permutation feature importance (PFI) measures the increase in the prediction error of the model after we permuted the feature’s values, which breaks the relationship between the feature and the true outcome.

Already having the model which predicted the target variable, PFI is measured by calculating the increase in the model's prediction error after permuting an feature of the dataset.

PFI evaluated how much the models prediction relies on each variable of the dataset.


Implementation
~~~~~~~~~~~~~~~~~

Input: Trained model m, Dataset Z, error measure L(y,m) 

Every variable in Dataset (Frame) Z, is randomly shuffled using Fisher-Yates algorithm (see: `https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle`). On the Frame Z replacing the original variable with the permuted one. Scoring the model again, calculating the error measure based on a metric selected by the user (AUC, MSE, etc.). Then Calculating PFI score by subtracting the original error measure by the error measure calcualted with the permuted variable.

Parameters
~~~~~~~~~~~~~~~~~

- **model**: A trained model for which it will be used to score the dataset.
- **frame**: the dataset which the model was trained with.
- **use_pandas**: If true returns a pandas frame instead of H2OFrame
- **metric**: the metric to be used to calculate the error measure.


Output
~~~~~~~~~~~~~~~~~

Output is a H2OFrame with the three rows and colums the number of variables + a column named "importance" which has as Rows "Relative Importance", "Scaled Importance", "Percentage". Or if `use_pandas` parameter was set to true returns a pandas Frame.

Examples
~~~~~~~~~~~~~~~~~

A jupyter notebook with python demo is available at 'https://github.com/h2oai/h2o-3/pull/4610/files#diff-b117ab9d8a9cec5269a3a700a4d9688a4276ed0071ffed2af29267064a8f6c11'

.. tabs::
   .. code-tab:: python

	# load data
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()

	# train model
    gbm_h2o = H2OGradientBoostingEstimator(distribution="bernoulli")
    gbm_h2o.train(x=list(range(1, prostate_train.ncol)), y="CAPSULE", training_frame=prostate_train)

	#get PFI H2OFrame
	pm_h2o_df = permutation_varimp(model, fr, use_pandas=True, metric=metric="auc)
	
	..code-tab:: r R

	library(h2o)
	h2o.init()

	# load data
	prosPath <- h2o:::.h2o.locate("smalldata/logreg/prostate.csv")
	prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")

	# train model
	prostate.gbm <- h2o.gbm(x = setdiff(colnames(prostate.hex), "CAPSULE"), y = "CAPSULE", training_frame = prostate.hex, ntrees = 10, max_depth = 5, learn_rate = 0.1, distribution = "bernoulli")

	# get pvi
	permutation_varimp <- h2o.permutation_varimp(iris.gbm, prostate.hex)



