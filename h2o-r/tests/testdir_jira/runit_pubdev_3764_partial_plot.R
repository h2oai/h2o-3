setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

## PUBDEV-3764
# Run random forest on the prostate dataset and calculate the partial dependence of categorical feature "RACE"
# Check to make sure the manually calculated mean response when running h2o.predict matches results from
# h2o.partialPlot function. There is two issues here:
# 1) Numerically wrong: Dataset with only one present level of the entire domain returns wrong meanResponse
# 2) Unexpected Behavior: Parital plots should be scored with the level present in the dataset or with all
# available levels extracted from the model, not the first level listed in the domain
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
  prostate_hex = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), "prostate.hex")

  ## Change CAPSULE and RACE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"])
  prostate_hex[, "RACE"] = as.factor(prostate_hex[, "RACE"])
  
  ## Run a Random Forest in H2O
  seed = .Random.seed[1]
  Log.info(paste0("Random seed used = ", seed))
  prostate_drf = h2o.randomForest(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 50, seed = seed)
  
  ## Calculate the partial dependence manually using breaks from results of h2o.partialPlot
  ## Define function
  partialDependence <- function(object, pred.data, xname, h2o.pp) {
    xv <- pred.data[, xname]
    x.pt <- h2o.pp[,1]
    y.pt <- numeric(length(x.pt))
    
    for (i in seq(along = x.pt)) {
      x.data <- pred.data
      x.data[, xname] <- as.h2o(rep(x.pt[i], nrow(x.data)))
      pred <- h2o.predict(object = object, newdata = x.data)
      y.pt[i] <- mean( pred[,ncol(pred)])
    }
    return(data.frame(xname = x.pt, mean_response = y.pt))
  }
  
  ## Subset prostate_hex by RACE
  prostate_hex_race_0 <- prostate_hex[prostate_hex$RACE == "0", ]
  prostate_hex_race_1 <- prostate_hex[prostate_hex$RACE == "1", ]
  prostate_hex_race_2 <- prostate_hex[prostate_hex$RACE == "2", ]
  
  ## Calculate partial plot on the subsetted dataset
  h2o_pp_race_0 = h2o.partialPlot(object = prostate_drf, data = prostate_hex_race_0, cols = "RACE", plot = F)
  h2o_pp_race_1 = h2o.partialPlot(object = prostate_drf, data = prostate_hex_race_1, cols = "RACE", plot = F)
  h2o_pp_race_2 = h2o.partialPlot(object = prostate_drf, data = prostate_hex_race_2, cols = "RACE", plot = F)
  
  ## Calculate the partial dependence manually
  check_pp_race_0 = partialDependence(object = prostate_drf, pred.data = prostate_hex_race_0, xname = "RACE", h2o.pp = h2o_pp_race_0)
  check_pp_race_1 = partialDependence(object = prostate_drf, pred.data = prostate_hex_race_1, xname = "RACE", h2o.pp = h2o_pp_race_1)
  check_pp_race_2 = partialDependence(object = prostate_drf, pred.data = prostate_hex_race_2, xname = "RACE", h2o.pp = h2o_pp_race_2)
  
  ## Check the partial plot from h2o 
  checkEqualsNumeric(check_pp_race_0[,"mean_response"], h2o_pp_race_0[,"mean_response"])
  checkEqualsNumeric(check_pp_race_1[,"mean_response"], h2o_pp_race_1[,"mean_response"])
  checkEqualsNumeric(check_pp_race_2[,"mean_response"], h2o_pp_race_2[,"mean_response"])
  
  ## H2O partial plot on the entire dataset
  h2o_pp_race   = h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = F)
  
  ## Dataset with only one level only scores with the first level in the column domain based off of the model
  checkEquals(h2o_pp_race_0$race, "0")
  checkEquals(h2o_pp_race_1$race, "1")
  checkEquals(h2o_pp_race_2$race, "2")
  
  ## Note: Partial plots in R/sckit-learn generates the bins based on the data and the domain from the data
  ## However it can be useful to score for RACE = "0", "1", "2" instead of just "0"
}

doTest("Test Partial Dependence Plots in H2O: ", test)

