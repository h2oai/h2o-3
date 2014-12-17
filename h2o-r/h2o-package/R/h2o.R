#'
#' H2O <-> R Communication and Utility Methods
#'
#' Collected here are the various methods used by the h2o-R package to communicate with the H2O
#' backend. There are methods for checking cluster health, polling, and inspecting objects in
#' the H2O store.

#-----------------------------------------------------------------------------------------------------------------------
#   GET & POST
#-----------------------------------------------------------------------------------------------------------------------

.h2o.__REST_API_VERSION = 3

.skip_if_not_developer <- function() {
  if (Sys.getenv("USER") %in% c("tomk", "amy")) {
    return()
  }

  skip("Not a developer")
}

.h2o.calcBaseURL <- function(conn, h2oRestApiVersion, urlSuffix) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")

  if (missing(h2oRestApiVersion) || (h2oRestApiVersion < 0)) {
    url = sprintf("http://%s:%s/%s", conn@ip, as.character(conn@port), urlSuffix)
  } else {
    url = sprintf("http://%s:%s/%d/%s", conn@ip, as.character(conn@port), h2oRestApiVersion, urlSuffix)
  }

  return(url)
}

.h2o.doRawREST <- function(conn, h2oRestApiVersion, urlSuffix, parms, method, fileData) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")
  if (! missing(parms)) { stopifnot(class(parms) == "list") }
  if (missing(parms)) {
    parms = list()
  }
  if (missing(method)) stop()
  stopifnot(class(method) == "character")
  if (! missing(fileData)) { stopifnot(class(fileData) == "FileUploadInfo") }

  url = .h2o.calcBaseURL(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix)

  queryString = ""
  i = 1
  while (i <= length(parms)) {
    name = names(parms)[i]
    value = parms[i]
    escaped_value = curlEscape(value)
    if (i > 1) {
      queryString = sprintf("%s&", queryString)
    }
    queryString = sprintf("%s%s=%s", queryString, name, escaped_value)
    i = i + 1
  }

  postBody = ""
  if (missing(fileData)) {
    # This is the typical case.
    if (method == "POST") {
      postBody = queryString
    } else {
      if (nchar(queryString) > 0) {
        url = sprintf("%s?%s", url, queryString)
      }
    }
  } else {
    stopifnot(method == "POST")
    if (nchar(queryString) > 0) {
      url = sprintf("%s?%s", url, queryString)
    }
  }

  .__curlError = FALSE
  .__curlErrorMessage = ""
  httpStatusCode = -1
  httpStatusMessage = ""
  payload = ""

  if (.h2o.isLogging()) {
    .h2o.logRest("------------------------------------------------------------")
    .h2o.logRest("")
    .h2o.logRest(sprintf("%s %s", method, url))
    .h2o.logRest(sprintf("postBody: %s", postBody))
  }

  beginTimeSeconds = as.numeric(proc.time())[3]

  if (method == "GET") {
    h = basicHeaderGatherer()
    tmp = tryCatch(getURL(url = url, headerfunction = h$update),
                   error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
    if (! .__curlError) {
      httpStatusCode = as.numeric(h$value()["status"])
      httpStatusMessage = h$value()["statusMessage"]
      payload = tmp
    }
  } else if (! missing(fileData)) {
    stopifnot(method == "POST")
    h = basicHeaderGatherer()
    t = basicTextGatherer()
    tmp = tryCatch(postForm(uri = url, .params = list(fileData = fileData), .opts=curlOptions(writefunction = t$update, headerfunction=h$update, verbose = FALSE)),
                   error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
    if (! .__curlError) {
      httpStatusCode = as.numeric(h$value()["status"])
      httpStatusMessage = h$value()["statusMessage"]
      payload = t$value()
    }
  } else if (method == "POST") {
    h = basicHeaderGatherer()
    t = basicTextGatherer()
    tmp = tryCatch(curlPerform(url = url, postfields=postBody, writefunction = t$update, headerfunction = h$update, verbose = FALSE),
                   error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
    if (! .__curlError) {
      httpStatusCode = as.numeric(h$value()["status"])
      httpStatusMessage = h$value()["statusMessage"]
      payload = t$value()
    }
  }
  else {
    message = sprintf("Unknown HTTP method %s", method)
    stop(message)
  }

  endTimeSeconds = as.numeric(proc.time())[3]
  deltaSeconds = endTimeSeconds - beginTimeSeconds
  deltaMillis = deltaSeconds * 1000.0

  if (.h2o.isLogging()) {
    .h2o.logRest("")
    .h2o.logRest(sprintf("curlError:         %s", as.character(.__curlError)))
    .h2o.logRest(sprintf("curlErrorMessage:  %s", .__curlErrorMessage))
    .h2o.logRest(sprintf("httpStatusCode:    %d", httpStatusCode))
    .h2o.logRest(sprintf("httpStatusMessage: %s", httpStatusMessage))
    .h2o.logRest(sprintf("millis:            %s", as.character(as.integer(deltaMillis))))
    .h2o.logRest("")
    .h2o.logRest(payload)
    .h2o.logRest("")
  }

  rv = list(url = url,
            postBody = postBody,
            curlError = .__curlError,
            curlErrorMessage = .__curlErrorMessage,
            httpStatusCode = httpStatusCode,
            httpStatusMessage = httpStatusMessage,
            payload = payload)

  return(rv)
}

#' Perform a low-level HTTP GET operation on an H2O instance
#'
#' Does not do any I/O level error checking.  Caller must do its own validations.
#' Does not modify the response payload in any way.
#' Log the request and response if h2o.startLogging() has been called.
#'
#' The return value is a list as follows:
#'     $url                -- Final calculated URL.
#'     $postBody           -- The body of the POST request from client to server.
#'     $curlError          -- TRUE if a socket-level error occurred.  FALSE otherwise.
#'     $curlErrorMessage   -- If curlError is TRUE a message about the error.
#'     $httpStatusCode     -- The HTTP status code.  Usually 200 if the request succeeded.
#'     $httpStatusMessage  -- A string describing the httpStatusCode.
#'     $payload            -- The raw response payload as a character vector.
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, the version prefix is skipped.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return A list object as described above
h2o.doRawGET <- function(conn, h2oRestApiVersion, urlSuffix, parms) {
  rv = .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = "GET")
  return(rv)
}

#' Perform a low-level HTTP POST operation on an H2O instance
#'
#' Does not do any I/O level error checking.  Caller must do its own validations.
#' Does not modify the response payload in any way.
#' Log the request and response if h2o.startLogging() has been called.
#'
#' The return value is a list as follows:
#'     $url                -- Final calculated URL.
#'     $postBody           -- The body of the POST request from client to server.
#'     $curlError          -- TRUE if a socket-level error occurred.  FALSE otherwise.
#'     $curlErrorMessage   -- If curlError is TRUE a message about the error.
#'     $httpStatusCode     -- The HTTP status code.  Usually 200 if the request succeeded.
#'     $httpStatusMessage  -- A string describing the httpStatusCode.
#'     $payload            -- The raw response payload as a character vector.
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, the version prefix is skipped.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param fileData (Optional) Information to POST (NOTE: changes Content-type from XXX-www-url-encoded to multi-part).  Use fileUpload(normalizePath("/path/to/file")).
#' @return A list object as described above
h2o.doRawPOST <- function(conn, h2oRestApiVersion, urlSuffix, parms, fileData) {
  rv = .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = "POST", fileData = fileData)
  return(rv)
}

