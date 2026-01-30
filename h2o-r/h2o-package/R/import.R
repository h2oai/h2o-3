#
# Data Import
#
# Importing data is a _lazy_ parse of the data. It adds an extra step so that a user may specify a variety of options
# including a header file, separator type, and in the future column type. Additionally, the import phase provides
# feedback on whether or not a folder or group of files may be imported together.


#'
#' Import Files into H2O
#'
#' Imports files into an H2O cluster. The default behavior is to pass-through to the parse phase
#' automatically.
#'
#' \code{h2o.importFile} is a parallelized reader and pulls information from the server from a location specified
#' by the client. The path is a server-side path. This is a fast, scalable, highly optimized way to read data. H2O
#' pulls the data from a data store and initiates the data transfer as a read operation.
#'
#' Unlike the import function, which is a parallelized reader, \code{h2o.uploadFile} is a push from
#' the client to the server. The specified path must be a client-side path. This is not scalable and is only
#' intended for smaller data sizes. The client pushes the data from a local filesystem (for example,
#' on your machine where R is running) to H2O. For big-data operations, you don't want the data
#' stored on or flowing through the client.
#'
#' \code{h2o.importFolder} imports an entire directory of files. If the given path is relative, then it
#' will be relative to the start location of the H2O instance. The default
#' behavior is to pass-through to the parse phase automatically.
#'
#' \code{h2o.importHDFS} is deprecated. Instead, use \code{h2o.importFile}.
#'
#' @param path The complete URL or normalized file path of the file to be
#'        imported. Each row of data appears as one line of the file.
#' @param destination_frame (Optional) The unique hex key assigned to the imported file. If none
#'        is given, a key will automatically be generated based on the URL path.
#' @param pattern (Optional) Character string containing a regular expression to match file(s) in
#'        the folder.
#' @param parse (Optional) A logical value indicating whether the file should be
#'        parsed after import, for details see \link{h2o.parseRaw}.
#' @param header (Optional) A logical value indicating whether the first line of
#'        the file contains column headers. If left empty, the parser will try
#'        to automatically detect this.
#' @param sep (Optional) The field separator character. Values on each line of
#'        the file are separated by this character. If \code{sep = ""}, the
#'        parser will automatically detect the separator.
#' @param col.names (Optional) An H2OFrame object containing a single
#'        delimited line with the column names for the file.
#' @param col.types (Optional) A vector to specify whether columns should be
#'        forced to a certain type upon import parsing.
#' @param na.strings (Optional) H2O will interpret these strings as missing.
#' @param parse_type (Optional) Specify which parser type H2O will use.
#'        Valid types are "ARFF", "XLS", "CSV", "SVMLight"
#' @param progressBar (Optional) When FALSE, tell H2O parse call to block
#'        synchronously instead of polling.  This can be faster for small
#'        datasets but loses the progress bar.
#' @param decrypt_tool (Optional) Specify a Decryption Tool (key-reference
#'        acquired by calling \link{h2o.decryptionSetup}.
#' @param skipped_columns a list of column indices to be skipped during parsing.
#' @param force_col_types (Optional) If TRUE, will force the column types to be either the ones in Parquet 
#'        schema for Parquet files or the ones specified in column_types.  This parameter is used for 
#'        numerical columns only.  Other column settings will happen without setting this parameter.  
#'        Defaults to FALSE.
#' @param custom_non_data_line_markers (Optional) If a line in imported file starts with any character in given string it will NOT be imported. Empty string means all lines are imported, NULL means that default behaviour for given format will be used
#' @param partition_by names of the columns the persisted dataset has been partitioned by.
#' @param quotechar A hint for the parser which character to expect as quoting character. None (default) means autodetection.
#' @param escapechar (Optional) One ASCII character used to escape other characters.
#' @seealso \link{h2o.import_sql_select}, \link{h2o.import_sql_table}, \link{h2o.parseRaw}
#' @examples
#' \dontrun{
#' h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
#' prostate_path = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate = h2o.importFile(path = prostate_path)
#' class(prostate)
#' summary(prostate)
#'
#' #Import files with a certain regex pattern by utilizing h2o.importFolder()
#' #In this example we import all .csv files in the directory prostate_folder
#' prostate_path = system.file("extdata", "prostate_folder", package = "h2o")
#' prostate_pattern = h2o.importFolder(path = prostate_path, pattern = ".*.csv")
#' class(prostate_pattern)
#' summary(prostate_pattern)
#' }


