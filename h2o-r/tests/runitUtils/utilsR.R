#'
#'
#' ----------------- Additional Runit Utilities -----------------
#'
#'

#' Hadoop helper
hadoop.namenode.is.accessible <- function() {
    url <- sprintf("http://%s:50070", HADOOP.NAMENODE);
    internal <- url.exists(url, timeout = 5)
    return(internal)
}

#' Locate a file given the pattern <bucket>/<path/to/file>
#' e.g. locate( "smalldata/iris/iris22.csv") returns the absolute path to iris22.csv
locate<-
function(pathStub, root.parent = NULL) {
  if (ON.HADOOP) {
    # Jenkins jobs create symbolic links to smalldata and bigdata on the machine that starts the test. However,
    # in an h2o multinode hadoop cluster scenario, the clustered machines don't know about the symbolic link.
    # Consequently, `locate` needs to return the actual path to the data on the clustered machines. ALL jenkins
    # machines store smalldata and bigdata in /home/0xdiag/. If ON.HADOOP is set by the run.py, the pathStub arg MUST
    # be an immediate subdirectory of /home/0xdiag/. Moreover, the only guaranteed subdirectories of /home/0xdiag/ are
    # smalldata and bigdata.
    path <- normalizePath(paste0("/home/0xdiag/",pathStub))
    if (!file.exists(path)) stop(paste("Could not find the dataset: ", path, sep = ""))
    return(path)
  }
  pathStub <- clean(pathStub)
  bucket <- pathStub[1]
  offset <- pathStub[-1]
  cur.dir <- getwd()

  #recursively ascend until `bucket` is found
  bucket.abspath <- path.compute(cur.dir, bucket, root.parent)
  if (length(offset) != 0) return(paste(c(bucket.abspath, offset), collapse = "/", sep = "/"))
  else return(bucket.abspath)
}

#' Clean a path up: change \ -> /; remove starting './'; split
clean<-
function(p) {
  if (.Platform$file.sep == "/") {
    p <- gsub("[\\]", .Platform$file.sep, p)
    p <- unlist(strsplit(p, .Platform$file.sep))
  } else {
    p <- gsub("/", "\\\\", p)  # is this right?
    p <- unlist(strsplit(p, "\\\\"))
  }
  p
}

#' Compute a path distance.
#'
#' We are looking for a directory `root`. Recursively ascend the directory structure until the root is found.
#' If not found, produce an error.
#'
#' @param cur.dir: the current directory
#' @param root: the directory that is being searched for
#' @param root.parent: if not null, then the `root` must have `root.parent` as immediate parent
#' @return: Return the absolute path to the root.
path.compute<-
function(cur.dir, root, root.parent = NULL) {
  parent.dir  <- dirname(cur.dir)
  parent.name <- basename(parent.dir)

  # root.parent is null
  if (is.null(root.parent)) {

    # first check if cur.dir is root
    if (basename(cur.dir) == root) return(normalizePath(cur.dir))

    # next check if root is in cur.dir somewhere
    if (root %in% dir(cur.dir)) return(normalizePath(paste(cur.dir, .Platform$file.sep, root, sep = "")))

    # the root is the parent
    if (parent.name == root) return(normalizePath(paste(parent.dir, .Platform$file.sep, root, sep = "")))

    # the root is h2o-dev, check the children here (and fail if `root` not found)
    if (parent.name == PROJECT.ROOT || parent.name == "workspace") {
      if (root %in% dir(parent.dir)) return(normalizePath(paste(parent.dir, .Platform$file.sep, root, sep = "")))
      else stop(paste("Could not find the dataset bucket: ", root, sep = "" ))
    }
  # root.parent is not null
  } else {

    # first check if cur.dir is root
    if (basename(cur.dir) == root && parent.name == root.parent) return(normalizePath(cur.dir))

    # next check if root is in cur.dir somewhere (if so, then cur.dir is the parent!)
    if (root %in% dir(cur.dir) && root.parent == basename(cur.dir)) {
      return(normalizePath(paste(cur.dir, .Platform$file.sep, root, sep = ""))) }

    # the root is the parent
    if (parent.name == root && basename(dirname(parent.dir)) == root.parent) {
      return(path.compute(parent.dir, root, root.parent)) }

    # fail if reach h2o-dev
    if (parent.name == PROJECT.ROOT) {
        stop(paste0("Reached the root ", PROJECT.ROOT, ". Didn't find the bucket with the root.parent")) }
  }
  return(path.compute(parent.dir, root, root.parent))
}

#' Load a handful of packages automatically. Runit tests that require additional packages must be loaded explicitly
default.packages <-
function() {
  to_require <- c("jsonlite", "RCurl", "RUnit", "R.utils", "testthat", "ade4", "glmnet", "gbm", "ROCR", "e1071",
                  "tools", "statmod", "fpc", "cluster")
  if (Sys.info()['sysname'] == "Windows") {
    options(RCurlOptions = list(cainfo = system.file("CurlSSL", "cacert.pem", package = "RCurl"))) }
  invisible(lapply(to_require,function(x){require(x,character.only=TRUE,quietly=TRUE,warn.conflicts=FALSE)}))
}

read.zip<-
function(zipfile, exdir,header=T) {
  zipdir <- exdir
  unzip(zipfile, exdir=zipdir)
  files <- list.files(zipdir)
  file <- paste(zipdir, files[1], sep="/")
  read.csv(file,header=header)
}

# returns the directory of the sandbox for the given test.
sandbox<-
function(create=FALSE) {
  Rsandbox <- paste0("./Rsandbox_", basename(TEST.NAME))
  if (create) { dir.create(Rsandbox, showWarnings=FALSE) }
  if (dir.exists(Rsandbox)) { return(normalizePath(Rsandbox))
  } else { Log.err(paste0("Sandbox directory: ",Rsandbox," does not exists")) }
}

# makes a directory in sandbox, one level down
sandboxMakeSubDir<-
function(dirname) {
  if (!is.character(dirname)) Log.err("dirname argument must be of type character")
  subdir <- file.path(sandbox(),dirname,fsep = "\\")
  dir.create(subdir, showWarnings=FALSE)
  return(subdir)
}

