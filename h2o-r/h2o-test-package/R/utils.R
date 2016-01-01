#'
#'
#' ----------------- Additional H2O (R) Testing Utilities -----------------
#'
#'

#' Hadoop helper
h2oTest.hadoopNamenodeIsAccessible <- function() {
    url <- sprintf("http://%s:50070", HADOOP.NAMENODE);
    internal <- url.exists(url, timeout = 5)
    return(internal)
}

#' Locate a file given the pattern <bucket>/<path/to/file>
#' e.g. locate( "smalldata/iris/iris22.csv") returns the absolute path to iris22.csv
h2oTest.locate<-
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
  pathStub <- h2oTest.clean(pathStub)
  bucket <- pathStub[1]
  offset <- pathStub[-1]
  cur.dir <- getwd()

  #recursively ascend until `bucket` is found
  bucket.abspath <- h2oTest.pathCompute(cur.dir, bucket, root.parent)
  if (length(offset) != 0) return(paste(c(bucket.abspath, offset), collapse = "/", sep = "/"))
  else return(bucket.abspath)
}

#' Clean a path up: change \ -> /; remove starting './'; split
h2oTest.clean<-
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
h2oTest.pathCompute<-
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
      return(h2oTest.pathCompute(parent.dir, root, root.parent)) }

    # fail if reach h2o-dev
    if (parent.name == PROJECT.ROOT) {
        stop(paste0("Reached the root ", PROJECT.ROOT, ". Didn't find the bucket with the root.parent")) }
  }
  return(h2oTest.pathCompute(parent.dir, root, root.parent))
}

#' Load a handful of packages automatically. Runit tests that require additional packages must be loaded explicitly
h2oTest.loadDefaultRPackages <-
function() {
  to_require <- c("jsonlite", "RCurl", "RUnit", "R.utils", "testthat", "ade4", "glmnet", "gbm", "ROCR", "e1071",
                  "tools", "statmod", "fpc", "cluster")
  if (Sys.info()['sysname'] == "Windows") {
    options(RCurlOptions = list(cainfo = system.file("CurlSSL", "cacert.pem", package = "RCurl"))) }
  invisible(lapply(to_require,function(x){require(x,character.only=TRUE,quietly=TRUE,warn.conflicts=FALSE)}))
}

h2oTest.readZip<-
function(zipfile, exdir,header=T) {
  zipdir <- exdir
  unzip(zipfile, exdir=zipdir)
  files <- list.files(zipdir)
  file <- paste(zipdir, files[1], sep="/")
  read.csv(file,header=header)
}

# returns the directory of the sandbox for the given test.
h2oTest.sandbox<-
function(create=FALSE) {
  Rsandbox <- paste0("./Rsandbox_", basename(TEST.NAME))
  if (create) { dir.create(Rsandbox, showWarnings=FALSE) }
  if (dir.exists(Rsandbox)) { return(normalizePath(Rsandbox))
  } else { h2oTest.logErr(paste0("Sandbox directory: ",Rsandbox," does not exists")) }
}

# makes a directory in sandbox, one level down
h2oTest.sandboxMakeSubDir<-
function(dirname) {
  if (!is.character(dirname)) h2oTest.logErr("dirname argument must be of type character")
  subdir <- file.path(sandbox(),dirname,fsep = "\\")
  dir.create(subdir, showWarnings=FALSE)
  return(subdir)
}

# renames sandbox subdir
h2oTest.sandboxRenameSubDir<-
function(oldSubDir,newSubDirName) {
  if (!is.character(oldSubDir)) h2oTest.logErr("oldSubDir argument must be of type character")
  if (!is.character(newSubDirName)) h2oTest.logErr("newSubDirName argument must be of type character")
  newSubDir <- file.path(h2oTest.sandbox(),newSubDirName)
  # Real trouble deleting a prior-existing newSubDir on Windows, that was filled with crap.
  # Calling system("rm -rf") seems to work, where calling unlink() would not.
  # Also renaming to an existing but empty directory does not work on windows.
  system(paste0("rm -rf ",newSubDir))
  res <- file.rename(oldSubDir, newSubDir)
  if( !res ) print(paste0("Warning: File rename failed FROM ",oldSubDir," TO ",newSubDir))
  return(newSubDir)
}

h2oTest.logInfo<-
function(m) {
  message <- paste("[INFO] : ",m,sep="")
  h2oTest.logging(message)
}

h2oTest.logWarn<-
function(m) {
  h2oTest.logging(paste("[WARN] : ",m,sep=""))
  traceback()
}

h2oTest.logErr<-
function(m) {
  h2oTest.logging(paste("[ERROR] : ",m,sep=""))
  h2oTest.logging("[ERROR] : TEST FAILED")
  traceback()
}

