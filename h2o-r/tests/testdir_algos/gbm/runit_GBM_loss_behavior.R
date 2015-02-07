setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.ecology <- function(conn) {  
  Log.info("==============================")
  Log.info("AUTO Behavior")
  Log.info("==============================")
  eco.hex <- h2o.importFile(conn, path=locate("smalldata/gbm_test/ecology_model.csv"))
  # 0/1 response: expect bernoulli
  eco.model <- h2o.gbm(x = 3:13, y = "Angaus", training_frame = eco.hex)
  expect_true(class(eco.model) == "H2OBinomialModel")
  # more than 2 integers for response: expect gaussian
  cars.hex <- h2o.importFile(conn, path=locate("smalldata/junit/cars.csv"))
  cars.mod <- h2o.gbm(x = 4:7, y = "cylinders", training_frame = cars.hex)
  expect_true(class(cars.mod) == "H2ORegressionModel")
  # character response: expect multinomial
  eco.model <- h2o.gbm(x = 1:8, y = "Method", training_frame = eco.hex)
  expect_true(class(eco.model) == "H2OMultinomialModel")


  Log.info("==============================")
  Log.info("Gaussian Behavior")
  Log.info("==============================")
  # 0/1 response: expect gaussian
  eco.model <- h2o.gbm(x = 3:13, y = "Angaus", training_frame = eco.hex, loss="gaussian")
  expect_true(class(eco.model) == "H2ORegressionModel")
  # character response: expect error
  expect_error(eco.model <- h2o.gbm(x = 1:8, y = "Method", training_frame = eco.hex, loss="gaussian"))

  Log.info("==============================")
  Log.info("Bernoulli Behavior")
  Log.info("==============================")
  # 0/1 response: expect bernoulli
  eco.model <- h2o.gbm(x = 3:13, y = "Angaus", training_frame = eco.hex, loss="bernoulli")
  expect_true(class(eco.model) == "H2OBinomialModel")
  # 2 level character response: expect bernoulli
  tree.hex <- h2o.importFile(conn, path=locate("smalldata/junit/test_tree_minmax.csv"))
  tree.model <- h2o.gbm(x = 1:3, y = "response", training_frame = tree.hex, loss="bernoulli")
  expect_true(class(tree.model) == "H2OBinomialModel")
  # more than two integers for response: expect error
  expect_error(cars.mod <- h2o.gbm(x = 4:7, y = "cylinders", training_frame = cars.hex, loss="bernoulli"))
  # more than two character levels for response: expect error
  expect_error(eco.model <- h2o.gbm(x = 1:8, y = "Method", training_frame = eco.hex, loss="bernoulli"))

  Log.info("==============================")
  Log.info("Multinomial Behavior")
  Log.info("==============================")
  # more than two integers for response: expect multinomial
  cars.mod <- h2o.gbm(x = 4:7, y = "cylinders", training_frame = cars.hex, loss="multinomial")
  expect_true(class(cars.mod) == "H2OMultinomialModel")
  # more than two character levels for response: expect multinomial
  eco.model <- h2o.gbm(x = 1:8, y = "Method", training_frame = eco.hex, loss="multinomial")
  expect_true(class(eco.model) == "H2OMultinomialModel")

  testEnd()
}

doTest("GBM: Ecology Data", test.GBM.ecology)

