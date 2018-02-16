setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


## Data: 5MM records and 500 features

CreateData <- function(nrows, ncols){
  
  airlines <- h2o.importFile("hdfs://mr-0xd6.0xdata.loc:8020/datasets/airlines_all.05p.csv")
  
  myX <- c("Year", "Month", "DayofMonth", "DayOfWeek", "Distance")
  myY <- "IsDepDelayed"
  
  airlines <- airlines[c(myX, myY)]
  
  new_features <- ncols - ncol(airlines)
  sample_data <- h2o.createFrame(rows = nrows, cols = new_features, categorical_fraction = 0,
                                 seed = 1234, seed_for_column_types = 1234)
  
  new_rows <- nrows - nrow(airlines)
  if(nrows > 0){
    extra_rows <- airlines[c(1:nrows), ]
    airlines <- h2o.rbind(airlines, extra_rows)
  }
  
  airlines <- airlines[c(1:nrows), ]
  full_data <- h2o.cbind(airlines, sample_data)
  
  return(full_data)
}


rtest <- function() {

      if (! "XGBoost" %in% h2o.list_all_extensions()) {
      	 print("XGBoost extension is not present.  Skipping test. . .")
	 return()
      }

      hdfs_name_node = HADOOP.NAMENODE

      create_data_time <- system.time(full_data <- CreateData(5E5, 500))
      # full_data <- h2o.importFile("../data/benchmarks.csv")
      print("Time it took to create the data: ")
      print(create_data_time)

      print("nrow(full_data): ")
      nrow(full_data)

      print("ncol(full_data): ")
      ncol(full_data)

      print("dim(full_data): ")
      dim(full_data)


      myX <- setdiff(colnames(full_data), y = "IsDepDelayed")
      xgboost_time <- system.time(xgboost_model <- h2o.xgboost(y = "IsDepDelayed", x = head(myX, n = 480), training_frame = full_data, model_id = "xgboost", tree_method = "approx"))

      print("Time it took to build XGBoost: ")
      print(xgboost_time)
      model.xgboost

      # TODO: train/test split
      pred = predict(model.xgboost, full_data)
      perf <- h2o.performance(model.xgboost, full_data)
      perf
}

doTest("Test",rtest)

