setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

##
# Run random forest on the prostate dataset and calculate the partial dependence of feature "RACE" and "AGE"
# Check to make sure the manually calculated mean response when running h2o.predict matches results from
# h2o.partialPlot function.
# Check error messages for failure cases.
##

## PUBDEV-3764
# Run random forest on the prostate dataset and calculate the partial dependence of categorical feature "RACE"
# Check to make sure the manually calculated mean response when running h2o.predict matches results from
# h2o.partialPlot function. There is two issues here:
# 1) Numerically wrong: Dataset with only one present level of the entire domain returns wrong meanResponse
# 2) Unexpected Behavior: Parital plots should be scored with the level present in the dataset or with all
# available levels extracted from the model, not the first level listed in the domain
##

## PUBDEV-3782
# Partial Plot errors out and doesn't compute partial plots if nbins < cardinality of categorical column.
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

  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"])
  ## Run Random Forest in H2O
  seed = .Random.seed[1]
  Log.info(paste0("Random seed used = ", seed))
  prostate_drf = h2o.randomForest(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 25, seed = seed)
  
  ## Calculate the partial dependence manually using breaks from results of h2o.partialPlot
  ## Define function
  partialDependence <- function(object, pred.data, xname, h2o.pp) {
    x.pt <- h2o.pp[,1]
    y.pt <- numeric(length(x.pt))
    y.sd <- numeric(length(x.pt))
    y.sem <- numeric(length(x.pt))
    
    for (i in seq(along = x.pt)) {
      x.data <- pred.data
      x.data[, xname] <- x.pt[i]
      pred <- h2o.predict(object = object, newdata = x.data)
      y.pt[i] <- mean(pred[,ncol(pred)])
      y.sd[i] <- sd(pred[,ncol(pred)])
      y.sem[i] <- y.sd[i]/sqrt(nrow(x.data))
      
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
    return(data.frame(xname = x.pt, mean_response = y.pt, stddev_response = y.sd, std_error_mean_response = y.sem))
  }
  
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  h2o_race_pp = h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = FALSE)
  h2o_age_pp = h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "AGE", plot = FALSE)
  h2o_age_pp_2 = partialDependence(object = prostate_drf, pred.data = prostate_hex, xname = "AGE", h2o.pp = h2o_age_pp)
  h2o_race_pp_2 = partialDependence(object = prostate_drf, pred.data = prostate_hex, xname = "RACE", h2o.pp = h2o_race_pp)
  
  ## Calculate partial dependence uisng h2o.partialPlot for column "AGE" on row 1
  h2o_row1_age_pp = h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "AGE", plot = FALSE, row_index=1)
  h2o_row1_age_pp_2 = partialDependence(object = prostate_drf, pred.data = prostate_hex[1,], xname = "AGE", h2o.pp = h2o_age_pp)
    
  #Mean response
  checkEqualsNumeric(h2o_age_pp_2[,"mean_response"], h2o_age_pp[,"mean_response"])
  checkEqualsNumeric(h2o_race_pp_2[,"mean_response"], h2o_race_pp[,"mean_response"])
  #Mean response for a single row
  checkEqualsNumeric(h2o_row1_age_pp_2[,"mean_response"], h2o_row1_age_pp[,"mean_response"])
    
  #Standard Deviation of Response
  checkEqualsNumeric(h2o_age_pp_2[,"stddev_response"], h2o_age_pp[,"stddev_response"])
  checkEqualsNumeric(h2o_race_pp_2[,"stddev_response"], h2o_race_pp[,"stddev_response"])
  #Standard Deviation of Response for a single row
  checkEqualsNumeric(h2o_row1_age_pp_2[,"stddev_response"], h2o_row1_age_pp[,"stddev_response"])
    
  #Standard Error of Mean Response
  checkEqualsNumeric(h2o_age_pp_2[,"std_error_mean_response"], h2o_age_pp[,"std_error_mean_response"])
  checkEqualsNumeric(h2o_race_pp_2[,"std_error_mean_response"], h2o_race_pp[,"std_error_mean_response"])
  #Standard Error of Mean Response for a single row
  checkEqualsNumeric(h2o_row1_age_pp_2[,"std_error_mean_response"], h2o_row1_age_pp[,"std_error_mean_response"])
    
  ## Check spliced/subsetted datasets
  prostate_hex[, "RACE"] = as.factor(prostate_hex[, "RACE"])
  prostate_drf = h2o.randomForest(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 25, seed = seed)
  
  ## Subset prostate_hex by RACE
  prostate_hex_race_0 <- prostate_hex[prostate_hex$RACE == "0", ]
  prostate_hex_race_1 <- prostate_hex[prostate_hex$RACE == "1", ]
  prostate_hex_race_2 <- prostate_hex[prostate_hex$RACE == "2", ]
  
  ## Calculate partial plot on the subsetted dataset
  h2o_pp_race_0 = h2o.partialPlot(object = prostate_drf, data = prostate_hex_race_0, cols = "RACE", plot = FALSE)
  h2o_pp_race_1 = h2o.partialPlot(object = prostate_drf, data = prostate_hex_race_1, cols = "RACE", plot = FALSE)
  h2o_pp_race_2 = h2o.partialPlot(object = prostate_drf, data = prostate_hex_race_2, cols = "RACE", plot = FALSE)
  
  ## Calculate the partial dependence manually
  check_pp_race_0 = partialDependence(object = prostate_drf, pred.data = prostate_hex_race_0, xname = "RACE", h2o.pp = h2o_pp_race_0)
  check_pp_race_1 = partialDependence(object = prostate_drf, pred.data = prostate_hex_race_1, xname = "RACE", h2o.pp = h2o_pp_race_1)
  check_pp_race_2 = partialDependence(object = prostate_drf, pred.data = prostate_hex_race_2, xname = "RACE", h2o.pp = h2o_pp_race_2)
  
  ## Check the partial plot from h2o
  
  #Mean response
  checkEqualsNumeric(check_pp_race_0[,"mean_response"], h2o_pp_race_0[,"mean_response"])
  checkEqualsNumeric(check_pp_race_1[,"mean_response"], h2o_pp_race_1[,"mean_response"])
  checkEqualsNumeric(check_pp_race_2[,"mean_response"], h2o_pp_race_2[,"mean_response"])
  
  #Standard Deviation of Response
  checkEqualsNumeric(check_pp_race_0[,"stddev_response"], h2o_pp_race_0[,"stddev_response"])
  checkEqualsNumeric(check_pp_race_1[,"stddev_response"], h2o_pp_race_1[,"stddev_response"])
  checkEqualsNumeric(check_pp_race_2[,"stddev_response"], h2o_pp_race_2[,"stddev_response"])
  
  #Standard Error of Mean Response
  checkEqualsNumeric(check_pp_race_0[,"std_error_mean_response"], h2o_pp_race_0[,"std_error_mean_response"])
  checkEqualsNumeric(check_pp_race_1[,"std_error_mean_response"], h2o_pp_race_1[,"std_error_mean_response"])
  checkEqualsNumeric(check_pp_race_2[,"std_error_mean_response"], h2o_pp_race_2[,"std_error_mean_response"])
  
  ## H2O partial plot on the entire dataset
  h2o_pp_race  = h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "RACE", plot = FALSE)
  
  ## Dataset with only one level only scores with the first level in the column domain based off of the model
  checkEquals(h2o_pp_race_0$RACE, "0")
  checkEquals(h2o_pp_race_1$RACE, "1")
  checkEquals(h2o_pp_race_2$RACE, "2")
  
  ## Check column name and column type matches using test dataset iris
  iris_hex = as.h2o(iris[1:100,])
  iris_gbm = h2o.gbm(x = 1:4, y = 5, training_frame = iris_hex)
  iris_pps = h2o.partialPlot(object = iris_gbm, data = iris_hex)
  iris_pps2 = lapply( iris_pps, function(x) partialDependence(object = iris_gbm, pred.data = iris_hex, xname = names(x)[1], h2o.pp = x))
  checkTrue(all(unlist(lapply(1:4, function(i) checkEqualsNumeric(iris_pps2[[i]]$mean_response, iris_pps[[i]]$mean_response)))))
  checkTrue(all(unlist(lapply(1:4, function(i) checkEqualsNumeric(iris_pps2[[i]]$stddev_response, iris_pps[[i]]$stddev_response)))))
  checkTrue(all(unlist(lapply(1:4, function(i) checkEqualsNumeric(iris_pps2[[i]]$std_error_mean_response, iris_pps[[i]]$std_error_mean_response)))))
    
  ## Ask to score on multinomial case
  iris[,'random'] <- as.factor(as.data.frame(unlist(sample(x = 1:4, size = length(iris[[1]]), replace=TRUE)))[[1]])
  iris_hex = as.h2o(iris)
  iris_gbm = h2o.gbm(x = c(1:4,6), y = 5, training_frame = iris_hex)
    
  # one column  
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Sepal.Length", targets=c("setosa"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp11")
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Sepal.Length", targets=c("setosa", "virginica", "versicolor"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp21")
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Sepal.Length", targets=c("setosa"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp12", plot_stddev = FALSE)
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Sepal.Length", targets=c("setosa", "virginica", "versicolor"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp22", plot_stddev = FALSE)

  #h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Petal.Length", targets=c("setosa"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp31")
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Petal.Length", targets=c("setosa", "virginica", "versicolor"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp41")
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Petal.Length", targets=c("setosa"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp32", plot_stddev = FALSE)
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Petal.Length", targets=c("setosa", "virginica", "versicolor"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp42", plot_stddev = FALSE)

  # two colums  
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols=c("Petal.Length", "Sepal.Length"), targets=c("setosa"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp5")
    
  # categorical column
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols=c("random"), targets=c("versicolor"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp61")
  h2o.partialPlot(object = iris_gbm, data = iris_hex, cols=c("random"), targets=c("versicolor", "setosa"), save_to="/home/mori/Documents/h2o/code/test/pdp_plot/pdp62", plot_stddev = FALSE)

  ## Check failure cases
  ## 1) Selection of incorrect columns 
  expect_error(h2o.partialPlot(object = prostate_drf, data = prostate_hex[-2], cols = "AGE"), "is not a column name")
  expect_error(h2o.partialPlot(object = prostate_drf, data = prostate_hex, cols = "BLAH"), "Invalid column names")
    
  ## 2) Nbins is smaller than cardinality of a categorical column
  prostate_hex[ ,"AGE"] = as.factor(prostate_hex[ ,"AGE"])
  prostate_gbm = h2o.gbm(x = c("AGE", "RACE"), y = "CAPSULE", training_frame = prostate_hex, ntrees = 10, seed = seed)
  expect_error(h2o.partialPlot(object = prostate_gbm, data = prostate_hex),"Column AGE's cardinality")
    
  ## 3) Target is not set for multinomial classification  
  expect_error(h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Sepal.Length"), "targets parameter has to be set for multinomial classification")

  ## 4) Target class is not in target domain   
  expect_error(h2o.partialPlot(object = iris_gbm, data = iris_hex, cols="Sepal.Length", targets=c("Iris")), "\n\nERROR MESSAGE:\n\nIncorrect target class: Iris.\n\n")

  ## 5) Target is set for non multinomial problem
  expect_error(h2o.partialPlot(object = prostate_drf, data = prostate_hex, targets=c("Iris")), "\n\nERROR MESSAGE:\n\nTargets parameter is available only for multinomial classification.\n\n")
}

doTest("Test Partial Dependence Plots in H2O: ", test)

