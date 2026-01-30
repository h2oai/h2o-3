setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.mojo_interactions_impl <- function(stratify_by = NULL) {
    training_frame <- h2o.importFile(locate("smalldata/coxph_test/heart_random_num_enum_cols.csv"))
    training_frame['surgery'] <- as.factor(training_frame['surgery'])
    training_frame['transplant'] <- as.factor(training_frame['transplant'])
    coxph_h2o <- h2o.coxph(x = c("age", "surgery", "transplant", "C1", "C2", "C3", "C4"), event_column = "event", 
                           stop_column = "stop", start_column = "start", 
                           interaction_pairs = list(c("C1", "C3"), c("C1", "C2"), c("C3", "C4"), c("C4", "C2"),  c("C1", "age"), c("surgery", "C3")),
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

test.CoxPH.mojo_all_interactions <- function() {
    test.CoxPH.mojo_interactions_impl()
    test.CoxPH.mojo_interactions_impl(stratify_by = c("sex"))
}

doTest("CoxPH: MOJO Interaction Test", test.CoxPH.mojo_all_interactions)
