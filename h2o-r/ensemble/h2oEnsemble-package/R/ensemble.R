h2o.ensemble <- function(x, y, training_frame, 
                         model_id = "", validation_frame = NULL,
                         family = c("AUTO", "binomial", "gaussian"),
                         learner = c("h2o.glm.wrapper", "h2o.randomForest.wrapper", "h2o.gbm.wrapper", "h2o.deeplearning.wrapper"),
                         metalearner = "h2o.glm.wrapper",
                         cvControl = list(V = 5, shuffle = TRUE),  #maybe change this to cv_control
                         seed = 1,
                         parallel = "seq",  #only seq implemented
                         keep_levelone_data = TRUE) 
{
  
  starttime <- Sys.time()
  runtime <- list()
  
  # Training_frame may be a key or an H2O Frame object
  if ((!inherits(training_frame, "Frame") && !inherits(training_frame, "H2OFrame")))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid H2O Frame or id")
             })
  if (!is.null(validation_frame)) {
    if (is.character(validation_frame))
      tryCatch(validation_frame <- h2o.getFrame(validation_frame),
               error = function(err) {
                 stop("argument \"validation_frame\" must be a valid H2O Frame or id")
               })
  }
  N <- dim(training_frame)[1L]  #Number of observations in training set
  if (is.null(validation_frame)) {
    validation_frame <- training_frame
  }
  
  # Determine prediction task family type automatically
  # TO DO: Add auto-detection for other distributions like gamma - right now auto-detect as "gaussian"
  if (length(family) > 0) {
    family <- match.arg(family)
  }
  if (family == "AUTO") {
    if (is.factor(training_frame[,y])) {
      numcats <- length(h2o.levels(training_frame[,y]))
      if (numcats == 2) {
        family <- "binomial" 
      } else {
        stop("multinomial case not yet implemented.")
      }
    } else {
      family <- "gaussian"
    }
  }
  # Check that if specified, family matches data type for response
  # binomial must be factor/enum and gaussian must be numeric
  if (family == c("gaussian")) {
    if (!is.numeric(training_frame[,y])) {
      stop("When `family` is gaussian, the repsonse column must be numeric.")
    }
    # TO DO: Update this ylim calc when h2o.range method gets implemented for H2OFrame cols
    ylim <- c(min(training_frame[,y]), max(training_frame[,y]))  #Used to enforce bounds  
  } else {
    if (!is.factor(training_frame[,y])) {
      stop("When `family` is binomial, the repsonse column must be a factor.")
    }
    ylim <- NULL
  }
  
  # Update control args by filling in missing list elements
  cvControl <- do.call(".cv_control", cvControl)
  V <- cvControl$V      #Number of CV folds
  L <- length(learner)  #Number of distinct learners
  idxs <- expand.grid(1:V,1:L)
  names(idxs) <- c("v","l")
  
  # Validate learner and metalearner arguments
  if (length(metalearner)>1 | !is.character(metalearner) | !exists(metalearner)) {
    stop("The 'metalearner' argument must be a string, specifying the name of a base learner wrapper function.")
  }
  if (sum(!sapply(learner, exists))>0) {
    stop("'learner' function name(s) not found.")
  }
  if (!exists(metalearner)) {
    stop("'metalearner' function name not found.")
  } 
  # The 'family' must be a string, not an R function input like gaussian()
  # No support for multiclass at the moment, just binary classification or regression
  if (!(family %in% c("binomial", "gaussian"))) {
    stop("'family' not supported")
  }
  if (inherits(parallel, "character")) {
    if (!(parallel %in% c("seq","multicore"))) {
      stop("'parallel' must be either 'seq' or 'multicore' or a snow cluster object")
    }
  } else if (!inherits(parallel, "cluster")) {
    stop("'parallel' must be either 'seq' or 'multicore' or a snow cluster object")
  }
  
  # Begin ensemble code
  if (is.numeric(seed)) set.seed(seed)  #If seed is specified, set seed prior to next step
  folds <- sample(rep(seq(V), ceiling(N/V)))[1:N]  # Cross-validation folds (stratified folds not yet supported)
  training_frame$fold_id <- as.h2o(folds)  # Add a fold_id column for each observation so we can subset by row later

  # What type of metalearning function do we have?
  # The h2o version is memory-optimized (the N x L level-one matrix, Z, never leaves H2O memory);
  # SuperLearner metalearners provide additional metalearning algos, but has a much bigger memory footprint
  if (grepl("^SL.", metalearner)) {
    metalearner_type <- "SuperLearner"
  } else if (grepl("^h2o.", metalearner)){
    metalearner_type <- "h2o"
  }
  
  # Create the Z matrix of cross-validated predictions
  mm <- .make_Z(x = x, y = y, training_frame = training_frame, 
                family = family, 
                learner = learner, 
                parallel = parallel, 
                seed = seed, 
                V = V, 
                L = L, 
                idxs = idxs,
                metalearner_type = metalearner_type)
  # TO DO: Could pass on the metalearner arg instead of metalearner_type and get this info internally
  basefits <- mm$basefits
  Z <- mm$Z  #pure Z (dimension N x L)
  
  # Metalearning: Regress y onto Z to learn optimal combination of base models
  # TO DO: Replace grepl for metalearner_type
  # TO DO: Pass on additional args to match.fun(metalearner) for h2o type
  print("Metalearning")
  if (is.numeric(seed)) set.seed(seed)  #If seed given, set seed prior to next step
  if (grepl("^SL.", metalearner)) {
    # this is very hacky and should be used only for testing
    if (is.character(family)) {
      familyFun <- get(family, mode = "function", envir = parent.frame())
      #print(familyFun$family)  #does not work for SL.glmnet
    } 
    Zdf <- as.data.frame(Z)
    Y <- as.data.frame(training_frame[,c(y)])[,1]
    # TO DO: for parity, need to add y col to Z like we do below
    runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(Y = Y, 
                                                                          X = Zdf, 
                                                                          newX = Zdf, 
                                                                          family = familyFun, 
                                                                          id = seq(N), 
                                                                          obsWeights = rep(1,N)), gcFirst = FALSE)
  } else {
    Z$y <- training_frame[,c(y)]  # do we want to add y to the Z frame?  
    runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(x = learner, 
                                                                          y = "y", 
                                                                          training_frame = Z, 
                                                                          validation_frame = NULL, 
                                                                          family = family), gcFirst = FALSE)
  }
  
  # Since baselearning is now performed along with CV, see if we can get this info, or deprecate this
  runtime$baselearning <- NULL
  runtime$total <- Sys.time() - starttime
  
  # Keep level-one data?
  if (!keep_levelone_data) {
    Z <- NULL
  }
  
  # Ensemble model
  out <- list(x = x,
              y = y, 
              family = family, 
              learner = learner,
              metalearner = metalearner,
              cvControl = cvControl,
              folds = folds,
              ylim = ylim, 
              seed = seed,
              parallel = parallel,
              basefits = basefits, 
              metafit = metafit,
              levelone = Z,  #levelone = cbind(Z, y)
              runtime = runtime,
              h2o_version = packageVersion(pkg = "h2o"),
              h2oEnsemble_version = packageVersion(pkg = "h2oEnsemble"))
  class(out) <- "h2o.ensemble"
  return(out)
}



