setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.predict_contribs.on.cv.model <- function() {
    
    prostate <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate_gbm <- h2o.gbm(4:9, "AGE", prostate, nfolds = 2)

    contribs_main <- h2o.predict_contributions(prostate_gbm, prostate)

    # use the first CV model to calculate contributions
    cv1 <- h2o.getModel(prostate_gbm@model$cross_validation_models[[1]]$name)
    contribs_cv1 <- h2o.predict_contributions(cv1, prostate)

    expect_equal(
        colnames(contribs_main),
        colnames(contribs_cv1)
    )
}

doTest("Test predict contributions on a CV model", test.predict_contribs.on.cv.model)
