# client -- Connection object returned from h2o.init().
# page   -- URL to access within the H2O server.
# parms  -- List of parameters to send to the server.
.h2o.__remoteSendWithParms <- function(client, page, parms) {
  cmd = ".h2o.__remoteSend(client, page"

  for (i in 1:length(parms)) {
    thisparmname = names(parms)[i]
    cmd = sprintf("%s, %s=parms$%s", cmd, thisparmname, thisparmname)
  }

  cmd = sprintf("%s)", cmd)

  rv = eval(parse(text=cmd))
  return(rv)
}

.h2o.__remoteSend <- function(client, page, ...) {
  .h2o.__checkClientHealth(client)
  ip = client@ip
  port = client@port
  myURL = paste("http://", ip, ":", port, "/", page, sep="")

  # Sends the given arguments as URL arguments to the given page on the specified server
  #
  # Re-enable POST since we found the bug in NanoHTTPD which was causing POST
  # payloads to be dropped.
  #

  hg = basicHeaderGatherer()
  tg = basicTextGatherer()
  postForm(myURL, style = "POST", .opts = curlOptions(headerfunction = hg$update, writefunc = tg[[1]]), ...)
  temp = tg$value()

  after = gsub('"Infinity"', '"Inf"', temp[1])
  after = gsub('"-Infinity"', '"-Inf"', after)
  res = fromJSON(after)

  if(!is.null(res$error)) {
    if(.pkg.env$IS_LOGGING) .h2o.__writeToFile(res, .pkg.env$h2o.__LOG_ERROR)
    stop(paste(myURL," returned the following error:\n", .h2o.__formatError(res$error)))
  }
  res
}

.h2o.__cloudSick <- function(node_name = NULL, client) {
  url <- paste("http://", client@ip, ":", client@port, "/Cloud.html", sep = "")
  m1 <- "Attempting to execute action on an unhealthy cluster!\n"
  m2 <- ifelse(node_name != NULL, paste("The sick node is identified to be: ", node_name, "\n", sep = "", collapse = ""), "")
  m3 <- paste("Check cloud status here: ", url, sep = "", collapse = "")
  m <- paste(m1, m2, "\n", m3, sep = "")
  stop(m)
}

.h2o.__checkClientHealth <- function(client) {
  grabCloudStatus <- function(client) {
    ip <- client@ip
    port <- client@port
    url <- paste("http://", ip, ":", port, "/", .h2o.__PAGE_CLOUD, sep = "")
    if(!url.exists(url)) stop(paste("H2O connection has been severed. Instance no longer up at address ", ip, ":", port, "/", sep = "", collapse = ""))
    fromJSON(getURLContent(url))
  }
  checker <- function(node, client) {
    status <- node$node_healthy
    elapsed <- node$elapsed_time
    nport <- unlist(strsplit(node$name, ":"))[2]
    if(!status) .h2o.__cloudSick(node_name = node$name, client = client)
    if(elapsed > 45000) .h2o.__cloudSick(node_name = NULL, client = client)
    if(elapsed > 10000) {
        Sys.sleep(5)
        lapply(grabCloudStatus(client)$nodes, checker, client)
    }
    return(0)
  }
  cloudStatus <- grabCloudStatus(client)
  if(!cloudStatus$cloud_healthy) .h2o.__cloudSick(node_name = NULL, client = client)
  lapply(cloudStatus$nodes, checker, client)
  return(0)
}

#------------------------------------ Job Polling ------------------------------------#
.h2o.__poll <- function(client, keyName) {
  if(missing(client)) stop("client is missing!")
  if(class(client) != "H2OClient") stop("client must be a H2OClient object")
  if(missing(keyName)) stop("keyName is missing!")
  if(!is.character(keyName) || nchar(keyName) == 0) stop("keyName must be a non-empty string")

  res = .h2o.__remoteSend(client, .h2o.__PAGE_JOBS)
  res = res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog = NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key == keyName)
      prog = res[[i]]
  }
  if(is.null(prog)) stop("Job key ", keyName, " not found in job queue")
  # if(prog$end_time == -1 || prog$progress == -2.0) stop("Job key ", keyName, " has been cancelled")
  if(!is.null(prog$result$val) && prog$result$val == "CANCELLED") stop("Job key ", keyName, " was cancelled by user")
  else if(!is.null(prog$result$exception) && prog$result$exception == 1) stop(prog$result$val)
  prog$progress
}

.h2o.__allDone <- function(client) {
  res = .h2o.__remoteSend(client, .h2o.__PAGE_JOBS)
  notDone = lapply(res$jobs, function(x) { !(x$progress == -1.0 || x$cancelled) })
  !any(unlist(notDone))
}

.h2o.__pollAll <- function(client, timeout) {
  start = Sys.time()
  while(!.h2o.__allDone(client)) {
    Sys.sleep(1)
    if(as.numeric(difftime(Sys.time(), start)) > timeout)
      stop("Timeout reached! Check if any jobs have frozen in H2O.")
  }
}

.h2o.__waitOnJob <- function(client, job_key, pollInterval = 1, progressBar = TRUE) {
  if(!is.character(job_key) || nchar(job_key) == 0) stop("job_key must be a non-empty string")
  if(progressBar) {
    pb = txtProgressBar(style = 3)
    tryCatch(while((prog = .h2o.__poll(client, job_key)) != -1) { Sys.sleep(pollInterval); setTxtProgressBar(pb, prog) },
             error = function(e) { cat("\nPolling fails:\n"); print(e) },
             finally = .h2o.__cancelJob(client, job_key))
    setTxtProgressBar(pb, 1.0); close(pb)
  } else
    tryCatch(while(.h2o.__poll(client, job_key) != -1) { Sys.sleep(pollInterval) },
             finally = .h2o.__cancelJob(client, job_key))
}

