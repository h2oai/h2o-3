# Checks H2O connection and installs H2O R package matching version on server if indicated by user
# 1) If can't connect and user doesn't want to start H2O, stop immediately
# 2) If user does want to start H2O and running locally, attempt to bring up H2O launcher
# 3) If user does want to start H2O, but running non-locally, print an error

#' Initialize and Connect to H2O
#'
#' Attempts to start and/or connect to and H2O instance.
#'
#' By defualt, this method first checks if an H2O instance is connectible. If it cannot connect and \code{start = TRUE} with \code{ip = "localhost"}, it will attempt to start and instance of H2O at localhost:54321. Otherwise it stops with an error.
#'
#' When initializing H2O locally, this method searches for h2o.jar in the R library resources (\code{system.file("java", "h2o.jar", package = "h2o")}), and if the file does not exist, it will automatically attempt to download the correct version from Amazon S3. The user must have Internet access for this process to be successful.
#'
#' Once connected, the method checks to see if the local H2O R package version matches the version of H2O running on the server. If there is a mismatch and the user indicates she wishes to upgrade, it will remove the local H2O R package and download/install the H2O R package from the server.
#'
#' @param ip Object of class \code{character} representing the IP address of the server where H2O is running.
#' @param port Object of class \code{numeric} representing the port number of the H2O server.
#' @param startH2O A \code{logical} value indicating whether to try to start H2O from R if no connection with H2O is detected. This is only possible if \code{ip = "localhost"} or \code{ip = "127.0.0.1"}.  If an existing connection is detected, R does not start H2O.
#' @param forceDL A \code{logical} value indicating whether to force download of the H2O executable. Defaults to FALSE, so the executable will only be downloaded if it does not already exist in the h2o R library resources directory \code{h2o/java/h2o.jar}.  This value is only used when R starts H2O.
#' @param Xmx DEPRECATED A \code{character} string specifying the maximum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @param beta A \code{logical} value indicating whether H2O should launch in beta mode. This value is only used when R starts H2O.
#' @param assertion A \code{logical} value indicating whether H2O should be launched with assertions enabled. Used mainly for error checking and debugging purposes.  This value is only used when R starts H2O.
#' @param license A \code{character} string value specifying the full path of the license file.  This value is only used when R starts H2O.
#' @param max_mem_size A \code{character} string specifying the maximum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @param min_mem_size A \code{character} string specifying the minimum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @return this method will load it and return a \code{H2OClient} object containing the IP address and port number of the H2O server.
#' @note Users may wish to manually upgrade their package (rather than waiting until being prompted), which requires
#' that they fully uninstall and reinstall the H2O package, and the H2O client package. You must unload packages running
#' in the environment before upgrading. It's recommended that users restart R or R studio after upgrading
#' @seealso \href{http://docs.h2o.ai/Ruser/top.html}{H2O R package documentation} for more details, or type \code{\link{h2o}} in the R console. \code{\link{h2o.shutdown}} for shutting down from R.
#' @examples
#' \donttest{
#' # Try to connect to a local H2O instance that is already running.
#' # If not found, start a local H2O instance from R with the default settings.
#' localH2O = h2o.init()
#'
#' # Try to connect to a local H2O instance.
#' # If not found, raise an error.
#' localH2O = h2o.init(startH2O = FALSE)
#'
#' # Try to connect to a local H2O instance that is already running.
#' # If not found, start a local H2O instance from R with 5 gigabytes of memory.
#' localH2O = h2o.init(max_mem_size = "5g")
#'
#' # Try to connect to a local H2O instance that is already running.
#' # If not found, start a local H2O instance from R that uses 5 gigabytes of memory.
#' localH2O = h2o.init(max_mem_size = "5g")
#' }
#'
h2o.init <- function(ip = "127.0.0.1", port = 54321, startH2O = TRUE, forceDL = FALSE, Xmx,
                     beta = FALSE, assertion = TRUE, license = NULL, max_mem_size = "1g", min_mem_size = "1g") {
  if(!is.character(ip)) stop("ip must be of class character")
  if(!is.numeric(port)) stop("port must be of class numeric")
  if(!is.logical(startH2O)) stop("startH2O must be of class logical")
  if(!is.logical(forceDL)) stop("forceDL must be of class logical")
  if(!missing(Xmx) && !is.character(Xmx)) stop("Xmx must be of class character")
  if(!is.character(max_mem_size)) stop("max_mem_size must be of class character")
  if(!is.character(min_mem_size)) stop("min_mem_size must be of class character")
  if(!regexpr("^[1-9][0-9]*[gGmM]$", max_mem_size)) stop("max_mem_size option must be like 1g or 1024m")
  if(!regexpr("^[1-9][0-9]*[gGmM]$", min_mem_size)) stop("min_mem_size option must be like 1g or 1024m")
  if(!missing(Xmx) &&   !regexpr("^[1-9][0-9]*[gGmM]$", Xmx)) stop("Xmx option must be like 1g or 1024m")
  if(!is.logical(beta)) stop("beta must be of class logical")
  if(!is.logical(assertion)) stop("assertion must be of class logical")
  if(!is.null(license) && !is.character(license)) stop("license must be of class character")

  if(!missing(Xmx)) {
    warning("Xmx is a deprecated parameter. Use `max_mem_size` and `min_mem_size` to set the memory boundaries. Using `Xmx` to set these.")
    max_mem_size <- Xmx
    min_mem_size <- Xmx
  }

  myURL = paste("http://", ip, ":", port, sep="")
  if(!.uri.exists(myURL)) {
    if(!startH2O)
      stop(paste("Cannot connect to H2O server. Please check that H2O is running at", myURL))
    else if(ip == "localhost" || ip == "127.0.0.1") {
      cat("\nH2O is not running yet, starting it now...\n")
      .h2o.startJar(max_memory = max_mem_size, min_memory = min_mem_size, beta = beta, assertion = assertion, forceDL = forceDL, license = license)
      count = 0; while(!.uri.exists(myURL) && count < 60) { Sys.sleep(1); count = count + 1 }
      if(!.uri.exists(myURL)) stop("H2O failed to start, stopping execution.")
    } else stop("Can only start H2O launcher if IP address is localhost.")
  }
  cat("Successfully connected to", myURL, "\n")
  H2Oserver = new("H2OClient", ip = ip, port = port)
  # Sys.sleep(0.5)    # Give cluster time to come up
  h2o.clusterInfo(H2Oserver); cat("\n")
  
#  if((verH2O = .h2o.__version(H2Oserver)) != (verPkg = packageVersion("h2o")))
#    stop("Version mismatch! H2O is running version ", verH2O, " but R package is version ", toString(verPkg), "\n")
#  assign("SERVER", H2Oserver, .pkg.env)
  return(H2Oserver)
}