# renames sandbox subdir
sandboxRenameSubDir<-
function(oldSubDir,newSubDirName) {
  if (!is.character(oldSubDir)) Log.err("oldSubDir argument must be of type character")
  if (!is.character(newSubDirName)) Log.err("newSubDirName argument must be of type character")
  newSubDir <- file.path(sandbox(),newSubDirName)
  # Real trouble deleting a prior-existing newSubDir on Windows, that was filled with crap.
  # Calling system("rm -rf") seems to work, where calling unlink() would not.
  # Also renaming to an existing but empty directory does not work on windows.
  system(paste0("rm -rf ",newSubDir))
  res <- file.rename(oldSubDir, newSubDir)
  if( !res ) print(paste0("Warning: File rename failed FROM ",oldSubDir," TO ",newSubDir))
  return(newSubDir)
}

Log.info<-
function(m) {
  message <- paste("[INFO] : ",m,sep="")
  logging(message)
}

Log.warn<-
function(m) {
  logging(paste("[WARN] : ",m,sep=""))
  traceback()
}

Log.err<-
function(m) {
  logging(paste("[ERROR] : ",m,sep=""))
  logging("[ERROR] : TEST FAILED")
  traceback()
}

logging<-
function(m) {
  cat(sprintf("[%s] %s\n", Sys.time(),m))
}

PASS_BANNER<-
function() {
  cat("\n")
  cat("########     ###     ######   ###### \n")
  cat("##     ##   ## ##   ##    ## ##    ##\n")
  cat("##     ##  ##   ##  ##       ##      \n")
  cat("########  ##     ##  ######   ###### \n")
  cat("##        #########       ##       ##\n")
  cat("##        ##     ## ##    ## ##    ##\n")
  cat("##        ##     ##  ######   ###### \n")
  cat("\n")
}

FAIL_BANNER<-
function() {
  cat("\n")
  cat("########    ###    #### ##       \n")
  cat("##         ## ##    ##  ##       \n")
  cat("##        ##   ##   ##  ##       \n")
  cat("######   ##     ##  ##  ##       \n")
  cat("##       #########  ##  ##       \n")
  cat("##       ##     ##  ##  ##       \n")
  cat("##       ##     ## #### ######## \n")
  cat("\n")
}

PASS<-
function() {
  PASS_BANNER()
  q("no",0,TRUE)
}

FAIL<-
function(e) {
  FAIL_BANNER()
  Log.err(e)
  q("no",1,TRUE) #exit with nonzero exit code
}

SKIP<-
function() {
  q("no",42,TRUE) #exit with nonzero exit code
}

WARN<-
function(w) {
  Log.warn(w)
}

#----------------------------------------------------------------------
# Print out a message with clear whitespace.
#
# Parameters:  x -- Message to print out.
#              n -- (optional) Step number.
#
# Returns:     none
#----------------------------------------------------------------------
heading <- function(x, n = -1) {
  Log.info("")
  Log.info("")
  if (n < 0) {
    Log.info(sprintf("STEP: %s", x))
  } else {
    Log.info(sprintf("STEP %2d: %s", n, x))
  }
  Log.info("")
  Log.info("")
}

#----------------------------------------------------------------------
# "Safe" system.  Error checks process exit status code.  stop() if it failed.
#
# Parameters:  x -- String of command to run (passed to system()).
#
# Returns:     none
#----------------------------------------------------------------------
safeSystem <- function(x) {
  print(sprintf("+ CMD: %s", x))
  res <- system(x)
  print(res)
  if (res != 0) {
    msg <- sprintf("SYSTEM COMMAND FAILED (exit status %d)", res)
    stop(msg)
  }
}

withWarnings <- function(expr) {
    myWarnings <- NULL
    wHandler <- function(w) {
        myWarnings <<- c(myWarnings, list(w))
        invokeRestart("muffleWarning")
    }
    val <- withCallingHandlers(expr, warning = wHandler)
    list(value = val, warnings = myWarnings)
    for(w in myWarnings) WARN(w)
}

cleanSummary <- function(mysum, alphabetical = FALSE) {
  # Returns string without leading or trailing whitespace
  trim <- function(x) { gsub("^\\s+|\\s+$", "", x) }
  
  lapply(1:ncol(mysum), { 
    function(i) {
      nams <- sapply(mysum[,i], function(x) { trim(unlist(strsplit(x, ":"))[1]) })
      vals <- sapply(mysum[,i], function(x) {
        numMatch <- sum(unlist(strsplit(x, "")) == ":")
        # If only one colon, then it contains numeric data
        # WARNING: This assumes categorical levels don't contain colons
        if(is.na(numMatch) || numMatch <= 1) {
          as.numeric(unlist(strsplit(x, ":"))[2])
        } else {
          # Otherwise, return a string for min/max/quantile
          tmp <- unlist(strsplit(as.character(x), ":"))[-1]
          paste(tmp, collapse = ":")
        }
      })
      names(vals) <- nams
      vals <- vals[!is.na(nams)]
      if(alphabetical) vals <- vals[order(names(vals))]
      return(vals)
    }
  })
}

checkSummary <- function(object, expected, tolerance = 1e-6) {
  sumR <- cleanSummary(expected, alphabetical = TRUE)
  sumH2O <- cleanSummary(object, alphabetical = TRUE)
  
  expect_equal(length(sumH2O), length(sumR))
  lapply(1:length(sumR), function(i) {
    vecR <- sumR[[i]]; vecH2O <- sumH2O[[i]]
    expect_equal(length(vecH2O), length(vecR))
    expect_equal(names(vecH2O), names(vecR))
    for(j in 1:length(vecR))
      expect_equal(vecH2O[j], vecR[j], tolerance = tolerance)
  })
}

