setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests offsets in glm ######
# Call:  glm(formula = CAPSULE ~ . - ID, family = binomial, data = D)
#
# Coefficients:
# (Intercept)          AGE         RACE        DPROS        DCAPS          PSA          VOL      GLEASON
#   -7.27968     -0.01213     -0.62424      0.55661      0.48375      0.02739     -0.01124      0.97632
#
# Degrees of Freedom: 379 Total (i.e. Null);  372 Residual
# Null Deviance:	    512.3
# Residual Deviance: 378.6 	AIC: 394.6
test <- function(h) {
    prostate_h2o <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
    x=c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
    model <- h2o.glm(x = x, y="CAPSULE", training_frame=prostate_h2o,family="binomial", alpha=.99,lambda=.2)
    new_beta <- c(-7.27968, -0.01213, -0.62424, 0.55661, 0.48375, 0.02739, -0.01124, 0.97632)
    names(new_beta) <- c("Intercept","AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
    new_glm <- h2o.makeGLMModel(model, new_beta)
    # compare actual predictions
    preds_h2o = as.data.frame(h2o.predict(object = new_glm,newdata=prostate_h2o))[,3]
    prostate_r <- as.data.frame(prostate_h2o)
    preds_r <-  1.0 / (exp(-(as.matrix(cbind(1,prostate_r[,x])) %*% new_beta)) + 1)
    checkTrue(sum(abs(preds_r - preds_h2o)) < 1e-13)
    perfs = h2o.performance(model = new_glm,data = prostate_h2o)
    checkTrue(round(10*perfs@metrics$residual_deviance) == 3786, "residual deviance mismatch")
    h2o.removeAll()
}
h2oTest.doTest("GLM makeGLMModel", test)
