####################################################################################################
##
## Testing model saving when a validation model metric is present
##
####################################################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.save.all.algos <- function(conn) {
  pros.hex <- h2o.uploadFile(conn, locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
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

  tmp_dir <- tempdir()

  Log.info("Order is Mult (w  w/o val) Regr (w w/o val) Bin (w w/o val)")

  Log.info("Saving gbm models...")
  iris.no_val.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train)
  iris.nv.path <- h2o.saveModel(iris.no_val.gbm, tmp_dir)
  iris.val.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train,
                          validation_frame = iris.test)
  iris.v.path <- h2o.saveModel(iris.val.gbm, tmp_dir)
  cars.no_val.gbm <- h2o.gbm(x = 3:8, y = 2, training_frame = cars.train)
  cars.nv.path <- h2o.saveModel(cars.no_val.gbm, tmp_dir)
  cars.val.gbm <- h2o.gbm(x = 3:8, y = 2, training_frame = cars.train,
                          validation_frame = cars.test)
  cars.v.path <- h2o.saveModel(cars.val.gbm, tmp_dir)
  pros.no_val.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train)
  pros.nv.path <- h2o.saveModel(pros.no_val.gbm, tmp_dir)
  pros.val.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train,
                          validation_frame = pros.test)
  pros.v.path <- h2o.saveModel(pros.val.gbm, tmp_dir)

  Log.info("Saving deeplearning models...")
  iris.no_val.dl <- h2o.deeplearning(x = 1:4, y = 5, training_frame = iris.train)
  iris.nv.path.dl <- h2o.saveModel(iris.no_val.dl, tmp_dir)
  iris.val.dl <- h2o.deeplearning(x = 1:4, y = 5, training_frame = iris.train,
                                  validation_frame = iris.test)
  iris.v.path.dl <- h2o.saveModel(iris.val.dl, tmp_dir)
  cars.no_val.dl <- h2o.deeplearning(x = 3:8, y = 2, training_frame = cars.train)
  cars.nv.path.dl <- h2o.saveModel(cars.no_val.dl, tmp_dir)
  cars.val.dl <- h2o.deeplearning(x = 3:8, y = 2, training_frame = cars.train,
                                  validation_frame = cars.test)
  cars.v.path.dl <- h2o.saveModel(cars.val.dl, tmp_dir)
  pros.no_val.dl <- h2o.deeplearning(x = 3:9, y = 2, training_frame = pros.train)
  pros.nv.path.dl <- h2o.saveModel(pros.no_val.dl, tmp_dir)
  pros.val.dl <- h2o.deeplearning(x = 3:9, y = 2, training_frame = pros.train,
                                  validation_frame = pros.test)
  pros.v.path.dl <- h2o.saveModel(pros.val.dl, tmp_dir)

  Log.info("Saving glm models...")
  cars.no_val.glm <- h2o.glm(x = 3:8, y = 2, training_frame = cars.train)
  cars.nv.path.glm <- h2o.saveModel(cars.no_val.glm, tmp_dir)
  cars.val.glm <- h2o.glm(x = 3:8, y = 2, training_frame = cars.train,
                          validation_frame = cars.test)
  cars.v.path.glm <- h2o.saveModel(cars.val.glm, tmp_dir)
  pros.no_val.glm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.train,
                             family = "binomial")
  pros.nv.path.glm <- h2o.saveModel(pros.no_val.glm, tmp_dir)
  pros.val.glm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.train,
                          validation_frame = pros.test, family = "binomial")
  pros.v.path.glm <- h2o.saveModel(pros.val.glm, tmp_dir)


  Log.info("Saving random forest models...")
  iris.no_val.drf <- h2o.randomForest(x = 1:4, y = 5, training_frame = iris.train)
  iris.nv.path.drf <- h2o.saveModel(iris.no_val.drf, tmp_dir)
  iris.val.drf <- h2o.randomForest(x = 1:4, y = 5, training_frame = iris.train,
                                  validation_frame = iris.test)
  iris.v.path.drf <- h2o.saveModel(iris.val.drf, tmp_dir)
  cars.no_val.drf <- h2o.randomForest(x = 3:8, y = 2, training_frame = cars.train)
  cars.nv.path.drf <- h2o.saveModel(cars.no_val.drf, tmp_dir)
  cars.val.drf <- h2o.randomForest(x = 3:8, y = 2, training_frame = cars.train,
                                  validation_frame = cars.test)
  cars.v.path.drf <- h2o.saveModel(cars.val.drf, tmp_dir)
  pros.no_val.drf <- h2o.randomForest(x = 3:9, y = 2, training_frame = pros.train)
  pros.nv.path.drf <- h2o.saveModel(pros.no_val.drf, tmp_dir)
  pros.val.drf <- h2o.randomForest(x = 3:9, y = 2, training_frame = pros.train,
                                  validation_frame = pros.test)
  pros.v.path.drf <- h2o.saveModel(pros.val.drf, tmp_dir)


  endTest()
}

doTest("Saving Models of All Algos with/without Validation", test.save.all.algos)