genDummyCols <- function(df, use_all_factor_levels = TRUE) {
  NUM <- function(x) { x[,sapply(x, is.numeric)] }
  FAC <- function(x) { x[,sapply(x, is.factor)]  }
  FAC_LEVS <- function(x) { sapply(x, function(z) { length(levels(z)) })}
  
  df_fac <- data.frame(FAC(df))
  if(ncol(df_fac) == 0) {
    DF <- data.frame(NUM(df))
    names(DF) <- colnames(df)[which(sapply(df, is.numeric))]
  } else {
    if(!"ade4" %in% rownames(installed.packages())) install.packages("ade4")
    require(ade4)
    
    df_fac_acm <- acm.disjonctif(df_fac)
    if (!use_all_factor_levels) {
      fac_offs <- cumsum(c(1, FAC_LEVS(df_fac)))
      fac_offs <- fac_offs[-length(fac_offs)]
      df_fac_acm <- data.frame(df_fac_acm[,-fac_offs])
    }
    DF <- data.frame(df_fac_acm, NUM(df))
    fac_nams <- mapply(function(x, cname) { 
      levs <- levels(x)
      if(!use_all_factor_levels) levs <- levs[-1]
      paste(cname, levs, sep = ".") }, 
      df_fac, colnames(df)[which(sapply(df, is.factor))])
    fac_nams <- as.vector(unlist(fac_nams))
    fac_range <- 1:ncol(df_fac_acm)
    names(DF)[fac_range] <- fac_nams
    
    if(ncol(NUM(df)) > 0) {
      num_range <- (ncol(df_fac_acm)+1):ncol(DF)
      names(DF)[num_range] <- colnames(df)[which(sapply(df, is.numeric))]
    }
  }
  
  return(DF)
}

alignData <- function(df, center = FALSE, scale = FALSE, ignore_const_cols = TRUE, use_all_factor_levels = TRUE) {
  df.clone <- df
  is_num <- sapply(df.clone, is.numeric)
  if(any(is_num)) {
    df.clone[,is_num] <- scale(df.clone[,is_num], center = center, scale = scale)
    df.clone <- df.clone[, c(which(!is_num), which(is_num))]   # Move categorical column to front
  }
  
  if(ignore_const_cols) {
    is_const <- sapply(df.clone, function(z) { var(z, na.rm = TRUE) == 0 })
    if(any(is_const))
      df.clone <- df.clone[,!is_const]
  }
  genDummyCols(df.clone, use_all_factor_levels)
}

doTest<-
function(testDesc, test) {
    tryCatch(test_that(testDesc, withWarnings(test())), warning = function(w) WARN(w), error =function(e) FAIL(e))
    PASS()
}

setupSeed<-
function(seed = NULL, master_seed = FALSE) {
    possible_seed_path <- paste("./Rsandbox_", TEST.NAME, "/seed", sep = "")

    if (!is.null(seed)) {
        SEED <<- seed
        set.seed(seed)
        write.table(seed, possible_seed_path)
        cat("\n\n\n", paste("[INFO]: Using master SEED: ", seed), "\n\n\n\n")
    } else if (file.exists(possible_seed_path)) {
        fileseed <- read.table(possible_seed_path)[[1]]
        SEED <<- fileseed
        set.seed(fileseed)
        cat("\n\n\n", paste("[INFO]: Reusing seed for this test from test's Rsandbox", fileseed), "\n\n\n\n")
    } else {
        maxInt <- .Machine$integer.max
        seed <- sample(maxInt, 1)
        SEED <<- seed
        set.seed(seed)
        write.table(seed, possible_seed_path)
        cat("\n\n\n", paste("[INFO]: Generating new random SEED: ", seed), "\n\n\n\n")
    }
    Log.info(paste("USING SEED: ", SEED))
}

h2o_and_R_equal <- function(h2o_obj, r_obj, tolerance = 1e-6) {
  df_h2o_obj <- as.data.frame(h2o_obj)
  df_r_obj <- as.data.frame(r_obj)
  expect_equal(length(df_h2o_obj), length(df_r_obj))
  
  #Check NAs are in same places 
  df_h2o_nas <- is.na(df_h2o_obj)
  df_r_nas <- is.na(df_r_obj)
  expect_true(all(df_h2o_nas == df_r_nas))
  
  #Check non-NAs are same vals
  df_h2o_obj_free <- df_h2o_obj[!df_h2o_nas]
  df_r_na_free <- df_r_obj[!df_r_nas]
  
  expect_equal(length(df_h2o_obj_free), length(df_r_na_free))
  if (length(df_r_na_free) > 0)
    expect_true(all(abs(df_h2o_obj_free - df_r_na_free) < tolerance))
  
}

#----------------------------------------------------------------------
# genRegressionData generate a random data set according to the following formula
# y = W * X + e where e is random Gaussian noise, W is randomly generated and
# X is the randomly generated predictors
#
# Parameters:  col_number -- Integer, number of predictors
#              row_number -- Integer, number of training data samples
#              max_w_value -- maximum weight/bias value allowed
#              min_w_value -- minimum weight/bias value allowed
#              max_p_value -- maximum predictor value allowed
#              min_p_value -- minimum predictor value allowed
#              noise_std -- noise standard deviation that is used to generate random noise
#
# Returns:     data frame containing the predictors and response as the last column
#----------------------------------------------------------------------
genRegressionData <- function(col_number, row_number, max_w_value, min_w_value, max_p_value, min_p_value, noise_std) {

  # generate random predictor
  data = matrix(runif(col_number*row_number, min_p_value, max_p_value), row_number, col_number)
  weight = matrix(runif(col_number, min_w_value, max_w_value), col_number, 1)  # generate random weight
  noise = matrix(rnorm(row_number, mean=0, sd=noise_std), row_number, 1)        # generate random noise
  bias = matrix(rep(runif(1, min_w_value, max_w_value), row_number), row_number, 1)   # random bias

  response = data %*% weight + bias + noise   # form the response

  training_data = as.data.frame(cbind(data, response))  # generate data frame from predictor and response

  return(training_data)
}


#----------------------------------------------------------------------
# genBinaryData generates training data set for Binomial
# classification for GLM algo.  For the Binomial family, the relationship between
# the response Y and predictor vector X is assumed to be
# Prob(Y = 1|X) = exp(W^T * X + e)/(1+exp(W^T * X + e))
# where e is the random Gaussian noise added to the response.
#
# Parameters:  col_number -- Integer, number of predictors
#              row_number -- Integer, number of training data samples
#              max_w_value -- maximum weight/bias value allowed
#              min_w_value -- minimum weight/bias value allowed
#              max_p_value -- maximum predictor value allowed
#              min_p_value -- minimum predictor value allowed
#              noise_std -- noise standard deviation that is used to generate random noise
#
# Returns:     data frame containing the predictors and response as the last column.  The
#              response in this case is integer starting from 0 to class_number-1
#----------------------------------------------------------------------
genBinaryData <- function(col_number, row_number, max_w_value, min_w_value, max_p_value, min_p_value, noise_std) {
  data = matrix(runif(col_number*row_number, min_p_value, max_p_value), row_number, col_number)
  weight = matrix(runif(col_number, min_w_value, max_w_value), col_number, 1)  # generate random weight
  noise = matrix(rnorm(row_number, mean=0, sd=noise_std), row_number, 1)        # generate random noise
  bias = matrix(rep(runif(1, min_w_value, max_w_value), row_number), row_number, 1)   # random bias

  temp = exp(data %*% weight + bias + noise)   # form the response
  prob1 = temp / (1+temp)   # form probability of y=1

  # calculate response as class with maximum probability
  response = matrix(0, row_number, 1)
  response[prob1>0.5] = 1

  training_data = as.data.frame(cbind(data, response))  # generate data frame from predictor and response

  return(training_data)
}

