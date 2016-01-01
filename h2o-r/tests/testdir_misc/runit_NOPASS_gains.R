setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


library(gains)


test.h2o.gains <- function() {
  tolerance <- 3e-2
  hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/logreg/prostate.csv")))
  hex.df <- as.data.frame(hex)
  m <- h2o.gbm(x = 3:9, y = 2, training_frame = hex)
  preds <- h2o.predict(m, hex)

  # first create the lift/gains w/ h2o.gains
  g <- h2o.gains(actual = hex[,2], predicted = preds[,3])  # 3rd column is class 1 probs

  h2oTest.logInfo("H2O's Gains Table")
  print(g)

  preds.df <- as.data.frame(preds)
  g.df <- gains(actual=hex.df[,2], predicted=preds.df[,3])

  print("R's Gains Table")
  print(g.df)

  mean.resp <- g$Mean.Response
  mean.resp.df <- g.df$mean.resp

  if( any(abs(mean.resp - mean.resp.df) > tolerance)) {
    h2oTest.logInfo("Got bad mean response rates")
    print("H2O's: ")
    print(mean.resp)
    print("R's: ")
    print(mean.resp.df)

    print("DIFFERENCES: ")
    print(abs(mean.resp - mean.resp.df))

    stop("`h2o.gains` differs from the `gains` package computation")
  }

  cume <- g$Cume.Pct.Total.Lift
  cume.df <- g.df$cume.pct.of.total

  if (any(abs(cume.df - cume) > 0.01)) {
    h2oTest.logInfo("Got bad cume response")
    h2oTest.logInfo("Got bad mean response rates")
    print("H2O's: ")
    print(cume)
    print("R's: ")
    print(cume.df)

    print("DIFFERENCES:")
    print(abs(cume.df - cume))

    print("WHICH FAILED:")
    print(any(abs(cume.df - cume) > 0.01))

    stop("`h2o.gains` differs from the `gains` package computation")
  }

  
}

h2oTest.doTest("Test H2O Gains", test.h2o.gains)
