##
# Test out the H2OExec2Demo.R
# This demo imports a dataset, parses it, and prints a summary
# Then, it runs data munging operations such as slice, quantile, and column creation
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.exec2.demo <- function() {
  prosPath <- system.file("extdata", "prostate.csv", package="h2o")
  Log.info(paste("Importing", prosPath))
  prostate.hex <- h2o.importFile(path = prosPath, destination_frame = "prostate.hex")

  Log.info("Print out summary, head, and tail")
  print(summary(prostate.hex))
  print(head(prostate.hex))
  print(tail(prostate.hex))

  Log.info("Convert CAPSULE column from numeric to factor variable")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  print(prostate.hex)
  expect_true(is.factor(prostate.hex$CAPSULE))
  print(summary(prostate.hex))


  Log.info("Convert RACE column from numeric to factor variable")
  prostate.hex$RACE <- as.factor(prostate.hex$RACE)
  print(prostate.hex)
  expect_true(is.factor(prostate.hex$RACE))
  print(summary(prostate.hex))

  Log.info("Display count of AGE column levels")
  Log.info("Note: Currently only working on a single integer or factor column")
  age.count <- h2o.table(prostate.hex$AGE)
  print(head(age.count))

  Log.info("Run GLM2 on random sample of 50 observations")
  Log.info("y = CAPSULE, x = AGE, RACE, PSA, VOL, GLEASON")
  prostate.samp <- prostate.hex[sample(1:nrow(prostate.hex), 50),]
  prostate.samp.df <- as.data.frame(prostate.samp)    # Pull into R as a data frame
  expect_that(nrow(prostate.samp), equals(50))
  expect_that(nrow(prostate.samp.df), equals(50))
  # glm(CAPSULE ~ AGE + PSA + VOL + GLEASON, family = binomial(), data = prostate.samp.df)
  prostate.glm <- h2o.glm(x = c("AGE", "RACE", "PSA", "VOL", "GLEASON"), y = "CAPSULE", training_frame = prostate.samp, family = "binomial")
  print(prostate.glm)

  Log.info("Get quantiles of PSA column")
  prostate.qs <- quantile(prostate.hex$PSA)
  print(prostate.qs)

  Log.info("Extract outliers based on 5% and 95% quantile cutoffs")
  # Note: Right now, assignment must be done manually with h2o.assign!
  PSA.outliers <- prostate.hex[prostate.hex$PSA <= prostate.qs[2] | prostate.hex$PSA >= prostate.qs[10],]
  # PSA.outliers.ind = prostate.hex$PSA <= prostate.qs[2] | prostate.hex$PSA >= prostate.qs[10]
  # PSA.outliers = prostate.hex[PSA.outliers.ind,]
  PSA.outliers <- h2o.assign(PSA.outliers, "PSA.outliers")
  print(paste("Number of outliers:", nrow(PSA.outliers)))
  print(head(PSA.outliers))
  print(tail(PSA.outliers))

  Log.info("Trim outliers from dataset")
  prostate.trim <- prostate.hex[prostate.hex$PSA > prostate.qs[2] & prostate.hex$PSA < prostate.qs[10],]
  # prostate.trim = prostate.hex[!PSA.outliers.ind,]
  prostate.trim <- h2o.assign(prostate.trim, "prostate.trim")
  print(paste("Number of rows in trimmed dataset:", nrow(prostate.trim)))

  Log.info("Construct test and train sets using runif")
  s <- h2o.runif(prostate.hex)
  prostate.train <- prostate.hex[s <= 0.8,]
  prostate.train <- h2o.assign(prostate.train, "prostate.train")
  prostate.test <- prostate.hex[s > 0.8,]
  prostate.test <- h2o.assign(prostate.test, "prostate.test")
  expect_that(nrow(prostate.train) + nrow(prostate.test), equals(nrow(prostate.hex)))

  Log.info("For large datasets, need to sample using h2o.runif")
  s2 <- h2o.runif(prostate.hex)
  prostate.train2 <- prostate.hex[s2 <= 0.8,]
  prostate.train2 <- h2o.assign(prostate.train2, "prostate.train2")
  prostate.test2 <- prostate.hex[s2 > 0.8,]
  prostate.test2 <- h2o.assign(prostate.test2, "prostate.test2")
  expect_that(nrow(prostate.train2) + nrow(prostate.test2), equals(nrow(prostate.hex)))

  myY <- "CAPSULE"; myX = setdiff(colnames(prostate.train), c(myY, "ID"))
  Log.info(paste("Run GBM with y = CAPSULE, x =", paste(myX, collapse = ",")))
  prostate.gbm <- h2o.gbm(x = myX, y = myY, training_frame = prostate.train,
    validation_frame = prostate.test, distribution = "bernoulli")
  print(prostate.gbm)

  Log.info("Generate GBM predictions on test set")
  prostate.pred <- predict(prostate.gbm, prostate.test)
  summary(prostate.pred)
  head(prostate.pred)
  tail(prostate.pred)

  Log.info("Create new boolean column based on 25% quantile cutoff")
  prostate.hex[,10] <- prostate.hex$PSA <= prostate.qs["25%"]
  head(prostate.hex)
  expect_that(ncol(prostate.hex), equals(10))
  # prostate.hex[,11] = prostate.hex$PSA >= prostate.qs["75%"]

  Log.info("Run GLM2 with y = new boolean column, x = AGE, RACE, VOL, GLEASON")
  prostate.glm.lin <- h2o.glm(y = 10, x = c("AGE", "RACE", "VOL", "GLEASON"), training_frame = prostate.hex, family = "binomial")
  print(prostate.glm.lin)

  
}

doTest("Test out H2OExec2Demo.R", test.exec2.demo)
