Permutation Variable Importance
-----------------------------------

Introduction
~~~~~~~~~~~~~~~~~

Permutation feature (variable) importance (PFI) measures the increase in the prediction error of the model after we permuted the feature’s values, which breaks the relationship between the feature and the true outcome.

Already having the model which predicted the target variable, PFI is measured by calculating the increase in the model's prediction error after permuting an feature of the dataset.

PFI evaluated how much the models prediction relies on each variable of the dataset.

One At a Time (OAT) is a screening method which each input is varied (permuted) while fixing the others.

The  method  of  Morris  allows  to  classify  the  features  in  three  groups: features having negligible effects, features having large linear effects without interactions, and features  having  large  non-linear  and/or  interaction  effects.

Implementation
~~~~~~~~~~~~~~~~~

PFI
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

OAT Morris method
~~~~~~~~~~~~~~~~~

Input: Trained model m, Dataset Z, error measure L(y,m) 

Every variable in Dataset (Frame) Z, is randomly shuffled using Fisher-Yates algorithm (see: `https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle`). On the Frame Z replacing the original variable with the permuted one. Scoring the model again, calculating the error measure based on a metric selected by the user (AUC, MSE, etc.). Then Calculating PFI score by subtracting the original error measure by the error measure calculated with the permuted variable.

Parameters
~~~~~~~~~~~~~~~~~

- **model**: The trained model used on PFI.
- **frame**: the dataset which the model was trained with (as the frame used in PFI).

Output
~~~~~~~~~~~~~~~~~

Output is a H2OFrame with the two rows and columns the number of variables; which has as Rows "Mean of the absolute value", "standard deviation".
Mean of the absolute value of the variable importance and standard deviation of the variable importance


Output is a H2OFrame with the three rows and columns the number of variables + a column named "importance" which has as Rows "Relative Importance", "Scaled Importance", "Percentage". Or if `use_pandas` parameter was set to true returns a pandas Frame.

Examples
~~~~~~~~~~~~~~~~~

A jupyter notebook with python demo (OAT not included) is available at 'https://github.com/h2oai/h2o-3/pull/4610/files#diff-b117ab9d8a9cec5269a3a700a4d9688a4276ed0071ffed2af29267064a8f6c11'

.. tabs::
	.. code-tab:: r R

        # load data
        pros.train <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))
    
        # train model
        pros.glm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.train, family = "binomial")
        
        # calculate importance
        permutation_varimp <- h2o.permutation_varimp(pros.glm, pros.train, metric = "MSE")

        # calculate OAT
        permutation_varimp,oat <- h2o.permutation_varimp(pros.glm, pros.train)
                

   .. code-tab:: python

        # load data
        prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
        prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    
        # train model
        gbm_h2o = H2OGradientBoostingEstimator(distribution="bernoulli")
        gbm_h2o.train(x=list(range(1, prostate_train.ncol)), y="CAPSULE", training_frame=prostate_train)
    
        # calculate importance
        pm_h2o_df = permutation_varimp(model, fr, use_pandas=True, metric=metric="auc)
        
        # calculate OAT 
        oat_h2o_df = h2o.permutation_varimp.oat(model, fr)
