library(h2o)
h2o.init()

# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 = F

locate_source <- function(s) {
  if (data_source_is_s3)
    myPath <- paste0("s3n://h2o-public-test-data/", s)
  else
    myPath <- h2o:::.h2o.locate(s)
}

readMultiChar <- function(fileName, separators) {
  data <- readLines(con <- file(fileName))
  close(con)
  records <- sapply(data, strsplit, split = separators)
  dataFrame <- data.frame(t(sapply(records,c)))
  rownames(dataFrame) <- 1: nrow(dataFrame)
  return(as.data.frame(dataFrame, stringsAsFactors = FALSE))
}

SEED <- sample(.Machine$integer.max, 1)
k_dim <- 15
frac_known <- 0.10

print("Import and parse MovieLens user-movie rating matrix...")
ratings <- h2o.uploadFile(locate_source("smalldata/demos/movielens_1m.zip"), header = TRUE)
print(summary(ratings))

print(paste("Run GLRM on user rating matrix with k =", k_dim, "and loss = Quadratic"))
fitH2O <- h2o.glrm(ratings, x = 2:ncol(ratings), k = k_dim, ignore_const_cols = FALSE, transform = "NONE", init = "PlusPlus", loss = "Quadratic", regularization_x = "Quadratic", regularization_y = "Quadratic", gamma_x = 0.15, gamma_y = 0.15, max_iterations = 1000, seed = SEED)
print(fitH2O)

print("Archetype to full movie mapping (Y):")
fitY <- fitH2O@model$archetypes
print(head(fitY))

# print("Plot first archetype on a subset of movies")
# feat_cols <- 1:50
# movies <- readMultiChar(locate_source("smalldata/demos/movies.dat"), separators = "::")
# plot(1:length(feat_cols), fitY[1,feat_cols], xlab = "Feature", ylab = "Archetypal Weight", main = "First Archetype's Movie Weights", col = "blue", pch = 19, lty = "solid")
# text(1:length(feat_cols), fitY[1,feat_cols], labels = movies[feat_cols,2], cex = 0.7, pos = 3)
# plot(1:length(feat_cols), fitY[1,feat_cols], xlab = "Feature", ylab = "Archetypal Weight", main = "First Archetype's Movie Weights by Genre", col = "blue", pch = 19, lty = "solid")
# text(1:length(feat_cols), fitY[1,feat_cols], labels = movies[feat_cols,3], cex = 0.7, pos = 3)

print("Embedding of users into movie archetypes (X):")
fitX <- h2o.getFrame(fitH2O@model$loading_key$name)
print(head(fitX))

print("Impute missing ratings from XY decomposition")
pred <- predict(fitH2O, ratings)
print(head(pred))

print(paste("Construct new user with", 100*frac_known, "% known movie ratings:"))
num_movies <- ncol(ratings)-1    # First column is user ID
num_known <- trunc(frac_known * num_movies)
idx_known <- sample(1:num_movies, num_known)
val_known <- sample(1:5, num_known, replace = TRUE)
names(val_known) <- paste("Movie", idx_known, sep = "_")
print(val_known)

print("Upload new user rating vector to H2O")
ratings_new.df <- as.numeric(rep(NA, num_movies))
ratings_new.df[idx_known] <- val_known
ratings_new <- as.h2o(t(data.frame(ratings_new.df)))

print(paste("Run GLRM on new user rating vector with k =", k_dim, "and initial Y from previous model"))
fitH2O_new <- h2o.glrm(ratings_new, k = k_dim, ignore_const_cols = FALSE, transform = "NONE", user_y = fitY, init = "User", loss = "Quadratic", regularization_x = "Quadratic", regularization_y = "Quadratic", gamma_x = 0.15, gamma_y = 0.15, max_iterations = 1000, seed = SEED)
print(fitH2O_new)

print("Embedding of new user into movie archetypes (X):")
fitX_new <- h2o.getFrame(fitH2O_new@model$loading_key$name)
print(fitX_new)

print("Impute new user's missing ratings from XY decomposition")
pred_new <- predict(fitH2O_new, ratings_new)
print(pred_new)