library(h2o)
h2o.init()

## Function for reading movie names into R
readMultiChar <- function(fileName, separators) {
  data <- readLines(con <- file(fileName))
  close(con)
  records <- sapply(data, strsplit, split = separators)
  dataFrame <- data.frame(t(sapply(records,c)))
  rownames(dataFrame) <- 1: nrow(dataFrame)
  return(as.data.frame(dataFrame, stringsAsFactors = FALSE))
}

## Find and import data into H2O
pathToData   <- h2o:::.h2o.locate("smalldata/demos/movielens_1m.zip")
pathToMovies <- h2o:::.h2o.locate("smalldata/demos/movies.dat")
# movieInfo    <- readMultiChar(pathToMovies, separators = "::")
print("Importing MovieLens 1M dataset into H2O...")
ratings.hex <- h2o.importFile(path = pathToData, header = TRUE, destination_frame = "ratings.hex")

## Grab a summary of imported frame
summary(ratings.hex)

#---------------------------------------#
#          Matrix Decomposition         #
#---------------------------------------#
## Basic GLRM with quadratic loss and regularization
ratings.glrm <- h2o.glrm(ratings.hex, cols = 2:ncol(ratings.hex), k = 15, ignore_const_cols = FALSE, transform = "NONE",
                         init = "PlusPlus", loss = "Quadratic", regularization_x = "Quadratic", regularization_y = "Quadratic",
                         gamma_x = 0.15, gamma_y = 0.15, max_iterations = 1000)
ratings.glrm

## Decompose training frame into XY with rank k
print("Archetype to movie mapping (Y):")
ratings.y <- ratings.glrm@model$archetypes
ratings.y

print("Plot first archetype on a subset of movies")
movie_idx <- 1:50
plot(1:length(movie_idx), ratings.y[1,movie_idx], xlab = "Movie", ylab = "Archetypal Weight", main = "First Archetype's Movie Weights", col = "blue", pch = 19, lty = "solid")
# text(1:length(movie_idx), ratings.y[1,movie_idx], labels = movieInfo[1:50,2], cex = 0.7, pos = 3)
plot(1:length(movie_idx), ratings.y[1,movie_idx], xlab = "Movie", ylab = "Archetypal Weight", main = "First Archetype's Movie Weights by Genre", col = "blue", pch = 19, lty = "solid")
# text(1:length(movie_idx), ratings.y[1,movie_idx], labels = movieInfo[1:50,3], cex = 0.7, pos = 3)

# print("Plot subset of movies in 2-dimensional archetype space")
# movie_idx <- 1:20
# plot(as.numeric(ratings.y[1,movie_idx]), as.numeric(ratings.y[2,movie_idx]), xlab = "Archetype 1", ylab = "Archetype 2", main = "Movies in Subspace Spanned by Archetypes 1 and 2", col = "blue", pch = 19, lty = "solid")
# text(as.numeric(ratings.y[1,movie_idx]), as.numeric(ratings.y[2,movie_idx]), labels = movieInfo[1:25,2], cex = 0.7, pos = 3)
# plot(as.numeric(ratings.y[1,movie_idx]), as.numeric(ratings.y[2,movie_idx]), xlab = "Archetype 1", ylab = "Archetype 2", main = "Movies in Subspace Spanned by Archetypes 1 and 2", col = "blue", pch = 19, lty = "solid")
# text(as.numeric(ratings.y[1,movie_idx]), as.numeric(ratings.y[2,movie_idx]), labels = movieInfo[1:25,3], cex = 0.7, pos = 3)

print("Projection of users into archetype space (X):")
ratings.x <- h2o.getFrame(ratings.glrm@model$representation_name)
head(ratings.x)

print("Impute missing ratings from X and Y")
pred <- predict(ratings.glrm, ratings.hex)
head(pred)

#---------------------------------------#
#           New User Ratings            #
#---------------------------------------#
## Introduce new user with subset of movie ratings
print("Construct new user with 10% known movie ratings")
num_movies <- ncol(ratings.hex)-1    # First column is user ID
num_known <- trunc(0.1 * num_movies)
idx_known <- sample(1:num_movies, num_known)
val_known <- sample(1:5, num_known, replace = TRUE)
names(val_known) <- paste("Movie", idx_known, sep = "_")
val_known

print("Upload new user rating vector to H2O")
ratings_new.df <- as.numeric(rep(NA, num_movies))
ratings_new.df[idx_known] <- val_known
ratings_new.hex <- as.h2o(t(data.frame(ratings_new.df)))

## Run GLRM on new user rating vector with initial Y from previous model
ratings_new.glrm <- h2o.glrm(ratings_new.hex, k = 15, ignore_const_cols = FALSE, transform = "NONE",
                             init = "User", user_y = ratings.y, loss = "Quadratic",
                             regularization_x = "Quadratic", regularization_y = "Quadratic",
                             gamma_x = 0.15, gamma_y = 0.15, max_iterations = 1000)
ratings_new.glrm

print("Projection of new user into archetype space (X):")
ratings_new.x <- h2o.getFrame(ratings_new.glrm@model$representation_name)
ratings_new.x

print("Impute new user's missing ratings from X and Y")
pred_new <- predict(ratings_new.glrm, ratings_new.hex)
pred_new
