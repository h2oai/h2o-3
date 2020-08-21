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
      parse_time <- system.time(data.hex <- h2o.importFile("/mnt/0xcustomer-datasets/c25/df_h2o.csv", header = TRUE))
      print("Time it took to parse")
      print(parse_time)

      colNames = {}
      for(col in names(data.hex)) {
          colName <- if(is.na(as.numeric(col))) col else paste0("C", as.character(col))
	  colNames = append(colNames, colName)
      }

      colNames[1] <- "C1"
      names(data.hex) <- colNames

      myY = colNames[1] 
      myX = setdiff(names(data.hex), myY)

      # Start modeling
      # XGBOOST on original dataset
      xgboost_time <- system.time(data1.xgboost <-  h2o.xgboost(x = myX, y = myY, training_frame = data.hex, ntrees = 10, max_depth = 5, distribution = "multinomial"))
      print("Time it took to build XGBoost")
      print(xgboost_time)
      data1.xgboost 
      
}

doTest("Test",rtest)
