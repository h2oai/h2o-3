# Given a list of H2O models, ensemble the base learners usig a metalearner (Stacking / Super Learning)

.get_cvpreds <- function(object, single_col = TRUE) {
  
  # Note: This function will be deprecated in next stable version of H2O 
  # since we are adding support for this functionality directly
  
  # TO DO: Check that object is an H2OModel
  # TO DO: Check that keep_cross_validation_predictions = TRUE in the model
  # TO DO: Need to add support for returning a multiclass prediction and binary (full frame: predict, p0, p1)
  # TO DO: Remove family variable and just check class(object) directly
  
  # Need to extract family from model object
  if (class(object) == "H2OBinomialModel") family <- "binomial"
  if (class(object) == "H2OMulticlassModel") family <- "multinomial"
  if (class(object) == "H2ORegressionModel") family <- "gaussian"
  
  # return the frame_id of the resulting 1-col Hdf of cvpreds for learner l
  V <- object@allparameters$nfolds
  
  if (single_col) {
    if (family %in% c("bernoulli", "binomial")) {
      predlist <- sapply(1:V, function(v) h2o.getFrame(object@model$cross_validation_predictions[[v]]$name)[,3], simplify = FALSE)
    } else {
      predlist <- sapply(1:V, function(v) h2o.getFrame(object@model$cross_validation_predictions[[v]]$name)$predict, simplify = FALSE)
    }
    cvpred_sparse <- h2o.cbind(predlist)  #N x V H2OFrame with rows that are all zeros, except corresponding to the v^th fold if that rows is associated with v
    cvpreds <- apply(cvpred_sparse, 1, sum)    
  } else {
    stop("single_col = FALSE not yet implemented")
  }
  return(cvpreds)
}



