setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.one.node.drf <- function() {
  h2oTest.logInfo("Loading data and building models...")
  airs.hex <- h2o.importFile(h2oTest.locate("smalldata/airlines/allyears2k.zip"))

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
    expect_match(e[[1]], "Cannot run on a single node in client mode")
  } else {
    h2oTest.logInfo("Multi Node:")
    print(paste("MSE:", h2o.mse(drf.mult)))
    print(paste("AUC:", h2o.auc(drf.mult)))
    print(paste("R^2:", h2o.r2(drf.mult)))
    h2oTest.logInfo("Single Node:")
    print(paste("MSE:", h2o.mse(drf.sing)))
    print(paste("AUC:", h2o.auc(drf.sing)))
    print(paste("R^2:", h2o.r2(drf.sing)))

    h2oTest.logInfo("MSE, AUC, and R2 should be the same...")
    print((h2o.mse(drf.sing)-h2o.mse(drf.mult))/h2o.mse(drf.mult))
    expect_equal(h2o.mse(drf.sing), h2o.mse(drf.mult), tolerance = 0.01)
    expect_equal(h2o.auc(drf.sing), h2o.auc(drf.mult), tolerance = 0.01)
    expect_equal(h2o.r2(drf.sing), h2o.r2(drf.mult), tolerance = 0.01)
  }

  
}

h2oTest.doTest("Testing One Node vs Multi Node Random Forest", test.one.node.drf)