#' @name h2o.importFile
#' @export
h2o.importFile <- function(path, destination_frame = "", parse = TRUE, header=NA, sep = "", col.names=NULL,
                           col.types=NULL, na.strings=NULL, decrypt_tool=NULL, skipped_columns=NULL, force_col_types=FALSE,
                           custom_non_data_line_markers=NULL, partition_by=NULL, quotechar=NULL, escapechar="") {
  h2o.importFolder(path, pattern = "", destination_frame=destination_frame, parse, header, sep, col.names, col.types,
                   na.strings=na.strings, decrypt_tool=decrypt_tool, skipped_columns=skipped_columns, force_col_types,
                   custom_non_data_line_markers=custom_non_data_line_markers, partition_by, quotechar, escapechar)
}


#' @rdname h2o.importFile
#' @export
h2o.importFolder <- function(path, pattern = "", destination_frame = "", parse = TRUE, header = NA, sep = "",
                             col.names = NULL, col.types=NULL, na.strings=NULL, decrypt_tool=NULL, skipped_columns=NULL,
                             force_col_types=FALSE, custom_non_data_line_markers=NULL, partition_by=NULL, quotechar=NULL, escapechar="\\") {
  if(!is.character(path) || any(is.na(path)) || any(!nzchar(path))) stop("`path` must be a non-empty character string")
  if(!is.character(pattern) || length(pattern) != 1L || is.na(pattern)) stop("`pattern` must be a character string")
  .key.validate(destination_frame)
  if(!is.logical(parse) || length(parse) != 1L || is.na(parse))
    stop("`parse` must be TRUE or FALSE")
  if(!is.null(quotechar) && !quotechar %in% c("\"", "'", NULL))
    stop("`quotechar` must be either NULL or single (') or double (\") quotes.")
  if (!is.null(skipped_columns) && (length(skipped_columns) > 0)) {
    for (a in c(1:length(skipped_columns))) {
      if (!is.numeric(skipped_columns[a]))
        stop("Skipped column indices must be integers from 1 to number of columns in your datafile.")
      skipped_columns[a] = skipped_columns[a]-1   # change index to be from 0 to ncol-1
    }
  }
  if(length(path) > 1L) {
    destFrames <- c()
    fails <- c()
    for(path2 in path){
      res <-.h2o.__remoteSend(.h2o.__IMPORT, path=path2,pattern=pattern)
      destFrames <- c(destFrames, res$destination_frames)
      fails <- c(fails, res$fails)
    }
    res$destination_frames <- destFrames
    res$fails <- fails
  } else {
    res <- .h2o.__remoteSend(.h2o.__IMPORT, path=path,pattern=pattern)
  }
  
  if(length(res$fails) > 0L) {
    for(i in seq_len(length(res$fails)))
      cat(res$fails[[i]], "failed to import")
  }
  # Return only the files that successfully imported
  if(length(res$files) <= 0L) stop("all files failed to import")
if(parse) {
    srcKey <- res$destination_frames
    return( h2o.parseRaw(data=.newH2OFrame(op="ImportFolder",id=srcKey,-1,-1),pattern=pattern, destination_frame=destination_frame,
            header=header, sep=sep, col.names=col.names, col.types=col.types, na.strings=na.strings, decrypt_tool=decrypt_tool,
            skipped_columns=skipped_columns, force_col_types=force_col_types, custom_non_data_line_markers=custom_non_data_line_markers, partition_by=partition_by,
            quotechar=quotechar, escapechar=escapechar) )
}
  myData <- lapply(res$destination_frames, function(x) .newH2OFrame( op="ImportFolder", id=x,-1,-1))  # do not gc, H2O handles these nfs:// vecs
  if(length(res$destination_frames) == 1L)
    return( myData[[1L]] )
  else
    return( myData )
}


