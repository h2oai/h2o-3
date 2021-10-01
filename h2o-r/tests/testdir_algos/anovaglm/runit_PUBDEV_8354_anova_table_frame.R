setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# test to make sure anova table frame contains same content as model summary
test.model.anovaglm.table.frame <- function() {
    prostate <- h2o.importFile(path = locate("smalldata/prostate/prostate.csv"))
    prostate$CAPSULE <- h2o.asfactor(prostate$CAPSULE)
    # check for classification
    modelBinomial <- h2o.anovaglm(y = "CAPSULE", x = c("AGE","VOL","DCAPS"), training_frame = prostate, family="binomial")
    anovaTableFrame <- as.data.frame(h2o.result(modelBinomial))
    modelSummary <- modelBinomial@model$model_summary
    for (ind in c(1:h2o.ncol(anovaTableFrame))) {
        eleSummary <- modelSummary[,ind]
        eleTable <- as.vector(anovaTableFrame[,ind])
        if (typeof(eleSummary)=="double")
            expect_equal(eleSummary, eleTable)
        else
            expect_true(length(intersect(eleSummary, eleTable))==length(intersect(eleSummary, eleSummary)))
    }
}

doTest("ANOVA GLM to test command anovaTableFrame", test.model.anovaglm.table.frame)
