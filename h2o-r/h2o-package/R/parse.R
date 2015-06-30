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
#' @param na.strings (Optional) H2O will interpret these strings as missing.
#' @param blocking (Optional) Tell H2O parse call to block synchronously instead
#'        of polling.  This can be faster for small datasets but loses the
#'        progress bar.
#' @param parse_type (Optional) Specify which parser type H2O will use.
#'        Valid types are "ARFF", "XLS", "CSV", "SVMLight"
#' @export
h2o.parseRaw <- function(data, destination_frame = "", header=NA, sep = "", col.names=NULL,
                         col.types=NULL, na.strings=NULL, blocking=FALSE, parse_type=NULL) {
  parse.params <- h2o.parseSetup(data,destination_frame,header,sep,col.names,col.types, na.strings=na.strings, parse_type=parse_type)

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
            na_strings = .collapse.array(parse.params$na_strings),
            chunk_size = parse.params$chunk_size,
            delete_on_done = parse.params$delete_on_done,
            blocking = blocking
            )

  # Perform the parse
  res <- .h2o.__remoteSend(.h2o.__PARSE, method = "POST", .params = parse.params)
  hex <- res$job$dest$name

  # Poll on job
  .h2o.__waitOnJob(res$job$key$name)

  # Return a new H2OFrame object
  .h2o.getGCFrame(id=hex)
}

#'
#' Get a parse setup back for the staged data.
#' @inheritParams h2o.parseRaw
#' @export
h2o.parseSetup <- function(data, destination_frame = "", header=NA, sep = "", col.names=NULL, col.types=NULL, na.strings=NULL, parse_type=NULL) {

  # quick sanity checking
  if(!is(data, "H2ORawData")) stop("`data` must be an H2ORawData object")
  .key.validate(destination_frame)
  if(!(is.na(header) || is.logical(header))) stop("`header` cannot be of class ", class(header))
  if(!is.character(sep) || length(sep) != 1L || is.na(sep)) stop("`sep` must a character string")

  # begin the setup
  # setup the parse parameters here
  parseSetup.params <- list()

  # Prep srcs: must be of the form [src1,src2,src3,...]
  parseSetup.params$source_frames = .collapse.char(data@id)

  # set field sep
  # if( nchar(sep) > 0 ) parseSetup.params$separator <- .asc(sep)
  if( nzchar(sep) ) parseSetup.params$separator <- .asc(sep)

  # check the header
  if( is.na(header) && is.null(col.names) ) parseSetup.params$check_header <-  0
  else if( !isTRUE(header) )                parseSetup.params$check_header <- -1
  else                                      parseSetup.params$check_header <-  1

  # set the column names
  if( !is.null(col.names) ) {
    if( is(col.names, "H2OFrame") ) parseSetup.params$column_names <- .collapse.char(colnames(col.names))
    else                            parseSetup.params$column_names <- .collapse.char(col.names)
  }

  # check the types
  if( !is.null(col.types) )  parseSetup.params$column_types <- .collapse.char(col.types)

  # check the na.strings
  if( !is.null(na.strings) ) parseSetup.params$na_strings <- .collapse.array(na.strings)

  # check the parse_type
  # currently valid types are ARFF, XLS, CSV, SVMLight
  if( !is.null(parse_type) ) parseSetup.params$parse_type <- parse_type

  # pass through ParseSetup
  parseSetup <- .h2o.__remoteSend(.h2o.__PARSE_SETUP, method = "POST", .params = parseSetup.params)

  # make a name only if there was no destination_frame ( i.e. !nzchar("") == TRUE )
  if( !nzchar(destination_frame) ) destination_frame <- .key.make(parseSetup$destination_frame)

  # return the parse setup as a list of setup :D
  parse.params <- list(
        source_frames      = sapply(parseSetup$source_frames, function(asrc) asrc$name),
        destination_frame  = destination_frame,
        separator          = parseSetup$separator,
        parse_type         = parseSetup$parse_type,
        single_quotes      = parseSetup$single_quotes,
        check_header       = parseSetup$check_header,
        number_columns     = parseSetup$number_columns,
        column_names       = parseSetup$column_names,
        column_types       = parseSetup$column_types,
        na_strings         = parseSetup$na_strings,
        chunk_size         = parseSetup$chunk_size,
        delete_on_done     = TRUE
        )
}

#'
#' Helper Collapse Function
#'
#' Collapse a character vector into a ','-sep array of the form: [thing1,thing2,...]
.collapse <- function(v) paste0('[', paste(v, collapse=','), ']')
.collapse.char <- function(v) paste0('[', paste0('"', v, '"', collapse=','), ']')
.collapse.array <- function(v) {
  if (!is.null(v)) paste0('[', paste0(lapply(v, .collapse.char), collapse=','), ']')
  else "[]"
}

.h2o.fetchNRows <- function(id) {
  .h2o.__remoteSend(paste0(.h2o.__FRAMES, "/", id))$frames[[1]]$rows
}

#'
#' The H2OFrame Constructor
.h2o.parsedData <- function(destination_frame, nrows, ncols, col_names) {
  mutable <- new("H2OFrameMutableState", nrows = nrows, ncols = ncols, col_names = col_names, computed=T)
  .newH2OFrame(id=destination_frame, mutable=mutable)
}


# ASCII lookup on sep
.asc <- function(c) strtoi(charToRaw(c),16L)