#' @rdname h2o.importFile
#' @export
h2o.importHDFS <- function(path, pattern = "", destination_frame = "", parse = TRUE, header = NA, sep = "", col.names = NULL, na.strings=NULL) {
  .Deprecated("h2o.importFolder")
}


#' @rdname h2o.importFile
#' @export
h2o.uploadFile <- function(path, destination_frame = "",
                           parse = TRUE, header = NA, sep = "", col.names = NULL,
                           col.types = NULL, na.strings = NULL, progressBar = FALSE,
                           parse_type=NULL, decrypt_tool=NULL, skipped_columns=NULL, force_col_types=FALSE,
                           quotechar=NULL, escapechar="\\") {
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")
  if (length(skipped_columns) > 0) { # check to make sure only valid column indices are here
    for (a in c(1:length(skipped_columns))) {
      if (!is.numeric(skipped_columns[a]))
        stop("Skipped column indices must be integers from 1 to number of columns in your datafile.")
      skipped_columns[a] <- skipped_columns[a]-1
    }
  }
  .key.validate(destination_frame)
  if(!is.logical(parse) || length(parse) != 1L || is.na(parse))
    stop("`parse` must be TRUE or FALSE")
  if(!is.null(quotechar) && !quotechar %in% c("\"", "'", NULL))
    stop("`quotechar` must be either NULL or single (') or double (\") quotes.")
  if(!is.logical(progressBar) || length(progressBar) != 1L || is.na(progressBar))
    stop("`progressBar` must be TRUE or FALSE")

  .h2o.gc()  # Clear out H2O to make space for new file
  path <- normalizePath(path, winslash = "/")
  srcKey <- .key.make( path )
  urlSuffix <- sprintf("PostFile?destination_frame=%s",  curlEscape(srcKey))
  verbose <- getOption("h2o.verbose", FALSE)
  if (verbose) pt <- proc.time()[[3]]
  fileUploadInfo <- fileUpload(path)
  .h2o.doSafePOST(h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = urlSuffix, fileUploadInfo = fileUploadInfo)
  if (verbose) cat(sprintf("uploading file using 'fileUpload' and '.h2o.doSafePOST' took %.2fs\n", proc.time()[[3]]-pt))
  if (verbose) pt <- proc.time()[[3]]
  rawData <- .newH2OFrame(op="PostFile",id=srcKey,-1,-1)
  if (verbose) cat(sprintf("loading data using '.newH2OFrame' took %.2fs\n", proc.time()[[3]]-pt))
  destination_frame <- if( destination_frame == "" ) .key.make(strsplit(basename(path), "\\.")[[1]][1]) else destination_frame
  if (parse) {
    if (verbose) pt <- proc.time()[[3]]
    ans <- h2o.parseRaw(data=rawData, destination_frame=destination_frame, header=header, sep=sep, col.names=col.names,
                        col.types=col.types, na.strings=na.strings, blocking=!progressBar, parse_type = parse_type,
                        decrypt_tool = decrypt_tool, skipped_columns = skipped_columns, force_col_types=force_col_types, 
                        quotechar=quotechar, escapechar=escapechar)
    if (verbose) cat(sprintf("parsing data using 'h2o.parseRaw' took %.2fs\n", proc.time()[[3]]-pt))
    ans
  } else {
    rawData
  }
}

