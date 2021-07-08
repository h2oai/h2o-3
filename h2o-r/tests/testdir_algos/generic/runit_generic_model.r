setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.generic.predict <- function() {
    # Train a model
    airlines <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    airlines_test <- h2o.importFile(path = locate('smalldata/testng/airlines_test.csv'))
    cols <- c("Origin", "Distance")
    model <- h2o.gbm(x=cols, y = "IsDepDelayed", ntrees = 10,
                     training_frame = airlines)
    predictions <- as.data.frame(h2o.predict(model, airlines_test))
    contributions <- as.data.frame(h2o.predict_contributions(model, airlines_test))

    # Download MOJO representation
    mojo_name <- h2o.download_mojo(model = model, path = sandbox())
    mojo_path <- file.path(sandbox(), mojo_name)
    
    # Import MOJO
    mojo_model <- h2o.import_mojo(mojo_path)

    # Test scoring is available on the model
    mojo_predictions  <- as.data.frame(h2o.predict(mojo_model, airlines_test))
    expect_equal(mojo_predictions, predictions)

    # Test predict contributions is available on the model
    mojo_contributions  <- as.data.frame(h2o.predict_contributions(mojo_model, airlines_test))
    expect_equal(mojo_contributions, contributions)    
}

doTest("Generic model from GBM MOJO", test.model.generic.predict)
