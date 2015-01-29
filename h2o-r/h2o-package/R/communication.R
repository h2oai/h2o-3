#'
#' H2O <-> R Communication and Utility Methods
#'
#' Collected here are the various methods used by the h2o-R package to communicate with the H2O
#' backend. There are methods for checking cluster health, polling, and inspecting objects in
#' the H2O store.

#-----------------------------------------------------------------------------------------------------------------------
#   GET & POST
#-----------------------------------------------------------------------------------------------------------------------

.skip_if_not_developer <- function() {
  # TODO: Verify this function serves a useful purpose
  if (!(Sys.getenv("USER") %in% c("tomk", "amy")))
    stop("Not a developer")
  invisible(NULL)
}

.h2o.calcBaseURL <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix) {
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(urlSuffix))

  if (missing(h2oRestApiVersion))
    sprintf("http://%s:%s/%s", conn@ip, as.character(conn@port), urlSuffix)
  else
    sprintf("http://%s:%s/%s/%s", conn@ip, as.character(conn@port), h2oRestApiVersion, urlSuffix)
}

.h2o.doRawREST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, method, fileUploadInfo) {
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(urlSuffix))
  if (missing(parms))
    parms = list()
  else {
    stopifnot(is.list(parms))
    # Uncomment line below if all keys should contain a session ID suffix
    #if (!is.null(parms[["key"]]) && !grepl(sprintf("%s$", conn@mutable$session_id), parms[["key"]])) parms[["key"]] <- paste0(parms[["key"]], conn@session_id)
  }
  stopifnot(is.character(method))
  if (!missing(fileUploadInfo)) stopifnot(is(fileUploadInfo, "FileUploadInfo"))

  url = .h2o.calcBaseURL(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix)

  queryString = ""
  i = 1L
  while (i <= length(parms)) {
    name = names(parms)[i]
    value = parms[i]
    escaped_value = curlEscape(value)
    if (i > 1L) {
      queryString = sprintf("%s&", queryString)
    }
    queryString = sprintf("%s%s=%s", queryString, name, escaped_value)
    i = i + 1L
  }

  postBody = ""
  if (missing(fileUploadInfo)) {
    # This is the typical case.
    if (method == "POST") {
      postBody = queryString
    } else if (nzchar(queryString)) {
      url = sprintf("%s?%s", url, queryString)
    }
  } else {
    stopifnot(method == "POST")
    if (nzchar(queryString)) {
      url = sprintf("%s?%s", url, queryString)
    }
  }

  .__curlError = FALSE
  .__curlErrorMessage = ""
  httpStatusCode = -1L
  httpStatusMessage = ""
  payload = ""

  if (.h2o.isLogging()) {
    .h2o.logRest("------------------------------------------------------------")
    .h2o.logRest("")
    .h2o.logRest(sprintf("Time:     %s", as.character(format(Sys.time(), "%Y-%m-%d %H:%M:%OS3"))))
    .h2o.logRest("")
    .h2o.logRest(sprintf("%-9s %s", method, url))
    .h2o.logRest(sprintf("postBody: %s", postBody))
  }

  beginTimeSeconds = as.numeric(proc.time())[3L]

  if (method == "GET") {
    h = basicHeaderGatherer()
    tmp = tryCatch(getURL(url = url, headerfunction = h$update),
                   error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
    if (! .__curlError) {
      httpStatusCode = as.numeric(h$value()["status"])
      httpStatusMessage = h$value()["statusMessage"]
      payload = tmp
    }
  } else if (! missing(fileUploadInfo)) {
    stopifnot(method == "POST")
    h = basicHeaderGatherer()
    t = basicTextGatherer()
    tmp = tryCatch(postForm(uri = url, .params = list(fileUploadInfo = fileUploadInfo), .opts=curlOptions(writefunction = t$update, headerfunction=h$update, verbose = FALSE)),
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
  } else if (method == "DELETE") {
    h <- basicHeaderGatherer()
    t <- basicTextGatherer()
    tmp <- tryCatch(curlPerform(url = url, customrequest = method, writefunction = t$update, headerfunction = h$update, verbose = FALSE),
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

  endTimeSeconds = as.numeric(proc.time())[3L]
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

  list(url = url,
       postBody = postBody,
       curlError = .__curlError,
       curlErrorMessage = .__curlErrorMessage,
       httpStatusCode = httpStatusCode,
       httpStatusMessage = httpStatusMessage,
       payload = payload)
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
.h2o.doRawGET <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms) {
  .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                 parms = parms, method = "GET")
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
#' @param fileUploadInfo (Optional) Information to POST (NOTE: changes Content-type from XXX-www-url-encoded to multi-part).  Use fileUpload(normalizePath("/path/to/file")).
#' @return A list object as described above
.h2o.doRawPOST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, fileUploadInfo) {
  .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                 parms = parms, method = "POST", fileUploadInfo = fileUploadInfo)
}

.h2o.doREST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, method, fileUploadInfo) {
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(urlSuffix))
  stopifnot(is.character(method))

  if (missing(h2oRestApiVersion)) {
    h2oRestApiVersion = .h2o.__REST_API_VERSION
  }

  .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                 parms = parms, method = method, fileUploadInfo)
}