.h2o.doREST <- function(conn, h2oRestApiVersion, urlSuffix, parms, method, fileData) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")
  if (missing(method)) stop()
  stopifnot(class(method) == "character")

  if (missing(h2oRestApiVersion)) {
    h2oRestApiVersion = .h2o.__REST_API_VERSION
  }

  rv = .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = method, fileData)
  return(rv)
}

#' Just like doRawGET but fills in the default h2oRestApiVersion if none is provided
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return A list object as described above
h2o.doGET <- function(conn, h2oRestApiVersion, urlSuffix, parms) {
  rv = .h2o.doREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = "GET")
  return(rv)
}

#' Just like doRawPOST but fills in the default h2oRestApiVersion if none is provided
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return A list object as described above
h2o.doPOST <- function(conn, h2oRestApiVersion, urlSuffix, parms) {
  rv = .h2o.doREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = "POST")
  return(rv)
}

.h2o.doSafeREST <- function(conn, h2oRestApiVersion, urlSuffix, parms, method, fileData) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  if (! missing(h2oRestApiVersion)) { stopifnot(class(h2oRestApiVersion) == "numeric") }
  if (missing(urlSuffix)) stop()
  stopifnot(class(urlSuffix) == "character")
  if (missing(method)) stop()
  stopifnot(class(method) == "character")
  if (! missing(fileData)) { stopifnot(class(fileData) == "FileUploadInfo") }

  rv = .h2o.doREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = method, fileData = fileData)

  if (rv$curlError) {
    stop(sprintf("Unexpected CURL error: %s", rv$curlErrorMessage))
  } else if (rv$httpStatusCode != 200) {
    stop(sprintf("Unexpected HTTP Status code: %d %s (url = %s)", rv$httpStatusCode, rv$httpStatusMessage, rv$url))
  }

  return(rv$payload)
}

