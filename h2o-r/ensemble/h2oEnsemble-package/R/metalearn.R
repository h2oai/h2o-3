# Given an "h2o.ensemble" model, recombine the base learners using a new metalearner


h2o.metalearn <- function(object,  #object must be an "h2o.ensemble" model fit with keep_levelone_data = TRUE
                          metalearner = "h2o.glm.wrapper", 
                          seed = 1,
                          keep_levelone_data = TRUE) {  
  
  
  # Pull in level-one data from h2o.ensemble object
  levelone <- object$levelone  #includes y
  # Check that levelone is not NULL
  if (is.null(levelone)) {
    stop("The `metalearn` function requires that the `levelone` object contain an H2O Frame (not NULL)")
  }
  N <- nrow(levelone)
  family <- object$family
  runtime <- object$runtime
  learner <- names(object$basefits)
  
  # object$levelone may be a key or an H2O Frame object
  if ((!inherits(levelone, "Frame") && !inherits(levelone, "H2OFrame")))
    tryCatch(levelone <- h2o.getFrame(levelone),
             error = function(err) {
               stop("object$levelone must be a valid H2O Frame or id")
             })
  
  
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
  fit <- object
  
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
    fit$runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(Y = Y, 
                                                                          X = Zdf, 
                                                                          newX = Zdf, 
                                                                          family = familyFun, 
                                                                          id = seq(N), 
                                                                          obsWeights = rep(1,N)), gcFirst = FALSE)
  } else {
    # H2O metalearner
    fit$runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(x = learner, 
                                                                          y = "y", 
                                                                          training_frame = levelone,   #levelone := cbind(Z, y)
                                                                          validation_frame = NULL, 
                                                                          family = family), gcFirst = FALSE)
  }
  
  if (!keep_levelone_data) {
    fit$levelone <- NULL
  }
  
  # Should we update the runtime$total?
  # For now, just leave as is
  fit$metafit <- metafit
  return(fit)
}