#' Just like doRawGET but fills in the default h2oRestApiVersion if none is provided
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return A list object as described above
.h2o.doGET <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms) {
  .h2o.doREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
              parms = parms, method = "GET")
}

#' Just like doRawPOST but fills in the default h2oRestApiVersion if none is provided
#'
#' @param conn An H2OConnection object
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @return A list object as described above
.h2o.doPOST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms) {
  .h2o.doREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
              parms = parms, method = "POST")
}

.h2o.doSafeREST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, method, fileUploadInfo) {
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(urlSuffix))
  stopifnot(is.character(method))
  if (!missing(fileUploadInfo)) stopifnot(is(fileUploadInfo, "FileUploadInfo"))

  rv = .h2o.doREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                   parms = parms, method = method, fileUploadInfo = fileUploadInfo)

  if (rv$curlError) {
    stop(sprintf("Unexpected CURL error: %s", rv$curlErrorMessage))
  } else if (rv$httpStatusCode != 200) {
    stop(sprintf("Unexpected HTTP Status code: %d %s (url = %s)", rv$httpStatusCode, rv$httpStatusMessage, rv$url))
  }

  rv$payload
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
.h2o.doSafeGET <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms) {
  .h2o.doSafeREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                  parms = parms, method = "GET")
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
#' @param fileUploadInfo (Optional) Information to POST (NOTE: changes Content-type from XXX-www-url-encoded to multi-part).  Use fileUpload(normalizePath("/path/to/file")).
#' @return The raw response payload as a character vector
.h2o.doSafePOST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, fileUploadInfo) {
  .h2o.doSafeREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                  parms = parms, method = "POST", fileUploadInfo = fileUploadInfo)
}

#----------------------------------------

.h2o.fromJSON <- function(txt, ...) {
  processMatrices <- function(x) {
    if (is.list(x)) {
      if (is.null(names(x)) &&
          ((nrow <- length(x)) > 1L) &&
          all(unlist(lapply(x, function(y) !is.null(y) && is.atomic(y)))) &&
          (length(ncol <- unique(unlist(lapply(x, length)))) == 1L))
        x <- matrix(unlist(x), nrow = nrow, ncol = ncol, byrow = TRUE)
      else
        x <- lapply(x, processMatrices)
    }
    x
  }
  processTables <- function(x) {
    if (is.list(x)) {
      if (is.list(x$"__meta") && identical(x$"__meta"$schema_type, "TwoDimTable")) {
        if (is.matrix(x$data))
          tbl <- t(x$data)
        else
          tbl <- do.call(cbind, lapply(x$data, sapply, function(cell) if (is.null(cell)) "" else cell))

        cnms <- sapply(x$columns, `[[`, "name")
        fmts <- sapply(x$columns, `[[`, "format")
        if (nzchar(cnms[1L]))
          colnames(tbl) <- make.unique(cnms)
        else {
          x$columns <- x$columns[-1L]
          rnms <- tbl[, 1L, drop = TRUE]
          cnms <- cnms[-1L]
          fmts <- fmts[-1L]
          tbl <- tbl[, -1L, drop = FALSE]
          if (all(nzchar(rnms)))
            dimnames(tbl) <- list(make.unique(rnms), make.unique(cnms))
          else
            colnames(tbl) <- make.unique(cnms)
        }
        tbl <- data.frame(tbl, check.names = FALSE, stringsAsFactors = FALSE)

        for (j in seq_along(tbl)) {
          switch(x$columns[[j]]$type,
                 integer = {
                   tbl[[j]] <- as.integer(tbl[[j]])
                 },
                 long   =,
                 float  =,
                 double = {
                   tbl[[j]] <- as.double(tbl[[j]])
                 },
                 string = {},
                 {})
        }
        attr(tbl, "header")  <- x$name
        attr(tbl, "formats") <- fmts
        oldClass(tbl) <- c("H2OTable", "data.frame")
        x <- tbl
      }
      else
        x <- lapply(x, processTables)
    }
    x
  }
  res <- processMatrices(fromJSON(txt, ...))
  processTables(res)
}