#'
#' Import SQL Table into H2O
#'
#' Imports SQL table into an H2O cluster. Assumes that the SQL table is not being updated and is stable.
#' Runs multiple SELECT SQL queries concurrently for parallel ingestion.
#' Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath:
#'    `java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp`
#' Also see h2o.import_sql_select.
#' Currently supported SQL databases are MySQL, PostgreSQL, MariaDB, Hive, Oracle and Microsoft SQL Server.
#'
#' For example, 
#'    my_sql_conn_url <- "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
#'    table <- "citibike20k"
#'    username <- "root"
#'    password <- "abc123"
#'    my_citibike_data <- h2o.import_sql_table(my_sql_conn_url, table, username, password)
#'
#' @param connection_url URL of the SQL database connection as specified by the Java Database Connectivity (JDBC) Driver.
#'        For example, "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
#' @param table Name of SQL table
#' @param username Username for SQL server
#' @param password Password for SQL server
#' @param columns (Optional) Character vector of column names to import from SQL table. Default is to import all columns. 
#' @param optimize (Optional) Optimize import of SQL table for faster imports. Default is true.
#'        Ignored - use fetch_mode instead.
#' @param fetch_mode (Optional) Set to DISTRIBUTED to enable distributed import. Set to SINGLE to force a sequential read
#'        from the database
#'        Can be used for databases that do not support OFFSET-like clauses in SQL statements.
#' @export
h2o.import_sql_table <- function(connection_url, table, username, password, columns = NULL, optimize = NULL, fetch_mode = NULL) {
  parms <- list()
  parms$connection_url <- connection_url
  parms$table <- table
  parms$username <- username
  parms$password <- password
  if (!is.null(columns)) {
    columns <- toString(columns)
    parms$columns <- columns
  }
  if (!is.null(fetch_mode)) parms$fetch_mode <- fetch_mode
  res <- .h2o.__remoteSend('ImportSQLTable', method = "POST", .params = parms, h2oRestApiVersion = 99)
  job_key <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

#'
#' Import SQL table that is result of SELECT SQL query into H2O
#'
#' Creates a temporary SQL table from the specified sql_query.
#' Runs multiple SELECT SQL queries on the temporary table concurrently for parallel ingestion, then drops the table.
#' Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath:
#'    `java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp`
#' Also see h2o.import_sql_table.
#' Currently supported SQL databases are MySQL, PostgreSQL, MariaDB, Hive, Oracle and Microsoft SQL Server.
#'
#' For example, 
#'    my_sql_conn_url <- "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
#'    select_query <- "SELECT bikeid from citibike20k"
#'    username <- "root"
#'    password <- "abc123"
#'    my_citibike_data <- h2o.import_sql_select(my_sql_conn_url, select_query, username, password)
#'
#' @param connection_url URL of the SQL database connection as specified by the Java Database Connectivity (JDBC) Driver.
#'        For example, "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
#' @param select_query SQL query starting with `SELECT` that returns rows from one or more database tables.
#' @param username Username for SQL server
#' @param password Password for SQL server
#' @param use_temp_table Whether a temporary table should be created from select_query
#' @param temp_table_name Name of temporary table to be created from select_query
#' @param optimize (Optional) Optimize import of SQL table for faster imports. Experimental. Default is true. 
#' @param fetch_mode (Optional) Set to DISTRIBUTED to enable distributed import. Set to SINGLE to force a sequential read
#'        from the database
#'        Can be used for databases that do not support OFFSET-like clauses in SQL statements.
#' @export
h2o.import_sql_select<- function(connection_url, select_query, username, password, 
                        use_temp_table = NULL, temp_table_name = NULL,
                        optimize = NULL, fetch_mode = NULL) {
  parms <- list()
  parms$connection_url <- connection_url
  parms$select_query <- select_query
  parms$username <- username
  parms$password <- password
  if (!is.null(use_temp_table)) parms$use_temp_table <- use_temp_table
  if (!is.null(temp_table_name)) parms$temp_table_name <- temp_table_name
  if (!is.null(fetch_mode)) parms$fetch_mode <- fetch_mode
  res <- .h2o.__remoteSend('ImportSQLTable', method = "POST", .params = parms, h2oRestApiVersion = 99)
  job_key <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

#'
#' Import Hive Table into H2O
#'
#' Import Hive table to H2OFrame in memory.
#' Make sure to start H2O with Hive on classpath. Uses hive-site.xml on classpath to connect to Hive.
#' When database is specified as jdbc URL uses Hive JDBC driver to obtain table metadata. then 
#' uses direct HDFS access to import data.
#' 
#' For example, 
#'     my_citibike_data = h2o.import_hive_table("default", "citibike20k", partitions = list(c("2017", "01"), c("2017", "02")))
#'     my_citibike_data = h2o.import_hive_table("jdbc:hive2://hive-server:10000/default", "citibike20k", allow_multi_format = TRUE)
#'
#' @param database Name of Hive database (default database will be used by default), can be also a JDBC URL
#' @param table name of Hive table to import
#' @param partitions a list of lists of strings - partition key column values of partitions you want to import.
#' @param allow_multi_format enable import of partitioned tables with different storage formats used. WARNING:
#'        this may fail on out-of-memory for tables with a large number of small partitions.
#' @export
h2o.import_hive_table <- function(database, table, partitions = NULL, allow_multi_format = FALSE) {
  parms <- list()
  parms$database <- database
  parms$table <- table
  if (!is.null(partitions)) {
      parts <- c()
      for (p in partitions) {
        parts <- c(parts, paste0("[", paste0(p, collapse = ","), "]"))
      }
      parms$partitions <- paste0("[", paste0(parts, collapse = ","), "]")

  }
  parms$allow_multi_format <- allow_multi_format
  res <- .h2o.__remoteSend('ImportHiveTable', method = "POST", .params = parms, h2oRestApiVersion = 3)
  job_key <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

#'
#' Load frame previously stored in H2O's native format.
#'
#' @name h2o.load_frame
#' @param frame_id the frame ID of the original frame
#' @param dir a filesystem location where to look for frame data
#' @param force \code{logical}. overwrite an already existing frame (defaults to true)
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' prostate_path = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate = h2o.importFile(path = prostate_path)
#' h2o.save_frame(prostate, "/tmp/prostate")
#' prostate.key <- h2o.getId(prostate)
#' h2o.rm(prostate)
#' prostate <- h2o.load_frame(prostate.key, "/tmp/prostate")
#' }
#' @export
h2o.load_frame <- function(frame_id, dir, force = TRUE) {
    res <- .h2o.__remoteSend(.h2o.__LOAD_FRAME, frame_id = frame_id, dir = dir, force = force, method = "POST")
    hex <- res$job$dest$name
    .h2o.__waitOnJob(res$job$key$name)
    x <- .newH2OFrame("Load", id=hex, -1, -1)
    .fetch.data(x,1L) # Fill in nrow and ncol
    x
}

#'
#' Load H2O Model from HDFS or Local Disk
#'
#' Load a saved H2O model from disk. (Note that ensemble binary models 
#' can now be loaded using this method.)
#'
#' @param path The path of the H2O Model to be imported.
#' @return Returns a \linkS4class{H2OModel} object of the class corresponding to the type of model
#'         loaded.
#' @seealso \code{\link{h2o.saveModel}, \linkS4class{H2OModel}}
#' @examples
#' \dontrun{
#' # library(h2o)
#' # h2o.init()
#' # prostate_path = system.file("extdata", "prostate.csv", package = "h2o")
#' # prostate = h2o.importFile(path = prostate_path)
#' # prostate_glm = h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"),
#' #   training_frame = prostate, family = "binomial", alpha = 0.5)
#' # glmmodel_path = h2o.saveModel(prostate_glm, dir = "/Users/UserName/Desktop")
#' # glmmodel_load = h2o.loadModel(glmmodel_path)
#' }
#' @export
h2o.loadModel <- function(path) {
  if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")

  res <- .h2o.__remoteSend(.h2o.__LOAD_MODEL, h2oRestApiVersion = 99, dir = path, method = "POST")$models[[1L]]
  res
  h2o.getModel(res$model_id$name)
}


#'
#' Upload a binary model from the provided local path to the H2O cluster.
#' (H2O model can be saved in a binary form either by saveModel() or by download_model() function.)
#' 
#'
#' @param path A path on the machine this python session is currently connected to, specifying the location of the model to upload.
#' @return Returns a new \linkS4class{H2OModel} object.
#' @seealso \code{\link{h2o.saveModel}}, \code{\link{h2o.download_model}}
#' @examples
#' \dontrun{
#' # library(h2o)
#' # h2o.init()
#' # prostate_path = system.file("extdata", "prostate.csv", package = "h2o")
#' # prostate = h2o.importFile(path = prostate_path)
#' # prostate_glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
#' #   training_frame = prostate, family = "binomial", alpha = 0.5)
#' # glmmodel_path = h2o.download_model(prostate_glm, dir = "/Users/UserName/Desktop")
#' # glmmodel_load = h2o.upload_model(glmmodel_path)
#' }
#' @export
h2o.upload_model <- function(path) {
    if(!is.character(path) || length(path) != 1L || is.na(path) || !nzchar(path))
    stop("`path` must be a non-empty character string")

    .h2o.gc()  # Clear out H2O to make space for new file
    path <- normalizePath(path, winslash = "/")
    srcKey <- .key.make( path )
    urlSuffix <- sprintf("PostFile.bin?destination_frame=%s", curlEscape(srcKey))
    fileUploadInfo <- fileUpload(path)
    .h2o.doSafePOST(h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = urlSuffix, fileUploadInfo = fileUploadInfo)
    res <- .h2o.__remoteSend(.h2o.__UPLOAD_MODEL, h2oRestApiVersion = 99, dir = srcKey, method = "POST")$models[[1L]]
    h2o.getModel(res$model_id$name)
}

#'
#' Creates a new Amazon S3 client internally with specified credentials.
#'
#' There are no validations done to the credentials. Incorrect credentials are thus revealed with first S3 import call.
#'
#' @param secretKeyId Amazon S3 Secret Key ID (provided by Amazon)
#' @param secretAccessKey Amazon S3 Secret Access Key (provided by Amazon)
#' @param sessionToken Amazon Session Token (optional, only when using AWS Temporary Credentials)
#' 
#' @export
h2o.set_s3_credentials <- function(secretKeyId, secretAccessKey, sessionToken = NULL){
  if(is.null(secretKeyId)) stop("Secret key ID must not be null.")
  if(is.null(secretAccessKey)) stop("Secret acces key must not be null.")
  if(!is.character(secretKeyId) || nchar(secretKeyId) == 0) stop("Secret key ID must be a non-empty character string.")
  if(!is.character(secretAccessKey) || nchar(secretAccessKey) == 0) stop("Secret access key must a non-empty character string.")
  parms <- list()
  parms$secret_key_id <- secretKeyId
  parms$secret_access_key <- secretAccessKey
  if(!is.null(sessionToken)){
    parms$session_token <- sessionToken
  }
  
  res <- .h2o.__remoteSend("PersistS3", method = "POST", .params = parms, h2oRestApiVersion = 3)
  print("Credentials successfully set.")
}


#' Loads previously saved grid with all it's models from the same folder
#'
#' Returns a reference to the loaded Grid.
#'
#' @param grid_path A character string containing the path to the file with the grid saved.
#' @param load_params_references A logical which if true will attemt to reload saved objects referenced by 
#'                    grid parameters (e.g. training frame, calibration frame), will fail if grid was saved 
#'                    without referenced objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#'iris <- as.h2o(iris)
#'
#'ntrees_opts = c(1, 5)
#'learn_rate_opts = c(0.1, 0.01)
#'size_of_hyper_space = length(ntrees_opts) * length(learn_rate_opts)
#'
#'hyper_parameters = list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
#'# Tempdir is chosen arbitrarily. May be any valid folder on an H2O-supported filesystem.
#'baseline_grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris,
#' hyper_params = hyper_parameters, export_checkpoints_dir = tempdir())
#'# Remove everything from the cluster or restart it
#'h2o.removeAll()
#'grid <- h2o.loadGrid(paste0(tempdir(),"/",baseline_grid@grid_id))
#' }
#' @export
h2o.loadGrid <- function(grid_path, load_params_references=FALSE){
  params <- list()
  params[["grid_path"]] <- grid_path
  params[["load_params_references"]] <- load_params_references
  
  res <- .h2o.__remoteSend(
    "Grid.bin/import",
    method = "POST",
    h2oRestApiVersion = 3, .params = params
  )
  
  h2o.getGrid(grid_id = res$name)
}
