setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test <- function() {
  ## Import data
  h2oData <- h2o.importFile("/mnt/0xcustomer-datasets/c27/data.csv")
  betaConstraints <- h2o.importFile("/mnt/0xcustomer-datasets/c27/constraints_indices.csv")
  betaConstraints <- betaConstraints[1:(nrow(betaConstraints)-1),] # remove intercept
  bc <- as.data.frame(betaConstraints)

  ## Set Parameters (default standardization = T)
  indVars <-  as.character(bc[1:nrow(bc), "names"])
  depVars <- "C3"
  alpha <- 0.5
  family_type <- "binomial"
  lower_bound <- -100000
  upper_bound <- 100000

  # Choose column to use as categorical and convert column to enum column.
  cat_col <- "C217"
  a <- h2oData[,c(indVars,depVars)]
  a[,cat_col] <- as.factor(a[,cat_col])

  h2oTest.logInfo("Pull data frame into R to run GLMnet...")
  data <- as.data.frame(a)
  h2oTest.logInfo("Prep Data H2OFrame for run in GLMnet, includes categorical expansions...")
  x_1 <- data[,setdiff(indVars, cat_col)]
  x_2 <- data.frame(C217.1 <- ifelse(data[,cat_col] == 1, 1, 0),
                    C217.2 <- ifelse(data[,cat_col] == 2, 1, 0),
                    C217.3 <- ifelse(data[,cat_col] == 3, 1, 0),
                    C217.6 <- ifelse(data[,cat_col] == 6, 1, 0))
  xDataH2OFrame <- cbind(x_1, x_2)
  xMatrix <- as.matrix(xDataH2OFrame)
  yMatrix <- as.matrix(data[,depVars])


  ## Run glmnet model
  model.r <- glmnet(x = xMatrix, alpha = alpha, standardize = T, y = yMatrix, family = family_type,
                    lower.limits = lower_bound, upper.limits = upper_bound)


  # Edit beta constraints frame to have bounds on categoricals
  bc_cat <- data.frame( names =  c("C217.1", "C217.2", "C217.3", "C217.6"),
                        lower_bounds = rep(lower_bound,4), upper_bounds = rep(upper_bound,4),
                        beta_given = c(-1, .5, 2.4, 1.5),
                        rho = rep( 1, 4))
  bc_cat <- rbind(bc_cat, bc[!(bc$names == cat_col),])
  bc_cat$lower_bounds <- lower_bound
  bc_cat$upper_bounds <- upper_bound
  bc_cat$rho <- 0


  ## Run H2O model
  model.h2o <- h2o.glm(x = indVars, y = depVars, training_frame = a, family = family_type, alpha = alpha ,
                      beta_constraints = bc_cat)



  ### Grab ROC and AUC
  library(AUC)
  # Find auc for both the testing and training set...
  glm_auc <- function(pred.r, ref.r){
    glmnet_pred <- pred.r[,ncol(pred.r)]
    glmnet_roc <- roc(glmnet_pred, factor(ref.r))
    glmnet_auc <- auc(glmnet_roc)
    return(glmnet_auc)
  }

  pred.r <- predict(model.r, newx = xMatrix, type = "response")
  glmnet_auc <- glm_auc(pred.r, yMatrix)

  print(paste0("H2O'S AUC : ", model.h2o@model$AUC))
  print(paste0("GLMNET'S AUC : ", glmnet_auc))

  h2oTest.checkGLMModel2(model.h2o, model.r)
}

h2oTest.doTest("GLM Test: GLM w/ Beta Constraints", test)
