


test.pub_542_kmeans_mismatched_size <- function() {
  Log.info("Importing iris_wheader.csv data...\n")
  iris.dat <- read.csv(locate("smalldata/iris/iris_wheader.csv"), header = TRUE)
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  iris.sum <- summary(iris.hex)
  print(iris.sum)
  
  Log.info("Run k-means on all columns except 'class' and predict on training set")
  myCols <- setdiff(names(iris.hex), "class")
  km <- h2o.kmeans(iris.hex, x = myCols, k = 3, max_iterations = 20, standardize = TRUE)
  pred_km <- predict(km, iris.hex)
  
  Log.info("Compare cluster sizes with assignments from prediction")
  pred_km.df <- as.data.frame(pred_km)
  pred_size <- table(pred_km.df)
  expect_equal(getClusterSizes(km), as.numeric(pred_size))
  
  Log.info("Compare H2O's predictions with R's assignments using l2 norm")
  closest <- function(row, centers) {
    l2norm = rep(0, nrow(centers))
    for(i in 1:nrow(centers)) {
      l2norm[i] = sum((row - centers[i,])^2)
    }
    which.min(l2norm)
  }
  iris.sub.std <- scale(subset(iris.dat, select = myCols), center = TRUE, scale = TRUE)
  pred_km.std.r <- apply(iris.sub.std, 1, closest, getCentersStd(km))
  expect_equal(as.numeric(pred_km.df[,1]+1), as.numeric(pred_km.std.r))
  
  
}

doTest("PUBDEV-542: K-means cluster sizes differ from labels generated during prediction", test.pub_542_kmeans_mismatched_size)