.h2o.__cancelJob <- function(client, keyName) {
  res = .h2o.__remoteSend(client, .h2o.__PAGE_JOBS)
  res = res$jobs
  if(length(res) == 0) stop("No jobs found in queue")
  prog = NULL
  for(i in 1:length(res)) {
    if(res[[i]]$key == keyName) {
      prog = res[[i]]; break
    }
  }
  if(is.null(prog)) stop("Job key ", keyName, " not found in job queue")
  if(!(prog$cancelled || prog$progress == -1.0 || prog$progress == -2.0 || prog$end_time == -1)) {
    .h2o.__remoteSend(client, .h2o.__PAGE_CANCEL, key=keyName)
    cat("Job key", keyName, "was cancelled by user\n")
  }
}

#------------------------------------ Exec2 ------------------------------------#


.h2o.__castType <- function(object) {
  if(!inherits(object, "H2OParsedData")) stop("object must be a H2OParsedData or H2OParsedDataVA object")
  .h2o.__checkClientHealth(object@h2o)
  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_INSPECT, key = object@key)
  if(is.null(res$value_size_bytes))
    return(new("H2OParsedData", h2o=object@h2o, key=object@key))
  else
    return(new("H2OParsedDataVA", h2o=object@h2o, key=object@key))
}

#------------------------------------ Utilities ------------------------------------#

.h2o.__checkForFactors <- function(object) {
  # if(class(object) != "H2OParsedData") return(FALSE)
  if(!class(object) %in% c("H2OParsedData", "H2OParsedDataVA")) return(FALSE)
  h2o.anyFactor(object)
}

.h2o.__version <- function(client) {
  res = .h2o.__remoteSend(client, .h2o.__PAGE_CLOUD)
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

h2o.clusterInfo <- function(client) {
  if(missing(client) || class(client) != "H2OClient") stop("client must be a H2OClient object")
  myURL = paste("http://", client@ip, ":", client@port, "/", .h2o.__PAGE_CLOUD, sep = "")
  if(!url.exists(myURL)) stop("Cannot connect to H2O instance at ", myURL)

  res = NULL
  {
    res = fromJSON(postForm(myURL, style = "POST"))

    nodeInfo = res$nodes
    numCPU = sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))

    if (numCPU == 0) {
      # If the cloud hasn't been up for a few seconds yet, then query again.
      # Sometimes the heartbeat info with cores and memory hasn't had a chance
      # to post it's information yet.
      threeSeconds = 3
      Sys.sleep(threeSeconds)
      res = fromJSON(postForm(myURL, style = "POST"))
    }
  }

  nodeInfo = res$nodes
  maxMem = sum(sapply(nodeInfo,function(x) as.numeric(x['max_mem_bytes']))) / (1024 * 1024 * 1024)
  numCPU = sum(sapply(nodeInfo,function(x) as.numeric(x['num_cpus'])))
  clusterHealth =  all(sapply(nodeInfo,function(x) as.logical(x['num_cpus']))==TRUE)

  cat("R is connected to H2O cluster:\n")
  cat("    H2O cluster uptime:       ", .readableTime(as.numeric(res$cloud_uptime_millis)), "\n")
  cat("    H2O cluster version:      ", res$version, "\n")
  cat("    H2O cluster name:         ", res$cloud_name, "\n")
  cat("    H2O cluster total nodes:  ", res$cloud_size, "\n")
  cat("    H2O cluster total memory: ", sprintf("%.2f GB", maxMem), "\n")
  cat("    H2O cluster total cores:  ", numCPU, "\n")
  cat("    H2O cluster healthy:      ", clusterHealth, "\n")
}

h2o.ls <- function(object, pattern = "") {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(pattern)) stop("pattern must be of class character")

  i = 0
  myList = list()
  page_keys = .MAX_INSPECT_ROW_VIEW

  # Need to pull all keys from every page in StoreView
  while(page_keys == .MAX_INSPECT_ROW_VIEW) {
    res = .h2o.__remoteSend(object, .h2o.__PAGE_VIEWALL, filter=pattern, offset=i*.MAX_INSPECT_ROW_VIEW, view=.MAX_INSPECT_ROW_VIEW)
    if(length(res$keys) == 0) return(myList)
    temp = lapply(res$keys, function(y) c(y$key, y$value_size_bytes))

    i = i + 1
    myList = c(myList, temp)
    page_keys = res$num_keys
  }
  tot_keys = page_keys + (i-1)*.MAX_INSPECT_ROW_VIEW

  temp = data.frame(matrix(unlist(myList), nrow=tot_keys, ncol=2, byrow = TRUE))
  colnames(temp) = c("Key", "Bytesize")
  temp$Key = as.character(temp$Key)
  temp$Bytesize = as.numeric(as.character(temp$Bytesize))
  return(temp)
}

h2o.rm <- function(object, keys) {
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(keys)) stop("keys must be of class character")

  for(i in 1:length(keys))
    .h2o.__remoteSend(object, .h2o.__PAGE_REMOVE, key=keys[[i]])
}

.h2o.__formatError <- function(error,prefix="  ") {
  result = ""
  items = strsplit(as.character(error),"\n")[[1]];
  for (i in 1:length(items))
    result = paste(result,prefix,items[i],"\n",sep="")
  result
}
