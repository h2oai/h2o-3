# Here are some examples of how to create variants of the h2o algorithm wrappers.

library(h2o)

# This will create h2o.glm wrappers with different alphas.
create_h2o_glm_wrappers <- function(alphas = c(0.0, 0.5, 1.0)) {
  for (i in seq(length(alphas))) {
    alpha <- alphas[i]
    body <- sprintf(' <- function(..., alpha = %s) h2o.glm.wrapper(..., alpha = alpha)', alpha)
    eval(parse(text = paste('h2o.glm.', i, body, sep = '')), envir = .GlobalEnv)
  }
}
create_h2o_glm_wrappers()

# This is another way to create wrappers:
h2o.randomForest.1 <- function(..., ntrees = 1000, nbins = 100, seed = 1) h2o.randomForest.wrapper(..., ntrees = ntrees, nbins = nbins, seed = seed)
h2o.deeplearning.1 <- function(..., hidden = c(500,500), activation = "Rectifier", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
h2o.deeplearning.2 <- function(..., hidden = c(200,200,200), activation = "Tanh", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)

learner <- c("h2o.randomForest.1", "h2o.deeplearning.1", "h2o.deeplearning.2")
