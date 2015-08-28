## Set your working directory
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

## Load library and initialize h2o
library(h2o)
cat("Launching H2O and initializing connection object...")
conn <- h2o.init(nthreads = -1)

## Find and import data into H2O
locate       <- h2o:::.h2o.locate
pathToData   <- locate("smalldata/glrm_test/subject01_walk1.csv")
pathToMissingData <- locate("smalldata/glrm_test/subject01_walk1_miss15.csv")
cat("Importing walking gait dataset into H2O...")
gait.hex <- h2o.importFile(path = pathToData, destination_frame = "gait.hex")

## Grab a summary of imported frame
summary(gait.hex)

#---------------------------------------#
#          Matrix Decomposition         #
#---------------------------------------#
## Basic GLRM using quadratic loss and no regularization (PCA)
gait.glrm <- h2o.glrm(training_frame = gait.hex, x = 2:ncol(gait.hex), k = 5, init = "PlusPlus", loss = "Quadratic", 
                      regularization_x = "None", regularization_y = "None", max_iterations = 1000)
gait.glrm

## Decompose training frame into XY with rank k
cat("Archetype to feature mapping (Y):")
gait.y <- gait.glrm@model$archetypes
gait.y

cat("Plot first archetype on z-coordinate features")
feat_cols <- seq(3, ncol(gait.y), by = 3)
plot(1:length(feat_cols), gait.y[1,feat_cols], xlab = "Feature", ylab = "Archetypal Weight", main = "First Archetype's Z-Coordinate Feature Weights", col = "blue", pch = 19, lty = "solid")
text(1:length(feat_cols), gait.y[1,feat_cols], labels = colnames(gait.y[1,feat_cols]), cex = 0.7, pos = 3)
abline(0, 0, lty = "dashed")

cat("Projection into archetype space (X):")
gait.x <- h2o.getFrame(gait.glrm@model$loading_key$name)
head(gait.x)

time.df <- as.data.frame(gait.hex$Time[1:150])[,1]
gait.x.df <- as.data.frame(gait.x[1:150,])
cat("Plot archetypes over time range [", time.df[1], ",", time.df[2], "]", sep = "")
matplot(time.df, gait.x.df, xlab = "Time", ylab = "Archetypal Projection", main = "Archetypes over Time", type = "l", lty = 1, col = 1:5)
legend("topright", legend = colnames(gait.x.df), col = 1:5, pch = 1)

# cat("Reconstruct data from matrix product XY")
# gait.pred <- predict(gait.glrm, gait.hex)
# head(gait.pred)
# 
# cat("Plot original and reconstructed L.Acromium.X over time range [", time.df[1], ",", time.df[2], "]", sep = "")
# lacro.df <- as.data.frame(gait.hex$L.Acromium.X[1:150])
# lacro.pred.df <- as.data.frame(gait.pred$reconstr_L.Acromium.X[1:150])
# matplot(time.df, cbind(lacro.df, lacro.pred.df), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = 1:2)
# legend("topright", legend = c("Original", "Reconstructed"), col = 1:2, pch = 1)

#---------------------------------------#
#        Imputing Missing Values        #
#---------------------------------------#
cat("Importing walking gait dataset with missing values into H2O...")
gait.miss <- h2o.importFile(path = pathToMissingData, destination_frame = "gait.miss")

## Grab a summary of imported frame
summary(gait.miss)

## Basic GLRM using quadratic loss and no regularization (PCA)
gait.glrm2 <- h2o.glrm(training_frame = gait.miss, validation_frame = gait.hex, x = 2:ncol(gait.miss), k = 15, init = "PlusPlus", 
                      loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_iterations = 500, min_step_size = 1e-7)
gait.glrm2

cat("Impute missing data from X and Y")
gait.pred2 <- predict(gait.glrm2, gait.miss)
head(gait.pred2)

cat("Plot original and imputed L.Acromium.X over time range [", time.df[1], ",", time.df[2], "]", sep = "")
lacro.df2 <- as.data.frame(gait.hex$L.Acromium.X[1:150])
lacro.pred.df2 <- as.data.frame(gait.pred2$reconstr_L.Acromium.X[1:150])
matplot(time.df, cbind(lacro.df2, lacro.pred.df2), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = 1:2)
legend("topright", legend = c("Original", "Imputed"), col = 1:2, pch = 1)
