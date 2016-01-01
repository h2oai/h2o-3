setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#Export file with h2o.export_file and compare with R counterpart when re importing file to check for parity.


test.export.file <- function() {
  pros.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")

  myglm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.train, family = "binomial")
  mypred <- h2o.predict(myglm, pros.test)

  fname <- paste(paste0(sample(letters, 3, replace = TRUE), collapse = ""),
                 paste0(sample(0:9, 3, replace = TRUE), collapse = ""),
                 "predict.csv", sep = "_")
  dname <- paste(h2oTest.sandbox(), fname, sep = .Platform$file.sep)

  h2oTest.logInfo("Exporting File...")
  h2o.exportFile(mypred, dname)

  h2oTest.logInfo("Comparing file with R...")
  R.pred <- read.csv(dname, colClasses=c("factor",NA,NA))
  print(head(R.pred))
  H.pred <- as.data.frame(mypred)
  print(head(H.pred))
  expect_identical(R.pred, H.pred)

  
}

h2oTest.doTest("Testing Exporting Files", test.export.file)