#----------------------------------------------------------------------
# hyperSpaceDimension calculate the possible number of gridsearch model
# that should be built based on the current hyper-space parameters specified.
# However, if your model contains bad parameter values, the actual number of
# models that can be built will be less.  You should take care of that yourself.
# Hence, this function will give you an upper bound of the actual model number.
#
# Parameters:  hyper_parameters -- Integer, number of .
#
# Returns:     integer representing the upper bound on number of grid search
# models that can be generated
#----------------------------------------------------------------------
hyperSpaceDimension <- function(hyper_parameters) {
  num_param = length(hyper_parameters)
  total_dim = 1

  for (index in 1:num_param) {
    total_dim = total_dim * length(hyper_parameters[[index]])
  }

  return(total_dim)
}

#----------------------------------------------------------------------
# This function given a grid_id list built by gridsearch will grab the model and
# go into the model and calculate the total amount of
# time it took to actually build all the models in second
#
# :param model_list: list of model built by gridsearch, cartesian or randomized
# :return: total_time_sec: total number of time in seconds in building all the models
#
# Parameters:  model_ids: list of model ids from which we can grab the actual model info
#
# Returns:     total_time_sec: total number of time in seconds in building all the models
#----------------------------------------------------------------------
find_grid_runtime <- function(model_ids) {
  total_run_time = 0

  all_models = lapply(model_ids, function(id) {model = h2o.getModel(id)})

  for (model in all_models) {   # run_time is in ms
    total_run_time = total_run_time + model@model$run_time

    # get run time of cross-validation
    for (xv_model in model@model$cross_validation_models) {
      temp_model = h2o.getModel(xv_model$name)
      total_run_time = total_run_time + temp_model@model$run_time
    }
  }

  return(total_run_time/1000.0)
}

#----------------------------------------------------------------------
# runMetricStop run the randomized gridsearch with values specified in the function argument lists and
# return true if the test passed or false if the test failed.  The metric stopping condition will be manually
# calculated and compare to the results returned by Java.
#
# Parameters:  predictor_names -- list of structures that contains all hyper-parameter specifications
#              response_name -- Integer, denoting how to generate model parameter value
#              train_data
#              family -- string, denoting family for GLM algo 'binomial' or 'gaussian'
#              nfolds -- integer, number of folds for CV
#              hyper_parameters -- equivalent to Python dict containing hyper-parameters for gridsearch
#              search_criteria -- equivalent to Python dict representing parameters passed to randomized gridsearch
#              is_decreasing -- boolean: true if metric is optimized by decreasing and vice versa
#              possible_model_number -- integer, possible number of grid search model built with currenty hyper-parameter
#
# Returns:     boolean representing test success/failure
#----------------------------------------------------------------------
runGLMMetricStop <- function(predictor_names, response_name, train_data, family, nfolds, hyper_parameters,
                             search_criteria, is_decreasing, possible_model_number, grid_name) {

  # start grid search
  glm_grid1 = h2o.grid("glm", grid_id=grid_name, x=predictor_names, y=response_name, training_frame=train_data,
                       family=family, nfolds=nfolds, hyper_params=hyper_parameters, search_criteria=search_criteria)

  tolerance = search_criteria[["stopping_tolerance"]]
  stop_round = search_criteria[["stopping_rounds"]]
  num_models_built = length(glm_grid1@model_ids)

  min_list_len = 2*stop_round
  metric_list = c()
  stop_now = FALSE

  # sort model_ids built by time, oldest one first
  sorted_model_metrics = sort_model_metrics_by_time(glm_grid1@model_ids, search_criteria[["stopping_metric"]])

  for (metric_value in sorted_model_metrics) {
    metric_list = c(metric_list, metric_value)

    if (length(metric_list) > min_list_len) {   # start processing when you have enough models
      stop_now = evaluate_early_stopping(metric_list, stop_round, tolerance, is_decreasing)
    }

    if (stop_now) {
      if (length(metric_list) < num_models_built) {
        
        Log.info("number of models built by gridsearch: ")
        print(num_models_built)
        Log.info("number of models built proposed by stopping metrics: ")
        print(length(metric_list))
        
        return(FALSE)
      } else {
        return(TRUE)
      }
    } 
  }

  if (length(metric_list) == possible_model_number) {
    return(TRUE)
  } else {
    return(FALSE)
  }
}

#----------------------------------------------------------------------
# evaluate_early_stopping mimics the early stopping function as implemented in ScoreKeeper.java.
# Please see the Java file comment to see the explanation of how the early stopping works.
#
# Parameters: metric_list: list containing the optimization metric under consideration for gridsearch model
#             stop_round:  integer, determine averaging length
#             tolerance:   real, tolerance to see if the grid search model has improved enough to stop
#             is_decreasing:    bool: True if metric is optimized as it gets smaller and vice versa
#
# Returns:    bool indicating if we should stop early and sorted metric_list
#----------------------------------------------------------------------
evaluate_early_stopping <- function(metric_list, stop_round, tolerance, is_decreasing) {

  metric_len = length(metric_list)
  metric_list = sort(metric_list, decreasing=!(is_decreasing))
  
  start_len = 2*stop_round
  
  bestInLastK = mean(metric_list[1:stop_round])
  lastBeforeK = mean(metric_list[(stop_round+1):start_len])

  if (!(sign(bestInLastK)) == sign(lastBeforeK))
    return(FALSE)
  
  ratio = bestInLastK/lastBeforeK
  
  if (is.nan(ratio))
    return(FALSE)
  
  if (is_decreasing)
    return(!(ratio < (1-tolerance)))
  else
    return(!(ratio > (1+tolerance)))
}

