#`
#` Data Export
#`
#` Export data to local disk or HDFS.
#` Save models to local disk or HDFS.

#' Export an H2O Data Frame to a File
#'
#' Exports an \linkS4class{H2OFrame} (which can be either VA or FV) to a file.
#' This file may be on the H2O instace's local filesystem, or to HDFS (preface
#' the path with hdfs://) or to S3N (preface the path with s3n://).
#'
#' In the case of existing files \code{forse = TRUE} will overwrite the file.
#' Otherwise, the operation will fail.
#'
#' @param data An \linkS4class{H2OFrame} data frame.
#' @param path The path to write the file to. Must include the directory and
#'        filename. May be prefaced with hdfs:// or s3n://. Each row of data
#'        appears as line of the file.
#' @param force logical, indicates how to deal with files that already exist.
#' @examples
#'\dontrun{
#' library(h2o)
#' localH2O <- h2o.init()
#' irisPath <- system.file("extdata", "iris.csv", package = "h2o")
#' iris.hex <- h2o.uploadFile(localH2O, path = irisPath)
#'
#' # These aren't real paths
#' # h2o.exportFile(iris.hex, path = "/path/on/h2o/server/filesystem/iris.csv")
#' # h2o.exportFile(iris.hex, path = "hdfs://path/in/hdfs/iris.csv")
#' # h2o.exportFile(iris.hex, path = "s3n://path/in/s3/iris.csv")
#' }
#' @export
h2o.exportFile <- function(data, path, force = FALSE) {
  if (!is(data, "H2OFrame"))
    stop("`data` must be an H2OFrame object")

  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")

  if(!is.logical(force) || length(force) != 1L || is.na(force))
    stop("`force` must be TRUE or FALSE")

  res <- .h2o.__remoteSend(data@conn, .h2o.__EXPORT_FILES(data,path,force), method="POST")
  .h2o.__waitOnJob(data@conn, res$job$key$name)
}

#'
#' Export a H2OFrame to HDFS
#'
#' Exports an \linkS4class{H2OFrame} to HDFS.
#'
#' @param data an \linkS4class{H2OFrame} class object.
#' @param path The path to write the data to. Must include the driectory and
#'        filename.
#' @param force logical, indicates how to deal with files that already exist.
#' @export
h2o.exportHDFS <- function(data,path,force=FALSE) { h2o.exportFile(data,path,force) }

#' Download H2O Data to Disk
#'
#' Download an H2O data set to a CSV file on the local disk
#'
#' @section Warning: Files located on the H2O server may be very large! Make
#'        sure you have enough hard drive psace to accomoadet the entire file.
#' @param data an \linkS4class{H2OFrame} object to be downloaded.
#' @param filename A string indicating the name that the CSV file should be
#'        should be saved to.
#' @examples
#' \dontrun{
#' library(h2o)
#' localH2O <- h2o.init()
#' irisPath <- system.file("extdata", "iris_wheader.csv", package = "h2o")
#' iris.hex <- h2o.uploadFile(localH2O, path = irisPath)
#'
#' myFile <- paste(getwd(), "my_iris_file.csv", sep = .Platform$file.sep)
#' h2o.downloadCSV(iris.hex, myFile)
#' file.info(myFile)
#' file.remove(myFile)
#' }
#' @export
h2o.downloadCSV <- function(data, filename) {
  if (!is(data, "H2OFrame"))
    stop("`data` must be an H2OFrame object")

  str <- paste0('http://', data@conn@ip, ':', data@conn@port, '/3/DownloadDataset?frame_id=', data@frame_id)
  has_wget <- nzchar(Sys.which('wget'))
  has_curl <- nzchar(Sys.which('curl'))
  if(!(has_wget || has_curl))
    stop("could not find wget or curl in system environment")

  if(has_wget){
    cmd <- "wget"
    args <- paste("-O", filename, str)
  } else {
    cmd <- "curl"
    args <- paste("-o", filename, str)
  }
  cat("cmd:", cmd, "\n")
  cat("args:", args, "\n")
  val <- system2(cmd, args, wait = TRUE)
  if(val != 0L)
    cat("Bad return val", val, "\n")
}

# ------------------- Save H2O Model to Disk ----------------------------------------------------
#'
#' Save an H2O Model Object to Disk
#'
#' Save an \linkS4class{H2OModel} to disk.
#'
#' In the case of existing files \code{force = TRUE} will overwrite the file.
#' Otherwise, the operation will fail.
#'
#' @param object an \linkS4class{H2OModel} object.
#' @param dir string indicating the directory the model will be written to.
#' @param name string name of the file.
#' @param filename the full path to the file.
#' @param force logical, indicates how to deal with files that already exist.
#' @seealso \code{\link{h2o.loadModel}} for loading a model to H2O from disk
#' @examples
#' \dontrun{
#' # library(h2o)
#' # localH2O <- h2o.init()
#' # prostate.hex <- h2o.importFile(localH2O, path = paste("https://raw.github.com",
#' #   "h2oai/h2o-2/master/smalldata/logreg/prostate.csv", sep = "/"),
#' #   destination_frame = "prostate.hex")
#' # prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
#' #   training_frame = prostate.hex, family = "binomial", alpha = 0.5)
#' # h2o.saveModel(object = prostate.glm, dir = "/Users/UserName/Desktop", save_cv = TRUE,
#' # force = TRUE)
#' }
#' @export
h2o.saveModel <- function(object, dir="", name="", filename="", force=FALSE) {
  if(!is(object, "H2OModel"))
    stop("`object` must be an H2OModel object")

  if(!is.character(dir) || length(dir) != 1L || is.na(dir))
    stop("`dir` must be a character string")

  if(!is.character(name) || length(name) != 1L || is.na(name))
    stop("`name` must be a character string")
  else if(!nzchar(name))
    name <- object@model_id

  if(!is.character(filename) || length(filename) != 1L || is.na(filename))
    stop("`filename` must be a character string")

  if(!is.logical(force) || length(force) != 1L || is.na(force))
    stop("`force` must be TRUE or FALSE")
  force <- as.integer(force)

  if(nzchar(filename))
    path <- filename
  else
    path <- file.path(dir, name)

  res <- .h2o.__remoteSend(object@conn, .h2o.__SAVE_MODEL(object@model_id), h2oRestApiVersion = 99, dir=path, force=force)

  # return the path
  res$dir
}