# Generate the CV predicted values for all learners
.make_Z <- function(x, y, training_frame, family, learner, parallel, seed, V, L, idxs, metalearner_type = c("h2o", "SuperLearner")) {
  
  # Do V-fold cross-validation of each learner (in a loop/apply over 1:L)...
  fitlist <- sapply(X = 1:L, FUN = .fitWrapper, y = y, xcols = x, training_frame = training_frame,
                    validation_frame = NULL, family = family, learner = learner, 
                    seed = seed, fold_column = "fold_id", 
                    simplify = FALSE)
  
  runtime <- list()
  runtime$cv <- lapply(fitlist, function(ll) ll$fittime)
  names(runtime$cv) <- learner
  basefits <- lapply(fitlist, function(ll) ll$fit)  #Base fits (trained on full data) to be saved
  names(basefits) <- learner      
  
  # In the case of binary classification, a 3-col HDF is returned, colnames == c("predict", "p0", "p1")
  # In the case of regression, 1-col HDF is already returned, colname == "predict"
  .compress_cvpred_into_1col <- function(l, family) {
    # return the frame_id of the resulting 1-col Hdf of cvpreds for learner l
    if (family %in% c("bernoulli", "binomial")) {
      predlist <- sapply(1:V, function(v) h2o.getFrame(basefits[[l]]@model$cross_validation_predictions[[v]]$name)$p1, simplify = FALSE)
    } else {
      predlist <- sapply(1:V, function(v) h2o.getFrame(basefits[[l]]@model$cross_validation_predictions[[v]]$name)$predict, simplify = FALSE)
    }
    cvpred_sparse <- h2o.cbind(predlist)  #N x V Hdf with rows that are all zeros, except corresponding to the v^th fold if that rows is associated with v
    cvpred_col <- apply(cvpred_sparse, 1, sum)
    return(cvpred_col)
  } 
  cvpred_framelist <- sapply(1:L, function(l) .compress_cvpred_into_1col(l, family))
  Z <- h2o.cbind(cvpred_framelist)
  names(Z) <- learner
  return(list(Z = Z, basefits = basefits))
}