#' Print method for H2OTable objects
#'
#' @param x An H2OTable object
#' @param ... Further arguments passed to or from other methods.
#' @return The original x object
print.H2OTable <- function(x, ...) {
  # format columns
  formats <- attr(x, "formats")
  xx <- x
  for (j in seq_along(x))
    xx[[j]] <- ifelse(is.na(x[[j]]), "", sprintf(formats[j], x[[j]]))

  # drop empty columns
  nz <- unlist(lapply(xx, function(y) any(nzchar(y))), use.names = FALSE)
  xx <- xx[nz]
  # drop empty rows
  nz <- apply(xx, 1L, function(y) any(nzchar(y)))
  xx <- xx[nz, , drop = FALSE]

  # use data.frame print method
  xx <- data.frame(xx, check.names = FALSE, stringsAsFactors = FALSE)
  if (!is.null(attr(x, "header")))
    cat(attr(x, "header"), ":\n", sep = "")
  print(xx, ...)

  # return original object
  invisible(x)
}

# Make an HTTP request to the H2O backend.
#
# Error checking is performed.
#
# @return JSON object converted from the response payload
.h2o.__remoteSend <- function(conn = h2o.getConnection(), page, method = "GET", ..., .params = list()) {
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(method))
  stopifnot(is.list(.params))

  .h2o.__checkConnectionHealth(conn)

  if (length(.params) == 0L) {
    .params <- list(...)
  }

  .h2o.fromJSON(.h2o.doSafeREST(conn = conn, urlSuffix = page, parms = .params, method = method))
}


#-----------------------------------------------------------------------------------------------------------------------
#   H2O Server Health & Info
#-----------------------------------------------------------------------------------------------------------------------

#' Determine if an H2O cluster is up or not
#'
#' @param conn H2O connection object
#' @return TRUE if the cluster is up; FALSE otherwise
h2o.clusterIsUp <- function(conn = h2o.getConnection()) {
  if (!is(conn, "H2OConnection")) stop("`conn` must be an H2OConnection object")

  rv = .h2o.doRawGET(conn = conn, urlSuffix = "")

  !rv$curlError && (rv$httpStatusCode == 200)
}

#' Print H2O cluster info
#'
#' @param conn H2O connection object
h2o.clusterInfo <- function(conn = h2o.getConnection()) {
  stopifnot(is(conn, "H2OConnection"))
  if(! h2o.clusterIsUp(conn)) {
    ip = conn@ip
    port = conn@port
    stop(sprintf("Cannot connect to H2O instance at %s", h2o.getBaseURL(conn)))
  }

  res <- .h2o.fromJSON(.h2o.doSafeGET(conn = conn, urlSuffix = .h2o.__CLOUD))
  nodeInfo <- res$nodes
  numCPU <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))

  if (numCPU == 0L) {
    # If the cloud has not been up for a few seconds yet, then query again.
    # Sometimes the heartbeat info with cores and memory has not had a chance
    # to post its information yet.
    threeSeconds = 3L
    Sys.sleep(threeSeconds)
    res <- .h2o.fromJSON(.h2o.doSafeGET(conn = conn, urlSuffix = .h2o.__CLOUD))
  }

  nodeInfo <- res$nodes
  maxMem   <- sum(sapply(nodeInfo,function(x) as.numeric(x['max_mem']))) / (1024 * 1024 * 1024)
  numCPU   <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))
  allowedCPU = sum(sapply(nodeInfo,function(x) as.numeric(x['cpus_allowed'])))
  clusterHealth <- all(sapply(nodeInfo,function(x) as.logical(x['healthy'])))

  cat("R is connected to H2O cluster:\n")
  cat("    H2O cluster uptime:        ", .readableTime(as.numeric(res$cloud_uptime_millis)), "\n")
  cat("    H2O cluster version:       ", res$version, "\n")
  cat("    H2O cluster name:          ", res$cloud_name, "\n")
  cat("    H2O cluster total nodes:   ", res$cloud_size, "\n")
  cat("    H2O cluster total memory:  ", sprintf("%.2f GB", maxMem), "\n")
  cat("    H2O cluster total cores:   ", numCPU, "\n")
  cat("    H2O cluster allowed cores: ", allowedCPU, "\n")
  cat("    H2O cluster healthy:       ", clusterHealth, "\n")

  cpusLimited = sapply(nodeInfo, function(x) x[['num_cpus']] > 1L && x[['nthreads']] != 1L && x[['cpus_allowed']] == 1L)
  if(any(cpusLimited))
    warning("Number of CPU cores allowed is limited to 1 on some nodes.\n",
            "To remove this limit, set environment variable 'OPENBLAS_MAIN_FREE=1' before starting R.")
}

