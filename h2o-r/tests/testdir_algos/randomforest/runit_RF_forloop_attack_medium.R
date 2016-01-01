setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Constants and setters
bools <- c(TRUE, FALSE)
set_x <- function(cols) {
  if(sample(bools,1)) {
    while (TRUE){
      myX <- cols
      for(i in 1:length(cols))
        if (sample(bools, 1))
          myX <- myX[-i]
      if(length(myX) > 0)
        break
    }
    return(myX)
  } else
    cols
}
set_y <- function(col) return(col)
set_training_frame <- function(frame) return(frame)
set_validation_frame <- function(frame) return(frame)
set_mtries <- function(numcols) sample(c(-1, 1:numcols), 1)
set_sample_rate <- function() runif(1)
set_build_tree_one_node <- function() sample(bools, 1)
set_ntrees <- function() sample.int(10, 1)
set_max_depth <- function() sample.int(30,1)
set_min_rows <- function() sample.int(20,1)
set_nbins <- function() sample(2:1000, 1)
set_balance_classes <- function() sample(bools, 1)
set_max_after_balance_size <- function(balance) runif(1, 0, 10)

randomParams <- function(train, test, x, y) {
  parms <- list()

  parm_set <- function(parm, required = FALSE, dep = TRUE, ...) {
    if (!dep)
      return(NULL)
    if (required || sample(bools,1)) {
      val <- do.call(paste0("set_", parm), list(...))
      if (!is.null(val))
        if (is.vector(val))
          h2oTest.logInfo(paste0(sub("_", " ", parm), ": ", paste(val, collapse = ", ")))
        else if (class(val) == "H2OFrame")
          h2oTest.logInfo(paste("H2OFrame: ", head(val)))
        else
          h2oTest.logInfo(paste0(sub("_", " ", parm), ": ", val))
      return(val)
    }
    return(NULL)
  }

  parms$x <- parm_set("x", required = TRUE, cols = x)
  parms$y <- parm_set("y", required = TRUE, col = y)
  parms$training_frame <- parm_set("training_frame", required = TRUE, frame = train)
  parms$validation_frame <- parm_set("validation_frame", frame = test)
  parms$mtries <- parm_set("mtries", numcols = length(parms$x))
  parms$sample_rate <- parm_set("sample_rate")
  parms$build_tree_one_node <- parm_set("build_tree_one_node")
  parms$ntrees <- parm_set("ntrees")
  parms$max_depth <- parm_set("max_depth")
  parms$min_rows <- parm_set("min_rows")
  parms$nbins <- parm_set("nbins")
  parms$balance_classes <- parm_set("balance_classes", dep = is.factor(train[[y]]))
  parms$max_after_balance_size <- parm_set("max_after_balance_size",
    dep = !is.null(parms$balance_classes) && parms$balance_classes)

  t <- system.time(hh <- do.call("h2o.randomForest", parms))
  print(hh)

  h2o.rm(hh@model_id)
  print("#########################################################################################")
  print("")
  print(t)
  print("")
}

test.RF.rand_attk_forloop <- function() {
  h2oTest.logInfo("Import and data munging...")
  pros.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")

  iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"))
  i.sid <- h2o.runif(iris.hex)
  iris.train <- h2o.assign(iris.hex[i.sid > .2, ], "iris.train")
  iris.test <- h2o.assign(iris.hex[i.sid <= .2, ], "iris.test")

  cars.hex <- h2o.uploadFile( h2oTest.locate("smalldata/junit/cars.csv"))
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  h2oTest.logInfo("### Binomial ###")
  for(i in 1:10)
    randomParams(pros.train, pros.test, 3:9, 2)
  h2oTest.logInfo("### Multinomial ###")
  for(i in 1:10)
    randomParams(iris.train, iris.test, 1:4, 5)
  h2oTest.logInfo("### Regression ###")
  for(i in 1:10)
    randomParams(cars.train, cars.test, 4:7, 3)

  
}

h2oTest.doTest("Checking DRF in Random Attack For-loop", test.RF.rand_attk_forloop)
