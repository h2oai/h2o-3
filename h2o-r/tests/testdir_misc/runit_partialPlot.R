setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

##
# Run random forest on the prostate dataset and calculate the partial dependence of feature "RACE" and "AGE"
# Check to make sure the manually calculated mean response when running h2o.predict matches results from
# h2o.partialPlot function.
# Check error messages for failure cases.
##

test <- function() {
  # To examine results of partialPlot from the package randomForest
  # library(randomForest)
  # prostate_df = read.csv(file = prostate_path)
  # prostate_df[, "CAPSULE"] = as.factor(prostate_df[, "CAPSULE"])
  ## Run Random Forest in R
  # prostate_rf = randomForest(CAPSULE ~ AGE + RACE, data = prostate_df, ntree = 50)
  # r_age_pp = partialPlot(x = prostate_rf, pred.data = prostate_df, x.var = "AGE")
  # r_race_pp = partialPlot(x = prostate_rf, pred.data = prostate_df, x.var = "RACE")
  
  ## Import prostate dataset
  prostate_path = system.file("extdata", "prostate.csv", package="h2o")
  prostate_hex = h2o.importFile(prostate_path)

  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"])
  
  ## Run Random Forest in H2O
  seed = .Random.seed[1]
  Log.info(paste0("Random seed used = ", seed))
  prostate_drf = h2o.randomForest(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 50, seed = seed)

  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  h2o_race_pp = h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = F)
  h2o_age_pp = h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "AGE", plot = F)
  
  ## Calculate the partial dependence manually using breaks from results of h2o.partialPlot
  ## Define function
  partialDependence <- function(object, pred.data, xname, h2o.pp) {
    n.pt <- min(h2o.unique(pred.data[, xname]), 50)
    xv <- pred.data[, xname]
    x.pt <- h2o.pp[,1]
    y.pt <- numeric(length(x.pt))
    
    for (i in seq(along = x.pt)) {
      x.data <- pred.data
      x.data[, xname] <- x.pt[i]
      pred <- h2o.predict(object = object, newdata = x.data)
      y.pt[i] <- mean( pred[,ncol(pred)])
      
      # # The logic used in the package "RandomForest"
      # # used for binary classifers to bound the partial dependence between -1 and 1
      # if(prostate.gbm@parameters$distribution == "bernoulli") {
      #   pr = h2o.predict(object = object, newdata = x.data)[,c("p0", "p1")]
      #   pr_df = as.data.frame(pr)
      #   pr1 = log( ifelse( pr_df[,"p1"] == 0, .Machine$double.eps, pr_df[,"p1"]))
      #   pr_mean = rowMeans( log(pr_df))
      #   y.pt[i] = mean(pr1 - pr_mean)
      # } else {
      #   y.pt[i] = mean( h2o.predict(object = object, newdata = x.data))
      # 
      # }
      
    }
    return(data.frame(xname = x.pt, mean_response = y.pt))
  }
  
  ## Check that the mean response checks out
  h2o_age_pp_2 = partialDependence(object = prostate_drf, pred.data = prostate_hex, xname = "AGE", h2o.pp = h2o_age_pp)
  h2o_race_pp_2 = partialDependence(object = prostate_drf, pred.data = prostate_hex, xname = "RACE", h2o.pp = h2o_race_pp)
  checkEqualsNumeric(h2o_age_pp_2[,"mean_response"], h2o_age_pp[,"mean_response"])
  checkEqualsNumeric(h2o_race_pp_2[,"mean_response"], h2o_race_pp[,"mean_response"])
  
  ## Check failure cases
  ## 1) Selection of incorrect columns 
  expect_error(h2o.partialPlot(object = prostate_drf, data = prostate_hex[-2], cols = "AGE"), "is not a column name")
  expect_error(h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "BLAH"), "Invalid column names")
  
  ## 2) Ask to score on unsupported multinomial case 
  iris_hex = as.h2o( iris)
  iris_gbm = h2o.gbm(x = 1:4, y = 5, training_frame = iris_hex)
  expect_error(h2o.partialPlot(object = iris_gbm, data = iris_hex, "Sepal.Length"), "object must be a regression model or binary classfier")
  
}

doTest("Test Partial Dependence Plots in H2O: ", test)

