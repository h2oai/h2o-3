setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_missing <- function(conn) {
  Log.info("Test checks if Deep Learning works fine with a categorical dataset that has many missing values (in both train & test splits)")

  missing_ratios = c(0, 0.1, 0.25, 0.5, 0.75, 1.0)
  errors = c(0, 0, 0, 0, 0, 0)

  for(i in 1:length(missing_ratios)) {
    data = h2o.uploadFile(conn, locate("smalldata/junit/weather.csv"))
    data[,16] = as.factor(data[,16]) #ChangeTempDir
    data[,17] = as.factor(data[,17]) #ChangeTempMag
    data[,18] = as.factor(data[,18]) #ChangeWindDirect
    data[,19] = as.factor(data[,19]) #MaxWindPeriod
    data[,20] = as.factor(data[,20]) #RainToday
    data[,22] = as.factor(data[,22]) #PressureChange

    # add missing values to the data section of the file (leave the response alone)
    if (missing_ratios[i] > 0) {
      resp = data[,24]
      data_missing = h2o.insertMissingValues(data[,-24],fraction=missing_ratios[i])
      data = cbind(data_missing, resp)
    }

    # split into train + test datasets
    splits=h2o.splitFrame(data,ratios=c(.75),shuffle=T)
    train = splits[[1]]
    test  = splits[[2]]

    hh=h2o.deeplearning(x=3:22,y=24,training_frame=train,validation=test,
                        activation='RectifierWithDropout', hidden=c(200,200),
                        l1=1e-5,input_dropout=0.2);
    print(hh)
    errors[i] = hh@model$valid_class_error
  }

  for(i in 1:length(missing_ratios)) {
    print(paste("missing ratio: ", missing_ratios[i]*100, "% --> classification error: ", errors[i]))
  }
  checkTrue(sum(errors) < 2.2, "Sum of classification errors is too large!")

  testEnd()
}

doTest("Deep Learning Missing Values Test", check.deeplearning_missing)

