#'
#' H2O <-> R Communication and Utility Methods
#'
#' Collected here are the various methods used by the h2o-R package to communicate with the H2O
#' backend. There are methods for checking cluster health, polling, and inspecting objects in
#' the H2O store.
#' @import methods
#' @import RCurl
#' @importFrom graphics barplot lines
#' @importFrom stats binomial Gamma gaussian poisson runif quantile screeplot na.omit
#' @importFrom tools md5sum
#' @importFrom utils download.file packageVersion read.csv
#'           setTxtProgressBar txtProgressBar URLencode write.csv head tail

#-----------------------------------------------------------------------------------------------------------------------
#   GET & POST
#-----------------------------------------------------------------------------------------------------------------------

.skip_if_not_developer <- function() {
  # TODO: Verify this function serves a useful purpose
  if (!(Sys.getenv("USER") %in% c("cliffc", "tomk", "amy")))
    return(TRUE)
  FALSE
}

.h2o.calcBaseURL <- function(conn, h2oRestApiVersion, urlSuffix) {
  if( missing(conn) ) conn <- h2o.getConnection()
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(urlSuffix))

  if (conn@https) {
    scheme = "https"
  } else {
    scheme = "http"
  }

  if (missing(h2oRestApiVersion))
    if (is.na(conn@context_path))
      sprintf("%s://%s:%s/%s", scheme, conn@ip, as.character(conn@port), urlSuffix)
    else
      sprintf("%s://%s:%s/%s/%s", scheme, conn@ip, as.character(conn@port), conn@context_path, urlSuffix)
  else
    if (is.na(conn@context_path))
      sprintf("%s://%s:%s/%s/%s", scheme, conn@ip, as.character(conn@port), h2oRestApiVersion, urlSuffix)
    else
      sprintf("%s://%s:%s/%s/%s/%s", scheme, conn@ip, as.character(conn@port), conn@context_path, h2oRestApiVersion, urlSuffix)
}