# Shuts down H2O instance running at given IP and port
#' Shut Down H2O Instance
#'
#' Shut down the specified instance. All data will be lost.
#'
#' This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O instance.
#'
#' @section WARNING: All data, models, and other values stored on the server will be lost! Only call this function if you and all other clients connected to the H2O server are finished and have saved your work.
#' @param client An \linkS4class{H2OClient} client containing the IP address and port of the server running H2O.
#' @param promptn A \code{logical} value indicating whether to prompt the user before shutting down the H2O server.
#' @note Users must call h2o.shutdown explicitly in order to shut down the local H2O instance started by R. If R is closed before H2O, then an attempt will be made to automatically shut down H2O. This only applies to local instances started with h2o.init, not remote H2O servers.
#' @seealso \code{\link{h2o.init}}
#' @examples
#' # Don't run automatically to prevent accidentally shutting down a cloud
#' \dontrun{
#' library(h2o)
#' localH2O = h2o.init()
#' h2o.shutdown(localH2O)
#' }

h2o.shutdown <- function(client, prompt = TRUE) {
  if(class(client) != "H2OClient") stop("client must be of class H2OClient")
  if(!is.logical(prompt)) stop("prompt must be of class logical")
  
  myURL = paste("http://", client@ip, ":", client@port, sep="")
  if(!.uri.exists(myURL)) stop(paste("There is no H2O instance running at", myURL))
  
  if(prompt) {
    ans = readline(paste("Are you sure you want to shutdown the H2O instance running at", myURL, "(Y/N)? "))
    temp = substr(ans, 1, 1)
  } else temp = "y"
  
  if(temp == "Y" || temp == "y") {
    res = getURLContent(paste(myURL, .h2o.__PAGE_SHUTDOWN, sep="/"))
    res = fromJSON(res)
    if(!is.null(res$error))
      stop(paste("Unable to shutdown H2O. Server returned the following error:\n", res$error))
  }
  
  if((client@ip == "localhost" || client@ip == "127.0.0.1") && .h2o.startedH2O()) {
    pid_file <- .h2o.getTmpFile("pid")
    if(file.exists(pid_file)) file.remove(pid_file)
  }
}