h2oTest.logging<-
function(m) {
  cat(sprintf("[%s] %s\n", Sys.time(),m))
}

h2oTest.PassBanner<-
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

h2oTest.FailBanner<-
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

h2oTest.pass<-
function() {
  h2oTest.PassBanner()
  q("no",0,TRUE)
}

h2oTest.fail<-
function(e) {
  h2oTest.FailBanner()
  h2oTest.logErr(e)
  q("no",1,TRUE) #exit with nonzero exit code
}

h2oTest.skip<-
function() {
  q("no",42,TRUE) #exit with nonzero exit code
}

h2oTest.warn<-
function(w) {
  h2oTest.logWarn(w)
}

#----------------------------------------------------------------------
# Print out a message with clear whitespace.
#
# Parameters:  x -- Message to print out.
#              n -- (optional) Step number.
#
# Returns:     none
#----------------------------------------------------------------------
h2oTest.heading <- function(x, n = -1) {
  h2oTest.logInfo("")
  h2oTest.logInfo("")
  if (n < 0) {
    h2oTest.logInfo(sprintf("STEP: %s", x))
  } else {
    h2oTest.logInfo(sprintf("STEP %2d: %s", n, x))
  }
  h2oTest.logInfo("")
  h2oTest.logInfo("")
}

#----------------------------------------------------------------------
# "Safe" system.  Error checks process exit status code.  stop() if it failed.
#
# Parameters:  x -- String of command to run (passed to system()).
#
# Returns:     none
#----------------------------------------------------------------------
h2oTest.safeSystem <- function(x) {
  print(sprintf("+ CMD: %s", x))
  res <- system(x)
  print(res)
  if (res != 0) {
    msg <- sprintf("SYSTEM COMMAND FAILED (exit status %d)", res)
    stop(msg)
  }
}

h2oTest.withWarnings <- function(expr) {
    myWarnings <- NULL
    wHandler <- function(w) {
        myWarnings <<- c(myWarnings, list(w))
        invokeRestart("muffleWarning")
    }
    val <- withCallingHandlers(expr, warning = wHandler)
    list(value = val, warnings = myWarnings)
    for(w in myWarnings) h2oTest.warn(w)
}

h2oTest.cleanSummary <- function(mysum, alphabetical = FALSE) {
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

h2oTest.checkSummary <- function(object, expected, tolerance = 1e-6) {
  sumR <- h2oTest.cleanSummary(expected, alphabetical = TRUE)
  sumH2O <- h2oTest.cleanSummary(object, alphabetical = TRUE)
  
  expect_equal(length(sumH2O), length(sumR))
  lapply(1:length(sumR), function(i) {
    vecR <- sumR[[i]]; vecH2O <- sumH2O[[i]]
    expect_equal(length(vecH2O), length(vecR))
    expect_equal(names(vecH2O), names(vecR))
    for(j in 1:length(vecR))
      expect_equal(vecH2O[j], vecR[j], tolerance = tolerance)
  })
}

h2oTest.genDummyCols <- function(df, use_all_factor_levels = TRUE) {
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

h2oTest.alignData <- function(df, center = FALSE, scale = FALSE, ignore_const_cols = TRUE, use_all_factor_levels = TRUE) {
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
  h2oTest.genDummyCols(df.clone, use_all_factor_levels)
}

h2oTest.doTest<-
function(testDesc, test) {
    tryCatch(test_that(testDesc, h2oTest.withWarnings(test())), warning = function(w) h2oTest.warn(w), error =function(e) h2oTest.fail(e))
    h2oTest.pass()
}

h2oTest.setupSeed<-
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
    h2oTest.logInfo(paste("USING SEED: ", SEED))
}

h2o_and_R_equal <- function(h2o_obj, r_obj, tolerance = 1e-6) {
  df_h2o_obj <- as.data.frame(h2o_obj)
  df_r_obj <- as.data.frame(r_obj)
  expect_equal(length(df_h2o_obj), length(df_r_obj))
  
  #Check NAs are in same places 
  df_h2o_nas <- if (length(df_h2o_obj) == 1) df_h2o_obj == "NaN" else is.na(df_h2o_obj)
  if (length(df_h2o_nas) ==1 && is.na(df_h2o_nas)) df_h2o_nas <- is.na(df_h2o_obj)
  df_r_nas <- is.na(df_r_obj)
  expect_true(all(df_h2o_nas == df_r_nas))
  
  #Check non-NAs are same vals
  df_h2o_obj_free <- df_h2o_obj[!df_h2o_nas]
  df_r_na_free <- df_r_obj[!df_r_nas]
  
  expect_equal(length(df_h2o_obj_free), length(df_r_na_free))
  if (length(df_r_na_free) > 0)
    expect_true(all(abs(df_h2o_obj_free - df_r_na_free) < tolerance))
  
}