#' Perform a safe (i.e. error-checked) HTTP GET request to an H2O cluster.
#'
#' This function validates that no CURL error occurred and that the HTTP response code is successful.
#' If a failure occurred, then stop() is called with an error message.
#' Since all necessary error checking is done inside this call, the valid payload is directly returned if the function successfully finishes without calling stop().
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return The raw response payload as a character vector
h2o.doSafeGET <- function(conn, h2oRestApiVersion, urlSuffix, parms) {
  rv = .h2o.doSafeREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = "GET")
  return(rv)
}

#' Perform a safe (i.e. error-checked) HTTP POST request to an H2O cluster.
#'
#' This function validates that no CURL error occurred and that the HTTP response code is successful.
#' If a failure occurred, then stop() is called with an error message.
#' Since all necessary error checking is done inside this call, the valid payload is directly returned if the function successfully finishes without calling stop().
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param fileData (Optional) Information to POST (NOTE: changes Content-type from XXX-www-url-encoded to multi-part).  Use fileUpload(normalizePath("/path/to/file")).
#' @return The raw response payload as a character vector
h2o.doSafePOST <- function(conn, h2oRestApiVersion, urlSuffix, parms, fileData) {
  rv = .h2o.doSafeREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix, parms = parms, method = "POST", fileData = fileData)
  return(rv)
}

#----------------------------------------

# Make an HTTP request to the H2O backend.
# 
# Error checking is performed.
#
# @return JSON object converted from the response payload
.h2o.__remoteSend <- function(conn, page, method = "GET", ..., .params = list()) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")
  stopifnot(class(method) == "character")
  stopifnot(class(.params) == "list")
  
  .h2o.__checkConnectionHealth(conn)

  if (length(.params) == 0) {
    .params <- list(...)
  }

  payload = .h2o.doSafeREST(conn = conn, urlSuffix = page, parms = .params, method = method)
  res = fromJSON(payload)
  return(res)
}


#-----------------------------------------------------------------------------------------------------------------------
#   H2O Server Health & Info
#-----------------------------------------------------------------------------------------------------------------------

#' Determine if an H2O cluster is up or not
#'
#' @param conn H2O connection object
#' @return TRUE if the cluster is up; FALSE otherwise
h2o.clusterIsUp <- function(conn) {
  if (missing(conn)) conn <- .retrieveH2O(parent.frame())
  if (class(conn) != "h2o.client") stop("client must be a h2o.client object")

  rv = h2o.doRawGET(conn = conn, urlSuffix = "")

  if (rv$curlError) {
    return(FALSE)
  }
  if (rv$httpStatusCode != 200) {
    return(FALSE)
  }

  return(TRUE)
}

#' Print H2O cluster info
#'
#' @param conn H2O connection object
h2o.clusterInfo <- function(conn) {
  if(missing(conn)) conn <- .retrieveH2O(parent.frame())
  stopifnot(class(conn) == "h2o.client")
  if(! h2o.clusterIsUp(conn)) {
    ip = conn@ip
    port = conn@port
    message = sprintf("Cannot connect to H2O instance at %s", h2o.getBaseURL(conn))
    stop(message)
  }

  res = NULL
  {
    res <- fromJSON(h2o.doSafeGET(conn = conn, urlSuffix = .h2o.__CLOUD))
    nodeInfo <- res$nodes
    numCPU <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))

    if (numCPU == 0) {
      # If the cloud has not been up for a few seconds yet, then query again.
      # Sometimes the heartbeat info with cores and memory has not had a chance
      # to post its information yet.
      threeSeconds = 3
      Sys.sleep(threeSeconds)
      res <- fromJSON(h2o.doSafeGET(conn = conn, urlSuffix = .h2o.__CLOUD))
    }
  }

  nodeInfo <- res$nodes
  maxMem   <- sum(sapply(nodeInfo,function(x) as.numeric(x['max_mem']))) / (1024 * 1024 * 1024)
  numCPU   <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))
  clusterHealth <- all(sapply(nodeInfo,function(x) as.logical(x['healthy']))==TRUE)

  cat("R is connected to H2O cluster:\n")
  cat("    H2O cluster uptime:       ", .readableTime(as.numeric(res$cloud_uptime_millis)), "\n")
  cat("    H2O cluster version:      ", res$version, "\n")
  cat("    H2O cluster name:         ", res$cloud_name, "\n")
  cat("    H2O cluster total nodes:  ", res$cloud_size, "\n")
  cat("    H2O cluster total memory: ", sprintf("%.2f GB", maxMem), "\n")
  cat("    H2O cluster total cores:  ", numCPU, "\n")
  cat("    H2O cluster healthy:      ", clusterHealth, "\n")
}

