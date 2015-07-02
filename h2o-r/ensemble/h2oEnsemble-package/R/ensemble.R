h2o.ensemble <- function(x, y, training_frame, 
                         model_id = "", validation_frame = NULL,
                         family = c("AUTO", "binomial", "gaussian"),
                         learner = c("h2o.glm.wrapper", "h2o.randomForest.wrapper", "h2o.gbm.wrapper", "h2o.deeplearning.wrapper"),
                         metalearner = "h2o.glm.wrapper",
                         cvControl = list(V = 5, shuffle = TRUE),  #maybe change this to cv_control
                         seed = 1,
                         parallel = "seq",
                         ...) 
{
  
  starttime <- Sys.time()
  runtime <- list()
 
  # Training_frame may be a key or an H2OFrame object
  if (!inherits(training_frame, "H2OFrame"))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid H2OFrame or key")
             })
  if (!is.null(validation_frame)) {
    if (!inherits(validation_frame, "H2OFrame"))
      tryCatch(validation_frame <- h2o.getFrame(validation_frame),
               error = function(err) {
                 stop("argument \"validation_frame\" must be a valid H2OFrame or key")
               })
  }
  N <- dim(training_frame)[1L]  #Number of observations in training set
  if (is.null(validation_frame)) {
    validation_frame <- training_frame
  }
  
  # Determine prediction task family type automatically
  if (length(family) > 0) {
    family <- match.arg(family)
  }
  if (family == "AUTO") {
    if (is.factor(training_frame[,c(y)])) {
      numcats <- length(unique(as.data.frame(training_frame)[,c(y)]))
      if (numcats == 2) {
        family <- "binomial" 
      } else {
        stop("multinomial case not yet implemented.")
      }
    } else {
      family <- "gaussian"
    }
  }
  if (family == c("gaussian")) {
    ylim <- range(as.data.frame(training_frame[,c(y)]))  #Used to enforce bounds    
  } else {
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
  
  # Create the Z matrix of cross-validated predictions
  Z <- .make_Z(x = x, y = y, training_frame = training_frame, 
               family = family, 
               learner = learner, 
               parallel = parallel, 
               seed = seed, 
               V = V, 
               L = L, 
               idxs = idxs)
  
  # Metalearning: Regress y onto Z to learn optimal combination of base models
  print("Metalearning")
  if (is.numeric(seed)) set.seed(seed)  #If seed given, set seed prior to next step
  if (grepl("^SL.", metalearner)) {
    # this is very hacky and should be used only for testing until we get the h2o metalearner functions sorted out...
    if (is.character(family)) {
      familyFun <- get(family, mode = "function", envir = parent.frame())
      #print(familyFun$family)  #does not work for SL.glmnet
    } 
    Ztmp <- Z[, -which(names(Z) %in% c("fold_id", y))]
    runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(Y = as.data.frame(training_frame[,c(y)])[,1], X = Ztmp, newX = Ztmp, 
                                                                          family = familyFun, id = seq(N), obsWeights = rep(1,N)), gcFirst = FALSE)
  } else {
    # Convert Z to H2OParsedData object (should remove when .make_Z is modified to create the H2OParsedData object directly)
    #Z.hex <- as.h2o(Z, conn = localH2O, destination_frame = "Z.hex")
    Z.hex <- as.h2o(Z, destination_frame = "Z.hex")
    runtime$metalearning <- system.time(metafit <- match.fun(metalearner)(x = learner, y = y, training_frame = Z.hex, family = family), gcFirst=FALSE)
  }
    
  # Fit the final L models on all data to be saved with the fit
  # As above, this parallel code should probably be modified to use doPar or similar to replace all the if/else
  # Also, we should not "FORK"...
  print("Fitting final base models on full data")
  if (inherits(parallel, "cluster")) {
    #If the parallel object is a snow cluster
    fitlist <- parSapply(cl=parallel, X=1:L, FUN=.fitWrapper, y=y, xcols=x, training_frame=training_frame,
                         validation_frame = validation_frame, family=family, learner=learner, seed=seed, simplify=FALSE)    
  } else if (parallel=="multicore") {
    cl <- makeCluster(detectCores(), type="FORK") 
    fitlist <- parSapply(cl=cl, X=1:L, FUN=.fitWrapper, y=y, xcols=x, training_frame=training_frame,
                         validation_frame = validation_frame, family=family, learner=learner, seed=seed, simplify=FALSE)
    stopCluster(cl)
  } else {
    fitlist <- sapply(X=1:L, FUN=.fitWrapper, y=y, xcols=x, training_frame=training_frame,
                      validation_frame = validation_frame, family=family, learner=learner, seed=seed, simplify=FALSE)
  } 
  runtime$baselearning <- lapply(fitlist, function(ll) ll$fittime)
  names(runtime$baselearning) <- learner
  basefits <- lapply(fitlist, function(ll) ll$fit)  #Base fits (trained on full data) to be saved
  names(basefits) <- learner      
  runtime$total <- Sys.time() - starttime
  
  # Ensemble model
  out <- list(x = x,
              y = y, 
              family = family, 
              cvControl = cvControl,
              folds = folds,
              ylim = ylim, 
              seed = seed,
              parallel = parallel,
              basefits = basefits, 
              metafit = metafit,
              Z = Z, 
              runtime = runtime,
              h2o_version = packageVersion(pkg = "h2o"),
              h2oEnsemble_version = packageVersion(pkg = "h2oEnsemble"))
  class(out) <- "h2o.ensemble"
  return(out)
}



