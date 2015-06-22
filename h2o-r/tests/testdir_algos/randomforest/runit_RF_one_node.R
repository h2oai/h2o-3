setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.one.node.drf <- function(conn) {
  Log.info("Loading data and building models...")
  airs.hex <- h2o.importFile(locate("smalldata/airlines/allyears2k.zip"))

  e = tryCatch({
    drf.sing <- h2o.randomForest(x = 1:30, y = 31, training_frame = airs.hex,
      build_tree_one_node = T, seed = 1234)
    drf.mult <- h2o.randomForest(x = 1:30, y = 31, training_frame = airs.hex,
      build_tree_one_node = F, seed = 1234)
    NULL
    }, 
    error = function(err) { err }
  )

  if (!is.null(e[[1]])) {
    expect_identical(e[[1]], "Cannot run on a single node in client mode.\n")
  } else {
    Log.info("Multi Node:")
    print(paste("MSE:", h2o.mse(drf.mult)))
    print(paste("AUC:", h2o.auc(drf.mult)))
    print(paste("R^2:", h2o.r2(drf.mult)))
    Log.info("Single Node:")
    print(paste("MSE:", h2o.mse(drf.sing)))
    print(paste("AUC:", h2o.auc(drf.sing)))
    print(paste("R^2:", h2o.r2(drf.sing)))

    Log.info("MSE, AUC, and R2 should be the same...")
    print((h2o.mse(drf.sing)-h2o.mse(drf.mult))/h2o.mse(drf.mult))
    expect_equal(h2o.mse(drf.sing), h2o.mse(drf.mult), tolerance = 0.01)
    expect_equal(h2o.auc(drf.sing), h2o.auc(drf.mult), tolerance = 0.01)
    expect_equal(h2o.r2(drf.sing), h2o.r2(drf.mult), tolerance = 0.01)
  }

  testEnd()
}

doTest("Testing One Node vs Multi Node Random Forest", test.one.node.drf)