h2o.stack <- function(models,  #list of H2OModels
                      metalearner = "h2o.glm.wrapper", 
                      response_frame,  #must pass the response col as an H2OFrame
                      cvpreds_frame = NULL,
                      # maybe add a family argument (to overrule),
                      seed = 1,
                      keep_levelone_data = TRUE) {  
  
  # models: list of (supervised) H2OModel objects saved using with keep_levelone_data = TRUE and identical fold_column
  # metalearner: the metalearning algorithm 
  # response_frame: the response column, passed as an H2OFrame (coltype should be correct)
  # cvpreds_frame: the level-one cross-validated predicted values (# col == # length(model))
  
  # Check dimensions of response_frame
  if (!(ncol(response_frame) == 1)) {
    stop("response_frame must have 1 column that contains the original response, y, used to train base models")
  }
  # cvpreds_frame = NULL assumes that models[[l]]@model$cross_validated_predictions exists in memory (not null)
  # Add something about supporting a cvpreds_frame
  
  # Assure that these have the same # of folds
  nfold_sync <- length(unique(sapply(models, function(mm) mm@allparameters$nfolds)))
  if (nfold_sync != 1) {
    stop("All models must have used the same number of cv folds specified by the `nfolds` argument")
  }
  # Assure that these used the same folds (check for modulo; TO DO: add support for arbitrary folds)
  fold_sync <- prod(sapply(models, function(mm) mm@allparameters$fold_assignment == "Modulo"))
  if (fold_sync != 1) {
    stop("All models must have used fold_assingment == Modulo")
  }
  # Assure that these used the same response column (is this too restrictive?)
  y_sync <- length(unique(sapply(models, function(mm) mm@allparameters$y)))
  if (y_sync != 1) {
    stop("All models must have used response variable, y")
  }
  # Collect the superset of x colnames
  # TO DO: Maybe add some more checking here to ensure compatibilty
  x <- unique(unlist(sapply(models, function(mm) mm@allparameters$x, simplify = FALSE)))
  y <- models[[1]]@allparameters$y
  
  # TO DO: Clean this up
  class_sync1 <- prod(sapply(models, function(mm) class(mm) == "H2OBinomialModel"))
  class_sync2 <- prod(sapply(models, function(mm) class(mm) == "H2OMultinomialModel"))
  class_sync3 <- prod(sapply(models, function(mm) class(mm) == "H2ORegressionModel"))
  class_sync <- class_sync1 + class_sync2 + class_sync3
  if (class_sync != 1) {
    stop("All models must have the same class type; one of: H2OBinomialModel, H2OMultinomialModel, H2ORegressionModel")
  }
  if (class_sync1 == 1) {
    family <- "binomial"
    ylim <- c(min(response_frame[,y]), max(response_frame[,y]))  #Used to enforce bounds 
  } else if (class_sync2 == 1) {
    family <- "multinomial"
    stop("Multinomial case not yet supported in H2O Ensemble")
  } else {
    family <- "gaussian"
    ylim <- NULL
  }
  #   family <- models[[1]]@model$model_summary$family
  #   if (family == "binomial") {
  #     ylim <- c(min(response_frame[,y]), max(response_frame[,y]))  #Used to enforce bounds  
  #   } else if (family == "gaussian") {
  #     ylim <- NULL
  #   } else if (family == "multinomial") {
  #     stop("Multinomial case not yet supported in H2O Ensemble")
  #   }
  runtime <- list()
  
  # Build the levelone data using the cross_validated_predictions of each H2O Model
  if (!is.null(cvpreds_frame)) {
    Z <- cvpreds_frame
    # TO DO: Check dimensions and other properties of cvpreds_frame for correctness
  } else {
    Z <- do.call("h2o.cbind", sapply(models, .get_cvpreds, simplify = FALSE))
  }
  levelone <- h2o.cbind(Z, response_frame)
  learner <- sapply(models, function(mm) mm@model_id)
  names(models) <- learner
  names(levelone) <- c(learner, "y")
  N <- nrow(levelone)
  V <- models[[1]]@allparameters$nfolds  #Number of internal CV folds
  
  # Validate metalearner argument
  if (length(metalearner)>1 | !is.character(metalearner) | !exists(metalearner)) {
    stop("The 'metalearner' argument must be a string, specifying the name of a base learner wrapper function.")
  }
  if (!exists(metalearner)) {
    stop("'metalearner' function name not found.")
  } 
  
  # What type of metalearning function do we have?
  # The h2o version is memory-optimized (the N x L level-one matrix, Z, never leaves H2O memory);
  # SuperLearner metalearners provide additional metalearning algos, but has a much bigger memory footprint
  if (grepl("^SL.", metalearner)) {
    metalearner_type <- "SuperLearner"
  } else if (grepl("^h2o.", metalearner)){
    metalearner_type <- "h2o"
  }
  
  # TO DO: Maybe remove this:
  # Do we want to rename this to `fit`? or overwrite original object...
  # Currently making a copy of the model
  #fit <- object
  
  # Metalearning: Regress y onto Z to learn optimal combination of base models
  print("Metalearning")
  if (is.numeric(seed)) set.seed(seed)  #If seed given, set seed prior to next step
  if (grepl("^SL.", metalearner)) {
    # SuperLearner package metalearner
    if (is.character(family)) {
      familyFun <- get(family, mode = "function", envir = parent.frame())
      #print(familyFun$family)  #does not work for SL.glmnet
    } 
    # SL metalearner functions require pulling levelone data back into R memory (not recommended for big datasets)
    Zdf <- as.data.frame(levelone)[,-ncol(levelone)]  #TO DO: untested; levelone is Z,y
    Y <- as.data.frame(levelone)[,"y"]  # TO DO: untested
    runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(Y = Y, 
                                                                          X = Zdf, 
                                                                          newX = Zdf, 
                                                                          family = familyFun, 
                                                                          id = seq(N), 
                                                                          obsWeights = rep(1,N)), gcFirst = FALSE)
  } else {
    # H2O metalearner
    runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(x = learner, 
                                                                          y = "y", 
                                                                          training_frame = levelone,   #levelone := cbind(Z, y)
                                                                          validation_frame = NULL, 
                                                                          family = family), gcFirst = FALSE)
  }
  
  if (!keep_levelone_data) {
    levelone <- NULL
  }
  
  # Ensemble model
  out <- list(x = x,
              y = y, # should we add y to the input so multiple responses can be stored?
              family = family, 
              learner = learner,
              metalearner = metalearner,
              cvControl = list(V = V, shuffle = FALSE),
              folds = rep(1:V, N),  # currently must be modulo V... is this 1:V or 0:(V-1)?  Need to update for fold_column users
              ylim = ylim, 
              seed = seed,
              parallel = "seq",
              basefits = models, 
              metafit = metafit,
              levelone = levelone,  #levelone = cbind(Z, y)
              runtime = runtime,
              h2o_version = packageVersion(pkg = "h2o"),
              h2oEnsemble_version = packageVersion(pkg = "h2oEnsemble"))
  class(out) <- "h2o.ensemble"
  return(out)
}
