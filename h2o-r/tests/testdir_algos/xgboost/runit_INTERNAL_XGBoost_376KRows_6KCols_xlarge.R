setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rtest <- function() {

      if (! "XGBoost" %in% h2o.list_all_extensions()) {
      	 print("XGBoost extension is not present.  Skipping test. . .")
	 return()
      }


      hdfs_name_node = HADOOP.NAMENODE
      #----------------------------------------------------------------------
      # Parameters for the test.
      #----------------------------------------------------------------------
      parse_time <- system.time(data.hex <- h2o.importFile("/mnt/0xcustomer-datasets/c28/mr_output.tsv.sorted.gz"))
      print("Time it took to parse")
      print(parse_time)

      dim(data.hex)

      s = h2o.runif(data.hex)
      train = data.hex[s <= 0.8,]
      valid = data.hex[s > 0.8,]

      # XGBOOST model
      xgboost_time <- system.time(model.xgboost <- h2o.xgboost(x = 3:(ncol(train)), y = 2, training_frame = train, validation_frame=valid, ntrees=10, max_depth=5)) 
      print("Time it took to build XGBOOST")
      print(xgboost_time)
      model.xgboost

      pred = predict(model.xgboost, valid)
      perf <- h2o.performance(model.xgboost, valid)
}

doTest("Test",rtest)
