setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test_varimp_monotone_gbm <- function(){
    loans <- h2o.importFile(locate("bigdata/laptop/lending-club/loan.csv"))
    loans$bad_loan <- as.factor(loans$bad_loan)

    gbm_regular <- h2o.gbm(y = "bad_loan", training_frame = loans, nfold = 5, seed = 1234)
    auc_regular <- h2o.auc(gbm_regular, xval = T)
    varimp_regular <- h2o.varimp(gbm_regular)[,c("variable", "percentage")]

    gbm_constrained <- h2o.gbm(y = "bad_loan", training_frame = loans, nfold = 5, seed = 1234,
    monotone_constraints = list(int_rate = 1))
    auc_constrained <- h2o.auc(gbm_constrained, xval = T)
    varimp_constrained <- h2o.varimp(gbm_constrained)[,c("variable", "percentage")]

    varimps <- merge(varimp_regular, varimp_constrained, by="variable")
    print(varimps)
    expect_equal(varimps$percentage.x, varimps$percentage.y, tolerance = 0.01, scale = 1)
    
    expect_equal(auc_regular, auc_constrained, tolerance = 0.01, scale = 1)
}

doTest("GBM Test: Check that adding a monotone constraint to an almost-monotonic variable doesn't affect model performance and variable importace", test_varimp_monotone_gbm)
