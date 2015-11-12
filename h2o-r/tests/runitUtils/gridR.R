# Validate given models' parameters against expected values
expect_model_param <- function(models, attribute_name, expected_values) {
  params <- unique(lapply(models, function(model) { model@allparameters[[attribute_name]] } ))
  expect_equal(length(params), length(expected_values))
  Log.info(paste0("params: ", paste(params, collapse=",")))
  Log.info(paste0("expected_values: ", paste(expected_values, collapse=",")))
  expect_true(all(params %in% expected_values))
  expect_true(all(expected_values %in% params))
}

#' Constructs a named list of gridable parameters and their respective values, which will eventually be passed to
#' h2o.grid as the hyper_params argument. The  grid parameters, and their associated values, are randomly selected.
#' @param algo A string {"gbm", "randomForest", "deeplearning", "kmeans", "glm", "naiveBayes", "pca"}
#' @param ncols Used for mtries selection or k (pca)
#' @param nrows Used for k (pca)
#' @return A named list of gridable parameters and their respective values
makeRandomGridSpace <- function(algo,ncols=NULL,nrows=NULL) {
  grid_space <- list()
  if ( algo == "gbm" || algo == "drf" || algo == "randomForest") {
    if ( sample(0:1,1) ) { grid_space$ntrees <- sample(1:5, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$max_depth <- sample(1:5, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$min_rows <- sample(1:10, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$nbins <- sample(2:20, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$nbins_cats <- sample(2:1024, sample(2:3,1)) }
  }
  if ( algo == "gbm" ) {
    if ( sample(0:1,1) ) { grid_space$learn_rate <- round(1e-2*runif(sample(2:3,1)),6) }
    grid_space$distribution <- sample(c('bernoulli','multinomial','gaussian','poisson','tweedie','gamma'), 1)
  }
  if ( algo == "drf" || algo == "randomForest") {
    if ( sample(0:1,1) ) { grid_space$mtries <- sample(2:ncols, sample(2:3,1)) }
    grid_space$sample_rate <- round(runif(sample(2:3,1)),6)
  }
  if ( algo == "deeplearning" ) {
    if ( sample(0:1,1) ) { grid_space$activation <- sample(c("Rectifier", "Tanh", "TanhWithDropout",
                                                            "RectifierWithDropout", "MaxoutWithDropout"),
                                                          sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$l2 <- round(1e-3*runif(sample(2:3,1)),6) }
    if ( sample(0:1,1) ) { grid_space$hidden <- list(rep(sample(10:50,1),sample(2:3,1)), rep(sample(10:50,1),sample(2:3,1)))}
    grid_space$distribution <- sample(c('bernoulli','multinomial','gaussian','poisson','tweedie','gamma'), 1)
  }
  if ( algo == "kmeans" ) {
    if ( sample(0:1,1) ) { grid_space$max_iterations <- sample(1:1000, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$standardize <- c(TRUE, FALSE) }
    if ( sample(0:1,1) ) { grid_space$seed = sample(1:1000, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$init = sample(c('Random','PlusPlus','Furthest'), sample(2:3,1)) }
    grid_space$k <- sample(1:10, 1)
  }
  if ( algo == "glm" ) {
    lambda <- 0
    if ( sample(0:1,1) ) { grid_space$alpha <- lapply(round(runif(sample(2:3,1)),6), function (x) x) }
    grid_space$family <- sample(c('binomial','gaussian','poisson','tweedie','gamma'), 1)
    if ( grid_space$family == "tweedie" ) {
      if ( sample(0:1,1) ) {
        grid_space$tweedie_variance_power <- round(runif(sample(2:3,1))+1,6)
        grid_space$tweedie_link_power <- 1 - grid_space$tweedie_variance_power
      }
    }
  }
  if ( algo == "naiveBayes" ) {
    grid_space$laplace <- 0
    if ( sample(0:1,1) ) { grid_space$laplace <- round(runif(1)+sample(0:10,sample(2:3,1)),6) }
    if ( sample(0:1,1) ) { grid_space$min_sdev <- round(runif(sample(2:3,1)),6) }
    if ( sample(0:1,1) ) { grid_space$eps_sdev <- round(runif(sample(2:3,1)),6) }
  }
  if ( algo == "pca" ) {
    if ( sample(0:1,1) ) { grid_space$max_iterations <- sample(1:1000, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$transform = sample(c("NONE","STANDARDIZE","NORMALIZE","DEMEAN","DESCALE"), sample(2:3,1)) }
    grid_space$k <- sample(1:min(ncols,nrows), sample(2:3,1))
  }
  grid_space
}