#' Check H2O Server Health
#'
#' Warn if there are sick nodes.
.h2o.__checkConnectionHealth <- function(conn) {
  grabCloudStatus <- function(conn) {
    rv = h2o.doGET(conn = conn, urlSuffix = .h2o.__CLOUD)

    if (rv$curlError) {
      ip = conn@ip
      port = conn@port
      warning(rv$curlErrorMessage)
      message = sprintf("H2O connection has been severed.  Cannot connect to instance at %s", h2o.getBaseURL(conn))
      stop(message)
    }

    if (rv$httpStatusCode != 200) {
      ip = conn@ip
      port = conn@port
      message = sprintf("H2O returned HTTP status %d (%s)", rv$httpStatusCode, rv$httpStatusMessage)
      warning(message)
      message = sprintf("H2O connection has been severed.  Instance unhealthy at %s", h2o.getBaseURL(conn))
      stop(message)
    }

    tmp = fromJSON(rv$payload)
    return(tmp)
  }

  checker <- function(node, conn) {
    status <- as.logical(node$healthy)
    elapsed <- as.integer(as.POSIXct(Sys.time()))*1000 - node$last_ping
    nport <- unlist(strsplit(node$h2o$node, ":"))[2]
    if(!status) .h2o.__cloudSick(node_name = NULL, conn = conn)
    if(elapsed > 60000) .h2o.__cloudSick(node_name = NULL, conn = conn)
    if(elapsed > 10000) {
        Sys.sleep(5)
        invisible(lapply(grabCloudStatus(conn)$nodes, checker, conn))
    }
    return(0)
  }

  cloudStatus <- grabCloudStatus(conn)
  if(cloudStatus$bad_nodes != 0) .h2o.__cloudSick(node_name = NULL, conn = conn)
  lapply(cloudStatus$nodes, checker, conn)
  return(0)
}

#' Helper method to issue a warning.
.h2o.__cloudSick <- function(node_name = NULL, conn) {
  url <- .h2o.calcBaseURL(conn = conn, h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = .h2o.__CLOUD)
  m1 <- "Attempting to execute action on an unhealthy cluster!\n"
  m2 <- ifelse(node_name != NULL, paste("The sick node is identified to be: ", node_name, "\n", sep = "", collapse = ""), "")
  m3 <- paste("Check cloud status here: ", url, sep = "", collapse = "")
  m <- paste(m1, m2, "\n", m3, sep = "")
  warning(m)
}


#-----------------------------------------------------------------------------------------------------------------------
#   Job Polling
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Job Polling Top-Level Function
#'
#' Poll the H2O server with the current job key `job_key` for completion.
.h2o.__waitOnJob <- function(client, job_key, pollInterval = 1, progressBar = TRUE) {
  if(!is.character(job_key) || nchar(job_key) == 0) stop("job_key must be a non-empty string")
  if(progressBar) {
    pb <- txtProgressBar(style = 3)
    tryCatch(while((prog <- .h2o.__poll(client, job_key))$prog != 1 && !prog$DONE) { Sys.sleep(pollInterval); setTxtProgressBar(pb, prog$prog) },
             error = function(e) { cat("\nPolling fails:\n"); print(e) },
             finally = setTxtProgressBar(pb, 1.0))
    if (!prog$DONE) {
      tryCatch(while(!(prog <- .h2o.__poll(client, job_key))$DONE) { Sys.sleep(pollInterval/2) },
               error = function(e) { cat("\nPolling fails:\n"); print(e) })
    }
    close(pb)
  } else
    tryCatch(while(prog<- .h2o.__poll(client, job_key) != -1 && !prog$DONE) { Sys.sleep(pollInterval) },
             finally = .h2o.__cancelJob(client, job_key))
}

