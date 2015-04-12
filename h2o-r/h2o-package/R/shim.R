#' @export
h2o.shim <- function(start = TRUE) {
  if(!start) {
    rm(list = c("h2o.deeplearning",
                "h2o.gbm",
                "h2o.glm",
                "h2o.kmeans",
                "h2o.randomForest"), envir = parent.frame())
    stop("Removed Shims", call. = FALSE)
  }

  ### --- ALGO SHIMS --- ###

  ### - DeepLearning - ###

  h2o.dev.deeplearning <- h2o.deeplearning
  assign("h2o.deeplearning",
    function(x,
             y,
             data,
             key = "",
             override_with_best_model,
             classification = TRUE,
             nfolds = 0,
             validation,
             holdout_fraction = 0,
             checkpoint,
             autoencoder,
             use_all_factor_levels,
             activation,
             hidden,
             epochs,
             train_samples_per_iteration,
             seed,
             adaptive_rate,
             rho,
             epsilon,
             rate,
             rate_annealing,
             rate_decay,
             momentum_start,
             momentum_ramp,
             momentum_stable,
             nesterov_accelerated_gradient,
             input_dropout_ratio,
             hidden_dropout_ratios,
             l1,
             l2,
             max_w2,
             initial_weight_distribution,
             initial_weight_scale,
             loss,
             score_interval,
             score_training_samples,
             score_validation_samples,
             score_duty_cycle,
             classification_stop,
             regression_stop,
             quiet_mode,
             max_confusion_matrix_size,
             max_hit_ratio_k,
             balance_classes,
             class_sampling_factors,
             max_after_balance_size,
             score_validation_sampling,
             diagnostics,
             variable_importances,
             fast_mode,
             ignore_const_cols,
             force_load_balance,
             replicate_training_data,
             single_node_mode,
             shuffle_training_data,
             sparse,
             col_major,
             max_categorical_features,
             reproducible,
             ...)
    {
      # Map required for supported deprecated parameters
      .dl.dep.map <- c("data"         = "training_frame",
                        "key"         = "destination_key",
                        "validation"  = "validation_frame")
      # Map for unsupported deprecated parameters
      .dl.unsp.map <- c("classification" = "now automatically inferred from the data type.",
                        "holdout_fraction" = "no longer supported.")
      paramsIn <- list()

      if(!missing(x))
        paramsIn$x <- x
      if(!missing(y))
        paramsIn$y <- y
      if(!missing(data))
        paramsIn$data <- data
      if(!missing(key))
        paramsIn$key <- key
      if(!missing(override_with_best_model))
        paramsIn$override_with_best_model <- override_with_best_model
      if(!missing(classification))
        paramsIn$classification <- classification
      if(!missing(nfolds))
        paramsIn$nfolds <- nfolds
      if(!missing(validation))
        paramsIn$validation <- validation
      if(!missing(holdout_fraction))
        paramsIn$holdout_fraction <- holdout_fraction
      if(!missing(checkpoint))
        paramsIn$checkpoint <- checkpoint
      if(!missing(autoencoder))
        paramsIn$autoencoder <- autoencoder
      if(!missing(use_all_factor_levels))
        paramsIn$use_all_factor_levels <- use_all_factor_levels
      if(!missing(activation))
        paramsIn$activation <- activation
      if(!missing(hidden))
        paramsIn$hidden <- hidden
      if(!missing(epochs))
        paramsIn$epochs <- epochs
      if(!missing(train_samples_per_iteration))
        paramsIn$train_samples_per_iteration <- train_samples_per_iteration
      if(!missing(seed))
        paramsIn$seed <- seed
      if(!missing(adaptive_rate))
        paramsIn$adaptive_rate <- adaptive_rate
      if(!missing(rho))
        paramsIn$rho <- rho
      if(!missing(epsilon))
        paramsIn$epsilon <- epsilon
      if(!missing(rate))
        paramsIn$rate <- rate
      if(!missing(rate_annealing))
        paramsIn$rate_annealing <- rate_annealing
      if(!missing(rate_decay))
        paramsIn$rate_decay <- rate_decay
      if(!missing(momentum_start))
        paramsIn$momentum_start <- momentum_start
      if(!missing(momentum_ramp))
        paramsIn$momentum_ramp <- momentum_ramp
      if(!missing(momentum_stable))
        paramsIn$momentum_stable <- momentum_stable
      if(!missing(nesterov_accelerated_gradient))
        paramsIn$nesterov_accelerated_gradient <- nesterov_accelerated_gradient
      if(!missing(input_dropout_ratio))
        paramsIn$input_dropout_ratio <- input_dropout_ratio
      if(!missing(hidden_dropout_ratios))
        paramsIn$hidden_dropout_ratios <- hidden_dropout_ratios
      if(!missing(l1))
        paramsIn$l1 <- l1
      if(!missing(l2))
        paramsIn$l2 <- l2
      if(!missing(max_w2))
        paramsIn$max_w2 <- max_w2
      if(!missing(initial_weight_distribution))
        paramsIn$initial_weight_distribution <- initial_weight_distribution
      if(!missing(initial_weight_scale))
        paramsIn$initial_weight_scale <- initial_weight_scale
      if(!missing(loss))
        paramsIn$loss <- loss
      if(!missing(score_interval))
        paramsIn$score_interval <- score_interval
      if(!missing(score_training_samples))
        paramsIn$score_training_samples <- score_training_samples
      if(!missing(score_validation_samples))
        paramsIn$score_validation_samples <- score_validation_samples
      if(!missing(score_duty_cycle))
        paramsIn$score_duty_cycle <- score_duty_cycle
      if(!missing(classification_stop))
        paramsIn$classification_stop <- classification_stop
      if(!missing(regression_stop))
        paramsIn$regression_stop <- regression_stop
      if(!missing(quiet_mode))
        paramsIn$quiet_mode <- quiet_mode
      if(!missing(max_confusion_matrix_size))
        paramsIn$max_confusion_matrix_size <- max_confusion_matrix_size
      if(!missing(max_hit_ratio_k))
        paramsIn$max_hit_ratio_k <- max_hit_ratio_k
      if(!missing(balance_classes))
        paramsIn$balance_classes <- balance_classes
      if(!missing(class_sampling_factors))
        paramsIn$class_sampling_factors <- class_sampling_factors
      if(!missing(max_after_balance_size))
        paramsIn$max_after_balance_size <- max_after_balance_size
      if(!missing(score_validation_sampling))
        paramsIn$score_validation_sampling <- score_validation_sampling
      if(!missing(diagnostics))
        paramsIn$diagnostics <- diagnostics
      if(!missing(variable_importances))
        paramsIn$variable_importances <- variable_importances
      if(!missing(fast_mode))
        paramsIn$fast_mode <- fast_mode
      if(!missing(ignore_const_cols))
        paramsIn$ignore_const_cols <- ignore_const_cols
      if(!missing(force_load_balance))
        paramsIn$force_load_balance <- force_load_balance
      if(!missing(replicate_training_data))
        paramsIn$replicate_training_data <- replicate_training_data
      if(!missing(single_node_mode))
        paramsIn$single_node_mode <- single_node_mode
      if(!missing(shuffle_training_data))
        paramsIn$shuffle_training_data <- shuffle_training_data
      if(!missing(sparse))
        paramsIn$sparse <- sparse
      if(!missing(col_major))
        paramsIn$col_major <- col_major
      if(!missing(max_categorical_features))
        paramsIn$max_categorical_features <- max_categorical_features
      if(!missing(reproducible))
        paramsIn$reproducible <- reproducible
      # Fix up parameters for H2ODev
      paramsDev <- .dep.params(paramsIn, .dl.dep.map, .dl.unsp.map)
      paramsDev <- append(paramsDev, list(...))
      if(classification)
        paramsDev$training_frame[,y] <- as.factor(paramsDev$training_frame[,y])
      m <- do.call("h2o.dev.deeplearning", paramsDev)
      m@model <- .dep.model(m)
      m
    },
    envir = parent.frame())

  ### - GBM - ###

  h2o.dev.gbm <- h2o.gbm
  assign("h2o.gbm",
    function(x,
             y,
             distribution = 'multinomial',
             data,
             key = "",
             n.trees = 10,
             interaction.depth = 5,
             n.minobsinnode = 10,
             shrinkage = 0.1,
             n.bins = 20,
             group_split = TRUE,
             importance = FALSE, nfolds = 0,
             validation,
             holdout.fraction = 0,
             balance.classes = FALSE,
             max.after.balance.size = 5,
             class.sampling.factors = NULL,
             grid.parallelism = 1,
             ...)
    {
      # Map for supported deprecated parameters
      .gbm.dep.map <- c("data"                    = "training_frame",
                        "key"                     = "destination_key",
                        "distribution"            = "loss",
                        "n.trees"                 = "ntrees",
                        "interaction.depth"       = "max_depth",
                        "n.minobsinnode"          = "min_rows",
                        "shrinkage"               = "learn_rate",
                        "n.bins"                  = "nbins",
                        "validation"              = "validation_frame",
                        "balance.classes"         = "balance_classes",
                        "max.after.balance.size"  = "max_after_balance_size")
      # Map for unsupported deprecated parameters
      .gbm.unsp.map <- c("group_split" = "now the default.",
                         "importance" = "now computed by default.",
                         "holdout.fraction" = "no longer supported.",
                         "class.sampling.factors" = "no longer supported.",
                         "grid.parallelism" = "no longer supported")
      paramsIn <- list()
      if(!missing(x))
        paramsIn$x <- x
      if(!missing(y))
        paramsIn$y <- y
      if(!missing(distribution))
        paramsIn$distribution <- distribution
      if(!missing(data))
        paramsIn$data <- data
      if(!missing(key))
        paramsIn$key <- key
      if(!missing(n.trees))
        paramsIn$n.trees <- n.trees
      if(!missing(interaction.depth))
        paramsIn$interaction.depth <- interaction.depth
      if(!missing(n.minobsinnode))
        paramsIn$n.minobsinnode <- n.minobsinnode
      if(!missing(shrinkage))
        paramsIn$shrinkage <- shrinkage
      if(!missing(n.bins))
        paramsIn$n.bins <- n.bins
      if(!missing(group_split))
        paramsIn$group_split <- group_split
      if(!missing(importance))
        paramsIn$importance <- importance
      if(!missing(validation))
        paramsIn$validation <- validation
      if(!missing(holdout.fraction))
        paramsIn$holdout.fraction <- holdout.fraction
      if(!missing(balance.classes))
        paramsIn$balance.classes <- balance.classes
      if(!missing(max.after.balance.size))
        paramsIn$max.after.balance.size <- max.after.balance.size
      if(!missing(class.sampling.factors))
        paramsIn$class.sampling.factors <- class.sampling.factors
      if(!missing(grid.parallelism))
        paramsIn$grid.parallelism <- grid.parallelism
      # Fix up parameters for H2ODev
      paramsDev <- .dep.params(paramsIn, .gbm.dep.map, .gbm.unsp.map)
      paramsDev <- append(paramsDev, list(...))
      m <- do.call("h2o.dev.gbm", paramsDev)
      m@model <- .dep.model(m)
      m
    },
    envir = parent.frame())

  ### - GLM - ###

  h2o.dev.glm <- h2o.glm
  assign("h2o.glm",
    function(x,
             y,
             data,
             key = "",
             offset = NULL,
             family,
             link,
             tweedie.p = ifelse(family == "tweedie", 1.5, NA_real_),
             prior = NULL,
             nfolds = 0,
             alpha = 0.5,
             lambda = 1e-5,
             lambda_search = FALSE,
             nlambda = -1,
             lambda.min.ratio = -1,
             max_predictors = -1,
             return_all_lambda = FALSE,
             strong_rules = TRUE,
             standardize = TRUE,
             intercept = TRUE,
             non_negative = FALSE,
             use_all_factor_levels = FALSE,
             variable_importances = FALSE,
             epsilon = 1e-4,
             iter.max = 100,
             higher_accuracy = FALSE,
             beta_constraints = NULL,
             disable_line_search = FALSE,
             ...)
    {
      # Map required for supported deprecated parameters
      .glm.dep.map <- c("data"              = "training_frame",
                        "key"               = "destination_key",
                        "validation"        = "validation_frame",
                        "prior"             = "prior1",
                        "nlambda"           = "nlambdas",
                        "lambda.min.ratio"  = "lambda_min_ratio",
                        "iter.max"          = "max_iter",
                        "epsilon"           = "beta_eps")
      # Map for unsupported deprecated parameters
      .glm.unsp.map <- c("return_all_lambda" = "not currently supported.",
                         "higher_accuracy" = "no longer supported.",
                         "strong_rules" = "no longer supported.",
                         "intercept" = "no longer supported.",
                         "non_negative" = "no longer supported.",
                         "variable_importances" = "now computed by default.",
                         "disable_line_search" = "no longer supported.",
                         "offset" = "no longer supported.",
                         "max_predictors" = "no longer supported.",
                         "class_sampling_factors" = "no longer supported.",
                         "max_after_balance_size" = "no longer supported.",
                         "solver" = "not currently supported.")
      # Create input parameters
      paramsIn <- list()
      if(!missing(x))
        paramsIn$x <- x
      if(!missing(y))
        paramsIn$y <- y
      if(!missing(data))
        paramsIn$data <- data
      if(!missing(key))
        paramsIn$key <- key
      if(!missing(offset))
        paramsIn$offset <- offset
      if(!missing(family))
        paramsIn$family <- family
      if(!missing(link))
        paramsIn$link <- link
      if(!missing(tweedie.p))
        paramsIn$tweedie.p <- tweedie.p
      if(!missing(prior))
        paramsIn$prior <- prior
      if(!missing(nfolds))
        paramsIn$nfolds <- nfolds
      if(!missing(alpha))
        paramsIn$alpha <- alpha
      if(!missing(lambda))
        paramsIn$lambda <- lambda
      if(!missing(lambda_search))
        paramsIn$lambda_search <- lambda_search
      if(!missing(nlambda))
        paramsIn$nlambda <- nlambda
      if(!missing(lambda.min.ratio))
        paramsIn$lambda.min.ratio <- lambda.min.ratio
      if(!missing(max_predictors))
        paramsIn$max_predictors <- max_predictors
      if(!missing(return_all_lambda))
        paramsIn$return_all_lambda <- return_all_lambda
      if(!missing(strong_rules))
        paramsIn$strong_rules <- strong_rules
      if(!missing(standardize))
        paramsIn$standardize <- standardize
      if(!missing(intercept))
        paramsIn$intercept <- intercept
      if(!missing(non_negative))
        paramsIn$non_negative <- non_negative
      if(!missing(use_all_factor_levels))
        paramsIn$use_all_factor_levels <- use_all_factor_levels
      if(!missing(variable_importances))
        paramsIn$variable_importances <- variable_importances
      if(!missing(epsilon))
        paramsIn$epsilon <- epsilon
      if(!missing(iter.max))
        paramsIn$iter.max <- iter.max
      if(!missing(higher_accuracy))
        paramsIn$higher_accuracy <- higher_accuracy
      if(!missing(beta_constraints))
        paramsIn$beta_constraints <- beta_constraints
      if(!missing(disable_line_search))
        paramsIn$disable_line_search <- disable_line_search
      # Fix up parameters for H2ODev
      paramsDev <- .dep.params(paramsIn, .glm.dep.map, .glm.unsp.map)
      paramsDev <- append(paramsDev, list(...))
      m <- do.call("h2o.dev.glm", paramsDev)
      m@model <- .dep.model(m)
      m
    },
    envir = parent.frame())

  ### - Kmeans - ###

  h2o.dev.kmeans <- h2o.kmeans
  assign("h2o.kmeans",
    function(data,
             centers,
             cols = '',
             key = "",
             iter.max = 10,
             normalize = FALSE,
             init = "none",
             seed = 0,
             dropNACols = FALSE,
             ...)
    {
      # Map for supported deprecated parameters
      .km.dep.map <- c("centers"    = "k",
                       "cols"       = "x",
                       "key"        = "destination_key",
                       "iter.max"   = "max_iterations",
                       "normalize"  = "standardize")
      # Create input parameters
      paramsIn <- list
      if(!missing(data))
        paramsIn$data <- data
      if(!missing(centers))
        paramsIn$centers <- centers
      if(!missing(cols))
        paramsIn$cols <- cols
      if(!missing(key))
        paramsIn$key <- key
      if(!missing(iter.max))
        paramsIn$iter.max <- iter.max
      if(!missing(normalize))
        paramsIn$normalize <- normalize
      if(!missing(init))
        paramsIn$init <- init
      if(!missing(seed))
        paramsIn$seed <- seed
      if(!missing(dropNACols))
        paramsIn$dropNACols <- dropNACols
      # Fix up parameters for H2ODev
      paramsDev <- .dep.params(paramsIn, .km.dep.map, .km.unsp.map)
      paramsDev <- append(paramsDev, list(...))
      m <- do.call("h2o.dev.kmeans", dots)
      m@model <- .dep.model(m)
      m
    },
    envir = parent.frame())

  ### - DRF - ###

  h2o.dev.randomForest <- h2o.randomForest
  assign("h2o.randomForest",
    function(x,
             y,
             data,
             key="",
             classification=TRUE,
             ntree=50,
             depth=20,
             mtries = -1,
             sample.rate=2/3,
             nbins=20,
             seed=-1,
             importance=FALSE,
             score.each.iteration=FALSE,
             nfolds=0,
             validation,
             holdout.fraction=0,
             nodesize=1,
             balance.classes=FALSE,
             max.after.balance.size=5,
             class.sampling.factors = NULL,
             doGrpSplit=TRUE,
             verbose = FALSE,
             oobee = TRUE,
             stat.type = "ENTROPY",
             type = "fast",
             ...)
    {
      # Map for supported deprecated parameters
      .drf.dep.map <- c("data"            = "training_frame",
                        "key"             = "destination_key",
                        "validation"      = "validation_frame",
                        "sample.rate"     = "sample_rate",
                        "ntree"           = "ntrees",
                        "depth"           = "max_depth",
                        "balance.classes" = "balance_classes")
      # Map for unsupported deprecated parameters
      .drf.unsp.map <- c("classification" = "now automatically inferred from the data type.",
                         "importance" = "now computed automatically.",
                         "holdout.fraction" = "no longer supported.",
                         "class.sampling.factors" = "no longer supported.",
                         "doGrpSplit" = "now the default.",
                         "verbose" = "not currently supported.",
                         "obee" = "no longer supported.",
                         "stat.type" = "no longer supported.",
                         "type" = "no longer supported. Only 'BigData' run now.",
                         "nfolds" = "not currently supported.")
      # Create input parameters
      paramsIn <- list()
      if(!missing(x))
        paramsIn$x <- x
      if(!missing(y))
        paramsIn$y <- y
      if(!missing(data))
        paramsIn$data <- data
      if(!missing(key))
        paramsIn$key <- key
      if(!missing(classification))
        paramsIn$classification <- classification
      if(!missing(ntree))
        paramsIn$ntree <- ntree
      if(!missing(depth))
        paramsIn$depth <- depth
      if(!missing(mtries))
        paramsIn$mtries <- mtries
      if(!missing(sample.rate))
        paramsIn$sample.rate <- sample.rate
      if(!missing(nbins))
        paramsIn$nbins <- nbins
      if(!missing(seed))
        paramsIn$seed <- seed
      if(!missing(importance))
        paramsIn$importance <- importance
      if(!missing(score.each.iteration))
        paramsIn$score.each.iteration <- score.each.iteration
      if(!missing(nfolds))
        paramsIn$nfolds <- nfolds
      if(!missing(validation))
        paramsIn$validation <- validation
      if(!missing(holdout.fraction))
        paramsIn$holdout.fraction <- holdout.fraction
      if(!missing(nodesize))
        paramsIn$nodesize <- nodesize
      if(!missing(balance.classes))
        paramsIn$balance.classes <- balance.classes
      if(!missing(max.after.balance.size))
        paramsIn$max.after.balance.size <- max.after.balance.size
      if(!missing(class.sampling.factors))
        paramsIn$class.sampling.factors <- class.sampling.factors
      if(!missing(doGrpSplit))
        paramsIn$doGrpSplit <- doGrpSplit
      if(!missing(verbose))
        paramsIn$verbose <- verbose
      if(!missing(oobee))
        paramsIn$oobee <- oobee
      if(!missing(stat.type))
        paramsIn$stat.type <- stat.type
      if(!missing(type)){
        paramsIn$type <- type
        if (type == "fast")
          stop("SpeedRF is no longer a supported model type.", call. = F)
      }
      paramsDev <- .dep.params(paramsIn, .drf.dep.map, .drf.unsp.map)
      paramsDev <- append(paramsDev, list(...))
      if(classification)
        paramsDev$training_frame[,y] <- as.factor(paramsDev$training_frame[,y])
      m <- do.call("h2o.dev.randomForest", paramsDev)
      m@model <- .dep.model(m)
      m
    },
    envir = parent.frame())

  ### --- MODEL SHIMS --- ###

  .dep.model <- function(old) {
    model <- old@model
    algo <- old@algorithm


    #### Deprecated features start here ####
    model$params <- old@allparameters
    if(algo == "gbm") {
      model$err <- model$mse_train
    }
    if(algo == "drf")
    {
      model$mse <- model$mse_train
      model$forest <- warning("forest output is not currently supported")
    }
    if(algo == "glm") {
      TRUE
    }
    if(algo == "deeplearning") {
      TRUE
    }
    if (class(model) %in% c("H2OBinomialModel", "H2OMultinomialModel")){
      model$priorDistribution <- warning("priorDistribution output field is not currently supported")
      model$classification <- TRUE
      warning("classification is no longer a supported output field")
      cm <- h2o.confusionMatrix(h2o.getModel(key, conn),
                                h2o.getFrame(allparams$training_frame, conn))
      model$confusion <- cm
    } else
      model$classification <- FALSE

    model
  }

  # Handling Deprecated Parameters
  .dep.params <- function(params, dep.map, unsp.map) {
    out <- list()
    for(type in names(params)){
      if(type %in% names(dep.map)) {
        out[[dep.map[[type]]]] <- params[[type]]
        warning(paste0("'", type, "' is a deprecated parameter, please use '",
                       dep.map[[type]], "' instead."), call. = FALSE)
      } else if (type %in% names(unsp.map)) {
        warning(paste0("'", type, "' is ", unsp.map[[type]]), call. = FALSE)
      } else
        out[[type]] <- params[[type]]
    }
    out
  }
}