#----------------------------------------------------------------------
# sort_model_metrics_by_time will sort the model by time.  The oldest model will come first.
# Next, it will build a list containing the metrics of the oldest model first followed by
# later model metrics.
#
# Parameters:  model_ids -- list of string containing model id which we can get a model out of
#              metric_name -- string, denoting the metric's name that we are optimizing over.
#
# Returns:     metric_list : list of metrics value sorted by time, oldest metric will come first
#----------------------------------------------------------------------
sort_model_metrics_by_time <- function(model_ids, metric_name) {
  all_models = lapply(model_ids, function(id) {model = h2o.getModel(id)})
  sorted_metrics = rep(0, length(model_ids))
  m_index = 1

  for (model_id in model_ids) {
    # find id of the model, starting from 0
    temp_list = strsplit(model_id, '_')[[1]]
    index = as.integer(temp_list[length(temp_list)])
    the_model = all_models[m_index][[1]]
    m_index = m_index + 1
    # get the metric value and put it in right place
    sorted_metrics[index+1] = the_model@model$cross_validation_metrics@metrics[[metric_name]]
  }

  return(sorted_metrics)
}

#----------------------------------------------------------------------
# summarize_failures will generate a failure message describing what tests for the
# randomized grid search has failed.  There are four tests conducted for randomized
# grid search.  A test_failed_array is passed as argument.  A failed test will have
# a value of 1.
#
# Parameters:  test_failed_array -- list of integer denoting if a test fail or pass.
#
# Returns:     failure_message : text describing failure messages
#----------------------------------------------------------------------
summarize_failures <- function(test_failed_array) {
  failure_message = ""
  if (test_failed_array[1] == 1)
    failure_message = "test 1 failed"

  if (test_failed_array[2] == 1)
    failure_message = paste(failure_message, "test 2 test max_models stopping condition failed", sep = ", ")

  if (test_failed_array[3] == 1)
    failure_message = paste(failure_message, "test 3 test max_runtime_secs stopping condition failed", sep = ", ")

  if (test_failed_array[4] == 1)
    failure_message = paste(failure_message, "test 4 test decreasing stopping metric failed", sep = ", ")

  if (test_failed_array[5] == 1)
    failure_message = paste(failure_message, "test 5 test increasing stopping metric failed", sep = ", ")

  return(failure_message)
}

#----------------------------------------------------------------------
# This function will compare a 2-D table results as a matrix
#
# Parameters:  table1, table2: 2D table from Java passed to R
#
# Returns:     Exception will be thrown if comparison failed
#----------------------------------------------------------------------
compare_tables <- function(table1, table2, tol=1e-6) {
  dim1 = dim(table1)
  dim2 = dim(table2)

  expect_equal(dim1, dim2)

  for (i in 1:dim1[1]) {
    for (j in 1:dim1[2]) {
      expect_equal(TRUE, (abs(table1[i,j]-table2[i,j]) < tol))
    }
  }
}

#----------------------------------------------------------------------
# This function will generate a random dataset for regression/binomial
# and multinomial.  Copied from Pasha.
#
# Parameters:  response_type should be "regression", "binomial" or "multinomial"
#----------------------------------------------------------------------
random_dataset <-
  function(response_type,
           max_row = 25000,
           min_row = 15000,
           max_col = 100,
           min_col = 20,
           testrow = 1000) {
    num_rows <- round(runif(1, min_row, max_row))
    num_cols <- round(runif(1, min_col, max_col))
    if (response_type == 'regression') {
      response_num = 1
    } else if (response_type == 'binomial') {
      response_num = 2
    } else {
      # assume all else as multinomial
      response_num = round(runif(1, 3, 10))
    }
    
    # generate all the fractions
    fractions <-
      c(runif(1, 0, 1),
        runif(1, 0, 1),
        runif(1, 0, 1),
        runif(1, 0, 1),
        runif(1, 0, 1))
    fractions <- fractions / sum(fractions)
    random_frame <-
      h2o.createFrame(
        rows = num_rows,
        cols = num_cols,
        randomize = TRUE,
        has_response = TRUE,
        categorical_fraction = fractions[1],
        integer_fraction = fractions[2],
        binary_fraction = fractions[3],
        time_fraction = fractions[4],
        string_fraction = 0,
        response_factors = response_num,
        missing_fraction = runif(1, 0, 0.05)
      )
    
    return(random_frame)
  }

#----------------------------------------------------------------------
# This function will generate a random dataset containing enum columns only.
# Copied from Pasha.
#
# Parameters:  denote factor range
#----------------------------------------------------------------------
random_dataset_enum_only <-
function(numFactors, num_rows, num_cols) {

  random_frame <-
  h2o.createFrame(
  rows = num_rows,
  cols = num_cols,
  randomize = TRUE,
  has_response = FALSE,
  categorical_fraction = 1,
  integer_fraction = 0,
  binary_fraction = 0,
  time_fraction = 0,
  string_fraction = 0,
  factor = numFactors,
  missing_fraction = runif(1, 0, 0.05)
  )

  return(random_frame)
}

#----------------------------------------------------------------------
# This function will generate a random dataset containing real and integer columns only.
# Copied from Pasha.
#
# Parameters:  denote factor range
#----------------------------------------------------------------------
random_dataset_numerics_only <-
function(integerRange, num_rows, num_cols) {

  random_frame <-
  h2o.createFrame(
  rows = num_rows,
  cols = num_cols,
  randomize = TRUE,
  has_response = FALSE,
  categorical_fraction = 0,
  integer_fraction = 0.9,
  binary_fraction = 0,
  time_fraction = 0,
  string_fraction = 0,
  integer_ranger = integerRange,
  missing_fraction = runif(1, 0, 0.05)
  )

  return(random_frame)
}

#----------------------------------------------------------------------
# This function will generate a random neural network in the form of
# a hidden layer matrix specifying the number of nodes per layer.
#
# Parameters:  actFunc is the activation function of the neural network
#----------------------------------------------------------------------
random_NN <- function(actFunc, max_layers, max_node_number) {
  # generate random neural network architecture
  no_hidden_layers <- round(runif(1, 1, max_layers))
  hidden <- c()
  hiddenDropouts <- c()
  for (ind in 1:no_hidden_layers) {
    hidden <- c(hidden, round(runif(1, 1, max_node_number)))
    
    if (grepl('Dropout', actFunc, fixed = TRUE)) {
      hiddenDropouts <- c(hiddenDropouts, runif(1, 0, 0.1))
      
    }
  }
  return(list("hidden" = hidden, "hiddenDropouts" = hiddenDropouts))
}

