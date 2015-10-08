#'
#' H2O Data Parsing
#'
#' The second phase in the data ingestion step.
#'
#' Parse the Raw Data produced by the import phase.
#'
#' @param data An H2O Frame object to be parsed.
#' @param destination_frame (Optional) The hex key assigned to the parsed file.
#' @param header (Optional) A logical value indicating whether the first row is
#'        the column header. If missing, H2O will automatically try to detect
#'        the presence of a header.
#' @param sep (Optional) The field separator character. Values on each line of
#'        the file are separated by this character. If \code{sep = ""}, the
#'        parser will automatically detect the separator.
#' @param col.names (Optional) A Frame object containing a
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

  # Return a new Frame object
  .newFrame("Parse",id=hex)
}

#'
#' Get a parse setup back for the staged data.
#' @inheritParams h2o.parseRaw
#' @export
h2o.parseSetup <- function(data, destination_frame = "", header=NA, sep = "", col.names=NULL, col.types=NULL, na.strings=NULL, parse_type=NULL) {

  # quick sanity checking
  if (class(data) == "Frame") data <- list(data)

  for (d in data) chk.Frame(d)

  .key.validate(destination_frame)
  if(!(is.na(header) || is.logical(header))) stop("`header` cannot be of class ", class(header))
  if(!is.character(sep) || length(sep) != 1L || is.na(sep)) stop("`sep` must a character string")

  # begin the setup
  # setup the parse parameters here
  parseSetup.params <- list()

  # Prep srcs: must be of the form [src1,src2,src3,...]
  parseSetup.params$source_frames <- .collapse.char(sapply(data, function (d) attr(d, "id")))

  parseSetup <- .h2o.__remoteSend(.h2o.__PARSE_SETUP, method = "POST", .params = parseSetup.params)

  # set field sep
  if( nzchar(sep) ) parseSetup$separator <- .asc(sep)

  # check the header
  if( is.na(header) && is.null(col.names) ) parseSetup$check_header <-  0
  else if( !isTRUE(header) )                parseSetup$check_header <- -1
  else                                      parseSetup$check_header <-  1

  # set the column names
  if(!is.null(col.names)) parseSetup$column_names <- if(is.Frame(col.names)) colnames(col.names) else col.names

  # set col.types
  if( !is.null(col.types) ) {
    if (typeof(col.types) == "character") {
        parseSetup$column_types <- col.types
    } else if ((typeof(col.types) == "list")) {
        nms <- names(col.types)
        if (is.null(nms)) stop("col.types must be named list")
        if (is.null(col.names)) stop("if col.types is a named list, then col.names must be specified")
        if (length(setdiff(nms, col.names)) > 0) stop("names specified in col.types must be a subset of col.names")
        if (length(col.names) != parseSetup$number_columns) stop("length of col.names must equal to the number of columns in dataset")
        for (n in nms) {
            i <- 1
            for (cn in parseSetup$column_names) {
                if (n == cn) break
                i <- i + 1
            }
            parseSetup$column_types[[i]] <- col.types[[n]]
        }
    } else { stop("col.types must be a character vector or a named list") }
  }

  # check the na.strings
  if( !is.null(na.strings) ) parseSetup$na_strings <- .collapse.array(na.strings)

  # check the parse_type
  # currently valid types are ARFF, XLS, CSV, SVMLight
  if( !is.null(parse_type) ) parseSetup$parse_type <- parse_type

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
.collapse.char.empty.nulls <- function(v) {
  if (!is.null(v)) paste0('[', paste0('"', v, '"', collapse=','), ']')
  else "[]"
}
.collapse.array <- function(v) {
  if (!is.null(v)) paste0('[', paste0(lapply(v, .collapse.char.empty.nulls), collapse=','), ']')
  else "[]"
}

# ASCII lookup on sep
.asc <- function(c) strtoi(charToRaw(c),16L)

