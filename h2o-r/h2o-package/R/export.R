#`
#` Data Export
#`
#` Export data to local disk or HDFS.
#` Save models to local disk or HDFS.

#' Export an H2O Data Frame (H2OFrame) to a File or to a collection of Files.
#'
#' Exports an H2OFrame (which can be either VA or FV) to a file.
#' This file may be on the H2O instace's local filesystem, or to HDFS (preface
#' the path with hdfs://) or to S3N (preface the path with s3n://).
#'
#' In the case of existing files \code{force = TRUE} will overwrite the file.
#' Otherwise, the operation will fail.
#'
#' @param data An H2OFrame object.
#' @param path The path to write the file to. Must include the directory and
#'        also filename if exporting to a single file. May be prefaced with
#'        hdfs:// or s3n://. Each row of data appears as line of the file.
#' @param force logical, indicates how to deal with files that already exist.
#' @param sep The field separator character. Values on each line of
#'        the file will be separated by this character (default ",").
#' @param compression How to compress the exported dataset
#         (default none; gzip, bzip2 and snappy available)
#' @param parts integer, number of part files to export to. Default is to
#'        write to a single file. Large data can be exported to multiple
#'        'part' files, where each part file contains subset of the data.
#'        User can specify the maximum number of part files or use value
#'        -1 to indicate that H2O should itself determine the optimal
#'        number of files.
#'        Parameter path will be considered to be a path to a directory
#'        if export to multiple part files is desired. Part files conform
#'        to naming scheme 'part-m-?????'.
#'        
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#'
#' # These aren't real paths
#' # h2o.exportFile(iris_hf, path = "/path/on/h2o/server/filesystem/iris.csv")
#' # h2o.exportFile(iris_hf, path = "hdfs://path/in/hdfs/iris.csv")
#' # h2o.exportFile(iris_hf, path = "s3n://path/in/s3/iris.csv")
#' }
#' @export
h2o.exportFile <- function(data, path, force = FALSE, sep = ",", compression = NULL, parts = 1) {
  if (!is.H2OFrame(data))
    stop("`data` must be an H2OFrame object")

  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")

  if(!is.logical(force) || length(force) != 1L || is.na(force))
    stop("`force` must be TRUE or FALSE")

  if(!is.numeric(parts) || length(parts) != 1L || is.na(parts) || (! all.equal(parts, as.integer(parts))))
    stop("`parts` must be -1, 1 or any other positive integer number")

    
  params <- list(path=path, num_parts=parts, force=force, separator=.asc(sep))
  if (! is.null(compression)) {
    params$compression <- compression
  }
  res <- .h2o.__remoteSend(.h2o.__EXPORT_FILES(data), method="POST", .params = params)
  .h2o.__waitOnJob(res$job$key$name)
}

#'
#' Export a Model to HDFS
#'
#' Exports an \linkS4class{H2OModel} to HDFS.
#'
#' @param object an \linkS4class{H2OModel} class object.
#' @param path The path to write the model to. Must include the driectory and
#'        filename.
#' @param force logical, indicates how to deal with files that already exist.
#' @export
h2o.exportHDFS <- function(object, path, force=FALSE) { h2o.exportFile(object,path,force) }

