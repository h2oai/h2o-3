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
#' @param algo A string {"gbm", "drf", "deeplearning", "kmeans", "glm"}
#' @param ncols Used for mtries selection
#' @return A named list of gridable parameters and their respective values
makeRandomGridSpace <- function(algo,ncols=NULL) {
  grid_space <- list()
  if ( algo == "gbm" || algo == "drf" ) {
    if ( sample(0:1,1) ) { grid_space$ntrees <- sample(1:5, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$max_depth <- sample(1:5, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$min_rows <- sample(1:10, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$nbins <- sample(2:20, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$nbins_cats <- sample(2:1024, sample(2:3,1)) }
  }
  if ( algo == "gbm" ) {
    if ( sample(0:1,1) ) { grid_space$learn_rate <- round(runif(sample(2:3,1)),6) }
    grid_space$distribution <- sample(c('bernoulli','multinomial','gaussian','poisson','tweedie','gamma'), 1)
  }
  if ( algo == "drf" ) {
    if ( sample(0:1,1) ) { grid_space$mtries <- sample(2:ncols, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$sample_rate <- round(runif(sample(2:3,1)),6) }
  }
  if ( algo == "deeplearning" ) {
    if ( sample(0:1,1) ) { grid_space$activation <- sample(c("Rectifier", "Tanh", "TanhWithDropout",
                                                            "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),
                                                          sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$hidden <- lapply(sample(1:3,sample(2:3,1)), function (x) rep(sample(10:200,1),sample(2:3,1))) }
    if ( sample(0:1,1) ) { grid_space$epochs <- sample(1:10, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$loss <- sample(c("Automatic", "CrossEntropy", "MeanSquare", "Huber", "Absolute"),
                                                    sample(2:3,1)) }
    grid_space$distribution <- sample(c('bernoulli','multinomial','gaussian','poisson','tweedie','gamma'), 1)
  }
  if ( algo == "kmeans" ) {
    if ( sample(0:1,1) ) { grid_space$max_iterations <- sample(1:1000, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$standardize <- c(TRUE, FALSE) }
    if ( sample(0:1,1) ) { grid_space$seed = sample(1:1000, sample(2:3,1)) }
    if ( sample(0:1,1) ) { grid_space$init = sample(c('Random','PlusPlus','Furthest'), sample(2:3,1)) }
    grid_space$k <- sample(1:10, 1)
  }
  grid_space
}