#' Check H2O Server Health
#'
#' Warn if there are sick nodes.
.h2o.__checkConnectionHealth <- function(conn = h2o.getConnection()) {
  max_retries <- 10
  retries <- 0
  grabCloudStatus <- function(conn = h2o.getConnection()) {
    rv <- .h2o.doGET(conn = conn, urlSuffix = .h2o.__CLOUD)

    if (rv$curlError) {
      ip = conn@ip
      port = conn@port
      stop(sprintf("H2O connection has been severed. Cannot connect to instance at %s\n", h2o.getBaseURL(conn)),
           rv$curlErrorMessage)
    }

    if (rv$httpStatusCode != 200L) {
      ip = conn@ip
      port = conn@port
      stop(sprintf("H2O connection has been severed. Instance unhealthy at %s\n", h2o.getBaseURL(conn)),
           sprintf("H2O returned HTTP status %d (%s)", rv$httpStatusCode, rv$httpStatusMessage))
    }

    .h2o.fromJSON(rv$payload)
  }

  checker <- function(node, conn = h2o.getConnection()) {
    status <- as.logical(node$healthy)
    elapsed <- as.integer(as.POSIXct(Sys.time()))*1000 - node$last_ping
    nport <- unlist(strsplit(node$h2o$node, ":"))[2L]
    if(!status) .h2o.__cloudSick(node_name = NULL, conn = conn)
    if(elapsed > 60*1000) .h2o.__cloudSick(node_name = NULL, conn = conn)
    if(elapsed > 10*1000 && retries < max_retries) {
        retries <<- retries + 1
        Sys.sleep(5L)
        invisible(lapply(grabCloudStatus(conn)$nodes, checker, conn))
    }
    0L
  }

  cloudStatus <- grabCloudStatus(conn)
  if(cloudStatus$bad_nodes != 0L) .h2o.__cloudSick(node_name = NULL, conn = conn)
  lapply(cloudStatus$nodes, checker, conn)
  0L
}

#' Helper method to issue a warning.
.h2o.__cloudSick <- function(node_name = NULL, conn = h2o.getConnection()) {
  url <- .h2o.calcBaseURL(conn = conn, h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = .h2o.__CLOUD)
  m1 <- "Attempting to execute action on an unhealthy cluster!\n"
  m2 <- ifelse(node_name != NULL, paste0("The sick node is identified to be: ", node_name, "\n", collapse = ""), "")
  m3 <- paste0("Check cloud status here: ", url, collapse = "")
  m <- paste0(m1, m2, "\n", m3)
  warning(m)
}


#-----------------------------------------------------------------------------------------------------------------------
#   Job Polling
#-----------------------------------------------------------------------------------------------------------------------

.h2o.__waitOnJob <- function(conn = h2o.getConnection(), job_key, pollInterval = 1, progressBar = TRUE) {
  if (progressBar) {
    pb <- txtProgressBar(style = 3L)
  }

  keepRunning <- TRUE
  while (keepRunning) {
    myJobUrlSuffix <- paste0(.h2o.__JOBS, "/", job_key)
    rawResponse <- .h2o.doSafeGET(urlSuffix = myJobUrlSuffix)
    jsonObject <- .h2o.fromJSON(rawResponse)
    jobs <- jsonObject$jobs
    if (length(jobs) > 1) {
      stop("Job list has more than 1 entry")
    } else if (length(jobs) == 0) {
      stop("Job list is empty")
    }

    job = jobs[[1]]

    key = job$key
    name = key$name
    if (name != job_key) {
      message <- sprintf("Job %s not found in job list", job_key)
      stop(message)
    }

    if (progressBar) {
      progress = job$progress
      if (is.numeric(progress)) {
        setTxtProgressBar(pb, progress)
      }
    }

    status = job$status
    stopifnot(is.character(status))

    if (status == "CANCELLED") {
      stop("Job key ", job_key, " cancelled by user")
    }

    if (status == "FAILED") {
      stop("Job key ", job_key, " failed")
    }

    if ((status == "CREATED") || (status == "RUNNING")) {
      # Do nothing, keep running...
    } else {
      stopifnot(status == "DONE")
      keepRunning <- FALSE
    }

    if (keepRunning) {
      Sys.sleep(pollInterval)
    } else {
      if (progressBar) {
        close(pb)
      }
    }
  }
}

#------------------------------------ Utilities ------------------------------------#
h2o.getBaseURL <- function(conn = h2o.getConnection()) {
  stopifnot(is(conn, "H2OConnection"))

  .h2o.calcBaseURL(conn = conn, urlSuffix = "")
}

h2o.getVersion <- function(conn = h2o.getConnection()) {
  stopifnot(is(conn, "H2OConnection"))

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
  showVec <- head(durationVector[durationVector > 0], 2L)
  x1 = as.numeric(showVec)
  x2 = names(showVec)
  paste(x1,x2)
}

.seq_to_string <- function(vec = as.numeric(NA)) {
  vec <- sort(vec)
  if(length(vec) > 2L) {
    vec_diff <- diff(vec)
    if(abs(max(vec_diff) - min(vec_diff)) < .Machine$double.eps^0.5)
      return(paste(min(vec), max(vec), vec_diff[1], sep = ":"))
  }
  paste(vec, collapse = ",")
}
