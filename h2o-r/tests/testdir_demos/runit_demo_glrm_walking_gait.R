setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Connect to a cluster
# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 = F

locate_source <- function(s) {
  if (data_source_is_s3)
    myPath <- paste0("s3n://h2o-public-test-data/", s)
  else
    myPath <- locate(s)
}

test.walking_gait.demo <- function(conn) {
  Log.info("Import and parse walking gait data...")
  gait.hex <- h2o.importFile(locate("smalldata/glrm_test/subject01_walk1.csv"), destination_frame = "gait.hex")
  print(summary(gait.hex))
  
  Log.info("Basic GLRM using quadratic loss and no regularization (PCA)")
  gait.glrm <- h2o.glrm(training_frame = gait.hex, x = 2:ncol(gait.hex), k = 5, init = "PlusPlus", loss = "Quadratic", 
                        regularization_x = "None", regularization_y = "None", max_iterations = 1000)
  print(gait.glrm)
  
  Log.info("Archetype to feature mapping (Y):")
  gait.y <- gait.glrm@model$archetypes
  print(gait.y)
  
  Log.info("Plot first archetype on z-coordinate features")
  feat_cols <- seq(3, ncol(gait.y), by = 3)
  plot(1:length(feat_cols), gait.y[1,feat_cols], xlab = "Feature", ylab = "Archetypal Weight", main = "First Archetype's Z-Coordinate Feature Weights", col = "blue", pch = 19, lty = "solid")
  text(1:length(feat_cols), gait.y[1,feat_cols], labels = colnames(gait.y[1,feat_cols]), cex = 0.7, pos = 3)
  abline(0, 0, lty = "dashed")
  
  Log.info("Projection into archetype space (X):")
  gait.x <- h2o.getFrame(gait.glrm@model$loading_key$name)
  print(head(gait.x))
  
  time.df <- as.data.frame(gait.hex$Time[1:150])[,1]
  gait.x.df <- as.data.frame(gait.x[1:150,])
  Log.info(paste0("Plot archetypes over time range [", time.df[1], ",", time.df[2], "]"))
  matplot(time.df, gait.x.df, xlab = "Time", ylab = "Archetypal Projection", main = "Archetypes over Time", type = "l", lty = 1, col = 1:5)
  legend("topright", legend = colnames(gait.x.df), col = 1:5, pch = 1)
  
  # Log.info("Reconstruct data from matrix product XY")
  # gait.pred <- predict(gait.glrm, gait.hex)
  # print(head(gait.pred))
  # 
  # Log.info(paste0("Plot original and reconstructed L.Acromium.X over time range [", time.df[1], ",", time.df[2], "]"))
  # lacro.df <- as.data.frame(gait.hex$L.Acromium.X[1:150])
  # lacro.pred.df <- as.data.frame(gait.pred$reconstr_L.Acromium.X[1:150])
  # matplot(time.df, cbind(lacro.df, lacro.pred.df), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = 1:2)
  # legend("topright", legend = c("Original", "Reconstructed"), col = 1:2, pch = 1)
  
  Log.info("Import and parse walking gait data with missing values...")
  gait.miss <- h2o.importFile(locate("smalldata/glrm_test/subject01_walk1_miss15.csv"), destination_frame = "gait.miss")
  print(summary(gait.miss))
  
  Log.info("Basic GLRM using quadratic loss and no regularization (PCA)")
  gait.glrm2 <- h2o.glrm(training_frame = gait.miss, validation_frame = gait.hex, x = 2:ncol(gait.miss), k = 15, init = "PlusPlus", 
                         loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_iterations = 500, min_step_size = 1e-7)
  print(gait.glrm2)
  
  Log.info("Impute missing data from X and Y")
  gait.pred2 <- predict(gait.glrm2, gait.miss)
  print(head(gait.pred2))
  
  Log.info(paste0("Plot original and imputed L.Acromium.X over time range [", time.df[1], ",", time.df[2], "]"))
  lacro.df2 <- as.data.frame(gait.hex$L.Acromium.X[1:150])
  lacro.pred.df2 <- as.data.frame(gait.pred2$reconstr_L.Acromium.X[1:150])
  matplot(time.df, cbind(lacro.df2, lacro.pred.df2), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = 1:2)
  legend("topright", legend = c("Original", "Imputed"), col = 1:2, pch = 1)
  testEnd()
}

doTest("Test out Walking Gait Demo", test.walking_gait.demo)