#'
#' Return the progress so far and check if job is done
.h2o.__poll <- function(client, keyName) {
  if(missing(client)) stop("client is missing!")
  if(class(client) != "h2o.client") stop("client must be a h2o.client object")
  if(missing(keyName)) stop("keyName is missing!")
  if(!is.character(keyName) || nchar(keyName) == 0) stop("keyName must be a non-empty string")

  page <- 'Jobs.json/' %p0% keyName
  res <- .h2o.__remoteSend(client, page)

  res <- res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog <- list(prog = numeric(0), DONE = FALSE)
  jobRes <- NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key$name == keyName)
      jobRes <- res[[i]]
  }
  if(is.null(jobRes)) stop("Job key ", keyName, " not found in job queue")
  if(!is.null(jobRes$status) && jobRes$status == "CANCELLED") stop("Job key ", keyName, " was cancelled by user")
  else if(!is.null(jobRes$exception) && jobRes$exception == 1) stop(jobRes$status)
  prog$prog <- jobRes$progress
  if (jobRes$status == "DONE") prog$DONE <- TRUE
  prog
}

#'
#' Cancel a job.
.h2o.__cancelJob <- function(client, keyName) {
  res = .h2o.__remoteSend(client, .h2o.__JOBS)
  res = res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog = NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key$name == keyName) {
      prog = res[[i]]; break
    }
  }
  if(is.null(prog)) stop("Job key ", keyName, " not found in job queue")
#  if(!(prog$cancelled || prog$progress == -1.0 || prog$progress == -2.0 || prog$end_time == -1)) {
##    .h2o.__remoteSend(client, .h2o.__PAGE_CANCEL, key=keyName)
#    cat("Job key", keyName, "was cancelled by user\n")
#  }
  cat("Job key", keyName, "was cancelled by user\n")
}

#'
#' Check if any jobs are still running.
.h2o.__allDone <- function(client) {
  res = .h2o.__remoteSend(client, .h2o.__JOBS)
  notDone = lapply(res$jobs, function(x) { !(x$progress == -1.0 || x$cancelled) })
  !any(unlist(notDone))
}

#'
#' Poll on all jobs until they are all done.
.h2o.__pollAll <- function(client, timeout) {
  start = Sys.time()
  while(!.h2o.__allDone(client)) {
    Sys.sleep(1)
    if(as.numeric(difftime(Sys.time(), start)) > timeout)
      stop("Timeout reached! Check if any jobs have frozen in H2O.")
  }
}

#------------------------------------ Utilities ------------------------------------#
.h2o.__checkForFactors <- function(object) {
  if(class(object) != "h2o.frame") return(FALSE)
  h2o.anyFactor(object)
}

h2o.getBaseURL <- function(conn) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")

  rv = .h2o.calcBaseURL(conn = conn, urlSuffix = "")
  return(rv)
}

h2o.getVersion <- function(conn) {
  if (missing(conn)) stop()
  stopifnot(class(conn) == "h2o.client")

  res = .h2o.__remoteSend(conn, .h2o.__CLOUD)
  res$version
}

.readableTime <- function(epochTimeMillis) {
  days = epochTimeMillis/(24*60*60*1000)
  hours = (days-trunc(days))*24
  minutes = (hours-trunc(hours))*60
  seconds = (minutes-trunc(minutes))*60
  milliseconds = (seconds-trunc(seconds))*1000
  durationVector = trunc(c(days,hours,minutes,seconds,milliseconds))
  names(durationVector) = c("days","hours","minutes","seconds","milliseconds")
  if(length(durationVector[durationVector > 0]) > 1)
    showVec <- durationVector[durationVector > 0][1:2]
  else
    showVec <- durationVector[durationVector > 0]
  x1 = as.numeric(showVec)
  x2 = names(showVec)
  return(paste(x1,x2))
}

.uri.exists <- function(uri) url.exists(uri)

.h2o.__formatError <- function(error,prefix="  ") {
  result = ""
  items = strsplit(as.character(error),"\n")[[1]];
  for (i in 1:length(items))
    result = paste(result,prefix,items[i],"\n",sep="")
  result
}