#' Download H2O Data to Disk
#'
#' Download an H2O data set to a CSV file on the local disk
#'
#' @section Warning: Files located on the H2O server may be very large! Make
#'        sure you have enough hard drive space to accomodate the entire file.
#' @param data an H2OFrame object to be downloaded.
#' @param filename A string indicating the name that the CSV file should be
#'        should be saved to.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#'
#' file_path <- paste(getwd(), "my_iris_file.csv", sep = .Platform$file.sep)
#' h2o.downloadCSV(iris_hf, file_path)
#' file.info(file_path)
#' file.remove(file_path)
#' }
#' @export
h2o.downloadCSV <- function(data, filename) {
  if (!is.H2OFrame(data))
    stop("`data` must be an H2OFrame object")

  conn = h2o.getConnection()
  path <- paste0('3/DownloadDataset?frame_id=', h2o.getId(data))
  str <- .h2o.calcBaseURL(conn, urlSuffix = path)
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
#' Save an \linkS4class{H2OModel} to disk. (Note that ensemble binary models 
#' can be saved.)
#'
#' In the case of existing files \code{force = TRUE} will overwrite the file.
#' Otherwise, the operation will fail.
#'
#' @param object an \linkS4class{H2OModel} object.
#' @param path string indicating the directory the model will be written to.
#' @param force logical, indicates how to deal with files that already exist.
#' @seealso \code{\link{h2o.loadModel}} for loading a model to H2O from disk
#' @examples
#' \dontrun{
#' # library(h2o)
#' # h2o.init()
#' # prostate <- h2o.importFile(path = paste("https://raw.github.com",
#' #    "h2oai/h2o-2/master/smalldata/logreg/prostate.csv", sep = "/"))
#' # prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
#' #    training_frame = prostate, family = "binomial", alpha = 0.5)
#' # h2o.saveModel(object = prostate_glm, path = "/Users/UserName/Desktop", force = TRUE)
#' }
#' @export
h2o.saveModel <- function(object, path="", force=FALSE) {
  if(!is(object, "H2OModel")) stop("`object` must be an H2OModel object")
  if(!is.character(path) || length(path) != 1L || is.na(path)) stop("`path` must be a character string")
  if(!is.logical(force) || length(force) != 1L || is.na(force)) stop("`force` must be TRUE or FALSE")
  path <- file.path(path, object@model_id)
  res <- .h2o.__remoteSend(paste0("Models.bin/",object@model_id),dir=path,force=force,h2oRestApiVersion=99)
  res$dir
}

#' Save an H2O Model Object as Mojo to Disk
#'
#' Save an MOJO (Model Object, Optimized) to disk.
#'
#' MOJO will download as a zip file. In the case of existing files \code{force = TRUE}
#' will overwrite the file. Otherwise, the operation will fail.
#'
#' @param object an \linkS4class{H2OModel} object.
#' @param path string indicating the directory the model will be written to.
#' @param force logical, indicates how to deal with files that already exist.
#' @seealso \code{\link{h2o.saveModel}} for saving a model to disk as a binary object.
#' @examples
#' \dontrun{
#' # library(h2o)
#' # h2o.init()
#' # prostate <- h2o.uploadFile(path = system.file("extdata", "prostate.csv", package="h2o"))
#' # prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
#' #                         training_frame = prostate, family = "binomial", alpha = 0.5)
#' # h2o.saveMojo(object = prostate_glm, path = "/Users/UserName/Desktop", force = TRUE)
#' }
#' @export
h2o.saveMojo <- function(object, path="", force=FALSE) {
  if(!is(object, "H2OModel")) stop("`object` must be an H2OModel object")
  if(!is.character(path) || length(path) != 1L || is.na(path)) stop("`path` must be a character string")
  if(!is.logical(force) || length(force) != 1L || is.na(force)) stop("`force` must be TRUE or FALSE")
  path <- file.path(path, "/" ,object@model_id, ".zip", fsep = "")
  res <- .h2o.__remoteSend(paste0("Models.mojo/",object@model_id),dir=path,force=force,h2oRestApiVersion=99)
  res$dir
}

#' Save an H2O Model Details
#'
#' Save Model Details of an H2O Model in JSON Format
#'
#' Model Details will download as a JSON file. In the case of existing files \code{force = TRUE}
#' will overwrite the file. Otherwise, the operation will fail.
#'
#' @param object an \linkS4class{H2OModel} object.
#' @param path string indicating the directory the model details will be written to.
#' @param force logical, indicates how to deal with files that already exist.
#' @examples
#' \dontrun{
#' # library(h2o)
#' # h2o.init()
#' # prostate <- h2o.uploadFile(path = system.file("extdata", "prostate.csv", package = "h2o"))
#' # prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
#' #                         training_frame = prostate, family = "binomial", alpha = 0.5)
#' # h2o.saveModelDetails(object = prostate_glm, path = "/Users/UserName/Desktop", force = TRUE)
#' }
#' @export
h2o.saveModelDetails <- function(object, path="", force=FALSE) {
  if(!is(object, "H2OModel")) stop("`object` must be an H2OModel object")
  if(!is.character(path) || length(path) != 1L || is.na(path)) stop("`path` must be a character string")
  if(!is.logical(force) || length(force) != 1L || is.na(force)) stop("`force` must be TRUE or FALSE")
  path <- file.path(path, "/" ,object@model_id, ".json", fsep = "")
  res <- .h2o.__remoteSend(paste0("Models/",object@model_id,"/json"),dir=path,force=force,h2oRestApiVersion=99)
  res$dir
}

#' Saves an existing Grid of models into a given folder.
#'
#' Returns a reference to the saved Grid.
#'
#' @param grid_directory A character string containing the path to the folder for the grid to be saved to.
#' @param grid_id A chracter string with identification of the grid to be saved.
#' @return Returns an object that is a subclass of \linkS4class{H2OGrid}.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#'iris.hex <- as.h2o(iris)
#'
#'ntrees_opts = c(1, 5)
#'learn_rate_opts = c(0.1, 0.01)
#'size_of_hyper_space = length(ntrees_opts) * length(learn_rate_opts)
#'
#'hyper_parameters = list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
#'# Tempdir is chosen arbitrarily. May be any valid folder on an H2O-supported filesystem.
#'baseline_grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex,
#' hyper_params = hyper_parameters)
#'
#'grid_path <- h2o.saveGrid(grid_directory = tempdir(), grid_id = baseline_grid@grid_id)
#'# Remove everything from the cluster or restart it
#'h2o.removeAll()
#'grid <- h2o.loadGrid(grid_path)
#' }
#' @export
h2o.saveGrid <- function(grid_directory, grid_id){
  params <- list()
  params[["grid_directory"]] <- grid_directory
  
  url <- paste0("Grid.bin/", grid_id,"/export")
  
  res <- .h2o.__remoteSend(
    url,
    method = "POST",
    h2oRestApiVersion = 3,.params = params
  )
  
  paste0(grid_directory,"/",grid_id)
}
