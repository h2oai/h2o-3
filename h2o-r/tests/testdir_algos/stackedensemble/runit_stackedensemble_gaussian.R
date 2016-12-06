setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.gaussian.test <- function() {
    australia.hex <- h2o.uploadFile(locate("smalldata/extdata/australia.csv"), destination_frame="australia.hex")
    print(summary(australia.hex))
    myX <- c("premax", "salmax", "minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs")

    my_gbm <- h2o.gbm(y = "runoffnew", x = myX, training_frame = australia.hex, ntrees = 10, max_depth = 3,
                      min_rows = 2, learn_rate = 0.2, nfolds = 5, fold_assignment='Modulo',
                      keep_cross_validation_predictions = TRUE, distribution= "gaussian")
    print("GBM performance: ")
    h2o.performance(my_gbm)

    my_glm <- h2o.glm(y = "runoffnew", x = myX, training_frame = australia.hex, family = "gaussian", nfolds = 5,
                      fold_assignment='Modulo', keep_cross_validation_predictions = TRUE)
    print("GLM performance: ")
    h2o.performance(my_glm)

    stacker <- h2o.stackedEnsemble(x = myX, y = "runoffnew", training_frame = australia.hex,
                                   model_id = "my_ensemble", selection_strategy = "choose_all",
                                   base_models = list(my_gbm@model_id, my_glm@model_id))

    predictions = h2o.predict(stacker, australia.hex) # training data
    print("preditions for ensemble are in: ")
    print(h2o.getId(predictions))
}

doTest("Stacked Ensemble Test", stackedensemble.gaussian.test)