.h2o.doRawREST.with.curl <- function(conn, url, method, parms, fileUploadInfo, binary=FALSE, parms_as_payload = FALSE, ...){
  timeout_secs <- 0
  handle <- curl::new_handle(customrequest = method, followlocation = FALSE)
  if (conn@use_spnego) {
    negotiateAuth <- 4
    curl::handle_setopt(handle, httpauth = negotiateAuth, userpwd = ":")
  } else if (!is.na(conn@username)) {
    userpwd <- sprintf("%s:%s", conn@username, conn@password)
    basicAuth <- 1L
    curl::handle_setopt(handle, userpwd = userpwd, httpauth = basicAuth)
  }
  if (conn@https) {
    if (conn@insecure) {
      curl::handle_setopt(handle, ssl_verifypeer = 0L, ssl_verifyhost=0L)
    } else if (! is.na(conn@cacert)) {
      curl::handle_setopt(handle, cainfo = conn@cacert)
    }
  }
  if (!is.na(conn@proxy)) {
    curl::handle_setopt(handle, proxy = conn@proxy)
  } else if (conn@ip == "localhost" || conn@ip == "127.0.0.1") {
    curl::handle_setopt(handle, noproxy="127.0.0.1,localhost")
  }

  queryString <- ""
  i <- 1L
  while (i <= length(parms)) {
    name <- names(parms)[i]
    value <- parms[i]
    escaped_value <- curl::curl_escape(value)
    if (i > 1L) {
      queryString <- sprintf("%s&", queryString)
    }
    queryString <- sprintf("%s%s=%s", queryString, name, escaped_value)
    i <- i + 1L
  }
  postBody <- ""
  if (missing(fileUploadInfo)) {
    # This is the typical case.
    if (method == "POST") {
      postBody <- queryString
    } else if (nzchar(queryString)) {
      url <- sprintf("%s?%s", url, queryString)
    }
  } else {
    stopifnot(method == "POST")
    if (nzchar(queryString)) {
      url <- sprintf("%s?%s", url, queryString)
    }
  }
  #For parms_as_payload
  if(parms_as_payload == TRUE){
    postBody <- jsonlite::toJSON(parms, auto_unbox=TRUE, pretty=TRUE)
    postBody <- sub('\"\\{', '\\{',postBody)
    postBody <- sub('\\}\"', '\\}',postBody)
  }

  beginTimeSeconds <- as.numeric(proc.time())[3L]

  header <- .default.headers(conn)
  if(!is.na(conn@cookies)) {
    header['Cookie'] <- paste0(conn@cookies, collapse=';')
  }
  curl::handle_setopt(handle, timeout = timeout_secs, useragent = R.version.string)

  if (.h2o.isLogging()) {
    .h2o.logRest("------------------------------------------------------------")
    .h2o.logRest("")
    .h2o.logRest(sprintf("Time:     %s", as.character(format(Sys.time(), "%Y-%m-%d %H:%M:%OS3"))))
    .h2o.logRest("")
    .h2o.logRest(sprintf("%-9s %s", method, url))
    .h2o.logRest(sprintf("postBody: %s", postBody))
  }

  .__curlError <- FALSE
  .__curlErrorMessage <- ""
  httpStatusCode <- -1L
  httpStatusMessage <- ""
  payload <- ""

  if ((method != "GET") && (method != "DELETE")) {
    if (!missing(fileUploadInfo)) {
      stopifnot(method == "POST")
      header['Expect'] <- ''
      curl::handle_setform(handle, fileUploadInfo = curl::form_file(fileUploadInfo$filename))
    } else if (method == "POST") {
      if (!parms_as_payload) {
        header['Expect'] <- ''
      }else {
        header["Content-Type"] <- "application/json"
      }
      curl::handle_setopt(handle, postfields = postBody)
    } else {
      message <- sprintf("Unknown HTTP method %s", method)
      stop(message)
    }
  }
  curl::handle_setheaders(handle, .list = header)

  tmp <- tryCatch(curl::curl_fetch_memory(url = URLencode(url), handle = handle),
                  error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
  if (! .__curlError) {
    httpStatusCode <- as.numeric(tmp$status_code)
    httpStatusMessage <- sub("HTTP.*?\\s+\\d+\\s+(.*?)\\s*$",
                             "\\1",
                             strsplit(rawToChar(tmp$headers), "[\n\r]")[[1]][[1]],
                             perl = TRUE)
    if(binary){
      payload <- as(tmp$content, "raw") #Return binary payload as is for output such as MOJOs and genmodel.jar
    }else{
      payload <- rawToChar(as(tmp$content, "raw")) #convert binary payload to text for other REST calls as they expect text responses
    }
  }

  endTimeSeconds <- as.numeric(proc.time())[3L]
  deltaSeconds <- endTimeSeconds - beginTimeSeconds
  deltaMillis <- deltaSeconds * 1000.0

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

.default.headers <- function(conn) {
  c(
    'Connection' = 'close',
    'Session-Key' = conn@mutable$session_id
  )
}

.h2o.doRawREST <- function(conn, h2oRestApiVersion, urlSuffix, parms, method, fileUploadInfo, binary=FALSE, parms_as_payload = FALSE, ...) {
  timeout_secs <- 0
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(urlSuffix))
  stopifnot(is.logical(binary))
  if(binary != FALSE && method != "GET"){
    stop("binary data is only supported with HTTP GET responses")
  }
  if (missing(parms))
    parms = list()
  else {
    stopifnot(is.list(parms))
    # Uncomment line below if all keys should contain a session ID suffix
    #if (!is.null(parms[["key"]]) && !grepl(sprintf("%s$", conn@mutable$session_id), parms[["key"]])) parms[["key"]] <- paste0(parms[["key"]], conn@session_id)
  }
  stopifnot(is.character(method))
  if (!missing(fileUploadInfo)) stopifnot(is(fileUploadInfo, "FileUploadInfo"))

  if( length(list(...)) != 0 ) {
    l <- list(...)
    # ok got some extra args -- ignore things that aren't timeout...
    if( !is.null(l$timeout) )
      timeout_secs <- l$timeout
  }

  url = .h2o.calcBaseURL(conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix)

  if (use.package("curl", version = "4.3.0", use = !getOption("prefer_RCurl", FALSE)))
    return(.h2o.doRawREST.with.curl(conn = conn,
                                    url = url,
                                    method = method,
                                    parms = parms,
                                    fileUploadInfo = fileUploadInfo,
                                    binary = binary,
                                    parms_as_payload = parms_as_payload, ...))

  opts = curlOptions()
  if (conn@use_spnego) {
    negotiateAuth = 4
    opts = curlOptions(httpauth = negotiateAuth, userpwd = ":", .opts = opts)
  } else if (!is.na(conn@username)) {
    userpwd = sprintf("%s:%s", conn@username, conn@password)
    basicAuth = 1L
    opts = curlOptions(userpwd = userpwd, httpauth = basicAuth, .opts = opts)
  }
  if (conn@https) {
    if (conn@insecure) {
      opts = curlOptions(ssl.verifypeer = 0L, ssl.verifyhost=0L, .opts = opts)
    } else if (! is.na(conn@cacert)) {
      opts = curlOptions(cainfo = conn@cacert, .opts = opts)
    }
  }
  if (!is.na(conn@proxy)) {
    opts = curlOptions(proxy = conn@proxy, .opts = opts)
  } else if (conn@ip == "localhost" || conn@ip == "127.0.0.1") {
    opts = curlOptions(noproxy="127.0.0.1,localhost", .opts = opts)
  }

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
    #For parms_as_payload
    if(parms_as_payload == TRUE){
      postBody <- jsonlite::toJSON(parms,auto_unbox=TRUE,pretty=TRUE)
      postBody <- sub('\"\\{', '\\{',postBody)
      postBody <- sub('\\}\"', '\\}',postBody)
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

  tmp <- NULL
  header <- .default.headers(conn)
  if(!is.na(conn@cookies)) {
    header['Cookie'] = paste0(conn@cookies, collapse=';')
  }

  if ((method == "GET") || (method == "DELETE")) {
    h <- basicHeaderGatherer()
    #Internal C-level data structure for collecting binary data.
    buf <- binaryBuffer()
    #C routine that puts the binary data in memory as its being processed. Here we are only interested in its
    #address in memory and will pass it into curlPerform() as the writefunction
    write <- getNativeSymbolInfo("R_curl_write_binary_data")$address
    #Note: binaryBuffer() is a constructor function for creating an internal data structure that is used when reading binary
    #data from an HTTP request via RCurl. It is used with the native routine R_curl_write_binary_data
    #for collecting the response from the HTTP query into a buffer that stores the bytes. The contents
    #can then be brought back into R as a raw vector and then used in different ways, e.g. uncompressed
    #with the Rcompression package, or written to a file via writeBin. We can also convert the raw vector to of type
    #character.
    tmp <- tryCatch(curlPerform(url = URLencode(url),
                                  customrequest = method,
                                  writefunction = write,
                                  headerfunction = h$update,
                                  useragent=R.version.string,
                                  httpheader = header,
                                  verbose = FALSE,
                                  timeout = timeout_secs,
                                  file = buf@ref, #Always get binary data
                                  .opts = opts),
                      error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
    if (! .__curlError) {
        httpStatusCode = as.numeric(h$value()["status"])
        httpStatusMessage = h$value()["statusMessage"]
        if(binary){
          payload = as(buf, "raw") #Return binary payload as is for output such as MOJOs and genmodel.jar
        }else{
          payload = rawToChar(as(buf, "raw")) #convert binary payload to text for other REST calls as they expect text responses
        }
    }
  } else if (! missing(fileUploadInfo)) {
    stopifnot(method == "POST")
    h = basicHeaderGatherer()
    t = basicTextGatherer(.mapUnicode = FALSE)
    header['Expect'] = ''
    tmp = tryCatch(postForm(uri = URLencode(url),
                            .params = list(fileUploadInfo = fileUploadInfo),
                            .opts=curlOptions(writefunction = t$update,
                                              headerfunction = h$update,
                                              useragent = R.version.string,
                                              httpheader = header,
                                              verbose = FALSE,
                                              timeout = timeout_secs,
                                              .opts = opts)),
                   error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
    if (! .__curlError) {
      httpStatusCode = as.numeric(h$value()["status"])
      httpStatusMessage = h$value()["statusMessage"]
      payload = t$value()
    }
  } else if (method == "POST") {
    h = basicHeaderGatherer()
    t = basicTextGatherer(.mapUnicode = FALSE)
    if(!parms_as_payload){
      header['Expect'] = ''
    }else{
      header["Content-Type"] <- "application/json"
    }
    tmp = tryCatch(curlPerform(url = URLencode(url),
                               postfields = postBody,
                               writefunction = t$update,
                               headerfunction = h$update,
                               useragent = R.version.string,
                               httpheader = header,
                               verbose = FALSE,
                               timeout = timeout_secs,
                               .opts = opts),
                   error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
    if (! .__curlError) {
      httpStatusCode = as.numeric(h$value()["status"])
      httpStatusMessage = h$value()["statusMessage"]
      payload = t$value()
    }
  } else {
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
#' @param conn H2OConnection
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, the version prefix is skipped.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param ... (Optional) Additional parameters.
#' @return A list object as described above
.h2o.doRawGET <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, ...) {
  .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                 parms = parms, method = "GET", ...)
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
#' @param conn H2OConnection
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, the version prefix is skipped.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param fileUploadInfo (Optional) Information to POST (NOTE: changes Content-type from XXX-www-url-encoded to multi-part).  Use fileUpload(normalizePath("/path/to/file")).
#' @param ... (Optional) Additional parameters.
#' @return A list object as described above
.h2o.doRawPOST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, fileUploadInfo, ...) {
  .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                 parms = parms, method = "POST", fileUploadInfo = fileUploadInfo, ...)
}

.h2o.doREST <- function(conn = h2o.getConnection(), h2oRestApiVersion, urlSuffix, parms, method, fileUploadInfo, parms_as_payload=FALSE, ...) {
  stopifnot(is(conn, "H2OConnection"))
  stopifnot(is.character(urlSuffix))
  stopifnot(is.character(method))

  if (missing(h2oRestApiVersion)) {
    h2oRestApiVersion = .h2o.__REST_API_VERSION
  }
  .h2o.doRawREST(conn = conn, h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                 parms = parms, method = method, fileUploadInfo, parms_as_payload=parms_as_payload, ...)
}

#' Just like doRawGET but fills in the default h2oRestApiVersion if none is provided
#'
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param ... (Optional) Additional parameters.
#' @return A list object as described above
.h2o.doGET <- function(h2oRestApiVersion, urlSuffix, parms, ...) {
  .h2o.doREST(h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
              parms = parms, method = "GET", ...)
}

#' Just like doRawPOST but fills in the default h2oRestApiVersion if none is provided
#'
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param ... (Optional) Additional parameters.
#' @return A list object as described above
.h2o.doPOST <- function(h2oRestApiVersion, urlSuffix, parms, ...) {
  .h2o.doREST(h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
              parms = parms, method = "POST", ...)
}

.h2o.doSafeREST <- function(h2oRestApiVersion, urlSuffix, parms, method, fileUploadInfo, parms_as_payload=FALSE, doValidation=TRUE, ...) {
  if (doValidation) {
    stopifnot(is.character(urlSuffix))
    stopifnot(is.character(method))
    if (!missing(fileUploadInfo)) stopifnot(is(fileUploadInfo, "FileUploadInfo"))
  }
  rv = .h2o.doREST(h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                   parms = parms, method = method, fileUploadInfo = fileUploadInfo, parms_as_payload=parms_as_payload, ...)

  if (rv$curlError) {
    errorMessage <- rv$curlErrorMessage
    if (!use.package("curl", version = "4.3.0", use = !getOption("prefer_RCurl", FALSE))){
      curlVersion <- as.numeric(strsplit(RCurl::curlVersion()$version, ".", fixed = TRUE)[[1]])
      # curl 7.68.0 introduces socketpair, RCurl does not always release the sockets which leads to errors
      if (curlVersion[[1]] >= 7 && curlVersion[[2]] >= 68) {
        errorMessage <- paste(
          errorMessage,
          "\nThis can be caused by issues with some versions of curl library together with RCurl package.",
          "Installing R package `curl` version 4.3.0 and above could help.",
          "Otherwise, using curl system library versions below 7.68.0 or compiled with --disable-socketpair could help,",
          "in case you cannot use curl R package."
        )
      }
    }
    stop(sprintf("Unexpected CURL error: %s", errorMessage))
  } else if (rv$httpStatusCode != 200) {
    cat("\n")
    cat(sprintf("ERROR: Unexpected HTTP Status code: %d %s (url = %s)\n", rv$httpStatusCode, rv$httpStatusMessage, rv$url))
    cat("\n")

    #Check if payload is a raw vector(binary data) and convert to character for error printing. Otherwise return
    #normal payload
    if(is.raw(rv$payload)){
      jsonObject = jsonlite::fromJSON(rawToChar(rv$payload), simplifyDataFrame=FALSE)
    }else{
      jsonObject = jsonlite::fromJSON(rv$payload, simplifyDataFrame=FALSE)
    }

    exceptionType = jsonObject$exception_type
    if (! is.null(exceptionType)) {
      cat(sprintf("%s\n", exceptionType))
    }

    stacktrace = jsonObject$stacktrace
    if (! is.null(stacktrace)) {
      print(jsonObject$stacktrace)
      cat("\n")
    }

    msg = jsonObject$msg
    if (! is.null(msg)) {
      stop(msg)
    } else {
      stop("Unexpected HTTP Status code")
    }
  }

  rv$payload
}

#' Perform a safe (i.e. error-checked) HTTP GET request to an H2O cluster.
#'
#' This function validates that no CURL error occurred and that the HTTP response code is successful.
#' If a failure occurred, then stop() is called with an error message.
#' Since all necessary error checking is done inside this call, the valid payload is directly returned if the function successfully finishes without calling stop().
#'
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param ... (Optional) Additional parameters.
#' @return The raw response payload as a character vector
.h2o.doSafeGET <- function(h2oRestApiVersion, urlSuffix, parms, ...) {
  .h2o.doSafeREST(h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                  parms = parms, method = "GET", ...)
}

#' Perform a safe (i.e. error-checked) HTTP POST request to an H2O cluster.
#'
#' This function validates that no CURL error occurred and that the HTTP response code is successful.
#' If a failure occurred, then stop() is called with an error message.
#' Since all necessary error checking is done inside this call, the valid payload is directly returned if the function successfully finishes without calling stop().
#'
#' @param h2oRestApiVersion (Optional) A version number to prefix to the urlSuffix.  If no version is provided, a default version is chosen for you.
#' @param urlSuffix The partial URL suffix to add to the calculated base URL for the instance
#' @param parms (Optional) Parameters to include in the request
#' @param fileUploadInfo (Optional) Information to POST (NOTE: changes Content-type from XXX-www-url-encoded to multi-part).  Use fileUpload(normalizePath("/path/to/file")).
#' @param ... (Optional) Additional parameters.
#' @return The raw response payload as a character vector
.h2o.doSafePOST <- function(h2oRestApiVersion, urlSuffix, parms, fileUploadInfo, ...) {
  .h2o.doSafeREST(h2oRestApiVersion = h2oRestApiVersion, urlSuffix = urlSuffix,
                  parms = parms, method = "POST", fileUploadInfo = fileUploadInfo, ...)
}

#----------------------------------------

.h2o.fromJSON <- function(txt, ...) {
  processMatrices <- function(x) {
    if (is.list(x)) {
      if (is.null(names(x)) &&
          ((nrow <- length(x)) > 1L) &&
          all(unlist(lapply(x, function(y) !is.null(y) && is.atomic(y)))) &&
          (length(ncol <- unique(unlist(lapply(x, length)))) == 1L)) {
        x <- lapply(x, function(y) {
          if (identical(y, "NaN")) NA_real_
          else if (identical(y, "Infinity")) Inf
          else if (identical(y, "-Infinity")) -Inf
          else y
          })
        x <- matrix(unlist(x), nrow = nrow, ncol = ncol, byrow = TRUE)
      } else
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
          tbl <- do.call(cbind, lapply(x$data, function(cell) if (is.null(cell)) "" else toString(cell)))
        cnms <- sapply(x$columns, `[[`, "name")
        fmts <- sapply(x$columns, `[[`, "format")
        descr <- x$description
        if( x$name=="Confusion Matrix" ) {
          colnames(tbl) <- make.unique(cnms)
          rownames(tbl) <- make.unique(c(cnms[1:(length(cnms)-2)], "Totals"))
        } else {
          if (nzchar(cnms[1L]))
            colnames(tbl) <- make.unique(cnms)
          else {
            x$columns <- x$columns[-1L]
            rnms <- tbl[, 1L, drop = TRUE]
            cnms <- cnms[-1L]
            fmts <- fmts[-1L]
            tbl <- tbl[, -1L, drop = FALSE]
            if (length(rnms) > 0 && all(nzchar(rnms)))
              dimnames(tbl) <- list(make.unique(rnms), make.unique(cnms))
            else
              colnames(tbl) <- make.unique(cnms)
          }
        }
        tbl <- data.frame(tbl, check.names = FALSE, stringsAsFactors = FALSE)

        for (j in seq_along(tbl)) {
          switch(x$columns[[j]]$type,
                 int = {
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
        attr(tbl, "description")   <- descr
        oldClass(tbl) <- c("H2OTable", "data.frame")
        x <- tbl
      }
      else
        x <- lapply(x, processTables)
    }
    x
  }
  res <- processMatrices(txt, ...)
  processTables(res)
}


.format.helper <- function(x, format) {
    tryCatch(
      if( is.list(x) ) lapply(x, .format.helper, format)
      else             sapply(x, function(i) if( is.na(i) ) "NA" else sprintf(format, i))
    , error=function(e) {
      print("\n\n Format Error \n\n")
      print("x:"); print(x); print("format: "); print(format); print(e)
    })
}

#' Print method for H2OTable objects
#'
#' This will print a truncated view of the table if there are more than 20 rows.
#'
#' @param x An H2OTable object
#' @param header A logical value dictating whether or not the table name should be printed.
#' @param ... Further arguments passed to or from other methods.
#' @return The original x object
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' print(cars, header = TRUE)
#' }
#' @export
print.H2OTable <- function(x, header=TRUE, ...) {
  # format columns
  formats <- attr(x, "formats")
  xx <- x
  if( !is.null(formats) ) {  # might be NULL if resulted from slicing H2OTable (no need for full blown slice method on H2OTable... allow to be data frame at that point)
    for (j in seq_along(x)) {
      if( formats[j] == "%d" ) formats[j] <- "%.f"
      xx[[j]] <- .format.helper(x[[j]], formats[j])
    }
  }
  # drop empty columns
  nz <- unlist(lapply(xx, function(y) any(nzchar(y))), use.names = FALSE)
  xx <- xx[nz]
  # drop empty rows
  nz <- apply(xx, 1L, function(y) any(nzchar(y)))
  xx <- xx[nz, , drop = FALSE]

  # use data.frame print method
  xx <- data.frame(xx, check.names = FALSE, stringsAsFactors = FALSE)
  if( header && !is.null(attr(x, "header")) ) {
    cat(attr(x, "header"), ":", sep="")
    if( !is.null(attr(x,"description")) )
      cat(" ", attr(x, "description"),sep="")
    cat("\n")
  }


  # pretty print the frame if it is large (e.g. > 20 rows)
  nr <- nrow(xx)
  if( nr > 20L ) {
    print(xx[1L:5L,],...)
    cat("\n---\n")
    print(xx[(nr-5L):nr,],...)
  } else {
    print(xx, ...)
  }
  # return original object
  invisible(x)
}

# Make an HTTP request to the H2O backend.
#
# Error checking is performed.
#
# @return JSON object converted from the response payload
.h2o.__remoteSend <- function(page, method = "GET", ..., parms_as_payload = FALSE, .params = list(), raw=FALSE, h2oRestApiVersion = .h2o.__REST_API_VERSION) {
  stopifnot(is.character(method))
  stopifnot(is.list(.params))

  .h2o.__checkConnectionHealth()
  timeout <- NULL
  if (length(.params) == 0L) {
    l <- list(...)
    if( "timeout" %in% names(l) && !is.null(l$timeout)) {
      timeout <- l$timeout
      l$timeout <- NULL
      .params <- l
    } else {
      .params <- list(...)
    }
  }

  rawREST <- .h2o.doSafeREST(h2oRestApiVersion = h2oRestApiVersion, urlSuffix = page, parms = .params, method = method, 
                             parms_as_payload=parms_as_payload, timeout=timeout)
  if( raw ) rawREST
  else {
    res <- .h2o.fromJSON(jsonlite::fromJSON(rawREST,simplifyDataFrame=FALSE, bigint_as_char=TRUE))
    if(getOption("h2o.warning.on.json.string.conversion", FALSE)) {
      resToCompare <- .h2o.fromJSON(jsonlite::fromJSON(rawREST,simplifyDataFrame=FALSE))
      if(!isTRUE(all.equal(res, resToCompare))){
        warning("During parsing the JSON response from H2O API to R bindings a conversion from long to string has occurred.")
      }
    }
    res
  }
}

#' Perform a REST API request to a previously connected server.
#' 
#' This function is mostly for internal purposes, but may occasionally be useful for direct access to the backend H2O server.
#' It has same parameters as :meth:`H2OConnection.request <h2o.backend.H2OConnection.request>`.
#' @param endpoint A H2O REST API endpoint.
#' @param params A list of params passed in the url.
#' @param json A list of params passed as a json payload.
#' @return The parsed response.
#' @details
#' REST API endpoints can be obtained using:
#' ```
#' endpoints <- sapply(h2o.api("GET /3/Metadata/endpoints")$routes, function(r) paste(r$http_method, r$url_pattern))
#' ```
#' For a given route, the supported params can be otained using:
#' ```
#' parameters <- sapply(h2o.api("GET /3/Metadata/schemas/{route$input_schema}")$schemas[[1]]$fields, function(f) { l <-list(); l[f$name] <- f$help; l })
#' ```
#' @examples
#' \dontrun{
#' res <- h2o.api("GET /3/NetworkTest")
#' res$table
#' }
#' @md
#' @export
h2o.api <- function(endpoint, params=NULL, json=NULL) {
    endpoint_pat <- "^(GET|POST|PUT|DELETE|PATCH|HEAD|TRACE) /(\\d+)/(.*)$"
    match <- unlist(regmatches(endpoint, regexec(endpoint_pat, endpoint)))
    if (length(match) < 4) 
        stop("endpoint should be in the format 'GET /3/Resource/subRes'")
    if (!is.null(params) && !is.null(json)) 
        stop("data can be passed either as URL params (using `params` list argument) or as a JSON payload (using `json` list argument), not both")
    method <- match[2]
    api_version <- match[3]
    page <- match[4]
    params <- if (is.null(params)) list() else params
    params_as_payload <- FALSE
    if (!is.null(json)) {
        params <- json
        params_as_payload <- TRUE
    }
    
    .h2o.__remoteSend(page, method=method, h2oRestApiVersion=api_version,
                      .params=params, parms_as_payload=params_as_payload)
}

#-----------------------------------------------------------------------------------------------------------------------
#   H2O Server Health & Info
#-----------------------------------------------------------------------------------------------------------------------

#' Determine if an H2O cluster is up or not
#'
#' @param conn H2OConnection object
#' @return TRUE if the cluster is up; FALSE otherwise
#' @export
h2o.clusterIsUp <- function(conn = h2o.getConnection()) {
  if (!is(conn, "H2OConnection")) stop("`conn` must be an H2OConnection object")

  rv <- .h2o.doRawGET(conn = conn, urlSuffix = "")
  if (rv$curlError) return(FALSE)

  if (rv$httpStatusCode == 401)
    warning("401 Unauthorized Access.  Did you forget to provide a username and password?")

  if((rv$httpStatusCode == 200) || (rv$httpStatusCode == 301)) {
    res <- .h2o.fromJSON(
             jsonlite::fromJSON(
               .h2o.doRawGET(conn = conn, urlSuffix = .h2o.__CLOUD, h2oRestApiVersion = .h2o.__REST_API_VERSION)$payload,
               simplifyDataFrame=FALSE
             )
           )

    if(!is.na(conn@name) && res$cloud_name != conn@name) {
        warning(sprintf("Trying to connect to cloud name [%s] but found [%s]!", conn@name, res$cloud_name))
        return(FALSE)
    }

    # Make sure the cached name is always up to date and we don't use the name from the previous h2o.init() run.
    .h2o.jar.env$name = conn@name

    return(TRUE)
  }
  return(FALSE)
}

#'
#' Dump the stack into the JVM's stdout.
#'
#' A poor man's profiler, but effective.
#'
#' @export
h2o.killMinus3 <- function() {
  rv <- .h2o.doSafeGET(urlSuffix="KillMinus3")
}

.h2o.list_extensions <- function(endpoint){
  res <- .h2o.fromJSON(jsonlite::fromJSON(.h2o.doSafeGET(urlSuffix = endpoint), simplifyDataFrame=FALSE))
  lapply(res$capabilities, function(x) x$name)
}

#' List all H2O registered extensions
#' @export
h2o.list_all_extensions <- function() {
  .h2o.list_extensions(endpoint = .h2o.__ALL_CAPABILITIES)
}

#' List registered core extensions
#' @export
h2o.list_core_extensions <- function() {
  .h2o.list_extensions(endpoint = .h2o.__CORE_CAPABILITIES)
}

#' List registered API extensions
#' @export
h2o.list_api_extensions <- function() {
  .h2o.list_extensions(endpoint = .h2o.__API_CAPABILITIES)
}

#' Print H2O cluster info
#' @export
h2o.clusterInfo <- function() {
  conn <- h2o.getConnection()
  if(! h2o.clusterIsUp(conn)) {
    stop(sprintf("Cannot connect to H2O instance at %s", h2o.getBaseURL(conn)))
  }

  ip <- conn@ip
  port <- conn@port
  proxy <- conn@proxy

  res <- .h2o.fromJSON(jsonlite::fromJSON(.h2o.doSafeGET(urlSuffix = .h2o.__CLOUD), simplifyDataFrame=FALSE))
  nodeInfo <- res$nodes
  numCPU <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))

  if (numCPU == 0L) {
    # If the cloud has not been up for a few seconds yet, then query again.
    # Sometimes the heartbeat info with cores and memory has not had a chance
    # to post its information yet.
    threeSeconds = 3L
    Sys.sleep(threeSeconds)
    res <- .h2o.fromJSON(jsonlite::fromJSON(.h2o.doSafeGET(urlSuffix = .h2o.__CLOUD), simplifyDataFrame=FALSE))
  }

  extensions <- h2o.list_api_extensions()

  nodeInfo <- res$nodes
  freeMem  <- sum(sapply(nodeInfo,function(x) as.numeric(x['free_mem']))) / (1024 * 1024 * 1024)
  numCPU   <- sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))
  allowedCPU <- sum(sapply(nodeInfo,function(x) as.numeric(x['cpus_allowed'])))
  clusterHealth <- all(sapply(nodeInfo,function(x) as.logical(x['healthy'])))

  is_client <- res$is_client

  if (is.null(is_client)) {
    is_client <- FALSE
  }
  assign("IS_CLIENT", is_client, .pkg.env)
  m <- ": \n"
  if( is_client ) m <- " (in client mode): \n"

  if (is.null(res$build_too_old)) {
    res$build_too_old <- TRUE
    res$build_age <- "PREHISTORIC"
  }

  cat(paste0("R is connected to the H2O cluster", m))
  cat("    H2O cluster uptime:        ", .readableTime(as.numeric(res$cloud_uptime_millis)), "\n")
  cat("    H2O cluster timezone:      ", res$cloud_internal_timezone, "\n")
  cat("    H2O data parsing timezone: ", res$datafile_parser_timezone, "\n")
  cat("    H2O cluster version:       ", res$version, "\n")
  cat("    H2O cluster version age:   ", res$build_age, "\n")
  cat("    H2O cluster name:          ", res$cloud_name, "\n")
  cat("    H2O cluster total nodes:   ", res$cloud_size, "\n")
  cat("    H2O cluster total memory:  ", sprintf("%.2f GB", freeMem), "\n")
  cat("    H2O cluster total cores:   ", numCPU, "\n")
  cat("    H2O cluster allowed cores: ", allowedCPU, "\n")
  cat("    H2O cluster healthy:       ", clusterHealth, "\n")
  cat("    H2O Connection ip:         ", ip, "\n")
  cat("    H2O Connection port:       ", port, "\n")
  cat("    H2O Connection proxy:      ", proxy, "\n")
  cat("    H2O Internal Security:     ", res$internal_security_enabled, "\n")
  cat("    R Version:                 ", R.version.string, "\n")

  cpusLimited <- sapply(nodeInfo, function(x) x[['num_cpus']] > 1L && x[['nthreads']] != 1L && x[['cpus_allowed']] == 1L)
  if(any(cpusLimited))
    warning("Number of CPU cores allowed is limited to 1 on some nodes.\n",
            "To remove this limit, set environment variable 'OPENBLAS_MAIN_FREE=1' before starting R.")
  if (res$build_too_old) {
    warning(sprintf("\nYour H2O cluster version is (%s) old. There may be a newer version available.\nPlease download and install the latest version from: https://h2o-release.s3.amazonaws.com/h2o/latest_stable.html", res$build_age))
  }
    
  if (is.null(res$web_ip)){
     warning("SECURITY_WARNING: web_ip is not specified. H2O Rest API is listening on all available interfaces.")
  }

}

.h2o.translateJobType <- function(type) {
    if (is.null(type)) {
      return('Removed')
    }
    pat <- "^Key<(\\w+)>"
    if (grepl(pat, type)) {
        return(sub(pat, "\\1", type))
    }
    "Unknown"
}

#' Return list of jobs performed by the H2O cluster
#' @export
h2o.list_jobs <- function() {
  myJobUrlSuffix <- paste0(.h2o.__JOBS)
  rawResponse <- .h2o.doSafeGET(urlSuffix = myJobUrlSuffix)
  jsonObject <- .h2o.fromJSON(jsonlite::fromJSON(rawResponse, simplifyDataFrame=FALSE))
  df <- data.frame()
  for (job in jsonObject$jobs) {
    df <- rbind(df, data.frame(
      type=.h2o.translateJobType(job$dest$type),
      dest=job$dest$name,
      description=job$description,
      status=job$status
    ))
  }
  df
}

h2o.get_job <- function(job_key, jobPollSuccess = FALSE, jobIsRecoverable = FALSE) {
  myJobUrlSuffix <- paste0(.h2o.__JOBS, "/", job_key)

  # If request fail, repeat the request except the cases when job is no longer exists in the cluster (e.g. in case of restart)
  i <- 0
  while (i < 30) {
    rawResponse <- try(.h2o.doSafeGET(urlSuffix = myJobUrlSuffix, doValidation=!jobPollSuccess))
    if (inherits(rawResponse, "try-error") && jobPollSuccess){
      error_type <- attr(rawResponse,"condition")
      if (jobIsRecoverable) {
        print(sprintf("Job request failed %s, waiting for cluster to restart.", error_type$message))
        Sys.sleep(10)
      } else {
        # This type of error handling is not ideal and safe for changes inside of the API
        if(grepl("Job is missing", error_type$message, fixed = TRUE)) {
          print(sprintf("Job is no longer exists: %s", error_type$message))
          break
        }
        print(sprintf("Job request failed %s, will retry after 3s.", error_type$message))
        Sys.sleep(3)
      }
      i <- i + 1
    } else {
      break
    }
  }

  # If request is still errored, stop with last error
  if(inherits(rawResponse, "try-error")) {
    stop(rawResponse)
  }

  # Parse job from response
  jsonObject <- .h2o.fromJSON(jsonlite::fromJSON(rawResponse, simplifyDataFrame=FALSE))
  jobs <- jsonObject$jobs
  if (length(jobs) > 1) {
    stop("Job list has more than 1 entry")
  } else if (length(jobs) == 0) {
    stop("Job list is empty")
  }
  jobs[[1]]
}

#' Check H2O Server Health
#'
#' Warn if there are sick nodes.
.h2o.__checkConnectionHealth <- function() {
  rv <- .h2o.doGET(urlSuffix = .h2o.__CLOUD)
  conn = h2o.getConnection()
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

  cloudStatus <- .h2o.fromJSON(jsonlite::fromJSON(rv$payload, simplifyDataFrame=FALSE))
  nodes = cloudStatus$nodes
  overallHealthy = TRUE
  for (i in 1:length(nodes)) {
    node = nodes[[i]]
    healthy = node$healthy
    if (! healthy) {
      ip_port = node$ip_port
      warning(paste0("H2O cluster node ", ip_port, " is behaving slowly and should be inspected manually"), immediate. = TRUE)
      overallHealthy = FALSE
    }
  }
  if (! overallHealthy) {
    url <- .h2o.calcBaseURL( conn, h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = .h2o.__CLOUD)
    warning(paste0("Check H2O cluster status here: ", url, "\n", collapse = ""), immediate. = TRUE)
  }
}

#'
#' Check Client Mode Connection
#'
#' @export
h2o.is_client <- function() get("IS_CLIENT", .pkg.env)


#'
#' Disable Progress Bar
#'
#' @param expr When specified, disable progress bar only for the evaluation of the expr and after the evaluation return to the previous setting (default is to show the progress bar), otherwise disable it globally.
#' @returns Value of expr if specified, otherwise NULL.
#'
#' @seealso \link{h2o.show_progress}
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' h2o.no_progress()
#' 
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
#' iris <- h2o.importFile(f)
#' iris["class"] <- as.factor(iris["class"])
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' splits <- h2o.splitFrame(iris, ratios = 0.8, seed = 1234)
#' train <- splits[[1]]
#' valid <- splits[[2]]
#' 
#' iris_km <- h2o.kmeans(x = predictors, 
#'                       training_frame = train, 
#'                       validation_frame = valid, 
#'                       k = 10, estimate_k = TRUE, 
#'                       standardize = FALSE, seed = 1234)
#' }
#' @export
h2o.no_progress <- function(expr) {
  if (missing(expr)) {
    assign("PROGRESS_BAR", FALSE, .pkg.env)
    invisible(NULL)
  } else {
    show_progress <- .h2o.is_progress()
    if (length(show_progress) == 0L || show_progress) {
      on.exit(h2o.show_progress())
    } else {
      on.exit(h2o.no_progress())
    }
    h2o.no_progress()
    force(expr)
  }
}

#'
#' Enable Progress Bar
#' @param expr When specified enable progress only for the evaluation of the expr and after the evaluation return to the previous setting (default is to show the progress bar), otherwise enable it globally.
#' @returns Value of expr if specified, otherwise NULL.
#'
#' @seealso \link{h2o.no_progress}
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' h2o.no_progress()
#' 
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv"
#' iris <- h2o.importFile(f)
#' iris["class"] <- as.factor(iris["class"])
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' splits <- h2o.splitFrame(iris, ratios = 0.8, seed = 1234)
#' train <- splits[[1]]
#' valid <- splits[[2]]
#' h2o.show_progress()
#' 
#' iris_km <- h2o.kmeans(x = predictors, 
#'                       training_frame = train, 
#'                       validation_frame = valid, 
#'                       k = 10, estimate_k = TRUE, 
#'                       standardize = FALSE, seed = 1234)
#' }
#' @export
h2o.show_progress <- function(expr) {
  if (missing(expr)) {
    assign("PROGRESS_BAR", TRUE, .pkg.env)
    invisible(NULL)
  } else {
    show_progress <- .h2o.is_progress()
    if (length(show_progress) == 0L || show_progress) {
      on.exit(h2o.show_progress())
    } else {
      on.exit(h2o.no_progress())
    }
    h2o.show_progress()
    force(expr)
  }
}

#'
#' Check if Progress Bar is Enabled
#'
.h2o.is_progress <- function() {
  progress <- mget("PROGRESS_BAR", .pkg.env, ifnotfound = TRUE)
  if (is.list(progress)) progress <- unlist(progress)
  progress
}

#-----------------------------------------------------------------------------------------------------------------------
#   Job Polling
#-----------------------------------------------------------------------------------------------------------------------

.h2o.__waitOnJob <- function(job_key, pollInterval = 1, pollUpdates = NULL) {
  progressBar <- .h2o.is_progress()
  if (progressBar) pb <- txtProgressBar(style = 3L)
  keepRunning <- TRUE
  tryCatch({
    jobPollSuccess <- FALSE
    jobIsRecoverable <- FALSE
    while (keepRunning) {
      job <- h2o.get_job(job_key, jobPollSuccess, jobIsRecoverable)
      status <- job$status
      stopifnot(is.character(status))
      jobPollSuccess <- TRUE
      jobIsRecoverable <- job$auto_recoverable

      # check failed up front...
      if( status == "FAILED" ) {
        cat("\n\n")
        cat(job$exception)
        cat("\n\n")

        if (!is.null(job$stacktrace)) {cat(job$stacktrace)}
        cat("\n")

        m <- strsplit(job$stacktrace, "\n")[[1]][1]
        m <- gsub(".*msg ","",m)
        stop(m, call.=FALSE)
      }

      # check cancelled up front...
       if( status == "CANCELLED" ) {
        stop("Job key ", job_key, " cancelled by user")
      }

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

      if ((status == "CREATED") || (status == "RUNNING")) {
        # Do nothing, keep running...
      } else {
        stopifnot(status == "DONE")
        keepRunning <- FALSE
      }

      if (keepRunning) {
        Sys.sleep(pollInterval)
        if (is.function(pollUpdates)) pollUpdates(job)
      } else {
        if (progressBar) {
          close(pb)
        }
        for(w in job$warnings){ warning(w)}
      }
    }
  },
  interrupt = function(x) {
    url.suf <- paste0(.h2o.__JOBS,"/",job_key,"/cancel")
    .h2o.doSafePOST(urlSuffix=url.suf)
    message(paste0("\nJob ",job_key," was cancelled.\n"))
    return()
  })
}

#------------------------------------ Utilities ------------------------------------#
h2o.getBaseURL <- function(conn) {
  .h2o.calcBaseURL(conn, urlSuffix = "")
}

#' Get h2o version
#'
#' @rdname h2o.getVersion
#' @export
h2o.getVersion <- function() {
  res = .h2o.__remoteSend(.h2o.__CLOUD)
  res$version
}

h2o.getBuildNumber <- function() {
  res = .h2o.__remoteSend(.h2o.__CLOUD)
  res$build_number
}

h2o.getBranchName <- function() {
  res = .h2o.__remoteSend(.h2o.__CLOUD)
  res$branch_name
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