# Train a model using learner l 
.fitFun <- function(l, y, x, training_frame, validation_frame, family, learner, seed, fold_column) {
  if (!is.null(fold_column)) cv = TRUE
  if (is.numeric(seed)) set.seed(seed)  #If seed given, set seed prior to next step
  fit <- match.fun(learner[l])(y = y, x = x, training_frame = training_frame, validation_frame = NULL, family = family, fold_column = fold_column, keep_cross_validation_folds = cv)
  #fit <- get(learner[l], mode = "function", envir = parent.frame())(y = y, x = x, training_frame = training_frame, validation_frame = NULL, family = family, fold_column = fold_column, keep_cross_validation_folds = cv)
  return(fit)
}


# Wrapper function for .fitFun to record system.time
.fitWrapper <- function(l, y, xcols, training_frame, validation_frame, family, learner, seed, fold_column) {
  print(sprintf("Cross-validating and training base learner %s: %s", l, learner[l]))
  fittime <- system.time(fit <- .fitFun(l, y, xcols, training_frame, validation_frame, family, 
                                        learner, seed, fold_column), gcFirst=FALSE)
  return(list(fit=fit, fittime=fittime))
}


.cv_control <- function(V = 5L, stratifyCV = TRUE, shuffle = TRUE){
  # Parameters that control the CV process
  # Only part of this being used currently --  
  # Stratification is not enabled yet in the h2o.ensemble function.
  # We can use a modified SuperLearner::CVFolds function (or similar) to 
  # enable stratification by outcome in the future.
  
  V <- as.integer(V)  #Number of cross-validation folds
  if(!is.logical(stratifyCV)) {
    stop("'stratifyCV' must be logical")
  }
  if(!is.logical(shuffle)) {
    stop("'shuffle' must be logical")
  }  
  return(list(V = V, stratifyCV = stratifyCV, shuffle = shuffle))
}


predict.h2o.ensemble <- function(object, newdata, ...) {
  
  L <- length(object$basefits)
  basepreddf <- as.data.frame(matrix(NA, nrow = nrow(newdata), ncol = L))
  for (l in seq(L)) {
    if (object$family == "binomial") {
      basepreddf[, l] <- as.data.frame(do.call('h2o.predict', list(object = object$basefits[[l]],
                                                                   newdata = newdata)))$p1 
    } else {
      basepreddf[, l] <- as.data.frame(do.call('h2o.predict', list(object = object$basefits[[l]],
                                                                   newdata = newdata)))$predict
    }
  }
  names(basepreddf) <- names(object$basefits)
  basepred <- as.h2o(basepreddf, destination_frame = "basepred")
  
  if (grepl("H2O", class(object$metafit))) {
    # H2O ensemble metalearner from wrappers.R
    pred <- h2o.predict(object = object$metafit, newdata = basepred)
  } else {
    # SuperLearner wrapper function metalearner
    pred <- predict(object = object$metafit$fit, newdata = basepred)
  }
  out <- list(pred = pred, basepred = basepred)
  return(out)
}


print.h2o.ensemble <- function(x, ...) {
  cat("\nH2O Ensemble fit")
  cat("\n----------------")
  cat("\nfamily: ")
  cat(x$family)
  cat("\nlearner: ")
  cat(x$learner)
  cat("\nmetalearner: ")
  cat(x$metalearner)
  cat("\n\n")
}


# plot.h2o.ensemble <- function(x, ...) {
#   cat("\nPlotting for an H2O Ensemble fit is not implemented at this time.")
# }


