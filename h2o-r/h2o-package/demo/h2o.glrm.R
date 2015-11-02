# This is a demo of H2O's GLRM function
# It imports a data set, parses it, and prints a summary
# Then, it runs GLRM with k = 5 centers on a subset of characteristics
# Note: This demo runs H2O on localhost:54321
library(h2o)
h2o.init()

gait.hex <- h2o.uploadFile(path = system.file("extdata", "walking.csv", package="h2o"), destination_frame = "gait")
summary(gait.hex)
gait.glrm <- h2o.glrm(training_frame = gait.hex, cols = 2:ncol(gait.hex), k = 5, init = "PlusPlus", loss = "Quadratic", 
                      regularization_x = "None", regularization_y = "None", max_iterations = 1000)
print(gait.glrm)

# Archetype to feature mapping
gait.y <- gait.glrm@model$archetypes
print(gait.y)

# Plot first archetype on z-coordinate features
feat_cols <- seq(3, ncol(gait.y), by = 3)
plot(1:length(feat_cols), gait.y[1,feat_cols], xlab = "Feature", ylab = "Archetypal Weight", main = "First Archetype's Z-Coordinate Feature Weights", col = "blue", pch = 19, lty = "solid")
text(1:length(feat_cols), gait.y[1,feat_cols], labels = colnames(gait.y[1,feat_cols]), cex = 0.7, pos = 3)
abline(0, 0, lty = "dashed")
