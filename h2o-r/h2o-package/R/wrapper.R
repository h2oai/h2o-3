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
#' @param startH2O (Optional) A \code{logical} value indicating whether to try to start H2O from R if no connection with H2O is detected. This is only possible if \code{ip = "localhost"} or \code{ip = "127.0.0.1"}.  If an existing connection is detected, R does not start H2O.
#' @param forceDL (Optional) A \code{logical} value indicating whether to force download of the H2O executable. Defaults to FALSE, so the executable will only be downloaded if it does not already exist in the h2o R library resources directory \code{h2o/java/h2o.jar}.  This value is only used when R starts H2O.
#' @param Xmx (Optional) (DEPRECATED) A \code{character} string specifying the maximum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @param beta (Optional) A \code{logical} value indicating whether H2O should launch in beta mode. This value is only used when R starts H2O.
#' @param assertion (Optional) A \code{logical} value indicating whether H2O should be launched with assertions enabled. Used mainly for error checking and debugging purposes.  This value is only used when R starts H2O.
#' @param license (Optional) A \code{character} string value specifying the full path of the license file.  This value is only used when R starts H2O.
#' @param nthreads (Optional) Number of threads in the thread pool.  This relates very closely to the number of CPUs used.  -2 means use the CRAN default of 2 CPUs.  -1 means use all CPUs on the host.  A positive integer specifies the number of CPUs directly.  This value is only used when R starts H2O.
#' @param max_mem_size (Optional) A \code{character} string specifying the maximum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @param min_mem_size (Optional) A \code{character} string specifying the minimum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @param strict_version_check (Optional) Setting this to FALSE is unsupported and should only be done when advised by technical support.
#' @return this method will load it and return a \code{H2OConnection} object containing the IP address and port number of the H2O server.
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
                     beta = FALSE, assertion = TRUE, license = NULL, nthreads = -2, max_mem_size = NULL, min_mem_size = NULL,
                     ice_root = NULL, strict_version_check = FALSE) {
  if(!is.character(ip)) stop("ip must be of class character")
  if(!is.numeric(port)) stop("port must be of class numeric")
  if(!is.logical(startH2O)) stop("startH2O must be of class logical")
  if(!is.logical(forceDL)) stop("forceDL must be of class logical")
  if(!missing(Xmx) && !is.character(Xmx)) stop("Xmx must be of class character")
  if(!is.numeric(nthreads)) stop("nthreads must be of class numeric")
  if(!is.null(max_mem_size) && !is.character(max_mem_size)) stop("max_mem_size must be of class character")
  if(!is.null(min_mem_size) && !is.character(min_mem_size)) stop("min_mem_size must be of class character")
  if(!is.null(max_mem_size) && !regexpr("^[1-9][0-9]*[gGmM]$", max_mem_size)) stop("max_mem_size option must be like 1g or 1024m")
  if(!is.null(min_mem_size) && !regexpr("^[1-9][0-9]*[gGmM]$", min_mem_size)) stop("min_mem_size option must be like 1g or 1024m")
  if(!missing(Xmx) && !regexpr("^[1-9][0-9]*[gGmM]$", Xmx)) stop("Xmx option must be like 1g or 1024m")
  if(!is.logical(beta)) stop("beta must be of class logical")
  if(!is.logical(assertion)) stop("assertion must be of class logical")
  if(!is.null(license) && !is.character(license)) stop("license must be of class character")
  if(!is.null(ice_root) && !is.character(ice_root)) stop("ice_root must be of class character")
  if(!is.logical(strict_version_check)) stop("strict_version_check must be of class logical")

  if ((R.Version()$major == "3") && (R.Version()$minor == "1.0")) {
    warning("H2O is specifically not compatible with this exact")
    warning("version of R 3.1.0.")
    warning("Please change to a newer or older version of R.")
    warning("(For technical details, search the r-devel mailing list")
    warning("for type.convert changes in R 3.1.0.)")
    stop("R 3.1.0 is not compatible with H2O!")
  }

  if(!missing(Xmx)) {
    warning("Xmx is a deprecated parameter. Use `max_mem_size` and `min_mem_size` to set the memory boundaries. Using `Xmx` to set these.")
    max_mem_size <- Xmx
    min_mem_size <- Xmx
  }

  if (is.null(ice_root))
    ice_root <- tempdir()

  warnNthreads = FALSE
  tmpConn = new("H2OConnection", ip = ip, port = port)
  if (! h2o.clusterIsUp(tmpConn)) {
    if (!startH2O)
      stop("Cannot connect to H2O server. Please check that H2O is running at ", h2o.getBaseURL(tmpConn))
    else if (ip == "localhost" || ip == "127.0.0.1") {
      cat("\nH2O is not running yet, starting it now...\n")

      if (nthreads == -2) {
        warnNthreads = TRUE
        nthreads = 2
      }

      .h2o.startJar(nthreads = nthreads, max_memory = max_mem_size, min_memory = min_mem_size, beta = beta,
                    assertion = assertion, forceDL = forceDL, license = license, ice_root = ice_root)

      count = 0L
      while(! h2o.clusterIsUp(conn = tmpConn) && (count < 60L)) {
        Sys.sleep(1L)
        count = count + 1L
      }

      if (! h2o.clusterIsUp(conn = tmpConn))
        stop("H2O failed to start, stopping execution.")
    } else
      stop("Can only start H2O launcher if IP address is localhost.")
  }

  conn = new("H2OConnection", ip = ip, port = port)
  cat("Successfully connected to", h2o.getBaseURL(conn), "\n\n")
  h2o.clusterInfo(conn)
  cat("\n")

  verH2O = h2o.getVersion(conn)
  verPkg = packageVersion("h2o")
  if (verH2O != verPkg) {
    message = sprintf("Version mismatch! H2O is running version %s but R package is version %s", verH2O, toString(verPkg))
    if (strict_version_check)
      stop(message)
    else
      warning(message)
  }

  if (warnNthreads) {
    cat("Note:  As started, H2O is limited to the CRAN default of 2 CPUs.\n")
    cat("       Shut down and restart H2O as shown below to use all your CPUs.\n")
    cat("           > h2o.shutdown(localH2O)\n")
    cat("           > localH2O = h2o.init(nthreads = -1)\n")
    cat("\n")
  }

  assign("SERVER", conn, .pkg.env)
  conn
}

