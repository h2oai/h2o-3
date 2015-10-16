library(h2o)
h2o.init()

## Find and import data into H2O
locate       <- h2o:::.h2o.locate
pathToData   <- locate("smalldata/glrm_test/subject01_walk1.csv")
pathToMissingData <- locate("smalldata/glrm_test/subject01_walk1_miss15.csv")
print("Importing walking gait dataset into H2O...")
gait.hex <- h2o.importFile(path = pathToData, destination_frame = "gait.hex")

## Grab a summary of imported frame
dim(gait.hex)
summary(gait.hex)

#---------------------------------------#
#          Matrix Decomposition         #
#---------------------------------------#
## Basic GLRM using quadratic loss and no regularization (PCA)
gait.glrm <- h2o.glrm(training_frame = gait.hex, x = 2:ncol(gait.hex), k = 5, init = "PlusPlus", loss = "Quadratic",
                      regularization_x = "None", regularization_y = "None", max_iterations = 1000)
gait.glrm
# EDIT: Rename x parameter to cols so less confusing
# EDIT: Include number of numeric and categorical entries in model output

## Decompose training frame into XY with rank k
print("Archetype to feature mapping (Y):")
gait.y <- gait.glrm@model$archetypes
gait.y

# print("Plot first archetype on z-coordinate features")
# feat_cols <- seq(3, ncol(gait.y), by = 3)
# plot(1:length(feat_cols), gait.y[1,feat_cols], xlab = "Feature", ylab = "Archetypal Weight", main = "First Archetype's Z-Coordinate Feature Weights", col = "blue", pch = 19, lty = "solid")
# text(1:length(feat_cols), gait.y[1,feat_cols], labels = colnames(gait.y[1,feat_cols]), cex = 0.7, pos = 3)
# abline(0, 0, lty = "dashed")

print("Plot archetypes on x- vs. y-coordinate features")
gait.y.mat <- as.matrix(gait.y)
x_coords <- seq(1, ncol(gait.y), by = 3)
y_coords <- seq(2, ncol(gait.y), by = 3)
feat_nams <- sapply(colnames(gait.y[,x_coords]), function(nam) { substr(nam, 1, nchar(nam)-1) })
for(k in 1:5) {
  plot(gait.y.mat[k,x_coords], gait.y.mat[k,y_coords], xlab = "X-Coordinate Weight", ylab = "Y-Coordinate Weight", main = paste("Feature Weights of Archetype", k), col = "blue", pch = 19, lty = "solid")
  text(gait.y.mat[k,x_coords], gait.y.mat[k,y_coords], labels = feat_nams, cex = 0.7, pos = 3)
  cat("Press [Enter] to continue")
  line <- readline()
}
# EDIT: Can also do this with original dataset (with one time slice and one row)

print("Projection into archetype space (X):")
gait.x <- h2o.getFrame(gait.glrm@model$representation_name)
head(gait.x)

print("Plot archetypes over time")
time.df <- as.data.frame(gait.hex$Time[1:150])[,1]
gait.x.df <- as.data.frame(gait.x[1:150,])
print(paste0("Plot archetypes over time range [", time.df[1], ",", time.df[2], "]"))
matplot(time.df, gait.x.df, xlab = "Time", ylab = "Archetypal Projection", main = "Archetypes over Time", type = "l", lty = 1, col = 1:5)
legend("topright", legend = colnames(gait.x.df), col = 1:5, pch = 1)

print("Reconstruct data from X and Y")
gait.pred <- predict(gait.glrm, gait.hex)
head(gait.pred)

print(paste0("Plot original and reconstructed L.Acromium.X over time range [", time.df[1], ",", time.df[2], "]"))
lacro.df <- as.data.frame(gait.hex$L.Acromium.X[1:150])
lacro.pred.df <- as.data.frame(gait.pred$reconstr_L.Acromium.X[1:150])
matplot(time.df, cbind(lacro.df, lacro.pred.df), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = 1:2)
legend("topright", legend = c("Original", "Reconstructed"), col = 1:2, pch = 1)

#---------------------------------------#
#        Imputing Missing Values        #
#---------------------------------------#
print("Importing walking gait dataset with missing values into H2O...")
gait.miss <- h2o.importFile(path = pathToMissingData, destination_frame = "gait.miss")

## Grab a summary of imported frame
dim(gait.miss)
summary(gait.miss)

## Basic GLRM using quadratic loss and no regularization (PCA)
gait.glrm2 <- h2o.glrm(training_frame = gait.miss, validation_frame = gait.hex, x = 2:ncol(gait.miss), k = 5, init = "SVD",
                      loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_iterations = 1000, min_step_size = 1e-7)
gait.glrm2
# EDIT: Why is the fit so bad compared to the full dataset model? Check for bugs in algorithm
# Try SVD initialize and add some L2 regularization as well. Want training error to be smaller than in full dataset model.

print("Impute missing data from X and Y")
gait.pred2 <- predict(gait.glrm2, gait.miss)
head(gait.pred2)

print(paste0("Plot original and imputed L.Acromium.X over time range [", time.df[1], ",", time.df[2], "]"))
lacro.df2 <- as.data.frame(gait.hex$L.Acromium.X[1:150])
lacro.pred.df2 <- as.data.frame(gait.pred2$reconstr_L.Acromium.X[1:150])
matplot(time.df, cbind(lacro.df2, lacro.pred.df2), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = c(1,4))
legend("topright", legend = c("Original", "Imputed"), col = c(1,4), pch = 1)

## Plot X's where training data contains missing values
lacro.miss.df2 <- as.data.frame(gait.miss$L.Acromium.X[1:150])
idx_miss <- which(is.na(lacro.miss.df2))
points(time.df[idx_miss], lacro.df2[idx_miss,1], col = 2, pch = 4, lty = 2)
