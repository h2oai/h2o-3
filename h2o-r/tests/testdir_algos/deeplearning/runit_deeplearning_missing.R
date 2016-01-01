setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_missing <- function() {
  h2oTest.logInfo("Test checks if Deep Learning works fine with a categorical dataset that has many missing values (in both train & test splits)")

  missing_ratios = c(0, 0.1, 0.25, 0.5, 0.75, 0.99)
  errors = c(0, 0, 0, 0, 0, 0)

  data = h2o.uploadFile(h2oTest.locate("smalldata/junit/weather.csv"))
  data[,16] = as.factor(data[,16]) #ChangeTempDir
  data[,17] = as.factor(data[,17]) #ChangeTempMag
  data[,18] = as.factor(data[,18]) #ChangeWindDirect
  data[,19] = as.factor(data[,19]) #MaxWindPeriod
  data[,20] = as.factor(data[,20]) #RainToday
  data[,22] = as.factor(data[,22]) #PressureChange
  data[,24] = as.factor(data[,24]) #RainTomorrow

  for(i in 1:length(missing_ratios)) {
    print(paste0("For missing ", missing_ratios[i]*100, "%"))

    # add missing values to the data section of the file (leave the response alone)
    if (missing_ratios[i] > 0) {
      resp = data[,24]
      pred = data[,-24]
      data_missing = h2o.insertMissingValues(pred,fraction=missing_ratios[i])
      Sys.sleep(1.5)    #sleep until waitOnJob is fixed
      data_fin = h2o.cbind(data_missing, resp)
    } else 
      data_fin = data

    # split into train + test datasets
    ratio <- h2o.runif(data_fin)
    train <- data_fin[ratio <= .75, ]
    test  <- data_fin[ratio >  .75, ]
    # splits=h2o.splitFrame(data,ratios=c(.75),shuffle=T)
    # train = splits[[1]]
    # test  = splits[[2]]

    hh=h2o.deeplearning(x=3:22,y=24,training_frame=train,validation=test,epochs=5,reproducible=T,seed=12345,
                        activation='RectifierWithDropout',l1=1e-5,input_dropout_ratio=0.2)

    errors[i] = 1 - hh@model$training_metrics@metrics$thresholds_and_metric_scores$accuracy[hh@model$training_metrics@metrics$max_criteria_and_metric_scores[1,4]]
  }

  for(i in 1:length(missing_ratios)) {
    print(paste0("missing ratio: ", missing_ratios[i]*100, "% --> classification error: ", errors[i]))
  }
  checkTrue(sum(errors) < 2.2, "Sum of classification errors is too large!")

  
}

h2oTest.doTest("Deep Learning Missing Values Test", check.deeplearning_missing)

