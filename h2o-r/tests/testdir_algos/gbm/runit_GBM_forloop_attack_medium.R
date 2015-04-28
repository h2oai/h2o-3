setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

randomParams <- function(distribution, train, test, x, y) {
  parms <- list()
  # used to sample a T or F
  bools <- c(TRUE, FALSE)
  parms$training_frame <- train
  Log.info(paste("Using training_frame:", deparse(substitute(train))))
  # Remove some cols, or not
  while (TRUE){
    myX <- x
    for(i in 1:length(x))
      if (sample(bools, 1))
        myX <- myX[-i]
    if(length(myX) > 0)
      break
  }
  parms$x <- myX
  Log.info(paste("x:", paste(parms$x, collapse = ", ")))
  parms$y <- y
  Log.info(paste("y:", parms$y))
  parms$distribution <- distribution
  Log.info(paste("distribution:", parms$distribution))
  # [1, 100,000]
  parms$ntrees <- sample.int(1000,1)
  Log.info(paste("ntrees:", parms$ntrees))
  # [1, maxInt]
  parms$max_depth <- sample.int(30, 1)
  Log.info(paste("max_depth:", parms$max_depth))
  # [1, maxInt]
  parms$min_rows <- sample.int(20, 1)
  Log.info(paste("min_rows:", parms$min_rows))
  # (0, 1]
  parms$learn_rate <- runif(1)
  Log.info(paste("learn_rate:", parms$learn_rate))
  # [2, 1,000]  ## realistically important values, 1 shouldn't be acceptable
  parms$nbins <- sample(2:1000, 1)
  Log.info(paste("nbins:", parms$nbins))
  if(sample(bools,1))
    parms$validation_frame <- test
    Log.info(paste("validation_frame:", deparse(substitute(test))))
  # parms$score_each_iteration <- sample(bools, 1)

  if(distribution %in% c("multinomial", "bernoulli")) {
    parms$balance_classes <- sample(bools, 1)
    Log.info(paste("balance_classes:", parms$balance_classes))
  # if balance_classes TRUE, maybe max_size_after_balance
    if (parms$balance_classes && sample(bools, 1))
      # Pick either larger than initial size, or smaller
      if (sample(bools, 1)) {
        parms$max_after_balance_size <- runif(1, 1, 1000)
        Log.info(paste("max_after_balance_size:", parms$max_after_balance_size))
      } else {
        parms$max_after_balance_size <- runif(1)
        Log.info(paste("max_after_balance_size:", parms$max_after_balance_size))
      }
    hh <- do.call("h2o.gbm", parms)
  }
  else
    hh <- do.call("h2o.gbm", parms)

  h2o.rm(hh@model_id)
}

test.GBM.rand_attk_forloop <- function(conn) {
  Log.info("Import and data munging...")
  pros.hex <- h2o.uploadFile(conn, locate("smalldata/prostate/prostate.csv.zip"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  # This as.factor is bugged
  # pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")

  iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris_wheader.csv"))
  i.sid <- h2o.runif(iris.hex)
  iris.train <- h2o.assign(iris.hex[i.sid > .2, ], "iris.train")
  iris.test <- h2o.assign(iris.hex[i.sid <= .2, ], "iris.test")

  cars.hex <- h2o.uploadFile(conn, locate("smalldata/junit/cars.csv"))
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  Log.info("### Binomial ###")
  for(i in 1:3)
    randomParams("bernoulli", pros.train, pros.test, 3:9, 2)
  Log.info("### Multinomial ###")
  for(i in 1:3)
    randomParams("multinomial", iris.train, iris.test, 1:4, 5)
  Log.info("### Regression ###")
  for(i in 1:3)
    randomParams("gaussian", cars.train, cars.test, 4:7, 3)

  testEnd()
}

doTest("Checking GBM in Random Attack For Loops", test.GBM.rand_attk_forloop)
