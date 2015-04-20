#'
#' H2O Data Parsing
#'
#' The second phase in the data ingestion step.
#'
#' Parse the Raw Data produced by the import phase.
#'
#' @param data An \linkS4class{H2ORawData} object to be parsed.
#' @param destination_frame (Optional) The hex key assigned to the parsed file.
#' @param header (Optional) A logical value indicating whether the first row is
#'        the column header. If missing, H2O will automatically try to detect
#'        the presence of a header.
#' @param sep (Optional) The field separator character. Values on each line of
#'        the file are separated by this character. If \code{sep = ""}, the
#'        parser will automatically detect the separator.
#' @param col.names (Optional) A \linkS4class{H2OFrame} object containing a
#'        single delimited line with the column names for the file.
#' @param col.types (Optional) A vector specifying the types to attempt to force
#'        over columns.
#' @export
h2o.parseRaw <- function(data, destination_frame = "", header=NA, sep = "", col.names=NULL,
                         col.types=NULL) {
  parse.params <- h2o.parseSetup(data,destination_frame,header,sep,col.names,col.types)

  parse.params <- list(
            source_frames = .collapse.char(parse.params$source_frames),
            destination_frame  = parse.params$destination_frame,
            separator = parse.params$separator,
            parse_type = parse.params$parse_type,
            single_quotes = parse.params$single_quotes,
            check_header = parse.params$check_header,
            number_columns = parse.params$number_columns,
            column_names = .collapse.char(parse.params$column_names),
            column_types = .collapse.char(parse.params$column_types),
            na_strings = .collapse.char(parse.params$na_strings),
            chunk_size = parse.params$chunk_size,
            delete_on_done = parse.params$delete_on_done
            )

  linkToGC <- !nzchar(destination_frame)

  # Perform the parse
  res <- .h2o.__remoteSend(data@conn, .h2o.__PARSE, method = "POST", .params = parse.params)
  hex <- res$job$dest$name

  # Poll on job
  .h2o.__waitOnJob(data@conn, res$job$key$name)

  # Return a new H2OFrame object
  h2o.getFrame(data@conn,frame_id=hex, linkToGC=linkToGC)
}

#'
#' Get a parse setup back for the staged data.
#' @inheritParams h2o.parseRaw
#' @export
h2o.parseSetup <- function(data, destination_frame = "", header=NA, sep = "", col.names=NULL, col.types=NULL) {
  if(!is(data, "H2ORawData")) stop("`data` must be an H2ORawData object")
    .key.validate(destination_frame)
    if(!(is.na(header) || is.logical(header))) stop("`header` cannot be of class ", class(header))
    if(!is.character(sep) || length(sep) != 1L || is.na(sep)) stop("`sep` must a character string")
  #  if(!(missing(col.names) || is(col.names, "H2OFrame"))) stop("`col.names` cannot be of class ", class(col.names))

    parseSetup.params <- list()
    # Prep srcs: must be of the form [src1,src2,src3,...]
    parseSetup.params$source_frames = .collapse.char(data@key)
    if (nchar(sep) > 0) parseSetup.params$separator = sep
    if(is.na(header) && is.null(col.names)) {
      parseSetup.params$check_header = 0
    } else if (!isTRUE(header)) {
      parseSetup.params$check_header = -1
    } else parseSetup.params$check_header = 1
    if (!is.null(col.names)) {
      if (is(col.names, "H2OFrame")) parseSetup.params$column_names = .collapse.char(colnames(col.names))
      else parseSetup.params$column_names = .collapse.char(col.names)
    }
    if (!is.null(col.types)) parseSetup.params$column_types = .collapse.char(col.types)

    # First go through ParseSetup
    parseSetup <- .h2o.__remoteSend(data@conn, .h2o.__PARSE_SETUP, method = "POST", .params = parseSetup.params)
    ncols <- parseSetup$number_columns
    col.names <- parseSetup$column_names
    col.types <- parseSetup$column_types
    na.strings <- parseSetup$na_strings
    parsedSrcs <- sapply(parseSetup$source_frames, function(asrc) asrc$name)
    linkToGC <- !nzchar(destination_frame)
    if (linkToGC)
        destination_frame <- .key.make(data@conn, parseSetup$destination_frame)
    parse.params <- list(
          source_frames = parsedSrcs,
          destination_frame  = destination_frame,
          separator = parseSetup$separator,
          parse_type = parseSetup$parse_type,
          single_quotes = parseSetup$single_quotes,
          check_header = parseSetup$check_header,
          number_columns = ncols,
          column_names = col.names,
          column_types = col.types,
          na_strings = na.strings,
          chunk_size = parseSetup$chunk_size,
          delete_on_done = TRUE
          )
}

#'
#' Helper Collapse Function
#'
#' Collapse a character vector into a ','-sep array of the form: [thing1,thing2,...]
.collapse <- function(v) paste0('[', paste(v, collapse=','), ']')
.collapse.char <- function(v) paste0('[', paste0('"', v, '"', collapse=','), ']')

.h2o.fetchNRows <- function(conn = h2o.getConnection(), frame_id) {
  .h2o.__remoteSend(conn, paste0(.h2o.__FRAMES, "/", frame_id))$frames[[1]]$rows
}

#'
#' The H2OFrame Constructor
.h2o.parsedData <- function(conn = h2o.getConnection(), destination_frame, nrows, ncols, col_names, linkToGC = TRUE) {
  mutable <- new("H2OFrameMutableState", nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame("H2OFrame", conn=conn, frame_id=destination_frame, mutable=mutable, linkToGC=linkToGC)
}

#'
#' Create new H2OFrame object for predictions
.h2o.parsedPredData <- function(conn = h2o.getConnection(), predictions, linkToGC = TRUE) {
  key <- predictions$frame_id$name
  col_names <- sapply(predictions$columns, function(column) column$label)
  nrows <- predictions$rows
  ncols <- length(col_names)
  mutable <- new("H2OFrameMutableState", nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame("H2OFrame", conn=conn, frame_id=key, mutable=mutable, linkToGC=linkToGC)
}
