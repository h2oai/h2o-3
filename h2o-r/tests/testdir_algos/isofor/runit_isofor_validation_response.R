setwd(normalizePath(dirname(
    R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../../scripts/h2o-r-test-setup.R")



test.IsolationForest.ValidationResponseColumn <- function() {
    set.seed(1234)
    train <-
        h2o.importFile(locate("smalldata/anomaly/ecg_discord_train.csv"))
    test <-
        h2o.importFile(locate("smalldata/anomaly/ecg_discord_test.csv"))
    
    model <-
        h2o.isolationForest(training_frame = train,
                            seed = 1234,
                            ntrees = 10)
    predictions <- h2o::h2o.predict(model, test)
    threshold <-
        h2o.quantile(probs = c(0.8), x = predictions)["predictQuantiles"]
    print(threshold)
    labels_test <- predictions > threshold
    test["label"] = h2o.asfactor(labels_test["predict"])
    
    validated_model <-
        h2o.isolationForest(
            training_frame = train,
            validation_frame = test,
            seed = 1234,
            ntrees = 10,
            validation_response_column = "label"
        )
    expect_false(is.null(validated_model))
    
    tryCatch({
        validated_model <-
            h2o.isolationForest(
                training_frame = train,
                validation_frame = test,
                seed = 1234,
                ntrees = 10,
                validation_response_column = "Non-existent"
            )
        stop("Expected to fail with non-existent label column.")
    },
    error = function(err) {
        expect_true(
            grepl(x = conditionMessage(err), pattern = "Validation frame is missing response column `Non-existent`.")
        )
    })
}

doTest(
    "IsolationForest: Test validation label",
    test.IsolationForest.ValidationResponseColumn
)