#----------------------------------------------------------------------
# This function will compare two frames and make sure they are equal.
# However, the frames must contain columns that can be converted to
# numeric.  The column names are not compared.
#
# Parameters:  frame1, frame2: H2O frames to be compared.
#              tolerance: tolerance of comparison
#----------------------------------------------------------------------
compareFrames <- function(frame1, frame2, prob=0.5, tolerance=1e-6) {
  expect_true(nrow(frame1) == nrow(frame2) && ncol(frame1) == ncol(frame2), info="frame1 and frame2 are different in size.")
  for (colInd in range(1, ncol(frame1))) {
    temp1=as.numeric(frame1[,colInd])
    temp2=as.numeric(frame2[,colInd])
    for (rowInd in range(1,nrow(frame1))) {
      if (runif(1,0,1) < prob)
        if (is.na(temp1[rowInd, 1])) {
          expect_true(is.na(temp2[rowInd, 1]), info=paste0("Errow at row ", rowInd, ". Frame is value is na but Frame 2 value is ", temp2[rowInd,1]))
        } else {
          expect_true((abs(temp1[rowInd,1]-temp2[rowInd,1])/max(1,abs(temp1[rowInd,1]), abs(temp2[rowInd,1])))< tolerance, info=paste0("Error at row ", rowInd, ". Frame 1 value ", temp1[rowInd,1], ". Frame 2 value ", temp2[rowInd,1]))
        }
    }
  }
}

assertCorrectSkipColumns <-
  function(inputFileName, f1R,
           skip_columns,
           use_import, allFrameTypes) {
    if (use_import) {
      wholeFrame <<-
        h2o.importFile(inputFileName, skipped_columns = skip_columns)
    } else  {
      wholeFrame <<-
        h2o.uploadFile(inputFileName, skipped_columns = skip_columns)
    }

    expect_true(h2o.nrow(wholeFrame)==nrow(f1R))
    cfullnames <- names(f1R)
    f2R <- as.data.frame(wholeFrame)
    cskipnames <- names(f2R)
    skipcount <- 1
    rowNum <- h2o.nrow(f1R)
    for (ind in c(1:length(cfullnames))) {
      if (cfullnames[ind] == cskipnames[skipcount]) {
        if (allFrameTypes[ind]=="uuid")
          continue
        for (rind in c(1:rowNum)) {
          if (is.na(f1R[rind, ind]))
            expect_true(is.na(f2R[rind, skipcount]), info=paste0("expected NA but received: ", f2R[rind, skipcount], " in row: ", rind, " with column name: ", cfullnames[ind], " and skipped column name ", cskipnames[skipcount], sep=" "))
          else if (is.numeric(f1R[rind, ind])) {
            if (allFrameTypes[ind]=='time')
              expect_true(abs(f1R[rind, ind]-f2R[rind, skipcount])<10, info=paste0("expected: ", f1R[rind, ind], " but received: ", f2R[rind, skipcount], " in row: ", rind, " with column name: ", cfullnames[ind], " and skipped column name ", cskipnames[skipcount], sep=" "))

            else
              expect_true(abs(f1R[rind, ind]-f2R[rind, skipcount])<1e-10, info=paste0("expected: ", f1R[rind, ind], " but received: ", f2R[rind, skipcount], " in row: ", rind, " with column name: ", cfullnames[ind], " and skipped column name ", cskipnames[skipcount], sep=" "))
          } else
            expect_true(f1R[rind, ind] == f2R[rind, skipcount], info=paste0("expected: ", f1R[rind, ind], " but received: ", f2R[rind, skipcount], " in row: ", rind, " with column name: ", cfullnames[ind], " and skipped column name ", cskipnames[skipcount], sep=" "))
        }
        skipcount <- skipcount + 1
        if (skipcount > h2o.ncol(f2R))
          break
      }
    }
    print("Test completed!")
  }

compareFramesSVM <- function(f1, f2Svm, prob=0.5, tolerance=1e-6) {
  frame1 <- as.data.frame(f1)
  frame2 <- as.data.frame(f2Svm)

  expect_true(nrow(frame1) == nrow(frame2) && ncol(frame1) == ncol(frame2), info="frame1 and frame2 are different in size.")

  for (colInd in range(1, ncol(frame1))) {
    temp1=frame1[,colInd]
    temp2=frame2[,colInd]
    for (rowInd in range(1,nrow(frame1))) {
      if (runif(1,0,1) < prob)
      if (is.na(temp1[rowInd])) {
        expect_true(abs(temp2[rowInd])<tolerance, info=paste0("Errow at row ", rowInd, ". Frame is value is na but Frame 2 value is ", temp2[rowInd]))
      } else {
        expect_true((abs(temp1[rowInd]-temp2[rowInd])/max(1,abs(temp1[rowInd]), abs(temp2[rowInd])))< tolerance, info=paste0("Error at row ", rowInd, ". Frame 1 value ", temp1[rowInd], ". Frame 2 value ", temp2[rowInd]))
      }
    }
  }
}

compareStringFrames <- function(frame1, frame2, prob=0.5) {
  expect_true(nrow(frame1) == nrow(frame2) && ncol(frame1) == ncol(frame2), info="frame1 and frame2 are different in size.")
  dframe1 <- as.data.frame(frame1)
  dframe2 <- as.data.frame(frame2)
  cnames1 <- names(dframe1)
  cnames2 <- names(dframe2)
  for (colInd in range(1, ncol(frame1))) {
    temp1 <- dframe1[cnames1[colInd]]
    temp2 <- dframe2[cnames2[colInd]]
    if (runif(1,0,1)< prob)
      expect_true(sum(temp1==temp2)==nrow(frame1), info=paste0("Errow at column ", colInd, ". Frame is value is ", temp1, " , but Frame 2 value is ", temp2))
  }
}

