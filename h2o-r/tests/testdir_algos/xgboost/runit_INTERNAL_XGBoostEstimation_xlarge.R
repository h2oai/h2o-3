setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


CreateData <- function(nrows, ncols){
  hdfs_name_node = HADOOP.NAMENODE
  hdfs_data_file = "/datasets/airlines_all.05p.csv"
  airlines.url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
  airlines <- h2o.importFile(airlines.url)

  myX <- c("Year", "Month", "DayofMonth", "DayOfWeek", "Distance")
  myY <- "IsDepDelayed"

  airlines <- h2o.head(airlines[c(myX, myY)], nrows)

  new_features <- ncols - ncol(airlines)
  random_data <- h2o.createFrame(rows = nrows, cols = new_features, categorical_fraction = 0,
                                 seed = 1234, seed_for_column_types = 1234)

  full_data <- h2o.cbind(airlines, random_data)
  return(full_data)
}


## Data: 500k records and 500 features
xgboost.wide.test <- function() {

  if (! "XGBoost" %in% h2o.list_all_extensions()) {
    print("XGBoost extension is not present.  Skipping test. . .")
    return()
  }

  create_data_time <- system.time(full_data <- CreateData(5E5, 500))

  print("Time it took to create the data: ")
  print(create_data_time)

  print("nrow(full_data): ")
  nrow(full_data)

  print("ncol(full_data): ")
  ncol(full_data)

  print("dim(full_data): ")
  dim(full_data)

  xgboost_time <- system.time(
    xgboost_model <- h2o.xgboost(y = "IsDepDelayed", training_frame = full_data, tree_method = "hist")
  )

  print("Time it took to build XGBoost: ")
  print(xgboost_time)
  model.xgboost
}

doTest("Test training XGBoost on 500k records and 500 features", xgboost.wide.test)