# ----------------------- Diagnostics ----------------------- #
# **** TODO: This isn't really a cluster status... it's a node status check for the node we're connected to.
# This is possibly confusing because this can come back without warning,
# but if a user tries to do any remoteSend, they will get a "cloud sick warning"
# Suggest cribbing the code from Internal.R that checks cloud status (or just call it here?)

h2o.clusterStatus <- function(client) {
  if(missing(client) || class(client) != "H2OClient") stop("client must be a H2OClient object")
  myURL = paste("http://", client@ip, ":", client@port, "/", .h2o.__CLOUD, sep = "")
  if(!.uri.exists(myURL)) stop("Cannot connect to H2O instance at ", myURL)
  res = fromJSON(postForm(myURL, style = "POST"))
  
  cat("Version:", res$version, "\n")
  cat("Cloud name:", res$cloud_name, "\n")
  cat("Node name:", res$node_name, "\n")
  cat("Cloud size:", res$cloud_size, "\n")
  if(res$locked) cat("Cloud is locked\n\n") else cat("Accepting new members\n\n")
  if(is.null(res$nodes) || length(res$nodes) == 0) stop("No nodes found!")
  
  # Calculate how many seconds ago we last contacted cloud
  cur_time <- Sys.time()
  for(i in 1:length(res$nodes)) {
    last_contact_sec = as.numeric(res$nodes[[i]]$last_contact)/1e3
    time_diff = cur_time - as.POSIXct(last_contact_sec, origin = "1970-01-01")
    res$nodes[[i]]$last_contact = as.numeric(time_diff)
  }
  cnames = c("name", "value_size_bytes", "free_mem_bytes", "max_mem_bytes", "free_disk_bytes", "max_disk_bytes", "num_cpus", "system_load", "rpcs", "last_contact")
  temp = data.frame(t(sapply(res$nodes, c)))
  return(temp[,cnames])
}

#---------------------------- H2O Jar Initialization -------------------------------#
.h2o.pkg.path <- NULL
.h2o.jar.env <- new.env()    # Dummy variable used to shutdown H2O when R exits

