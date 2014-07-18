h2o.exportFile <- function(data, path, force = FALSE) {
    canHandle = FALSE
    if (class(data) == "H2OParsedData") { canHandle = TRUE }
    if (class(data) == "H2OParsedDataVA") { canHandle = TRUE }
    if (! canHandle) {
        stop("h2o.exportFile only works on H2OParsedData or H2OParsedDataVA frames")
    }
    if(!is.character(path)) stop("path must be of class character")
    if(nchar(path) == 0) stop("path must be a non-empty string")
    if(!is.logical(force)) stop("force must be of class logical")

    res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_EXPORTFILES, src_key = data@key, path = path, force = as.numeric(force))
}

h2o.exportHDFS <- function(object, path) {
  if(inherits(object, "H2OModelVA")) stop("h2o.exportHDFS does not work under ValueArray")
  else if(!inherits(object, "H2OModel")) stop("object must be an H2O model")
  if(!is.character(path)) stop("path must be of class character")
  if(nchar(path) == 0) stop("path must be a non-empty string")

  res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_EXPORTHDFS, source_key = object@key, path = path)
}

h2o.downloadCSV <- function(data, filename) {
  if( missing(data)) stop('Must specify data')
  if(! class(data) %in% c('H2OParsedDataVA', 'H2OParsedData'))
    stop('data is not an H2O data object')
  if( missing(filename) ) stop('Must specify filename')

  str <- paste('http://', data@h2o@ip, ':', data@h2o@port, '/2/DownloadDataset?src_key=', data@key, sep='')
  has_wget <- '' != Sys.which('wget')
  has_curl <- '' != Sys.which('curl')
  if( !(has_wget || has_curl)) stop("I can't find wget or curl on your system")
  if( has_wget ){
    cmd <- 'wget'
    args <- paste('-O', filename, str)
  } else {
    cmd <- 'curl'
    args <- paste('-o', filename, str)
  }

  print(paste('cmd:', cmd))
  print(paste('args:', args))
  val <- system2(cmd, args, wait=T)
  if( val != 0 )
    print(paste('Bad return val', val))
}