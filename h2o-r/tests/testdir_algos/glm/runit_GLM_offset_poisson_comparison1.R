setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests offset in glm on real data ######




test <- function(h) {
    insurance_h2o <- h2o.importFile(h2oTest.locate("smalldata/glm_test/insurance.csv"))
    insurance_h2o$logHolders <- log(insurance_h2o$Holders)
    insurance_h2o$District   <- as.factor(insurance_h2o$District)
    insurance_r   <- as.data.frame(insurance_h2o)

    glm.fitWithOffsets <- glm(Claims ~ factor(District) + factor(Group) + factor(Age) + offset(log(Holders)),
                              data = insurance_r,
                              family = poisson)

    h2oglm.fitWithOffsets<-h2o.glm(y="Claims",
                                   x=c("District","Group","Age"),
                                   training_frame=insurance_h2o,
                                   family="poisson",
                                   offset_column="logHolders",
                                   lambda=0)

    h2oglm.fitWithoutOffsets<-h2o.glm(y="Claims",
                                      x=c("District","Group","Age"),
                                      training_frame=insurance_h2o,
                                      family="poisson",
                                      lambda=0)

    h2o.rd <- h2oglm.fitWithOffsets@model$training_metrics@metrics$residual_deviance
    r.rd <- glm.fitWithOffsets$deviance
    h2oTest.logInfo(paste("H2O residual deviance: ", h2o.rd, ", and R residual deviance: ", r.rd))
    expect_equal(h2o.rd, r.rd, tolerance = 1e-4)

    h2o.rd.w <- h2oglm.fitWithOffsets@model$training_metrics@metrics$residual_deviance
    h2o.rd.wo <- h2oglm.fitWithoutOffsets@model$training_metrics@metrics$residual_deviance
    h2oTest.logInfo(paste("H2O residual deviance (offsets): ", h2o.rd.w, ", and H2O residual deviance (no offsets): ", h2o.rd.wo))
    expect_less_than(h2o.rd.w, h2o.rd.wo)

    h2o.nd.w <- h2oglm.fitWithOffsets@model$training_metrics@metrics$null_deviance
    h2o.nd.wo <- h2oglm.fitWithoutOffsets@model$training_metrics@metrics$null_deviance
    h2oTest.logInfo(paste("H2O null deviance (offsets): ", h2o.nd.w, ", and H2O null deviance (no offsets): ", h2o.nd.wo))
    expect_less_than(h2o.nd.w, h2o.nd.wo)

	
}

h2oTest.doTest("GLM poisson offset comparision1: ", test)
