setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Test k-means clustering on iris and prostate
test.km.scoring <- function() {
  
  seed = .Random.seed[1]
  Log.info(paste0("Random seed used = ", seed))
  
  k = 2
  Log.info("Importing iris dataset...\n")
  iris.hex = h2o.uploadFile( locate("smalldata/iris/iris2.csv"))
  
  Log.info("Take first numeric column and insert NAs...\n")
  iris2 = iris.hex[,2]
  iris2 = h2o.insertMissingValues(iris2, fraction = 0.10)
  
  Log.info("Run K-means with standardization off and only 2 centers...\n")
  km_iris = h2o.kmeans(iris2, k = 2, standardize = F, seed = seed)
  
  Log.info("Run manually calculation of model metrics to compare with H2O model metrics...\n") 
  mean0 = mean(iris2, na.rm = T)
  center1 = km_iris@model$centers[,2][1]
  center2 = km_iris@model$centers[,2][2]
  
  iris2_r = as.data.frame(iris2)
  iris2_r[,"Sepal.Length_Imputed"] = ifelse(is.na(iris2_r[,1]), mean0, iris2_r[,1])
  iris2_r[,"Dist_To_Center_1"] = abs(iris2_r[,"Sepal.Length_Imputed"] - center1)
  iris2_r[,"Dist_To_Center_2"] = abs(iris2_r[,"Sepal.Length_Imputed"] - center2)
  iris2_r[,"Cluster_ID"] = ifelse(iris2_r[,"Dist_To_Center_1"] < iris2_r[,"Dist_To_Center_2"], 1, 2 )
  
  set1 = iris2_r [ iris2_r$Cluster_ID == 1,]
  set2 = iris2_r [ iris2_r$Cluster_ID == 2,]
  set1[,"Squared_Error"] = (set1$Dist_To_Center_1)^2
  set2[,"Squared_Error"] = (set2$Dist_To_Center_2)^2
  
  ## Check that the cluster size is the same
  size_manual = table(iris2_r[,"Cluster_ID"])
  size_h2o = h2o.centroid_stats(km_iris)$size
  checkEqualsNumeric(size_manual, size_h2o, tolerance = 1e-03)
  ## Check the total sum of squares
  totss_manual = sum(abs(iris2_r$Sepal.Length_Imputed - mean0)^2)
  totss_h2o = h2o.totss(km_iris)
  checkEqualsNumeric(totss_manual, totss_h2o, tolerance = 1e-03)
  ## Check the within cluster sum of squares
  withinss_manual = c(sum(set1$Squared_Error), sum(set2$Squared_Error))
  withinss_h2o = h2o.centroid_stats(km_iris)$within_cluster_sum_of_squares
  checkEqualsNumeric(withinss_manual, withinss_h2o, tolerance = 1e-03)
  
## ---------------------------------------------------------------------------------------------------- ##
  
  Log.info("Importing prostate dataset...\n")
  prostate.hex = h2o.uploadFile( locate("smalldata/prostate/prostate.csv"))
  prostate.hex = prostate.hex[-1]

  check_kmeans_metrics <- function(model) {
    centroid_sizes_train = h2o.centroid_stats(model, train = T)
    centroid_sizes_valid = h2o.centroid_stats(model, valid = T)
    checkEqualsNumeric(centroid_sizes_train$size, centroid_sizes_valid$size, msg = "The sizes of the centroids is not equal.", tolerance = 1e-3)
    checkEqualsNumeric(centroid_sizes_train$within_cluster_sum_of_squares, centroid_sizes_valid$within_cluster_sum_of_squares, tolerance = 1e-3)
    checkEqualsNumeric(h2o.totss(model, train = T), h2o.totss(model, valid = T), msg = "The total set sum of square is not equal.", tolerance = 1e-3)
    checkEqualsNumeric(h2o.betweenss(model, train = T), h2o.betweenss(model, valid = T), msg = "The between set sum of square is not equal.", tolerance = 1e-3)
    checkEqualsNumeric(h2o.tot_withinss(model, train = T), h2o.tot_withinss(model, valid = T), msg = "The within set sum of square is not equal.", tolerance = 1e-3)
  }
  
  Log.info("Build K-means model with the original prostate data...\n")
  k = 5
  km_1 = h2o.kmeans(prostate.hex, k = k, validation_frame = prostate.hex, standardize = F, seed = seed)
  check_kmeans_metrics(km_1)
  
  Log.info("Set columns CAPSULE and RACE to enum...\n")
  prostate.hex[, "CAPSULE"] = as.factor(prostate.hex[, "CAPSULE"])
  prostate.hex[, "RACE"] = as.factor(prostate.hex[, "RACE"])
  Log.info("Build K-means model with data with numerics and categoricals...\n")
  km_2 = h2o.kmeans(prostate.hex, k = k, validation_frame = prostate.hex, standardize = F, seed = seed)
  check_kmeans_metrics(km_2)
  Log.info("Insert NAs into prostate dataset with numerics and categoricals...\n")
  prostate.hex = h2o.insertMissingValues(prostate.hex, fraction = 0.1, seed = seed)
  Log.info("Build K-means model with data with 10% missing values...\n")
  km_3 = h2o.kmeans(prostate.hex, k = k, validation_frame = prostate.hex, standardize = F, seed = seed)
  check_kmeans_metrics(km_3)
  Log.info("Build K-means model with standardization turned on...\n")
  km_4 = h2o.kmeans(prostate.hex, k = k, validation_frame = prostate.hex, standardize = T, seed = seed)
  check_kmeans_metrics(km_4)
  
}

doTest("K-means Scoring Test: Prostate Data", test.km.scoring)
