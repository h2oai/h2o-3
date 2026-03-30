setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.IsolationForest.single_value_enum <- function() {
    # GH-16460: H2O fails to predict on dataset with ENUM column
    # containing only one unique value (plus NAs).

    data <- data.frame(
        num1 = 1:20,
        cat_single = c(
            "alpha", "alpha", "alpha", NA, "alpha", "alpha",
            NA, "alpha", "alpha", "alpha",
            NA, NA, "alpha", "alpha", NA, "alpha",
            "alpha", NA, "alpha", "alpha"
        ),
        num2 = c(0.1, 0.5, 0.3, 0.7, 0.2, 0.9, 0.4, 0.6, 0.8, 0.1,
                 0.3, 0.5, 0.7, 0.2, 0.4, 0.6, 0.8, 0.9, 0.1, 0.5),
        stringsAsFactors = TRUE
    )

    train.hex <- as.h2o(data)
    expect_equal(h2o.nlevels(train.hex$cat_single), 1)

    isofor.model <- h2o.isolationForest(
        training_frame = train.hex,
        ntrees = 10,
        seed = 42
    )

    # This predict call fails with the bug (GH-16460)
    preds <- h2o.predict(isofor.model, train.hex)
    expect_equal(nrow(preds), nrow(train.hex))
    Log.info("PASS: Prediction with single-value enum column succeeded")
}

doTest("IsolationForest: Single Value Enum Prediction (GH-16460)", test.IsolationForest.single_value_enum)
