setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
####################################################################################################
##
## Testing model saving when a validation model metric is present
##
####################################################################################################




test.save.all.algos <- function() {

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

  cars.hex <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars.csv"))
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  tmp_dir <- h2oTest.sandbox()

  h2oTest.logInfo("Order is Multinmoal (w val, w/o val) Regression (w val, w/o val) Bin (w val, w/o val)")

  h2oTest.logInfo("Saving gbm models...")
  iris.no_val.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train)
  iris.nv.path.gbm <- h2o.saveModel(iris.no_val.gbm, tmp_dir)
  iris.val.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train,
                          validation_frame = iris.test)
  iris.v.path.gbm <- h2o.saveModel(iris.val.gbm, tmp_dir)
  cars.no_val.gbm <- h2o.gbm(x = 3:8, y = 2, training_frame = cars.train)
  cars.nv.path.gbm <- h2o.saveModel(cars.no_val.gbm, tmp_dir)
  cars.val.gbm <- h2o.gbm(x = 3:8, y = 2, training_frame = cars.train,
                          validation_frame = cars.test)
  cars.v.path.gbm <- h2o.saveModel(cars.val.gbm, tmp_dir)
  pros.no_val.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train)
  pros.nv.path.gbm <- h2o.saveModel(pros.no_val.gbm, tmp_dir)
  pros.val.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train,
                          validation_frame = pros.test)
  pros.v.path.gbm <- h2o.saveModel(pros.val.gbm, tmp_dir)

  h2oTest.logInfo("Saving deeplearning models...")
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

  h2oTest.logInfo("Saving glm models...")
  cars.no_val.glm <- h2o.getModel(h2o.glm(x = 3:8, y = 2, training_frame = cars.train)@"model_id")
  cars.nv.path.glm <- h2o.saveModel(cars.no_val.glm, tmp_dir)
  cars.val.glm <- h2o.getModel(h2o.glm(x = 3:8, y = 2, training_frame = cars.train, validation_frame = cars.test)@"model_id")
  cars.v.path.glm <- h2o.saveModel(cars.val.glm, tmp_dir)
  pros.no_val.glm <- h2o.getModel(h2o.glm(x = 3:9, y = 2, training_frame = pros.train, family = "binomial")@"model_id")
  pros.nv.path.glm <- h2o.saveModel(pros.no_val.glm, tmp_dir)
  pros.val.glm <- h2o.getModel(h2o.glm(x = 3:9, y = 2, training_frame = pros.train, validation_frame = pros.test, family = "binomial")@"model_id")
  pros.v.path.glm <- h2o.saveModel(pros.val.glm, tmp_dir)


  h2oTest.logInfo("Saving random forest models...")
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

  h2oTest.logInfo("Loading gbm models...")
  iris.nv.gbm  <- h2o.loadModel(iris.nv.path.gbm)
  iris.v.gbm  <- h2o.loadModel(iris.v.path.gbm)
  cars.nv.gbm  <- h2o.loadModel(cars.nv.path.gbm)
  cars.v.gbm  <- h2o.loadModel(cars.v.path.gbm)
  pros.nv.gbm  <- h2o.loadModel(pros.nv.path.gbm)
  pros.v.gbm  <- h2o.loadModel(pros.v.path.gbm)
  h2oTest.logInfo("Loading deeplearning models...")
  iris.nv.dl  <- h2o.loadModel(iris.nv.path.dl)
  iris.v.dl  <- h2o.loadModel(iris.v.path.dl)
  cars.nv.dl  <- h2o.loadModel(cars.nv.path.dl)
  cars.v.dl  <- h2o.loadModel(cars.v.path.dl)
  pros.nv.dl  <- h2o.loadModel(pros.nv.path.dl)
  pros.v.dl  <- h2o.loadModel(pros.v.path.dl)
  h2oTest.logInfo("Loading glm models...")
  cars.nv.glm  <- h2o.loadModel(cars.nv.path.glm)
  cars.v.glm  <- h2o.loadModel(cars.v.path.glm)
  pros.nv.glm  <- h2o.loadModel(pros.nv.path.glm)
  pros.v.glm  <- h2o.loadModel(pros.v.path.glm)
  h2oTest.logInfo("Loading random forest models...")
  iris.nv.drf  <- h2o.loadModel(iris.nv.path.drf)
  iris.v.drf  <- h2o.loadModel(iris.v.path.drf)
  cars.nv.drf  <- h2o.loadModel(cars.nv.path.drf)
  cars.v.drf  <- h2o.loadModel(cars.v.path.drf)
  pros.nv.drf  <- h2o.loadModel(pros.nv.path.drf)
  pros.v.drf  <- h2o.loadModel(pros.v.path.drf)

  # Checking gbm
  expect_equal(iris.nv.gbm, iris.no_val.gbm)
  expect_equal(iris.v.gbm, iris.val.gbm)
  expect_equal(cars.nv.gbm, cars.no_val.gbm)
  expect_equal(cars.v.gbm, cars.val.gbm)
  expect_equal(pros.nv.gbm, pros.no_val.gbm)
  expect_equal(pros.v.gbm, pros.val.gbm)
  # Checking dl
  expect_equal(iris.nv.gbm, iris.no_val.gbm)
  expect_equal(iris.v.dl, iris.val.dl)
  expect_equal(cars.nv.dl, cars.no_val.dl)
  expect_equal(cars.v.dl, cars.val.dl)
  expect_equal(pros.nv.dl, pros.no_val.dl)
  expect_equal(pros.v.dl, pros.val.dl)
  # Checking glm
  expect_equal(cars.nv.glm, cars.no_val.glm)
  expect_equal(cars.v.glm, cars.val.glm)
  expect_equal(pros.nv.glm, pros.no_val.glm)
  expect_equal(pros.v.glm, pros.val.glm)
  # Checking drf
  expect_equal(iris.nv.drf, iris.no_val.drf)
  expect_equal(iris.v.drf, iris.val.drf)
  expect_equal(cars.nv.drf, cars.no_val.drf)
  expect_equal(cars.v.drf, cars.val.drf)
  expect_equal(pros.nv.drf, pros.no_val.drf)
  expect_equal(pros.v.drf, pros.val.drf)

}

h2oTest.doTest("Saving Models of All Algos with/without Validation", test.save.all.algos)
