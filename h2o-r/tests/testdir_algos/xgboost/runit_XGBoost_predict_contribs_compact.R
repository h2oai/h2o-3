setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.predict_contribs_compact <- function() {
    prostate_frame <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate_frame$RACE <- as.factor(prostate_frame$RACE)
    prostate_frame$CAPSULE <- as.factor(prostate_frame$CAPSULE)

    x <- c("AGE", "RACE", "GLEASON", "DCAPS", "PSA", "VOL", "CAPSULE")
    y <- 'DPROS'

    xgboost_model <- h2o.xgboost(x=x, y=y, training_frame=prostate_frame)

    contribs_original <- h2o.predict_contributions(xgboost_model, prostate_frame)
    expect_equal(colnames(contribs_original), 
        c("RACE.0", "RACE.1", "RACE.2", "RACE.missing(NA)", "CAPSULE.0", "CAPSULE.1", "CAPSULE.missing(NA)", 
        "AGE", "DCAPS", "PSA", "VOL", "GLEASON", "BiasTerm")
    )

    contribs_compact <- h2o.predict_contributions(xgboost_model, prostate_frame, output_format="Compact")
    expect_equal(colnames(contribs_compact), 
        c("RACE", "CAPSULE", "AGE", "DCAPS", "PSA", "VOL", "GLEASON", "BiasTerm")
    )

    contribs_original_local <- as.data.frame(contribs_original)
    contribs_compact_local <- as.data.frame(contribs_compact)

    for (column in colnames(contribs_compact)) {
        expanded <- contribs_original_local[, grepl(column, colnames(contribs_original_local), fixed = TRUE)]
        expect_equal(rowSums(as.matrix(expanded)), contribs_compact_local[, column], tolerance = 1e-7)
    }
}

doTest("XGBoost Test: Output compact contributions", test.XGBoost.predict_contribs_compact)
