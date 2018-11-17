#' Initialize and Connect to H2O
#'
#' Attempts to start and/or connect to and H2O instance.
#'
#' By default, this method first checks if an H2O instance is connectible. If it cannot connect and \code{start = TRUE} with \code{ip = "localhost"}, it will attempt to start and instance of H2O at localhost:54321.
#' If an open ip and port of your choice are passed in, then this method will attempt to start an H2O instance at that specified ip  port.
#'
#' When initializing H2O locally, this method searches for h2o.jar in the R library resources (\code{system.file("java", "h2o.jar", package = "h2o")}), and if the file does not exist, it will automatically attempt to download the correct version from Amazon S3. The user must have Internet access for this process to be successful.
#'
#' Once connected, the method checks to see if the local H2O R package version matches the version of H2O running on the server. If there is a mismatch and the user indicates she wishes to upgrade, it will remove the local H2O R package and download/install the H2O R package from the server.
#'
#' @param ip Object of class \code{character} representing the IP address of the server where H2O is running.
#' @param port Object of class \code{numeric} representing the port number of the H2O server.
#' @param name (Optional) A \code{character} string representing the H2O cloud name.
#' @param startH2O (Optional) A \code{logical} value indicating whether to try to start H2O from R if no connection with H2O is detected. This is only possible if \code{ip = "localhost"} or \code{ip = "127.0.0.1"}.  If an existing connection is detected, R does not start H2O.
#' @param forceDL (Optional) A \code{logical} value indicating whether to force download of the H2O executable. Defaults to FALSE, so the executable will only be downloaded if it does not already exist in the h2o R library resources directory \code{h2o/java/h2o.jar}.  This value is only used when R starts H2O.
#' @param enable_assertions (Optional) A \code{logical} value indicating whether H2O should be launched with assertions enabled. Used mainly for error checking and debugging purposes.  This value is only used when R starts H2O.
#' @param license (Optional) A \code{character} string value specifying the full path of the license file.  This value is only used when R starts H2O.
#' @param nthreads (Optional) Number of threads in the thread pool.  This relates very closely to the number of CPUs used. -1 means use all CPUs on the host (Default).  A positive integer specifies the number of CPUs directly.  This value is only used when R starts H2O.
#' @param max_mem_size (Optional) A \code{character} string specifying the maximum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @param min_mem_size (Optional) A \code{character} string specifying the minimum size, in bytes, of the memory allocation pool to H2O. This value must a multiple of 1024 greater than 2MB. Append the letter m or M to indicate megabytes, or g or G to indicate gigabytes.  This value is only used when R starts H2O.
#' @param ice_root (Optional) A directory to handle object spillage. The defaul varies by OS.
#' @param log_dir (Optional) A directory where H2O server logs are stored. The default varies by OS.
#' @param log_level (Optional) The level of logging of H2O server. The default is INFO.
#' @param strict_version_check (Optional) Setting this to FALSE is unsupported and should only be done when advised by technical support.
#' @param proxy (Optional) A \code{character} string specifying the proxy path.
#' @param https (Optional) Set this to TRUE to use https instead of http.
#' @param insecure (Optional) Set this to TRUE to disable SSL certificate checking.
#' @param username (Optional) Username to login with.
#' @param password (Optional) Password to login with.
#' @param cookies (Optional) Vector(or list) of cookies to add to request.
#' @param context_path (Optional) The last part of connection URL: http://<ip>:<port>/<context_path>
#' @param ignore_config (Optional) A \code{logical} value indicating whether a search for a .h2oconfig file should be conducted or not. Default value is FALSE.
#' @param extra_classpath (Optional) A vector of paths to libraries to be added to the Java classpath when H2O is started from R.
#' @param jvm_custom_args (Optional) A \code{character} list of custom arguments for the JVM where new H2O instance is going to run, if started. Ignored when connecting to an existing instance.
#' @param bind_to_localhost (Optional) A \code{logical} flag indicating whether access to the H2O instance should be restricted to the local machine (default) or if it can be reached from other computers on the network. Only applicable when H2O is started from R.
#' @return this method will load it and return a \code{H2OConnection} object containing the IP address and port number of the H2O server.
#' @note Users may wish to manually upgrade their package (rather than waiting until being prompted), which requires
#' that they fully uninstall and reinstall the H2O package, and the H2O client package. You must unload packages running
#' in the environment before upgrading. It's recommended that users restart R or R studio after upgrading
#' @seealso \href{http://h2o-release.s3.amazonaws.com/h2o-dev/rel-shannon/2/docs-website/h2o-r/h2o_package.pdf}{H2O R package documentation} for more details. \code{\link{h2o.shutdown}} for shutting down from R.
#' @examples
#' \dontrun{
#' # Try to connect to a local H2O instance that is already running.
#' # If not found, start a local H2O instance from R with the default settings.
#' h2o.init()
#'
#' # Try to connect to a local H2O instance.
#' # If not found, raise an error.
#' h2o.init(startH2O = FALSE)
#'
#' # Try to connect to a local H2O instance that is already running.
#' # If not found, start a local H2O instance from R with 5 gigabytes of memory.
#' h2o.init(max_mem_size = "5g")
#'
#' # Try to connect to a local H2O instance that is already running.
#' # If not found, start a local H2O instance from R that uses 5 gigabytes of memory.
#' h2o.init(max_mem_size = "5g")
#' }
#' @export
h2o.init <- function(ip = "localhost", port = 54321, name = NA_character_, startH2O = TRUE, forceDL = FALSE,
                     enable_assertions = TRUE, license = NULL, nthreads = -1,
                     max_mem_size = NULL, min_mem_size = NULL,
                     ice_root = tempdir(), log_dir = NA_character_, log_level = NA_character_,
                     strict_version_check = TRUE, proxy = NA_character_,
                     https = FALSE, insecure = FALSE, username = NA_character_, password = NA_character_,
                     cookies = NA_character_, context_path = NA_character_, ignore_config = FALSE,
                     extra_classpath = NULL, jvm_custom_args = NULL,
                     bind_to_localhost = TRUE) {

    if(!(ignore_config)){
      # Check for .h2oconfig file
      # Find .h2oconfig file starting from currenting directory and going
      # up all parent directories until it reaches the root directory.
      config_path <- .find.config()

      #Read in config if available
      if(!(is.null(config_path))){

        h2oconfig <- .parse.h2oconfig(config_path,print_path=TRUE)

        #Check for each `allowed_config_keys` in the config file and set to counterparts in `h2o.init()`
        if(strict_version_check == TRUE && "init.check_version" %in% colnames(h2oconfig)){
          strict_version_check <- as.logical(trimws(toupper(as.character(h2oconfig$init.check_version))))
        }
        if(is.na(proxy) && "init.proxy" %in% colnames(h2oconfig)){
          proxy <- trimws(as.character(h2oconfig$init.proxy))
        }
        if(insecure == FALSE && "init.verify_ssl_certificates" %in% colnames(h2oconfig)){
          insecure <- as.logical(trimws(toupper(as.character(h2oconfig$init.verify_ssl_certificates))))
        }
        if(is.na(cookies) && "init.cookies" %in% colnames(h2oconfig)){
          cookies <- as.vector(trimws(strsplit(as.character(h2oconfig$init.cookies),";")[[1]]))
        }
        if(is.na(username) && "init.username" %in% colnames(h2oconfig)){
          username <- trimws(as.character(h2oconfig$init.username))
        }
        if(is.na(password) && "init.password" %in% colnames(h2oconfig)){
          password <- trimws(as.character(h2oconfig$init.password))
        }
      }
    }

  if(!is.character(ip) || length(ip) != 1L || is.na(ip) || !nzchar(ip))
    stop("`ip` must be a non-empty character string")
  if(!is.numeric(port) || length(port) != 1L || is.na(port) || port < 0 || port > 65536)
    stop("`port` must be an integer ranging from 0 to 65536")
  if(!is.character(name) && !nzchar(name))
    stop("`name` must be a character string or NA_character_")
  if(!is.logical(startH2O) || length(startH2O) != 1L || is.na(startH2O))
    stop("`startH2O` must be TRUE or FALSE")
  if(!is.logical(forceDL) || length(forceDL) != 1L || is.na(forceDL))
    stop("`forceDL` must be TRUE or FALSE")
  if(!is.numeric(nthreads) || length(nthreads) != 1L || is.na(nthreads) || nthreads < -2)
    stop("`nthreads` must an integer value greater than or equal to -2")
  if(!is.null(max_mem_size) &&
     !(is.character(max_mem_size) && length(max_mem_size) == 1L && !is.na(max_mem_size) && nzchar(max_mem_size)))
    stop("`max_mem_size` must be NULL or a non-empty character string")
  if(!is.null(max_mem_size) && !regexpr("^[1-9][0-9]*[gGmM]$", max_mem_size))
    stop("`max_mem_size` option must be like 1g or 1024m")
  if(!is.null(min_mem_size) &&
     !(is.character(min_mem_size) && length(min_mem_size) == 1L && !is.na(min_mem_size) && nzchar(min_mem_size)))
    stop("`min_mem_size` must be NULL or a non-empty character string")
  if(!is.null(min_mem_size) && !regexpr("^[1-9][0-9]*[gGmM]$", min_mem_size))
    stop("`min_mem_size` option must be like 1g or 1024m")
  if(!is.logical(enable_assertions) || length(enable_assertions) != 1L || is.na(enable_assertions))
    stop("`enable_assertions` must be TRUE or FALSE")
  if(!is.null(license) && !is.character(license))
    stop("`license` must be of class character")
  if(!is.character(ice_root) || length(ice_root) != 1L || is.na(ice_root) || !nzchar(ice_root))
    stop("`ice_root` must be a non-empty character string")
  if(!is.character(log_dir) && !nzchar(log_dir))
    stop("`log_dir` must be a character string or NA_character_")
  if(!is.character(log_level) && !nzchar(log_level))
    stop("`log_level` must be a character string or NA_character_")
  if(!is.logical(strict_version_check) || length(strict_version_check) != 1L || is.na(strict_version_check))
    stop("`strict_version_check` must be TRUE or FALSE")
  if(!is.character(proxy) || !nzchar(proxy))
    stop("`proxy` must be a character string or NA_character_")
  if(!is.logical(https) || length(https) != 1L || is.na(https))
    stop("`https` must be TRUE or FALSE")
  if(!is.logical(insecure) || length(insecure) != 1L || is.na(insecure))
    stop("`insecure` must be TRUE or FALSE")
  if(https != insecure)
    stop("`https` and `insecure` must both be TRUE to enable HTTPS")
  if(!is.character(username) || !nzchar(username))
    stop("`username` must be a character string or NA_character_")
  if (!is.character(password) || !nzchar(password))
    stop("`password` must be a character string or NA_character_")
  if (is.na(username) != is.na(password))
    stop("Must provide both `username` and `password`")
  if (!is.na(cookies) && (!is.vector(cookies)))
    stop("`cookies` must be a vector of cookie values")
  if(!is.character(context_path) || !nzchar(context_path))
    stop("`context_path` must be a character string or NA_character_")
  if(!is.null(extra_classpath) && !is.character(extra_classpath))
    stop("`extra_classpath` must be a character vector or NULL")

  if ((R.Version()$major == "3") && (R.Version()$minor == "1.0")) {
    stop("H2O is not compatible with R 3.1.0\n",
         "Please change to a newer or older version of R.\n",
         "(For technical details, search the r-devel mailing list\n",
         "for type.convert changes in R 3.1.0.)")
  }

  doc_ip <- Sys.getenv("H2O_R_CMD_CHECK_DOC_EXAMPLES_IP")
  doc_port <- Sys.getenv("H2O_R_CMD_CHECK_DOC_EXAMPLES_PORT")
  if (nchar(doc_ip)) ip <- doc_ip
  if (nchar(doc_port)) port <- as.numeric(doc_port)

  warnNthreads <- FALSE
  tmpConn <- new("H2OConnection", ip = ip, port = port, name = name, proxy = proxy, https = https, insecure = insecure,
    username = username, password = password,cookies = cookies, context_path = context_path)
  if (!h2o.clusterIsUp(tmpConn)) {
    if (!startH2O)
      stop("Cannot connect to H2O server. Please check that H2O is running at ", h2o.getBaseURL(tmpConn))
    else if (ip == "localhost" || ip == "127.0.0.1") {
      cat("\nH2O is not running yet, starting it now...\n")

      if (nthreads == -2) {
        warnNthreads <- TRUE
        nthreads <- 2
      }
      stdout <- .h2o.getTmpFile("stdout")
      .h2o.startJar(ip = ip, port = port, name = name, nthreads = nthreads,
                    max_memory = max_mem_size, min_memory = min_mem_size,
                    enable_assertions = enable_assertions, forceDL = forceDL, license = license,
                    extra_classpath = extra_classpath, ice_root = ice_root, stdout = stdout,
                    log_dir = log_dir, log_level = log_level,
                    jvm_custom_args = jvm_custom_args, bind_to_localhost = bind_to_localhost)

      count <- 0L
      cat("Starting H2O JVM and connecting: ")
      while(!h2o.clusterIsUp(conn = tmpConn) && (count < 60L)) {
        cat(".")
        Sys.sleep(1L)
        count <- count + 1L
      }

      if (!h2o.clusterIsUp(conn = tmpConn)) {
        cat(paste(readLines(stdout), collapse="\n"), "\n")
        print(tmpConn@ip)
        print(tmpConn@port)
        rv <- .h2o.doRawGET(conn = tmpConn, urlSuffix = "")
        print(rv$curlError)
        print(rv$httpStatusCode)
        print(rv$curlErrorMessage)

        stop("H2O failed to start, stopping execution.")
      }
    } else
      stop("Can only start H2O launcher if IP address is localhost.")
  }

  conn <- new("H2OConnection", ip = ip, port = port, name = .h2o.jar.env$name, proxy = proxy, https = https, insecure = insecure,
    username = username, password = password,cookies = cookies, context_path = context_path)
  assign("SERVER", conn, .pkg.env)
  cat(" Connection successful!\n\n")
  .h2o.jar.env$port <- port #Ensure right port is called when quitting R
  h2o.clusterInfo()
  cat("\n")

  if( strict_version_check && !nchar(Sys.getenv("H2O_DISABLE_STRICT_VERSION_CHECK"))) {
    verH2O <- h2o.getVersion()
    verPkg <- packageVersion("h2o")

    if( verH2O != verPkg ){
      build_number_H2O <- h2o.getBuildNumber()
      branch_name_H2O <- h2o.getBranchName()

      if( is.null( build_number_H2O ) ){
        stop(sprintf("Version mismatch! H2O is running version %s but h2o-R package is version %s.
        Upgrade H2O and R to latest stable version - http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html",
        verH2O, toString(verPkg)))
      } else if (build_number_H2O =="unknown"){
        stop(sprintf("Version mismatch! H2O is running version %s but h2o-R package is version %s.
        Upgrade H2O and R to latest stable version - http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html",
        verH2O, toString(verPkg)))
      } else if (build_number_H2O =="99999"){
        stop((sprintf("Version mismatch! H2O is running version %s but h2o-R package is version %s.
        This is a developer build, please contact your developer",verH2O, toString(verPkg) )))
      } else {
         stop(sprintf("Version mismatch! H2O is running version %s but h2o-R package is version %s.
         Install the matching h2o-R version from - http://h2o-release.s3.amazonaws.com/h2o/%s/%s/index.html",
         verH2O, toString(verPkg),branch_name_H2O,build_number_H2O))
      }
    }
  }

  if (warnNthreads) {
    cat("Note:  As started, H2O is limited to the CRAN default of 2 CPUs.\n")
    cat("       Shut down and restart H2O as shown below to use all your CPUs.\n")
    cat("           > h2o.shutdown()\n")
    cat("           > h2o.init(nthreads = -1)\n")
    cat("\n")
  }
  conn@mutable$session_id <- .init.session_id()
  invisible(conn)
}

#' Connect to a running H2O instance.
#'
#' @param ip Object of class \code{character} representing the IP address of the server where H2O is running.
#' @param port Object of class \code{numeric} representing the port number of the H2O server.
#' @param strict_version_check (Optional) Setting this to FALSE is unsupported and should only be done when advised by technical support.
#' @param proxy (Optional) A \code{character} string specifying the proxy path.
#' @param https (Optional) Set this to TRUE to use https instead of http.
#' @param insecure (Optional) Set this to TRUE to disable SSL certificate checking.
#' @param username (Optional) Username to login with.
#' @param password (Optional) Password to login with.
#' @param cookies (Optional) Vector(or list) of cookies to add to request.
#' @param context_path (Optional) The last part of connection URL: http://<ip>:<port>/<context_path>
#' @param config (Optional) A \code{list} describing connection parameters. Using \code{config} makes \code{h2o.connect} ignore
#'        other parameters and collect named list members instead (see examples).
#' @return an instance of \code{H2OConnection} object representing a connection to the running H2O instance.
#' @examples
#' \dontrun{
#' library(h2o)
#' # Try to connect to a H2O instance running at http://localhost:54321/cluster_X
#' # If not found, start a local H2O instance from R with the default settings.
#' #h2o.connect(ip = "localhost", port = 54321, context_path = "cluster_X")
#' # Or
#' #config = list(ip = "localhost", port = 54321, context_path = "cluster_X")
#' #h2o.connect(config = config)
#'
#' # Skip strict version check during connecting to the instance
#' #h2o.connect(config = c(strict_version_check = FALSE, config))
#' }
#' @export
h2o.connect <- function(ip = "localhost", port = 54321, strict_version_check = TRUE,
                        proxy = NA_character_ , https = FALSE, insecure = FALSE,
                        username = NA_character_, password = NA_character_,
                        cookies = NA_character_,
                        context_path = NA_character_,
                        config = NULL) {
 if (!is.null(config)) {
   # Check first if config has a special connect_params embedded object
   if (!is.null(config$connect_params)) {
      config <- config$connect_params
   }
   # Directly pass the config to h2o.init and let R and H2O.init decide about parameters validity
   do.call(h2o.init, c(startH2O=FALSE, config))
 } else {
   # Pass arguments directly
   h2o.init(ip=ip, port=port, strict_version_check=strict_version_check,
            proxy=proxy, https=https, insecure=insecure, password=password,
            username=username, cookies=cookies, context_path=context_path)
 }
}

#' Retrieve an H2O Connection
#'
#' Attempt to recover an h2o connection.
#'
#' @return Returns an \linkS4class{H2OConnection} object.
#' @export
h2o.getConnection <- function() {
  conn <- .attemptConnection()
  if (is.null(conn))
    stop("No active connection to an H2O cluster. Did you run `h2o.init()` ?")
  conn
}

.isConnected <- function() {
  conn <- .attemptConnection()
  return ( !is.null(conn) )
}

.attemptConnection <- function() {
  conn <- get("SERVER", .pkg.env)
  if (is.null(conn)) {
    # Try to recover an H2OConnection object from a saved session
    for (objname in ls(parent.frame(), all.names = TRUE)) {
      object <- get(objname, globalenv())
      if (is(object, "H2OConnection") && h2o.clusterIsUp(object)) {
        conn <- object
        assign("SERVER", conn, .pkg.env)
        break
      }
    }
  }
  conn
}

#' Shut Down H2O Instance
#'
#' Shut down the specified instance. All data will be lost.
#'
#' This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O instance.
#'
#' @section WARNING: All data, models, and other values stored on the server will be lost! Only call this function if you and all other clients connected to the H2O server are finished and have saved your work.
#' @param prompt A \code{logical} value indicating whether to prompt the user before shutting down the H2O server.
#' @note Users must call h2o.shutdown explicitly in order to shut down the local H2O instance started by R. If R is closed before H2O, then an attempt will be made to automatically shut down H2O. This only applies to local instances started with h2o.init, not remote H2O servers.
#' @seealso \code{\link{h2o.init}}
#' @examples
#' # Don't run automatically to prevent accidentally shutting down a cloud
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' h2o.shutdown()
#' }
#' @export
h2o.shutdown <- function(prompt = TRUE) {
  conn <- get("SERVER", .pkg.env)
  if( is.null(conn) ) stop("There is no H2O instance running.")
  if( !h2o.clusterIsUp(conn) ) stop("There is no H2O instance running at ", h2o.getBaseURL(conn))

  if(!is.logical(prompt) || length(prompt) != 1L || is.na(prompt)) stop("`prompt` must be TRUE or FALSE")
  if( prompt ) {
    message <- sprintf("Are you sure you want to shutdown the H2O instance running at %s (Y/N)? ", h2o.getBaseURL(conn))
    ans <- readline(message)
    temp <- substr(ans, 1L, 1L)
  } else { temp <- "y" }

  if(temp == "Y" || temp == "y") {
    .h2o.doRawREST(conn = conn, method="POST", urlSuffix = .h2o.__SHUTDOWN, h2oRestApiVersion = .h2o.__REST_API_VERSION)
    assign("SERVER", NULL, .pkg.env)
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

#' Return the status of the cluster
#'
#' Retrieve information on the status of the cluster running H2O.
#'
#' @seealso \linkS4class{H2OConnection}, \code{\link{h2o.init}}
#' @examples
#' \donttest{
#' h2o.init()
#' h2o.clusterStatus()
#' }
#' @export
h2o.clusterStatus <- function() {
  conn = h2o.getConnection()
  if(!h2o.clusterIsUp(conn))  stop("There is no H2O instance running at ", h2o.getBaseURL(conn))

  res <- .h2o.fromJSON(jsonlite::fromJSON(.h2o.doSafeGET(urlSuffix = .h2o.__CLOUD), simplifyDataFrame=FALSE))

  cat("Version:", res$version, "\n")
  cat("Cluster name:", res$cloud_name, "\n")
  cat("Cluster size:", res$cloud_size, "\n")
  if(res$locked) cat("Cluster is locked\n\n") else cat("Accepting new members\n\n")
  if(is.null(res$nodes) || length(res$nodes) == 0L) stop("No nodes found")

  # Calculate how many seconds ago we last contacted cloud
  cur_time <- Sys.time()
  for(i in seq_len(length(res$nodes))) {
    last_contact_sec <- as.numeric(res$nodes[[i]]$last_contact)/1e3
    time_diff <- cur_time - as.POSIXct(last_contact_sec, origin = "1970-01-01")
    res$nodes[[i]]$last_contact <- as.numeric(time_diff)
  }
  cnames <- c("h2o", "healthy", "last_ping", "num_cpus", "sys_load",
              "mem_value_size", "free_mem", "pojo_mem", "swap_mem",
              "free_disk", "max_disk", "pid", "num_keys", "tcps_active", "open_fds", "rpcs_active")
  temp <- data.frame(t(sapply(res$nodes, c)))
  temp[,cnames]
}

#
# Get a session ID at init
.init.session_id <- function() {
  res <- .h2o.fromJSON(jsonlite::fromJSON(.h2o.doSafeGET(urlSuffix = "InitID")))
  res$session_key
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
        stop("libcurl not found. Please install libcurl\n",
             "(version 7.14.0 or higher) from http://curl.haxx.se.\n",
             "On Linux systems you will often have to explicitly install\n",
             "libcurl-devel to have the header files and the libcurl library.")
    }
  }
}

.onAttach <- function(libname, pkgname) {
  msg = paste0(
    "\n",
    "----------------------------------------------------------------------\n",
    "\n",
    "Your next step is to start H2O:\n",
    "    > h2o.init()\n",
    "\n",
    "For H2O package documentation, ask for help:\n",
    "    > ??h2o\n",
    "\n",
    "After starting H2O, you can use the Web UI at http://localhost:54321\n",
    "For more information visit http://docs.h2o.ai\n",
    "\n",
    "----------------------------------------------------------------------\n")
  packageStartupMessage(msg)

  # Shut down local H2O when user exits from R ONLY if h2o started from R
  reg.finalizer(.h2o.jar.env, function(e) {
    ip_    <- "127.0.0.1"
    port_  <- if(!is.null(e$port)) e$port else 54321
    myURL <- paste0("http://", ip_, ":", port_)
    if( .h2o.startedH2O() && url.exists(myURL) ) h2o.shutdown(prompt = FALSE)
    pid_file <- .h2o.getTmpFile("pid")
    if(file.exists(pid_file)) file.remove(pid_file)

  }, onexit = TRUE)
}

.onDetach <- function(libpath) {
  if (! getOption("h2o.dev.shutdown.disable", default = FALSE)) { # we can disable shutdown in dev (good with devtools)
    ip_   <- "127.0.0.1"
    port_  <- if(!is.null(.h2o.jar.env$port)) .h2o.jar.env$port else 54321
    myURL <- paste0("http://", ip_, ":", port_)
    print("A shutdown has been triggered. ")
    if( url.exists(myURL) ) {
      tryCatch(h2o.shutdown(prompt = FALSE), error = function(e) {
        msg = paste(
          "\n",
          "----------------------------------------------------------------------\n",
              "\n",
              "Could not shut down the H2O Java Process!\n",
              "Please shutdown H2O manually by navigating to `http://localhost:54321/Shutdown`\n\n",
              "Windows requires the shutdown of h2o before re-installing -or- updating the h2o package.\n",
              "For more information visit http://docs.h2o.ai\n",
              "\n",
              "----------------------------------------------------------------------\n",
              sep = "")
        warning(msg)
      })
    }
  }
  try(.h2o.__remoteSend("InitID", method = "DELETE"), TRUE)
}

.Last <- function() { if ( .isConnected() ) try(.h2o.__remoteSend("InitID", method = "DELETE"), TRUE)}

#
# Returns error string if the check finds a problem with version.
# This implementation is supposed to blacklist known unsupported versions.
#
.h2o.check_java_version <- function(jver = NULL) {
  if(any(grepl("GNU libgcj", jver))) {
    return("Sorry, GNU Java is not supported for H2O.")
  }
  # NOTE for developers: keep the following blacklist in logically consistent with whitelist in java code - see water.H2O.checkUnsupportedJava, near line 1849
  if (any(grepl("^java version \"1\\.[1-6]\\.", jver))) {
    return(paste0("Your java is not supported: ", jver[1]))
  }
  return(NULL)
}

.h2o.startJar <- function(ip = "localhost", port = 54321, name = NULL, nthreads = -1,
                          max_memory = NULL, min_memory = NULL,
                          enable_assertions = TRUE, forceDL = FALSE, license = NULL, extra_classpath = NULL,
                          ice_root, stdout, log_dir, log_level, jvm_custom_args = NULL, bind_to_localhost) {
  command <- .h2o.checkJava()

  if (! is.null(license)) {
    if (! file.exists(license)) {
      stop("License file not found (", license, ")")
    }
  }

  if (missing(ice_root)) {
    stop("`ice_root` must be specified for .h2o.startJar")
  }

  # Note: Logging to stdout and stderr in Windows only works for R version 3.0.2 or later!
  stderr <- .h2o.getTmpFile("stderr")
  write(Sys.getpid(), .h2o.getTmpFile("pid"), append = FALSE)   # Write PID to file to track if R started H2O

  jar_file <- .h2o.downloadJar(overwrite = forceDL)
  jar_file <- paste0('"', jar_file, '"')

  # Throw an error if GNU Java is being used
  if (.Platform$OS.type == "windows") {
    command <- normalizePath(gsub("\"","",command))
  }

  jver <- tryCatch({system2(command, "-version", stdout = TRUE, stderr = TRUE)},
      error = function(err) {
        print(err)
        stop("You have a 32-bit version of Java. H2O works best with 64-bit Java.\n",
        "Please download the latest Java SE JDK 8 from the following URL:\n",
        "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html")
      }
    )
  jver_error <- .h2o.check_java_version(jver);
  if (!is.null(jver_error)) {
    stop(jver_error, "\n",
    "Please download the latest Java SE JDK 8 from the following URL:\n",
    "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html")
  }
  if(any(grepl("Client VM", jver))) {
    warning("You have a 32-bit version of Java. H2O works best with 64-bit Java.\n",
            "Please download the latest Java SE JDK 8 from the following URL:\n",
            "http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html")

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
  ltrs <- paste0(sample(letters,3, replace = TRUE), collapse="")
  nums <- paste0(sample(0:9, 3,  replace = TRUE),     collapse="")

  if(is.na(name)) name <- paste0("H2O_started_from_R_", gsub("\\s", "_", Sys.info()["user"]),"_",ltrs,nums)
  .h2o.jar.env$name <- name

  if(enable_assertions) args <- c(args, "-ea")
  if(!is.null(jvm_custom_args)) args <- c(args,jvm_custom_args)

  class_path <- paste0(c(jar_file, extra_classpath), collapse=.Platform$path.sep)
  args <- c(args, "-cp", class_path, "water.H2OApp")
  args <- c(args, "-name", name)
  args <- c(args, "-ip", ip)
  if (bind_to_localhost) {
    args <- c(args, "-web_ip", ip)
  }
  args <- c(args, "-port", port)
  args <- c(args, "-ice_root", slashes_fixed_ice_root)

  if(!is.na(log_dir)) args <- c(args, "-log_dir", log_dir)
  if(!is.na(log_level)) args <- c(args, "-log_level", log_level)

  if(nthreads > 0L) args <- c(args, "-nthreads", nthreads)
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
    usr <- gsub("[^A-Za-z0-9]", "_", Sys.getenv("USERNAME", unset="UnknownUser"))
  } else {
    usr <- gsub("[^A-Za-z0-9]", "_", Sys.getenv("USER", unset="UnknownUser"))
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
# X) NO: Fails on Windows.  Check for Java in user's PATH
# 2) Check for JAVA_HOME environment variable
# 3) If Windows, check standard install locations in Program Files folder. Will look for JRE.
# 4) Check for Java in user's PATH.
# 5) When all fails, stop and prompt user to download JRE from Oracle website.
.h2o.checkJava <- function() {
  if(nzchar(Sys.getenv("JAVA_HOME"))) {
    if(.Platform$OS.type == "windows") { file.path(Sys.getenv("JAVA_HOME"), "bin", "java.exe") }
    else                               { file.path(Sys.getenv("JAVA_HOME"), "bin", "java") }
  }
  else if(.Platform$OS.type == "windows") {
    # Note: Should we require the version (32/64-bit) of Java to be the same as the version of R?
    prog_folder <- c("Program Files", "Program Files (x86)")
    for(prog in prog_folder) {
      prog_path <- file.path("C:", prog, "Java")
      java_folder <- list.files(prog_path)

      for(java in java_folder) {
        path <- file.path(prog_path, java, "bin", "java.exe")
        if(file.exists(path)) return(path)
      }
    }
  }
  else if(nzchar(Sys.which("java")))
    Sys.which("java")
  else
    stop("Cannot find Java. Please install the latest JRE from\n",
         "http://www.oracle.com/technetwork/java/javase/downloads/index.html")
}

# This function returns a string to the valid path on the local filesystem of the h2o.jar file,
# or it calls stop() and does not return.
#
# It will download a jar file if it needs to.
.h2o.downloadJar <- function(overwrite = FALSE) {
  if(!is.logical(overwrite) || length(overwrite) != 1L || is.na(overwrite)) stop("`overwrite` must be TRUE or FALSE")

  # PUBDEV-3534 hook to use arbitrary h2o.jar
  own_jar = Sys.getenv("H2O_JAR_PATH")
  is_url = function(x) any(grepl("^(http|ftp)s?://", x), grepl("^(http|ftp)s://", x))
  if (nzchar(own_jar) && !is_url(own_jar)) {
    if (!file.exists(own_jar))
      stop(sprintf("Environment variable H2O_JAR_PATH is set to '%s' but file does not exists, unset environment variable or provide valid path to h2o.jar file.", own_jar))
    return(own_jar)
  }

  if (is.null(.h2o.pkg.path)) {
    pkg_path = dirname(system.file(".", package = "h2o"))
  } else {
    pkg_path = .h2o.pkg.path

    # Find h2o-jar from testthat tests inside RStudio.
    if (length(grep("h2o-dev/h2o-r/h2o$", pkg_path)) == 1L) {
      tmp = substr(pkg_path, 1L, nchar(pkg_path) - nchar("h2o-dev/h2o-r/h2o"))
      return(sprintf("%s/h2o-dev/build/h2o.jar", tmp))
    }
  }

  # Check for jar file in 'java' directory.
  if (! overwrite) {
    possible_file <- file.path(pkg_path, "java", "h2o.jar")
    if (file.exists(possible_file)) {
      return(possible_file)
    }
  }

  # Check for jar file in 'inst/java' directory.
  if (! overwrite) {
    possible_file <- file.path(pkg_path, "inst", "java", "h2o.jar")
    if (file.exists(possible_file)) {
      return(possible_file)
    }
  }

  branchFile <- file.path(pkg_path, "branch.txt")
  branch <- readLines(branchFile)

  buildnumFile <- file.path(pkg_path, "buildnum.txt")
  version <- readLines(buildnumFile)

  # mockup h2o package as CRAN release (no java/h2o.jar) hook h2o.jar url - PUBDEV-3534
  jarFile <- file.path(pkg_path, "jar.txt")
  if (file.exists(jarFile) && !nzchar(own_jar))
    own_jar <- readLines(jarFile)

  dest_folder <- file.path(pkg_path, "java")
  if (!file.exists(dest_folder)) {
    dir.create(dest_folder)
  }

  dest_file <- file.path(dest_folder, "h2o.jar")

  # Download if h2o.jar doesn't already exist or user specifies force overwrite
  if (nzchar(own_jar) && is_url(own_jar)) {
    h2o_url = own_jar # md5 must have same file name and .md5 suffix
    md5_url = paste(own_jar, ".md5", sep="")
  } else {
    base_url <- paste("s3.amazonaws.com/h2o-release/h2o", branch, version, "Rjar", sep = "/")
    h2o_url <- paste("http:/", base_url, "h2o.jar", sep = "/")
    # Get MD5 checksum
    md5_url <- paste("http:/", base_url, "h2o.jar.md5", sep = "/")
  }
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
  return(dest_file[file.exists(dest_file)])
}

#' View Network Traffic Speed
#'
#' View speed with various file sizes.
#' @return Returns a table listing the network speed for 1B, 10KB, and 10MB.
#' @export
h2o.networkTest <- function() {
  res <- .h2o.__remoteSend("NetworkTest", method = "GET")
  res$table
}

# Trigger an explicit garbage collection across all nodes in the H2O cluster.
.h2o.garbageCollect <- function() {
  res <- .h2o.__remoteSend("GarbageCollect", method = "POST")
}

#' Open H2O Flow
#'
#' Open H2O Flow in your browser
#'
#' @importFrom utils browseURL
#' @export
h2o.flow <- function(){
  browseURL(.h2o.calcBaseURL(urlSuffix=""))
}