calAccuracy <- function(rframe1, rframe2) {
  correctC = 0.0
  fLen = length(rframe1)
  for (ind in c(1:fLen)) {
    if (rframe1[ind]==rframe2[ind]) {
      correctC = correctC+1.0
    }
  }
  return(correctC/fLen)
}

buildModelSaveMojoTrees <- function(params, model_name) {
  if (model_name == "glm") {
    model <- do.call("h2o.gbm", params)
  } else {
    model <- do.call("h2o.randomForest", params)
  }
  model_key <- model@model_id
  tmpdir_name <- filePath(sandbox(), as.character(Sys.getpid()), fsep=.Platform$file.sep)
  if (.Platform$OS.type == "windows") {
    shell(sprintf("C:\\cygwin64\\bin\\rm.exe -fr %s", normalizePath(tmpdir_name)))
    shell(sprintf("C:\\cygwin64\\bin\\mkdir.exe -p %s", normalizePath(tmpdir_name)))
  } else {
    safeSystem(sprintf("rm -fr %s", tmpdir_name))
    safeSystem(sprintf("mkdir -p %s", tmpdir_name))
  }
  h2o.saveMojo(model, path = tmpdir_name, force = TRUE) # save mojo
  h2o.saveModel(model, path = tmpdir_name, force=TRUE) # save model to compare mojo/h2o predict offline

  return(list("model"=model, "dirName"=tmpdir_name))
}

buildModelSaveMojoGLM <- function(params) {
  model <- do.call("h2o.glm", params)
  model_key <- model@model_id
  tmpdir_name <- sprintf("%s/tmp_model_%s", sandbox(), as.character(Sys.getpid()))
  if (.Platform$OS.type == "windows") {
    shell(sprintf("C:\\cygwin64\\bin\\rm.exe -fr %s", normalizePath(tmpdir_name)))
    shell(sprintf("C:\\cygwin64\\bin\\mkdir.exe -p %s", normalizePath(tmpdir_name)))
  } else {
    safeSystem(sprintf("rm -fr %s", tmpdir_name))
    safeSystem(sprintf("mkdir -p %s", tmpdir_name))
  }
  h2o.saveMojo(model, path = tmpdir_name, force = TRUE) # save mojo
  h2o.saveModel(model, path = tmpdir_name, force=TRUE) # save model to compare mojo/h2o predict offline

  return(list("model"=model, "dirName"=tmpdir_name))
}

buildModelSaveMojoGLRM <- function(params) {
  model <- do.call("h2o.glrm", params)
  model_key <- model@model_id
  tmpdir_name <- sprintf("%s/tmp_model_%s", sandbox(), as.character(Sys.getpid()))
  if (.Platform$OS.type == "windows") {
    shell(sprintf("C:\\cygwin64\\bin\\rm.exe -fr %s", normalizePath(tmpdir_name)))
    shell(sprintf("C:\\cygwin64\\bin\\mkdir.exe -p %s", normalizePath(tmpdir_name)))
  } else {
    safeSystem(sprintf("rm -fr %s", tmpdir_name))
    safeSystem(sprintf("mkdir -p %s", tmpdir_name))
  }
  h2o.saveMojo(model, path = tmpdir_name, force = TRUE) # save mojo
  h2o.saveModel(model, path = tmpdir_name, force=TRUE) # save model to compare mojo/h2o predict offline

  return(list("model"=model, "dirName"=tmpdir_name))
}

mojoH2Opredict<-function(model, tmpdir_name, filename, get_leaf_node_assignment=FALSE, glrmReconstruct=FALSE) {
  newTest <- h2o.importFile(filename)
  predictions1 <- h2o.predict(model, newTest)

  a = strsplit(tmpdir_name, '/')
  endIndex <-(which(a[[1]]=="h2o-r"))-1
  genJar <-
  paste(a[[1]][1:endIndex], collapse='/')

  if (.Platform$OS.type == "windows") {
    cmd <-
    sprintf(
    "java -ea -cp %s/h2o-assemblies/genmodel/build/libs/genmodel.jar -Xmx4g -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --header --mojo %s/%s --input %s/in.csv --output %s/out_mojo.csv",
    genJar,
    tmpdir_name,
    paste(model_key, "zip", sep = '.'),
    tmpdir_name,
    tmpdir_name
    )
  } else {
    cmd <-
    sprintf(
    "java -ea -cp %s/h2o-assemblies/genmodel/build/libs/genmodel.jar -Xmx12g -XX:ReservedCodeCacheSize=256m hex.genmodel.tools.PredictCsv --mojo %s/%s --input %s/in.csv --output %s/out_mojo.csv --decimal",
    genJar,
    tmpdir_name,
    paste(model@model_id, "zip", sep = '.'),
    tmpdir_name,
    tmpdir_name
    )
  }

  if (get_leaf_node_assignment) {
    cmd<-paste(cmd, "--leafNodeAssignment")
    predictions1 = h2o.predict_leaf_node_assignment(model, newTest)
  }
  
  if (glrmReconstruct) {
    cmd <- paste(cmd, "--glrmReconstruct", sep=" ")
  }
  
  safeSystem(cmd)  # perform mojo prediction
  predictions2 = h2o.importFile(paste(tmpdir_name, "out_mojo.csv", sep =
  '/'), header=T)

  if (glrmReconstruct || !(model@algorithm=="glrm")) {
    return(list("h2oPredict"=predictions1, "mojoPredict"=predictions2))
  } else {
    return(list("frameId"=h2o.getId(newTest), "mojoPredict"=predictions2))
  }
}

assert_partialPlots_twoDTable_equal <- function(table1, table2) {
  checkEqualsNumeric(table1[, "mean_response"], table2[, "mean_response"][1:length(table1[, "mean_response"])])
  checkEqualsNumeric(table1[, "stddev_response"], table2[, "stddev_response"][1:length(table1[, "mean_response"])])
  checkEqualsNumeric(table1[, "std_error_mean_response"], table2[, "std_error_mean_response"][1:length(table1[, "mean_response"])])
}

