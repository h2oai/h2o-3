#'
#'
#' ----------------- Additional Runit Utilities -----------------
#'
#'

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
  test_name <- R.utils::commandArgs(asValues=TRUE)$"f"
  Rsandbox <- paste("./Rsandbox_", basename(test_name), sep = "")
  if (create) { dir.create(Rsandbox, showWarnings=FALSE) }
  if (dir.exists(Rsandbox)) { return(normalizePath(Rsandbox))
  } else { Log.err(paste0("Sandbox directory: ",Rsandbox," does not exists")) }
}

# makes a directory in sandbox, one level down
sandboxMakeSubDir<-
function(dirname) {
  if (!is.character(dirname)) Log.err("dirname argument must be of type character")
  subdir <- paste(sandbox(),dirname,sep=.Platform$file.sep)
  dir.create(subdir, showWarnings=FALSE)
  return(subdir)
}

# renames sandbox subdir
sandboxRenameSubDir<-
function(oldSubDir,newSubDirName) {
  if (!is.character(oldSubDir)) Log.err("oldSubDir argument must be of type character")
  if (!is.character(newSubDirName)) Log.err("newSubDirName argument must be of type character")
  newSubDir <- sandboxMakeSubDir(dirname=newSubDirName)
  file.rename(oldSubDir, newSubDir)
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
  q("no",0,FALSE)
}

FAIL<-
function(e) {
  FAIL_BANNER()
  Log.err(e)
  q("no",1,FALSE) #exit with nonzero exit code
}

SKIP<-
function() {
  q("no",42,FALSE) #exit with nonzero exit code
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

parseArgs<-
function(args) {
  i <- 1
  while (i <= length(args)) {
      s <- args[i]
      if (s == "--usecloud") {
        i <- i + 1
        if (i > length(args)) usage()
        argsplit <- strsplit(args[i], ":")[[1]]
        H2O.IP   <<- argsplit[1]
        H2O.PORT <<- as.numeric(argsplit[2])
      } else if (s == "--hadoopNamenode") {
        i <- i + 1
        if (i > length(args)) usage()
        HADOOP.NAMENODE <<- args[i]
      } else if (s == "--onHadoop") {
        ON.HADOOP <<- TRUE
      } else {
        unknownArg(s)
      }
      i <- i + 1
  }
}

usage<-
function() {
  print("")
  print("Usage for:  R -f runit.R --args [...options...]")
  print("")
  print("    --usecloud       connect to h2o on specified ip and port, where ip and port are specified as follows:")
  print("                     IP:PORT")
  print("")
  print("    --onJenkHadoop   signal to runit that it will be run on h2o-hadoop cluster with the specified hdfs name")
  print("                     node.")
  print("")
  q("no",1,FALSE) #exit with nonzero exit code
}

unknownArg<-
function(arg) {
  print("")
  print(paste0("ERROR: Unknown argument: ",arg))
  print("")
  usage()
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

doTest<-
function(testDesc, test) {
    h2o.removeAll()
    Log.info("======================== Begin Test ===========================\n")
    conn <- h2o.getConnection()
    conn@mutable$session_id <- .init.session_id()
    tryCatch(test_that(testDesc, withWarnings(test())), warning = function(w) WARN(w), error =function(e) FAIL(e))
    PASS()
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

#' Hadoop helpers
hadoop.namenode.is.accessible <- function() {
    url <- sprintf("http://%s:50070", HADOOP.NAMENODE);
    internal <- url.exists(url, timeout = 5)
    return(internal)
}

hadoop.namenode <- function() { return(HADOOP.NAMENODE) }
