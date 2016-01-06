#Understanding Cross-Validated Predictions

With cross-validated model building, H2O builds N+1 models: N cross-validated model and 1 overarching model over all of the training data.

Each cv-model produces a prediction frame pertaining to its fold. It can be saved and probed from the various clients if `keep_cross_validation_predictions` parameter is set in the model constructor.

These holdout predictions have some interesting properties. First they have names like:

```
  prediction_GBM_model_1452035702801_1_cv_1
```
and they contain, unsurprisingly, predictions for the data held out in the fold. They also have the same number of rows as the entire input training frame with `0`s filled in for all rows that are not in the hold out. 

Let's look at an example. 

Here is a snippet of a three-class classification dataset (last column is the response column), with a 3-fold identification column appended to the end:


| sepal_len | sepal_wid | petal_len | petal_wid | class   | foldId |
|-----------|-----------|-----------|-----------|---------|--------|
| 5.1       | 3.5       | 1.4       | 0.2       | setosa  | 0      |
| 4.9       | 3.0       | 1.4       | 0.2       | setosa  | 0      |
| 4.7       | 3.2       | 1.3       | 0.2       | setosa  | 2      |
| 4.6       | 3.1       | 1.5       | 0.2       | setosa  | 1      |
| 5.0       | 3.6       | 1.4       | 0.2       | setosa  | 2      |
| 5.4       | 3.9       | 1.7       | 0.4       | setosa  | 1      |
| 4.6       | 3.4       | 1.4       | 0.3       | setosa  | 1      |
| 5.0       | 3.4       | 1.5       | 0.2       | setosa  | 0      |
| 4.4       | 2.9       | 1.4       | 0.4       | setosa  | 1      |


Each cross-validated model produces a prediction frame

```
  prediction_GBM_model_1452035702801_1_cv_1
  prediction_GBM_model_1452035702801_1_cv_2 
  prediction_GBM_model_1452035702801_1_cv_3
```

and each one has the following shape (for example the first one):

```
  prediction_GBM_model_1452035702801_1_cv_1
``` 

| prediction | setosa | versicolor | virginica |
|------------|--------|------------|-----------|
| 1          | 0.0232 | 0.7321     | 0.2447    |
| 2          | 0.0543 | 0.2343     | 0.7114    |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0      | 0          | 0         |
| 0          | 0.8921 | 0.0321     | 0.0758    |
| 0          | 0      | 0          | 0         |

The training rows receive a prediction of `0` (more on this below) as well as `0` for all class probabilities. Each of these holdout predictions has the same number of rows as the input frame.

##Combining holdout predictions

The frame of cross-validated predictions is simply the superposition of the individual predictions. [Here's an example from R](https://0xdata.atlassian.net/browse/PUBDEV-2236):

``` 
library(h2o)
h2o.init()

# H2O Cross-validated K-means example 
prosPath <- system.file("extdata", "prostate.csv", package="h2o")
prostate.hex <- h2o.uploadFile(path = prosPath)
fit <- h2o.kmeans(training_frame = prostate.hex, 
                  k = 10, 
                  x = c("AGE", "RACE", "VOL", "GLEASON"), 
                  nfolds = 5,  #If you want to specify folds directly, then use "fold_column" arg
                  keep_cross_validation_predictions = TRUE)

# This is where cv preds are stored:
fit@model$cross_validation_predictions$name


# Compress the CV preds into a single H2O Frame:
# Each fold's preds are stored in a N x 1 col, where the row values for non-active folds are set to zero
# So we will compress this into a single 1-col H2O Frame (easier to digest)

nfolds <- fit@parameters$nfolds
predlist <- sapply(1:nfolds, function(v) h2o.getFrame(fit@model$cross_validation_predictions[[v]]$name)$predict, simplify = FALSE)
cvpred_sparse <- h2o.cbind(predlist)  # N x V Hdf with rows that are all zeros, except corresponding to the v^th fold if that rows is associated with v
pred <- apply(cvpred_sparse, 1, sum)  # These are the cross-validated predicted cluster IDs for each of the 1:N observations
```

This can be extended to other family types as well (multinomial, binomial, regression):

```
# helper function
.compress_to_cvpreds <- function(h2omodel, family) {
  # return the frame_id of the resulting 1-col Hdf of cvpreds for learner l
  V <- h2omodel@allparameters$nfolds
  if (family %in% c("bernoulli", "binomial")) {
    predlist <- sapply(1:V, function(v) h2o.getFrame(h2omodel@model$cross_validation_predictions[[v]]$name)[,3], simplify = FALSE)
  } else {
    predlist <- sapply(1:V, function(v) h2o.getFrame(h2omodel@model$cross_validation_predictions[[v]]$name)$predict, simplify = FALSE)
  }
  cvpred_sparse <- h2o.cbind(predlist)  # N x V Hdf with rows that are all zeros, except corresponding to the v^th fold if that rows is associated with v
  cvpred_col <- apply(cvpred_sparse, 1, sum)
  return(cvpred_col)
}


# Extract cross-validated predicted values (in order of original rows)
h2o.cvpreds <- function(object) {

  # Need to extract family from model object
  if (class(object) == "H2OBinomialModel") family <- "binomial"
  if (class(object) == "H2OMulticlassModel") family <- "multinomial"
  if (class(object) == "H2ORegressionModel") family <- "gaussian"
    
  cvpreds <- .compress_to_cvpreds(h2omodel = object, family = family)
  return(cvpreds)
}
```