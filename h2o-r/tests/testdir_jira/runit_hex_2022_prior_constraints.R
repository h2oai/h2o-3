###############################################################
####### Test for Beta Contraints with Priors for GLM  #########
###############################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.Priors.BetaConstraints <- function(conn) {

  Log.info("Import modelStack data into H2O...")
  pathToFile <- "/mnt/0xcustomer-datasets/c27/data.csv"
  pathToConstraints <- "/mnt/0xcustomer-datasets/c27/constraints_indices.csv"
  if (!file.exists(pathToFile) || !file.exists(pathToConstraints)) {
    testEnd()
  } else {
    modelStack <- h2o.importFile(conn, pathToFile)
    betaConstraints.hex <- h2o.importFile(conn, pathToConstraints)
    beta_nointercept.hex <- betaConstraints.hex[1:(nrow(betaConstraints.hex)-1),]

    ## Set Parameters (default standardization = T)
    betaConstraints <- as.data.frame(betaConstraints.hex)
    indVars <- as.character(betaConstraints$names[1:(nrow(betaConstraints)-1)])
    depVars <- "C3"
    lambda <- 0
    alpha <- 0
    family_type = "binomial"

    ## Take subset of data
    Log.info("Subset dataset to only predictor and response variables...")
    data.hex <- modelStack[,c(indVars, depVars)]
    summary(data.hex)

    ## Test/Train Split
    Log.info("Split into test/train frame...")
    data.split <- h2o.splitFrame(data = data.hex, ratios = 0.9)
    data.train <- data.split[[1]]
    data.test <- data.split[[2]]

    ## Run full H2O GLM
    Log.info("Run a logistic regression with no regularization and alpha = 0 and beta constraints without priors. ")
    data.train$C3 <- as.factor(data.train$C3)
    data.test$C3 <- as.factor(data.test$C3)
    glm.h2o <- h2o.glm(x=indVars, y=depVars, training_frame=data.train, lambda=lambda, alpha=alpha, family=family_type,
                       beta_constraint=beta_nointercept.hex)
    pred <- predict(glm.h2o, data.test)
    perf <- h2o.performance(glm.h2o, data.test)

    ## Run full glmnet
    Log.info("Run a logistic regression with alpha = 0 and beta constraints ")
    train.df <- as.data.frame(data.train)
    test.df <- as.data.frame(data.test)
    xMatrix <- as.matrix(train.df)

    glm.r <- glmnet(x = xMatrix, alpha = alpha, standardize = T, y = train.df[,depVars], family = family_type,
                    lower.limits = -100000, upper.limits = 100000, intercept=FALSE)

    xTestMatrix <- as.matrix(test.df)
    pred_test.r <- predict(glm.r, newx = xTestMatrix, type = "response")
    pred_train.r <- predict(glm.r, newx = xMatrix, type = "response")

    ### Grab ROC and AUC
    library(AUC)

    h2o_pred <- as.data.frame(pred)
    # print(h2o_pred)
    # colnames(h2o_pred)
    h2o_roc  <- roc(h2o_pred$p1, factor(test.df[, depVars]))
    h2o_auc <- auc(h2o_roc)

    # Find auc for both the testing and training set...
    glm_auc <- function(pred.r, ref.r){
      glmnet_pred <- pred.r[,ncol(pred.r)]
      glmnet_roc <- roc(glmnet_pred, factor(ref.r))
      glmnet_auc <- auc(glmnet_roc)
      return(glmnet_auc)
    }

    glmnet_test_auc <- glm_auc(pred_test.r, test.df[,depVars])
    glmnet_train_auc <- glm_auc(pred_train.r, train.df[,depVars])
    glmnet_deviance <- deviance(glm.r)
    h2o_deviance <- glm.h2o@model$training_metrics@metrics$residual_deviance

    print(paste0("AUC of H2O model on training set:  ", glm.h2o@model$training_metrics@metrics$AUC))
    print(paste0("AUC of H2O model on testing set:  ", h2o_auc))
    print(paste0("AUC of GLMnet model on training set:  ", glmnet_train_auc))
    print(paste0("AUC of GLMnet model on testing set:  ", glmnet_test_auc))

    checkEqualsNumeric(h2o_auc, glmnet_train_auc, tolerance = 0.05)

    ### Functions to calculate logistic gradient
    logistic_gradient <- function(x,y,beta) {
      y <- -1 + 2*y
      eta <- x %*% beta
      d <- 1 + exp(-y*eta)
      grad <- -y * (1-1.0/d)
      t(grad) %*% x
    }
    # no L1 here, alpha is 0
    h2o_logistic_gradient <- function(x,y,beta,beta_give,rho,lambda) {
      grad <- logistic_gradient(x,y,beta)/nrow(x) + (beta - beta_given)*rho + lambda*beta
    }

    ########### Run check of priors vs no priors
    data.hex$C3 <- as.factor(data.hex$C3)
    glm.h2o1 <- h2o.glm(x = indVars, y = depVars, training_frame = data.hex, family = family_type, standardize = F,
                        alpha = alpha, beta_constraint = beta_nointercept.hex)
    glm.h2o2 <- h2o.glm(x = indVars, y = depVars, training_frame = data.hex, family = family_type, standardize = F,
                        alpha = alpha, beta_constraint = beta_nointercept.hex[c("names","lower_bounds","upper_bounds")])
    y <- as.matrix(train.df[,depVars])
    x <- xMatrix
    beta1 <- glm.h2o1@model$coefficients_table$coefficients
    beta2 <- glm.h2o2@model$coefficients_table$coefficients
    beta_given <- as.data.frame(betaConstraints.hex$beta_given)
    rho <- as.data.frame(betaConstraints.hex$rho)
    logistic_gradient(x,y,beta1)
    gradient1 <- h2o_logistic_gradient(x,y,beta1, beta_given, rho=1, lambda)
    gradient2 <- h2o_logistic_gradient(x,y,beta2, beta_given, rho=0, lambda)

    Log.info("Check gradient of beta constraints with priors or beta given...")
    threshold = 1E-1
    print(as.numeric(gradient1$beta_given)[-23])
    all(as.numeric(gradient1$beta_given)[-23] < threshold)

    Log.info("Check gradient of beta constraints without priors or beta given...")
    all(as.numeric(gradient2$beta_given)[-23] < threshold)
    print(as.numeric(gradient1$beta_given)[-23])
    testEnd()
  }
}

doTest("GLM Test: Beta Constraints with Priors", test.Priors.BetaConstraints)