# Helper function for .make_Z:
# Identify the v^th training/test indices and the l^th learner,
# then train a model and generate predictions on the test set
.cvFun <- function(i, idxs, xcols, y, training_frame, family, learner, seed) {
  # Note: Using arg named 'xcols' instead of 'x' to avoid name collisions with clusterApply
  print(sprintf("Cross-validating learner %s: fold %s", idxs$l[i], idxs$v[i]))
  #train_idxs <- as.numeric(training_frame$fold_id!=idxs$v[i])  #cv validation fold indexes, but this is just T/F, so need to change?
  #train_tf <- as.logical(as.data.frame(train_idxs)[,1])
  train_hex <- h2o.assign(training_frame[training_frame$fold_id!=idxs$v[i],], "train_hex")
  test_hex <- h2o.assign(training_frame[training_frame$fold_id==idxs$v[i],], "test_hex")
  
  if (is.numeric(seed)) set.seed(seed)  #If seed is specified, set seed prior to next step
  fit <- match.fun(learner[idxs$l[i]])(y = y, x = xcols, training_frame = train_hex, validation_frame = test_hex, family = family, link = "family_default")
     
  # TO DO: Get preds directly from `fit` object above
  # Currently we will pull the `preds` vec into R, but this should be updated later
  #test_idxs <- training_frame$fold_id==idxs$v[i]
  if (family %in% c("binomial","bernoulli")) {
     preds <- as.data.frame(h2o.predict(fit, test_hex))$p1
  } else {
    # TO DO: check this
    preds <- as.data.frame(h2o.predict(fit, test_hex))$predict
  }
  # Note: column subsetting not supported yet in H2OParsedData object however, 
  # if we can enable that, then it is probably better to insert the preds into 
  # a H2OParsedData object instead of returning 'preds' and bringing into R memory.
  #Z[Z$fold_id==v,l] <- as.data.frame(h2o.predict(fit, data[data$fold_id==v]))$X1
  return(preds)
  #invisible(TRUE)
}            


# Generate the CV predicted values for all learners
.make_Z <- function(x, y, training_frame, family, learner, parallel, seed, V, L, idxs) {
  
  # TO DO: Modify to create H2OParsedData object instead of R data.frame.
  # Create Z matrix so we can fill it in later
  Zdf <- as.data.frame(matrix(0, nrow(training_frame), L))  # Therefore, instead we will fill in with zeros
  Zdf$fold_id <- as.data.frame(training_frame$fold_id)[,1]
  
  # should swap if/else clutter below for doPar or similar
  if (inherits(parallel, "cluster")) {
    #If the parallel object is a snow cluster
    require(parallel)
    cvRes <- parSapply(cl=parallel, X=seq(V*L), FUN=.cvFun, idxs=idxs, xcols=x, y=y, training_frame=training_frame, family=family,
                       learner=learner, seed=seed, simplify=FALSE)       
  } else if (parallel=="multicore") {
    require(parallel)
    cl <- makeCluster(detectCores(), type="FORK")  #May update in future to avoid copying all objects in memory
    cvRes <- parSapply(cl=cl, X=seq(V*L), FUN=.cvFun, idxs=idxs, xcols=x, y=y, training_frame=training_frame, family=family,
                       learner=learner, seed=seed, simplify=FALSE) 
    stopCluster(cl)
  } else {
    cvRes <- sapply(X=seq(V*L), FUN=.cvFun, idxs=idxs, xcols=x, y=y, training_frame=training_frame, family=family,
                    learner=learner, seed=seed, simplify=FALSE)  
  }
  #TO DO: Maybe change this step, this is clunky...
  for (i in seq(V*L)) {
    Zdf[Zdf$fold_id==idxs$v[i],idxs$l[i]]  <- cvRes[[i]]
  }
  names(Zdf) <- c(learner, "fold_id")
  # Regarding Z assignment commented below: When converting this h2o object to a data.frame later, 
  # it gets messed up...returning memory address instead of predicted value for h2o.gbm, for example
  # Z <- as.h2o(localH2O, Zdf, key="Z")
  # return(Z)  
  # Therefore, for now, we will return Z as an R data.frame.  
  # This should be fixed though, so we don't have to pull the Z matrix into R memory
  Zdf[,c(y)] <- as.data.frame(training_frame[,c(y)])[,1]  #Concat outcome column to Z
  if (family == "binomial") {
    Zdf[,c(y)] <- as.factor(Zdf[,c(y)])  #required because as.h2o does not currently preserve col types
  }
  return(Zdf)
}


# Train a model on full data for learner l 
.fitFun <- function(l, y, x, training_frame, validation_frame, family, learner, seed) {
  if (is.numeric(seed)) set.seed(seed)  #If seed given, set seed prior to next step
  fit <- match.fun(learner[l])(y=y, x=x, training_frame=training_frame, validation_frame = validation_frame, family=family)
  return(fit)
}


# Wrapper function for .fitFun to record system.time
.fitWrapper <- function(l, y, xcols, training_frame, validation_frame, family, learner, seed) {
  print(sprintf("Training base learner %s for final fit", l))
  fittime <- system.time(fit <- .fitFun(l, y, xcols, training_frame, validation_frame, family, 
                                        learner, seed), gcFirst=FALSE)
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


# TO DO:check if this is working
predict.h2o.ensemble <- function(object, newdata) {
  
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
  #basepred <- as.h2o(basepreddf, conn = localH2O, destination_frame ="basepred")
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




