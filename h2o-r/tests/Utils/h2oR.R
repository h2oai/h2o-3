##                                       ##
#                                         #  
#  Various Utility Functions for R Units  #
#                                         #
##                                       ##

grabRemote <- function(myURL, myFile) {
  temp <- tempfile()
  download.file(myURL, temp, method = "curl")
  aap.file <- read.csv(file = unz(description = temp, filename = myFile), as.is = TRUE)
  unlink(temp)
  return(aap.file)
}

read.zip<- 
function(zipfile, exdir,header=T) {
  zipdir <- exdir
  unzip(zipfile, exdir=zipdir)
  files <- list.files(zipdir)
  file <- paste(zipdir, files[1], sep="/")
  read.csv(file,header=header)
}

remove_exdir<- 
function(exdir) {
  exec <- paste("rm -r ", exdir, sep="")
  system(exec)
}

sandbox<-
function() {
  test_name <- R.utils::commandArgs(asValues=TRUE)$"f"
  if (is.null(test_name)) {
      test_name <- paste(getwd(), "r_command_line", sep="/")
  }
  # test_name can be a path..just what the basename
  Rsandbox <- paste("./Rsandbox_", basename(test_name), sep = "")
  dir.create(Rsandbox, showWarnings = FALSE)
  commandsLog <- paste(Rsandbox, "/commands.log", sep = "")
  errorsLog <- paste(Rsandbox, "/errors.log", sep = "")
  if(file.exists(commandsLog)) file.remove(commandsLog)
  if(file.exists(errorsLog)) file.remove(errorsLog)
  write.table(SEED, paste(Rsandbox, "/seed", sep = ""), row.names = F, col.names = F)
  h2o.startLogging(paste(Rsandbox, "/rest.log", sep = ""))
}

Log.info<-
function(m) {
  message <- paste("[INFO]: ",m, sep="")
  logging(message)
}

Log.warn<-
function(m) {
  logging(paste("[WARN] : ",m,sep=""))
  #temp <- strsplit(as.character(Sys.time()), " ")[[1]]
  #m <- paste('[',temp[1], ' ',temp[2],']', '\t', m)
  h2o.logIt("[WARN] :", m, "Error")
  traceback()
}

Log.err<-
function(m) {
  seedm <- paste("SEED used: ", SEED, sep = "")
  m <- paste(m, "\n", seedm, "\n", sep = "")
  logging(paste("[ERROR] : ",m,sep=""))
  logging("[ERROR] : TEST FAILED")
  #temp <- strsplit(as.character(Sys.time()), " ")[[1]]
  #m <- paste('[',temp[1], ' ',temp[2],']', '\t', m)
  h2o.logIt("[ERROR] :", m, "Error")
  traceback()
  q("no",1,FALSE) #exit with nonzero exit code
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

PASS<- 
function() {
  PASS_BANNER()
  Log.info("TEST PASSED")
  q("no",0,FALSE)
}

FAIL<-
function(e) {
  cat("\n")
  cat("########    ###    #### ##       \n")
  cat("##         ## ##    ##  ##       \n")
  cat("##        ##   ##   ##  ##       \n")
  cat("######   ##     ##  ##  ##       \n")
  cat("##       #########  ##  ##       \n")
  cat("##       ##     ##  ##  ##       \n")
  cat("##       ##     ## #### ######## \n")
  cat("\n")

  Log.err(e)
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
  }
  else {
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

get_args<-
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

testEnd<-
function() {
    Log.info("End of test.")
    PASSS <<- TRUE
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
    Log.info("======================== Begin Test ===========================\n")
    conn <<- new("H2OConnection", ip=myIP, port=myPort)
    conn@mutable$session_id <- .init.session_id(conn)
    assign("conn", conn, globalenv())
    tryCatch(test_that(testDesc, withWarnings(test(conn))), warning = function(w) WARN(w), error =function(e) FAIL(e), finally = h2o.removeAll(conn, timeout_secs=600))
    if (!PASSS) FAIL("Did not reach the end of test. Check Rsandbox/errors.log for warnings and errors.")
    PASS()
}

installDepPkgs <- function(optional = FALSE) {
  myPackages = rownames(installed.packages())
  myReqPkgs = c("RCurl", "rjson", "tools", "statmod")
  
  # For plotting clusters in h2o.kmeans demo
  if(optional)
    myReqPkgs = c(myReqPkgs, "fpc", "cluster")
  
  # For communicating with H2O via REST API
  temp = lapply(myReqPkgs, function(x) { if(!x %in% myPackages) install.packages(x) })
  temp = lapply(myReqPkgs, require, character.only = TRUE)
}

checkNLoadWrapper<-
function(ipPort) {
  Log.info("Check if H2O R wrapper package is installed\n")
  if (!"h2o" %in% rownames(installed.packages())) {
    envPath  = Sys.getenv("H2OWrapperDir")
    wrapDir  = ifelse(envPath == "", defaultPath, envPath)
    wrapName = list.files(wrapDir, pattern  = "h2o")[1]
    cat("wrapDir:", wrapDir, "wrapName:", wrapName)
    wrapPath = paste(wrapDir, wrapName, sep = "/")
    
    if (!file.exists(wrapPath))
      stop(paste("h2o package does not exist at", wrapPath));
    print(paste("Installing h2o package from", wrapPath))
    installDepPkgs()
    install.packages(wrapPath, repos = NULL, type = "source")
  }

  Log.info("Check that H2O R package matches version on server\n")
  installDepPkgs()
}

checkNLoadPackages<-
function() {
  Log.info("Checking Package dependencies for this test.\n")
  if (!"RUnit"    %in% rownames(installed.packages())) install.packages("RUnit")
  if (!"testthat" %in% rownames(installed.packages())) install.packages("testthat")
  if (!"R.utils" %in% rownames(installed.packages())) install.packages("R.utils") 

  if (Sys.info()['sysname'] == "Windows")
    options(RCurlOptions = list(cainfo = system.file("CurlSSL", "cacert.pem", package = "RCurl")))

  Log.info("Loading RUnit and testthat and R.utils\n")
  require(RUnit)
  require(R.utils)
  require(testthat)
}