#' Shut Down H2O Instance 
#'
#' Shut down the specified instance. All data will be lost.
#'
#' This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O instance.
#'
#' @section WARNING: All data, models, and other values stored on the server will be lost! Only call this function if you and all other clients connected to the H2O server are finished and have saved your work.
#' @param client An \linkS4class{H2OConnection} client containing the IP address and port of the server running H2O.
#' @param prompt A \code{logical} value indicating whether to prompt the user before shutting down the H2O server.
#' @note Users must call h2o.shutdown explicitly in order to shut down the local H2O instance started by R. If R is closed before H2O, then an attempt will be made to automatically shut down H2O. This only applies to local instances started with h2o.init, not remote H2O servers.
#' @seealso \code{\link{h2o.init}}
#' @examples
#' # Don't run automatically to prevent accidentally shutting down a cloud
#' \dontrun{
#' library(h2o)
#' localH2O = h2o.init()
#' h2o.shutdown(localH2O)
#' }
#'
h2o.shutdown <- function(conn, prompt = TRUE) {
  if(!is(conn, "H2OConnection")) stop("conn must be of class H2OConnection")
  if(!h2o.clusterIsUp(conn))  stop("There is no H2O instance running at ", h2o.getBaseURL(conn))

  if(!is.logical(prompt)) stop("prompt must be of class logical")
  if(prompt) {
    message = sprintf("Are you sure you want to shutdown the H2O instance running at %s (Y/N)? ", h2o.getBaseURL(conn))
    ans = readline(message)
    temp = substr(ans, 1L, 1L)
  } else {
    temp = "y"
  }
  
  if(temp == "Y" || temp == "y") {
    h2o.doSafePOST(conn = conn, urlSuffix = .h2o.__SHUTDOWN)
  }
  
  if((conn@ip == "localhost" || conn@ip == "127.0.0.1") && .h2o.startedH2O()) {
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
  if(!is(client, "H2OConnection")) stop("client must be a H2OConnection object")
  .h2o.__checkUp(client)
  myURL = paste0("http://", client@ip, ":", client@port, "/", .h2o.__PAGE_CLOUD)
  params = list(quiet="true", skip_ticks="true")
  res = fromJSON(h2o.doSafePOST(conn = conn, urlSuffix = .h2o.__PAGE_CLOUD, params = params))
  
  cat("Version:", res$version, "\n")
  cat("Cloud name:", res$cloud_name, "\n")
  cat("Node name:", res$node_name, "\n")
  cat("Cloud size:", res$cloud_size, "\n")
  if(res$locked) cat("Cloud is locked\n\n") else cat("Accepting new members\n\n")
  if(is.null(res$nodes) || length(res$nodes) == 0L) stop("No nodes found!")
  
  # Calculate how many seconds ago we last contacted cloud
  cur_time <- Sys.time()
  for(i in 1:length(res$nodes)) {
    last_contact_sec = as.numeric(res$nodes[[i]]$last_contact)/1e3
    time_diff = cur_time - as.POSIXct(last_contact_sec, origin = "1970-01-01")
    res$nodes[[i]]$last_contact = as.numeric(time_diff)
  }
  cnames = c("name", "value_size_bytes", "free_mem_bytes", "max_mem_bytes", "free_disk_bytes", "max_disk_bytes", "num_cpus", "system_load", "rpcs", "last_contact")
  temp = data.frame(t(sapply(res$nodes, c)))
  temp[,cnames]
}

#---------------------------- H2O Jar Initialization -------------------------------#
.h2o.pkg.path <- NULL
.h2o.jar.env <- new.env()    # Dummy variable used to shutdown H2O when R exits

.onLoad <- function(lib, pkg) {
  .h2o.pkg.path <<- file.path(lib, pkg)
  
  # installing RCurl requires curl and curl-config, which is typically separately installed
  rcurl_package_is_installed = length(find.package("RCurl", quiet = TRUE)) > 0L
  if(!rcurl_package_is_installed) {
    if(.Platform$OS.type == "unix") {
      # packageStartupMessage("Checking libcurl version...")
      curl_path <- Sys.which("curl-config")
      if(!nzchar(curl_path[[1L]]) || system2(curl_path, args = "--version") != 0L)
        stop("libcurl not found! Please install libcurl (version 7.14.0 or higher) from http://curl.haxx.se. On Linux systems, 
              you will often have to explicitly install libcurl-devel to have the header files and the libcurl library.")
    }
  }
}

.onAttach <- function(libname, pkgname) {
  msg = paste0(
    "\n",
    "----------------------------------------------------------------------\n",
    "\n",
    "Your next step is to start H2O and get a connection object (named\n",
    "'localH2O', for example):\n",
    "    > localH2O = h2o.init()\n",
    "\n",
    "For H2O package documentation, ask for help:\n",
    "    > ??h2o\n",
    "\n",
    "After starting H2O, you can use the Web UI at http://localhost:54321\n",
    "For more information visit http://docs.0xdata.com\n",
    "\n",
    "----------------------------------------------------------------------\n")
  packageStartupMessage(msg)
  
  # Shut down local H2O when user exits from R
  pid_file <- .h2o.getTmpFile("pid")
  if(file.exists(pid_file)) file.remove(pid_file)
  
  reg.finalizer(.h2o.jar.env, function(e) {
    ip = "127.0.0.1"; port = 54321
    myURL = paste0("http://", ip, ":", port)
            
    # require(RCurl); require(rjson)
    if(.h2o.startedH2O() && url.exists(myURL))
      h2o.shutdown(new("H2OConnection", ip=ip, port=port), prompt = FALSE)
  }, onexit = TRUE)
}


.onDetach <- function(libpath) {
  ip    <- "127.0.0.1";
  port  <- 54321
  myURL <- paste0("http://", ip, ":", port)
  if (url.exists(myURL)) {
    tryCatch(h2o.shutdown(new("H2OConnection", ip = ip, port = port), prompt = FALSE), error = function(e) {
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
#   if(.h2o.startedH2O() && url.exists(myURL))
#     h2o.shutdown(new("H2OConnection", ip=ip, port=port), prompt = FALSE)
# }

.h2o.startJar <- function(nthreads = -1, max_memory = NULL, min_memory = NULL, beta = FALSE, assertion = TRUE, forceDL = FALSE, license = NULL, ice_root) {
  command <- .h2o.checkJava()

  if (! is.null(license)) {
    if (! file.exists(license)) {
      stop("License file not found (", license, ")")
    }
  }

  if (missing(ice_root)) {
    stop("ice_root must be specified for .h2o.startJar")
  }

  # Note: Logging to stdout and stderr in Windows only works for R version 3.0.2 or later!
  stdout <- .h2o.getTmpFile("stdout")
  stderr <- .h2o.getTmpFile("stderr")
  write(Sys.getpid(), .h2o.getTmpFile("pid"), append = FALSE)   # Write PID to file to track if R started H2O
  
  jar_file <- .h2o.downloadJar(overwrite = forceDL)
  jar_file <- paste0('"', jar_file, '"')

  # Throw an error if GNU Java is being used
  jver <- system2(command, "-version", stdout = TRUE, stderr = TRUE)
  if(any(grepl("GNU libgcj", jver))) {
    stop("
Sorry, GNU Java is not supported for H2O.
Please download the latest Java SE JDK 7 from the following URL:
http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html")
  }

  if(any(grepl("Client VM", jver))) {
    warning("
You have a 32-bit version of Java.  H2O works best with 64-bit Java.
Please download the latest Java SE JDK 7 from the following URL:
http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html")

    # Set default max_memory to be 1g for 32-bit JVM.
    if(is.null(max_memory)) max_memory = "1g"
  }

  if (.Platform$OS.type == "windows") {
    slashes_fixed_ice_root = gsub("\\\\", "/", ice_root)
  }
  else {
    slashes_fixed_ice_root = ice_root
  }

  # Compose args
  mem_args <- c()
  if(!is.null(min_memory)) mem_args <- c(mem_args, paste0("-Xms", min_memory))
  if(!is.null(max_memory)) mem_args <- c(mem_args, paste0("-Xmx", max_memory))

  args <- mem_args
  if(assertion) args <- c(args, "-ea")
  args <- c(args, "-jar", jar_file)
  args <- c(args, "-name", "H2O_started_from_R")
  args <- c(args, "-ip", "127.0.0.1")
  args <- c(args, "-port", "54321")
  args <- c(args, "-ice_root", slashes_fixed_ice_root)
  if(nthreads > 0L) args <- c(args, "-nthreads", nthreads)
  if(beta) args <- c(args, "-beta")
  if(!is.null(license)) args <- c(args, "-license", license)

  cat("\n")
  cat(        "Note:  In case of errors look at the following log files:\n")
  cat(sprintf("    %s\n", stdout))
  cat(sprintf("    %s\n", stderr))
  cat("\n")

  # Print a java -version to the console
  system2(command, c(mem_args, "-version"))
  cat("\n")

  # Run the real h2o java command
  rc = system2(command,
               args=args,
               stdout=stdout,
               stderr=stderr,
               wait=FALSE)
  if (rc != 0L) {
    stop(sprintf("Failed to exec %s with return code=%s", jar_file, as.character(rc)))
  }
}

.h2o.getTmpFile <- function(type) {
  if(missing(type) || !(type %in% c("stdout", "stderr", "pid")))
    stop("type must be one of 'stdout', 'stderr', or 'pid'")

  if(.Platform$OS.type == "windows") {
    usr <- gsub("[^A-Za-z0-9]", "_", Sys.getenv("USERNAME"))
  } else {
    usr <- gsub("[^A-Za-z0-9]", "_", Sys.getenv("USER"))
  }

  if(type == "stdout")
    file.path(tempdir(), paste("h2o", usr, "started_from_r.out", sep="_"))
  else if(type == "stderr")
    file.path(tempdir(), paste("h2o", usr, "started_from_r.err", sep="_"))
  else
    file.path(tempdir(), paste("h2o", usr, "started_from_r.pid", sep="_"))
}

.h2o.startedH2O <- function() {
  pid_file <- .h2o.getTmpFile("pid")
  if(file.exists(pid_file)) {
    pid_saved <- as.numeric(readLines(pid_file))
    pid_saved == Sys.getpid()
  } else
    FALSE
}

# This function returns the path to the Java executable if it exists
# 1) Check for Java in user's PATH
# 2) Check for JAVA_HOME environment variable
# 3) If Windows, check standard install locations in Program Files folder. Warn if JRE found, but not JDK since H2O requires JDK to run.
# 4) When all fails, stop and prompt user to download JDK from Oracle website.
.h2o.checkJava <- function() {
  if(nzchar(Sys.which("java")))
    Sys.which("java")
  else if(nzchar(Sys.getenv("JAVA_HOME")))
    file.path(Sys.getenv("JAVA_HOME"), "bin", "java.exe")
  else if(.Platform$OS.type == "windows") {
    # Note: Should we require the version (32/64-bit) of Java to be the same as the version of R?
    prog_folder <- c("Program Files", "Program Files (x86)")
    for(prog in prog_folder) {
      prog_path <- file.path("C:", prog, "Java")
      jdk_folder <- list.files(prog_path, pattern = "jdk")
      
      for(jdk in jdk_folder) {
        path <- file.path(prog_path, jdk, "bin", "java.exe")
        if(file.exists(path)) return(path)
      }
    }
    
    # Check for existence of JRE and warn user
    for(prog in prog_folder) {
      path <- file.path("C:", prog, "Java", "jre7", "bin", "java.exe")
      if(file.exists(path)) warning("Found JRE at ", path, " but H2O requires the JDK to run.")
    }
  }
  else
    stop("Cannot find Java. Please install the latest JDK from http://www.oracle.com/technetwork/java/javase/downloads/index.html")
}

.h2o.downloadJar <- function(branch, version, overwrite = FALSE) {
  if (is.null(.h2o.pkg.path)) {
    pkg_path = dirname(system.file(".", package = "h2o"))
  } else {
    pkg_path = .h2o.pkg.path

    # Find h2o-jar from testthat tests inside R-Studio.
    if (length(grep("h2o-dev/h2o-r/h2o$", pkg_path)) == 1L) {
      tmp = substr(pkg_path, 1L, nchar(pkg_path) - nchar("h2o-dev/h2o-r/h2o"))
      return(sprintf("%s/h2o-dev/build/h2o.jar", tmp))
    }
  }

  if (missing(branch)) {
    branchFile <- file.path(pkg_path, "branch.txt")
    branch <- readLines(branchFile)
  }

  if (missing(version)) {
    buildnumFile <- file.path(pkg_path, "buildnum.txt")
    version <- readLines(buildnumFile)
  }

  if(!is.logical(overwrite)) stop("overwrite must be TRUE or FALSE")
  
  dest_folder <- file.path(pkg_path, "java")
  if(!file.exists(dest_folder)) dir.create(dest_folder)
  dest_file <- file.path(dest_folder, "h2o.jar")
  
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
    md5_check <- readLines(md5_file, n = 1L)
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
  dest_file
}
