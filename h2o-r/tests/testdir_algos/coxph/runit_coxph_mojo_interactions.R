setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.mojo_interactions_impl <- function(stratify_by = NULL) {
    data <- survival::cancer
    training_frame <- as.h2o(data)

    if (!is.null(stratify_by)) {
        for (strat_col in stratify_by) {
            training_frame[[strat_col]] <- as.factor(training_frame[[strat_col]])
        }
    }

    coxph_h2o <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time", ties = "efron", stratify_by = stratify_by,
                           training_frame = training_frame)

    predict_h2o <- h2o.predict(coxph_h2o, training_frame)
    print(predict_h2o)

    mojo_name <- h2o.download_mojo(model = coxph_h2o, path = tempdir())
    mojo_path <- file.path(tempdir(), mojo_name)

    coxph_mojo <- h2o.import_mojo(mojo_path)

    predict_mojo <- h2o.predict(coxph_mojo, training_frame)
    print(predict_mojo)

    expect_equal(as.data.frame(predict_h2o), as.data.frame(predict_mojo))
}

test.CoxPH.mojo_interactions <- function() {
    test.CoxPH.mojo_interactions_impl()
    test.CoxPH.mojo_interactions_impl(stratify_by = c("sex"))
}

doTest("CoxPH: MOJO Interaction Test", test.CoxPH.mojo_interactions)
