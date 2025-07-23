setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


glmMultinomialControlVals <- function() {
    cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
    cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
    res <- factor(c(1,2,0,0,2,1,0,1,0,1,2,1,1,2,1,0,2,0,2,0,2,0,1,2,1,1))
    data <- data.frame(cat1, cat2, res)
    
    library(glmnet)

    X<-model.matrix(~.,data[c("cat1", "cat2")])[,-1]
    X
    fit <- glmnet(X, data$res, family = "multinomial", type.multinomial = "grouped", lambda = 0, alpha=0.5)
    coef_glmnet <- coef(fit, s=0)

    preds_glmnet <- predict(fit, X)

    pred_glmnet_classes <- max.col(matrix(preds_glmnet, ncol=3))

    confusion.glmnet(fit, X, data$res)

    h2o_data <- as.h2o(data)

    h2o_fit <- h2o.glm(c("cat1", "cat2"), "res", training_frame = h2o_data, family="multinomial",
                       standardize = T, solver="IRLSM", lambda=0, objective_epsilon = 1e-6, beta_epsilon = 1e-5, lambda_search = F)
    coef_h2o <- h2o.coef(h2o_fit)
    preds_h2o <- h2o.predict(h2o_fit, h2o_data)

    coefdf_h2o <- as.data.frame(coef_h2o)
    rownames(coefdf_h2o) <- coefdf_h2o[, 1]
    coefdf_h2o <- coefdf_h2o[, -1]
    coefdf_h2o[1,] <- coefdf_h2o[1,]-mean(unlist(coefdf_h2o[1,]))
    coefdf_h2o[2,] <- coefdf_h2o[2,]-mean(unlist(coefdf_h2o[2,]))
    coefdf_h2o[3,] <- coefdf_h2o[3,]-mean(unlist(coefdf_h2o[3,]))
    
    print(preds_glmnet)
    print(pred_glmnet_classes)
    print(preds_h2o)
    print(coef_glmnet)
    print(coefdf_h2o)
}

doTest("GLM: Multinomial with control vals", glmMultinomialControlVals)
