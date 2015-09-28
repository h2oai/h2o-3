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

sandbox<-
function() {
  test_name <- R.utils::commandArgs(asValues=TRUE)$"f"
  if (is.null(test_name)) {
      test_name <- paste(getwd(), "r_command_line", sep="/")
  }
  Rsandbox <- paste("./Rsandbox_", basename(test_name), sep = "")
  dir.create(Rsandbox, showWarnings = FALSE)
  commandsLog <- paste(Rsandbox, "/commands.log", sep = "")
  errorsLog <- paste(Rsandbox, "/errors.log", sep = "")
  if(file.exists(commandsLog)) file.remove(commandsLog)
  if(file.exists(errorsLog)) file.remove(errorsLog)
  h2o.startLogging(paste(Rsandbox, "/rest.log", sep = ""))
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

getArgs<-
function(args) {
  fileName <- commandArgs()[grep('*\\.R',unlist(commandArgs()))]
  if (length(args) > 1) {
    m <- paste("Usage: R f ", paste(fileName, " --args H2OServer:Port",sep=""),sep="")
    stop(m);
  }

  if (length(args) == 0) {
    myIP   = "127.0.0.1"
    myPort = 54321
  } else {
    argsplit = strsplit(args[1], ":")[[1]]
    myIP     = argsplit[1]
    myPort   = as.numeric(argsplit[2])
  }
  return(list(myIP,myPort));
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

#' HDFS helper
is.running.internal.to.h2o <- function() {
    url <- sprintf("http://%s:50070", H2O.INTERNAL.HDFS.NAME.NODE);
    internal <- url.exists(url, timeout = 5)
    return(internal)
}