.onLoad <- function(lib, pkg) {
  .h2o.pkg.path <<- paste(lib, pkg, sep = .Platform$file.sep)
  
  # installing RCurl requires curl and curl-config, which is typically separately installed
  rcurl_package_is_installed = length(find.package("RCurl", quiet = TRUE)) > 0
  if(!rcurl_package_is_installed) {
    if(.Platform$OS.type == "unix") {
      # packageStartupMessage("Checking libcurl version...")
      curl_path <- Sys.which("curl-config")
      if(curl_path[[1]] == '' || system2(curl_path, args = "--version") != 0)
        stop("libcurl not found! Please install libcurl (version 7.14.0 or higher) from http://curl.haxx.se. On Linux systems, 
              you will often have to explicitly install libcurl-devel to have the header files and the libcurl library.")
    }
  }
}

.onAttach <- function(libname, pkgname) {
  msg = paste(
    "\n",
    "----------------------------------------------------------------------\n",
    "\n",
    "Your next step is to start H2O and get a connection object (named\n",
    "'localH2O', for example):\n",
    "    > localH2O = h2o.init()\n",
    "\n",
    "For H2O package documentation, first call init() and then ask for help:\n",
    "    > localH2O = h2o.init()\n",
    "    > ??h2o\n",
    "\n",
    "To stop H2O you must explicitly call shutdown (either from R, as shown\n",
    "here, or from the Web UI):\n",
    "    > h2o.shutdown(localH2O)\n",
    "\n",
    "After starting H2O, you can use the Web UI at http://localhost:54321\n",
    "For more information visit http://docs.0xdata.com\n",
    "\n",
    "----------------------------------------------------------------------\n",
    sep = "")
  packageStartupMessage(msg)
  
  # Shut down local H2O when user exits from R
  pid_file <- .h2o.getTmpFile("pid")
  if(file.exists(pid_file)) file.remove(pid_file)
  
  reg.finalizer(.h2o.jar.env, function(e) {
    ip = "127.0.0.1"; port = 54321
    myURL = paste("http://", ip, ":", port, sep = "")
            
    # require(RCurl); require(rjson)
    if(.h2o.startedH2O() && .uri.exists(myURL))
      h2o.shutdown(new("H2OClient", ip=ip, port=port), prompt = FALSE)
  }, onexit = TRUE)
}


.onDetach <- function(libpath) {
  ip    <- "127.0.0.1";
  port  <- 54321
  myURL <- paste("http://", ip, ":", port, sep = "")
  if (.uri.exists(myURL)) {
    tryCatch(h2o.shutdown(new("H2OClient", ip = ip, port = port), prompt = FALSE), error = function(e) {
      msg = paste(
        "\n",
        "----------------------------------------------------------------------\n",
            "\n",
            "Could not shut down the H2O Java Process!\n",
            "Please shutdown H2O manually by navigating to `http://localhost:54321/Shutdown`\n\n",
            "Windows requires the shutdown of h2o before re-installing -or- updating the h2o package.\n",
            "For more information visit http://docs.0xdata.com\n",
            "\n",
            "----------------------------------------------------------------------\n",
            sep = "")
      warning(msg)
    })
  }
}

#.onDetach <- function(libpath) {
#   if(exists(".LastOriginal", mode = "function"))
#      assign(".Last", get(".LastOriginal"), envir = .GlobalEnv)
#   else if(exists(".Last", envir = .GlobalEnv))
#     rm(".Last", envir = .GlobalEnv)
#}

# .onUnload <- function(libpath) {
#   ip = "127.0.0.1"; port = 54321
#   myURL = paste("http://", ip, ":", port, sep = "")
#   
#   require(RCurl); require(rjson)
#   if(.h2o.startedH2O() && .uri.exists(myURL))
#     h2o.shutdown(new("H2OClient", ip=ip, port=port), prompt = FALSE)
# }

.h2o.startJar <- function(max_memory = "1g", min_memory = "1g", beta = FALSE, assertion = TRUE, forceDL = FALSE, license = NULL) {
  command <- .h2o.checkJava()

  if (! is.null(license)) {
    if (! file.exists(license)) {
      stop(paste("License file not found (", license, ")", sep=""))
    }
  }

  # Note: Logging to stdout and stderr in Windows only works for R version 3.0.2 or later!
  stdout <- .h2o.getTmpFile("stdout")
  stderr <- .h2o.getTmpFile("stderr")
  write(Sys.getpid(), .h2o.getTmpFile("pid"), append = FALSE)   # Write PID to file to track if R started H2O
  
  # jar_file <- paste(.h2o.pkg.path, "java", "h2o.jar", sep = .Platform$file.sep)
  jar_file <- .h2o.downloadJar(overwrite = forceDL)
  jar_file <- paste('"', jar_file, '"', sep = "")

  # Compose args
  args <- c(paste("-Xms", min_memory, sep=""),
            paste("-Xmx", max_memory, sep=""))
  if(assertion) args <- c(args, "-ea")
  args <- c(args, "-jar", jar_file)
  args <- c(args, "-name", "H2O_started_from_R")
  args <- c(args, "-ip", "127.0.0.1")
  args <- c(args, "-port", "54321")
  if(beta) args <- c(args, "-beta")
  if(!is.null(license)) args <- c(args, "-license", license)

  cat("\n")
  cat(        "Note:  In case of errors look at the following log files:\n")
  cat(sprintf("           %s\n", stdout))
  cat(sprintf("           %s\n", stderr))
  cat("\n")
  
  # Throw an error if GNU Java is being used
  jver <- system2(command, "-version", stdout = TRUE, stderr = TRUE)
  if(any(grepl("GNU libgcj", jver))) {
    stop("Sorry, GNU Java is not supported for H2O.\n
          Please download the latest Java SE JDK 7 from the following URL:\n
          http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html")
  }
  system2(command, c("-version"))
  cat("\n")
  rc = system2(command,
               args=args,
               stdout=stdout,
               stderr=stderr,
               wait=FALSE)
  if (rc != 0) {
    stop(sprintf("Failed to exec %s with return code=%s", jar_file, as.character(rc)))
  }
}

.h2o.getTmpFile <- function(type) {
  if(missing(type) || !type %in% c("stdout", "stderr", "pid"))
    stop("type must be one of 'stdout', 'stderr', or 'pid'")
  
  if(.Platform$OS.type == "windows") {
    default_path <- paste("C:", "TMP", sep = .Platform$file.sep)
    if(file.exists(default_path))
      tmp_path <- default_path
    else if(file.exists(paste("C:", "TEMP", sep = .Platform$file.sep)))
      tmp_path <- paste("C:", "TEMP", sep = .Platform$file.sep)
    else if(file.exists(Sys.getenv("APPDATA")))
      tmp_path <- Sys.getenv("APPDATA")
    else
      stop("Error: Cannot log Java output. Please create the directory ", default_path, ", ensure it is writable, and re-initialize H2O")
    usr <- gsub("[^A-Za-z0-9]", "_", Sys.getenv("USERNAME"))
  } else {
    tmp_path <- paste(.Platform$file.sep, "tmp", sep = "")
    usr <- gsub("[^A-Za-z0-9]", "_", Sys.getenv("USER"))
  }
  
  if(type == "stdout")
    paste(tmp_path, paste("h2o", usr, "started_from_r.out", sep="_"), sep = .Platform$file.sep)
  else if(type == "stderr")
    paste(tmp_path, paste("h2o", usr, "started_from_r.err", sep="_"), sep = .Platform$file.sep)
  else
    paste(tmp_path, paste("h2o", usr, "started_from_r.pid", sep="_"), sep = .Platform$file.sep)
}

.h2o.startedH2O <- function() {
  pid_file <- .h2o.getTmpFile("pid")
  if(file.exists(pid_file)) {
    pid_saved <- as.numeric(readLines(pid_file))
    return(pid_saved == Sys.getpid())
  } else return(FALSE)
}

# This function returns the path to the Java executable if it exists
# 1) Check for Java in user's PATH
# 2) Check for JAVA_HOME environment variable
# 3) If Windows, check standard install locations in Program Files folder. Warn if JRE found, but not JDK since H2O requires JDK to run.
# 4) When all fails, stop and prompt user to download JDK from Oracle website.
.h2o.checkJava <- function() {
  if(nchar(Sys.which("java")) > 0)
    return(Sys.which("java"))
  else if(nchar(Sys.getenv("JAVA_HOME")) > 0)
    return(paste(Sys.getenv("JAVA_HOME"), "bin", "java.exe", sep = .Platform$file.sep))
  else if(.Platform$OS.type == "windows") {
    # Note: Should we require the version (32/64-bit) of Java to be the same as the version of R?
    prog_folder <- c("Program Files", "Program Files (x86)")
    for(prog in prog_folder) {
      prog_path <- paste("C:", prog, "Java", sep = .Platform$file.sep)
      jdk_folder <- list.files(prog_path, pattern = "jdk")
      
      for(jdk in jdk_folder) {
        path <- paste(prog_path, jdk, "bin", "java.exe", sep = .Platform$file.sep)
        if(file.exists(path)) return(path)
      }
    }
    
    # Check for existence of JRE and warn user
    for(prog in prog_folder) {
      path <- paste("C:", prog, "Java", "jre7", "bin", "java.exe", sep = .Platform$file.sep)
      if(file.exists(path)) warning("Found JRE at ", path, " but H2O requires the JDK to run.")
    }
  }
  
  stop("Cannot find Java. Please install the latest JDK from http://www.oracle.com/technetwork/java/javase/downloads/index.html")
}

.h2o.downloadJar <- function(branch, version, overwrite = FALSE) {
  # if(missing(branch)) branch <- packageDescription("h2o")$Branch
  if(missing(branch))
    branch <- readLines(paste(.h2o.pkg.path, "branch.txt", sep = .Platform$file.sep))
  if(missing(version)) version <- packageVersion("h2o")[1,4]
  if(!is.logical(overwrite)) stop("overwrite must be TRUE or FALSE")
  
  dest_folder <- paste(.h2o.pkg.path, "java", sep = .Platform$file.sep)
  if(!file.exists(dest_folder)) dir.create(dest_folder)
  dest_file <- paste(dest_folder, "h2o.jar", sep = .Platform$file.sep)
  
  # Download if h2o.jar doesn't already exist or user specifies force overwrite
  if(overwrite || !file.exists(dest_file)) {
    base_url <- paste("s3.amazonaws.com/h2o-release/h2o", branch, version, "Rjar", sep = "/")
    h2o_url <- paste("http:/", base_url, "h2o.jar", sep = "/")
    
    # Get MD5 checksum
    md5_url <- paste("http:/", base_url, "h2o.jar.md5", sep = "/")
    # ttt <- getURLContent(md5_url, binary = FALSE)
    # tcon <- textConnection(ttt)
    # md5_check <- readLines(tcon, n = 1)
    # close(tcon)
    md5_file <- tempfile(fileext = ".md5")
    download.file(md5_url, destfile = md5_file, mode = "w", cacheOK = FALSE, quiet = TRUE)
    md5_check <- readLines(md5_file, n = 1)
    if (nchar(md5_check) != 32) stop("md5 malformed, must be 32 characters (see ", md5_url, ")")
    unlink(md5_file)
    
    # Save to temporary file first to protect against incomplete downloads
    temp_file <- paste(dest_file, "tmp", sep = ".")
    cat("Performing one-time download of h2o.jar from\n")
    cat("    ", h2o_url, "\n")
    cat("(This could take a few minutes, please be patient...)\n")
    download.file(url = h2o_url, destfile = temp_file, mode = "wb", cacheOK = FALSE, quiet = TRUE)

    # Apply sanity checks
    if(!file.exists(temp_file))
      stop("Error: Transfer failed. Please download ", h2o_url, " and place h2o.jar in ", dest_folder)

    md5_temp_file = md5sum(temp_file)
    md5_temp_file_as_char = as.character(md5_temp_file)
    if(md5_temp_file_as_char != md5_check) {
      cat("Error: Expected MD5: ", md5_check, "\n")
      cat("Error: Actual MD5  : ", md5_temp_file_as_char, "\n")
      stop("Error: MD5 checksum of ", temp_file, " does not match ", md5_check)
    }

    # Move good file into final position
    file.rename(temp_file, dest_file)
  }
  return(dest_file)
}

#-------------------------------- Deprecated --------------------------------#
# NB: if H2OVersion matches \.99999$ is a development version, so pull package info out of file.  yes this is a hack
#     but it makes development versions properly prompt upgrade
# .h2o.checkPackage <- function(myURL, silentUpgrade, promptUpgrade) {
#   h2oWrapper.__formatError <- function(error, prefix="  ") {
#     result = ""
#     items = strsplit(error,"\n")[[1]];
#     for (i in 1:length(items))
#       result = paste(result, prefix, items[i], "\n", sep="")
#     result
#   }
#   
#   temp = postForm(paste(myURL, .h2o.__PAGE_RPACKAGE, sep="/"), style = "POST")
#   res = fromJSON(temp)
#   if (!is.null(res$error))
#     stop(paste(myURL," returned the following error:\n", h2oWrapper.__formatError(res$error)))
#   
#   H2OVersion = res$version
#   myFile = res$filename
#   
#   if( grepl('\\.99999$', H2OVersion) ){
#     H2OVersion <- sub('\\.tar\\.gz$', '', sub('.*_', '', myFile))
#   }
#   
#   # sigh. I so wish people would occasionally listen to me; R expects a version to be %d.%d.%d.%d and will ignore anything after
#   myPackages <- installed.packages()[,1]
#   needs_upgrade <- F
#   if( 'h2oRClient' %in% myPackages ){
#     ver <- unclass( packageVersion('h2oRClient') )
#     ver <- paste( ver[[1]], collapse='.' )
#     needs_upgrade <- !(ver == H2OVersion)
#   }
#   
#   if("h2oRClient" %in% myPackages && !needs_upgrade )
#     cat("H2O R package and server version", H2OVersion, "match\n")
#   else if(.h2o.shouldUpgrade(silentUpgrade, promptUpgrade, H2OVersion)) {
#     if("h2oRClient" %in% myPackages) {
#       cat("Removing old H2O R package version", toString(packageVersion("h2oRClient")), "\n")
#       if("package:h2oRClient" %in% search())
#         detach("package:h2oRClient", unload=TRUE)
#       remove.packages("h2oRClient")
#     }
#     cat("Downloading and installing H2O R package version", H2OVersion, "\n")
#     install.packages("h2oRClient", repos = c(H2O = paste(myURL, "R", sep = "/"), getOption("repos")))
#   }
# }
# 
# Check if user wants to install H2O R package matching version on server
# Note: silentUpgrade supercedes promptUpgrade
# .h2o.shouldUpgrade <- function(silentUpgrade, promptUpgrade, H2OVersion) {
#   if(silentUpgrade) return(TRUE)
#   if(promptUpgrade) {
#     ans = readline(paste("Do you want to install H2O R package version", H2OVersion, "from the server (Y/N)? "))
#     temp = substr(ans, 1, 1)
#     if(temp == "Y" || temp == "y") return(TRUE)
#     else if(temp == "N" || temp == "n") return(FALSE)
#     else stop("Invalid answer! Please enter Y for yes or N for no")
#   } else return(FALSE)
# }
