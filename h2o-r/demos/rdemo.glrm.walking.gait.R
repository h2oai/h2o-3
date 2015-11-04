library(h2o)
h2o.init(nthreads = -1, max_mem_size = "2G")

## Find and import data into H2O
locate     <- h2o:::.h2o.locate
pathToData <- locate("smalldata/glrm_test/subject01_walk1.csv")
pathToMissingData <- locate("smalldata/glrm_test/subject01_walk1_miss15.csv")
print("Importing walking gait dataset into H2O...")
gait.hex <- h2o.importFile(path = pathToData, destination_frame = "gait.hex")

## Grab a summary of imported frame
dim(gait.hex)
summary(gait.hex)

print("Plot first row on x- vs. y-coordinate locations")
gait.row <- as.matrix(gait.hex[1,2:ncol(gait.hex)])
x_coords <- seq(1, ncol(gait.row), by = 3)
y_coords <- seq(2, ncol(gait.row), by = 3)
feat_nams <- sapply(colnames(gait.row), function(nam) { substr(nam, 1, nchar(nam)-2) })
feat_nams <- as.character(feat_nams[x_coords])
plot(gait.row[x_coords], gait.row[y_coords], xlab = "X-Coordinate", ylab = "Y-Coordinate", main = paste("Location of Body Parts at Time 0"), col = "blue", pch = 19, lty = "solid")
text(gait.row[x_coords], gait.row[y_coords], labels = feat_nams, cex = 0.7, pos = 3)

#---------------------------------------#
#          Matrix Decomposition         #
#---------------------------------------#
## Basic GLRM using quadratic loss and no regularization (PCA)
gait.glrm <- h2o.glrm(training_frame = gait.hex, cols = 2:ncol(gait.hex), k = 10, loss = "Quadratic", 
                      regularization_x = "None", regularization_y = "None", max_iterations = 1000)
gait.glrm

print("Plot objective function value each iteration")
plot(gait.glrm)

## Decompose training frame into XY with rank k
print("Archetype to feature mapping (Y):")
gait.y <- gait.glrm@model$archetypes
gait.y

print("Plot archetypes on x- vs. y-coordinate features")
gait.y.mat <- as.matrix(gait.y)
x_coords <- seq(1, ncol(gait.y), by = 3)
y_coords <- seq(2, ncol(gait.y), by = 3)
feat_nams <- sapply(colnames(gait.y), function(nam) { substr(nam, 1, nchar(nam)-1) })
feat_nams <- as.character(feat_nams[x_coords])
for(k in 1:10) {
  plot(gait.y.mat[k,x_coords], gait.y.mat[k,y_coords], xlab = "X-Coordinate Weight", ylab = "Y-Coordinate Weight", main = paste("Feature Weights of Archetype", k), col = "blue", pch = 19, lty = "solid")
  text(gait.y.mat[k,x_coords], gait.y.mat[k,y_coords], labels = feat_nams, cex = 0.7, pos = 3)
  cat("Press [Enter] to continue")
  line <- readline()
}

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
matplot(time.df, cbind(lacro.df, lacro.pred.df), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = c(1,4))
legend("topright", legend = c("Original", "Reconstructed"), col = c(1,4), pch = 1)

#---------------------------------------#
#        Imputing Missing Values        #
#---------------------------------------#
print("Importing walking gait dataset with missing values into H2O...")
gait.miss <- h2o.importFile(path = pathToMissingData, destination_frame = "gait.miss")

## Grab a summary of imported frame
dim(gait.miss)
summary(gait.miss)
sum(is.na(gait.miss))   # Total number of missing values

## Basic GLRM using quadratic loss and no regularization (PCA)
gait.glrm2 <- h2o.glrm(training_frame = gait.miss, validation_frame = gait.hex, cols = 2:ncol(gait.miss), k = 10, init = "SVD", svd_method = "GramSVD",
                      loss = "Quadratic", regularization_x = "None", regularization_y = "None", max_iterations = 2000, min_step_size = 1e-6)
gait.glrm2

print("Plot objective function value each iteration")
plot(gait.glrm2)

print("Impute missing data from X and Y")
gait.pred2 <- predict(gait.glrm2, gait.miss)
head(gait.pred2)
sum(is.na(gait.pred2))   # No missing values!

print(paste0("Plot original and imputed L.Acromium.X over time range [", time.df[1], ",", time.df[2], "]"))
lacro.pred.df2 <- as.data.frame(gait.pred2$reconstr_L.Acromium.X[1:150])
matplot(time.df, cbind(lacro.df, lacro.pred.df2), xlab = "Time", ylab = "X-Coordinate of Left Acromium", main = "Position of Left Acromium over Time", type = "l", lty = 1, col = c(1,4))
legend("topright", legend = c("Original", "Imputed"), col = c(1,4), pch = 1)

## Mark points where training data contains missing values
lacro.miss.df <- as.data.frame(gait.miss$L.Acromium.X[1:150])
idx_miss <- which(is.na(lacro.miss.df))
points(time.df[idx_miss], lacro.df[idx_miss,1], col = 2, pch = 4, lty = 2)
