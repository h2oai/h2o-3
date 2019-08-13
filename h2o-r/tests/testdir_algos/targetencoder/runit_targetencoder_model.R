setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.targetencoder <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    encoded_columns <- c("Origin")
    target_encoder <- h2o.targetencoder(training_frame = data, encoded_columns= encoded_columns, target_column = "IsDepDelayed")
    encoded_data <- h2o.predict(target_encoder, data) # For now, there is only the predict method
    expect_false(is.null(encoded_data))
    expect_equal(h2o.ncol(data) + length(encoded_columns), h2o.ncol(encoded_data))
    
}

doTest("Target Encoder Model test", test.model.targetencoder )