manual_partial_dependency <- function(model, dataframe, xlist, xname, weight_vector, target_index) {
  nrow <- h2o.nrow(dataframe)
  ncol <- h2o.ncol(dataframe)
  weightedStats <- matrix(0, 3, length(xlist))
  xnames <- h2o.names(dataframe)
  temp <- (xnames != xname)
  xnames_list <- xnames[temp]
  rowIndex <- 1
  
  for (xval in xlist) {
    sumEle <- 0.0
    sumEleSq <- 0.0
    sumWeight <- 0.0
    nonZero <- 0

    tempFrame <- dataframe[xnames_list]
    if (!is.nan(xval)) { # only do this for nonNAs
    tempcol <- as.h2o(matrix(xval, nrow, 1))
    colnames(tempcol) <- xname
    tempFrame <- h2o.cbind(tempFrame, tempcol)
    }
    pred <- h2o.predict(model, tempFrame)
    predRow <- h2o.nrow(pred)
    predF <- as.data.frame(pred)
    m <- sqrt(1.0/predRow)
    for (rIndex in c(1:predRow)) {
      val <- predF[rIndex, target_index]
      weight <- weight_vector[rIndex, 1]
      
      if ((abs(weight) > 0) && !is.nan(val)) {
        tempV <- val*weight
        sumEle <- sumEle+tempV
        sumEleSq <- sumEleSq+tempV*val
        sumWeight <- sumWeight+weight
        nonZero <- nonZero+1
      }
    }
    scale <- nonZero/(nonZero-1.0)
    weightedStats[1, rowIndex] <- sumEle/sumWeight
    weightedStats[2, rowIndex] <- sqrt((sumEleSq/sumWeight-weightedStats[1, rowIndex]*weightedStats[1, rowIndex])*scale)
    weightedStats[3, rowIndex] <- weightedStats[2, rowIndex]*m
    rowIndex <- rowIndex+1
  }
  return(weightedStats)
}

# The following two functions are written for comparing results of h2o.partialplots only.
assert_twoDTable_array_equal <- function(table1, arraymean, arraystd, arraystderr) {
  checkEqualsNumeric(table1[, "mean_response"], arraymean)
  checkEqualsNumeric(table1[, "stddev_response"], arraystd)
  checkEqualsNumeric(table1[, "std_error_mean_response"], arraystderr)
  
}
assert_twoDTable_equal <- function(table1, table2) {
  checkEqualsNumeric(table1[, "mean_response"], table2[, "mean_response"][1:length(table1[, "mean_response"])])
  checkEqualsNumeric(table1[, "stddev_response"], table2[, "stddev_response"][1:length(table1[, "mean_response"])])
  checkEqualsNumeric(table1[, "std_error_mean_response"], table2[, "std_error_mean_response"][1:length(table1[, "mean_response"])])
}

assertCorrectSkipColumnsNamesTypes <- function(originalFile, parsePath, skippedColumns, all_column_names, all_column_types, mode, allFrameTypes) {
    colnames <- c()
    coltype <- c()
    colidx <- c()

    for (cind in c(1:length(all_column_names)))  {
        if (!(cind %in% skippedColumns)) {
            colnames <- c(colnames, all_column_names[cind])
            coltype <- c(coltype, all_column_types[[cind]])
            colidx <- c(colidx, cind)
        }
    }

    if (mode == 0) # use by by.col.names
      coltypes <- list(by.col.name=colnames, types=coltype)
    else if (mode==1) # use by.col.idx
      coltypes <- list(by.col.idx=colidx, types=coltype)
    else
    coltypes <- coltype

    if (mode == 0)  {
        # use both name and type
        f1 <- h2o.importFile(parsePath, col.names = colnames, col.types = coltypes, skipped_columns=skippedColumns)
    } else if (mode == 1) {
        f1 <- h2o.uploadFile(parsePath, col.names = colnames, skipped_columns=skippedColumns)
    } else {
        f1 <- h2o.importFile(parsePath, col.types = coltypes, skipped_columns=skippedColumns)
    }

    expect_true(h2o.nrow(originalFile) == h2o.nrow(f1))
  #  expect_true(h2o.nrow(f2) == h2o.nrow(f1))
    cfullnames <- names(originalFile)
    originalR <- as.data.frame(originalFile)
    f1R <- as.data.frame(f1)
  #  f2R <- as.data.frame(f2)
    cskipnames <- names(f1R)
    skipcount <- 1
    rowNum <- h2o.nrow(f1)
    for (ind in c(1:length(cfullnames))) {
        if (cfullnames[ind] == cskipnames[skipcount]) {
            if (allFrameTypes[ind] == "uuid")
            continue
            for (rind in c(1:rowNum)) {
                if (is.na(originalR[rind, ind])) {
                    expect_true(
                    is.na(f1R[rind, skipcount]),
                    info = paste0(
                    "expected NA but received: ",
                    f1R[rind, skipcount],
                    " in row: ",
                    rind,
                    " with column name: ",
                    cfullnames[ind],
                    " and skipped column name ",
                    cskipnames[skipcount],
                    sep = " "
                    )
                    )

                } else if (is.numeric(originalR[rind, ind])) {
                    if (allFrameTypes[ind] == 'time') {
                        expect_true(
                        abs(f1R[rind, ind] - originalR[rind, skipcount]) < 10,
                        info = paste0(
                        "expected: ",
                        originalR[rind, ind],
                        " but received: ",
                        f1R[rind, skipcount],
                        " in row: ",
                        rind,
                        " with column name: ",
                        cfullnames[ind],
                        " and skipped column name ",
                        cskipnames[skipcount],
                        sep = " "
                        )
                        )

                    } else {
                        expect_true(
                        abs(originalR[rind, ind] - f1R[rind, skipcount]) < 1e-10,
                        info = paste0(
                        "expected: ",
                        originalR[rind, ind],
                        " but received: ",
                        f1R[rind, skipcount],
                        " in row: ",
                        rind,
                        " with column name: ",
                        cfullnames[ind],
                        " and skipped column name ",
                        cskipnames[skipcount],
                        sep = " "
                        )
                        )

                    }
                } else {
                    expect_true(
                    originalR[rind, ind] == f1R[rind, skipcount],
                    info = paste0(
                    "expected: ",
                    originalR[rind, ind],
                    " but received: ",
                    f1R[rind, skipcount],
                    " in row: ",
                    rind,
                    " with column name: ",
                    cfullnames[ind],
                    " and skipped column name ",
                    cskipnames[skipcount],
                    sep = " "
                    )
                    )
                }
            }

            skipcount <- skipcount + 1
            if (skipcount > h2o.ncol(f1))
              break
        }
    }
    print("Test completed!")
}
