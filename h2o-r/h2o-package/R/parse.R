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
  x <- .newFrame("Parse",id=hex,-1,-1)
  .fetch.data(x,1L) # Fill in nrow and ncol
  x
}


#'
#' Get a parse setup back for the staged data.
#' @inheritParams h2o.parseRaw
#' @export
h2o.parseSetup <- function(data, destination_frame = "", header=NA, sep = "", col.names=NULL, col.types=NULL, na.strings=NULL, parse_type=NULL) {

  # Allow single frame or list of frames; turn singleton into a list
  if( is.Frame(data) ) data <- list(data)
  for (d in data) chk.Frame(d)

  .key.validate(destination_frame)
  if(!(is.na(header) || is.logical(header))) stop("`header` cannot be of class ", class(header))
  if(!is.character(sep) || length(sep) != 1L || is.na(sep)) stop("`sep` must a character string")

  # begin the setup
  # setup the parse parameters here
  parseSetup.params <- list()

  # Prep srcs: must be of the form [src1,src2,src3,...]
  parseSetup.params$source_frames <- .collapse.char(sapply(data, function (d) attr(d, "id")))

  # check the header
  if( is.na(header) && is.null(col.names) ) parseSetup.params$check_header <-  0
  else if( !isTRUE(header) )                parseSetup.params$check_header <- -1
  else                                      parseSetup.params$check_header <-  1

  # set field sep
  if( nzchar(sep) ) parseSetup.params$separator <- .asc(sep)

  # check the na.strings
  if( !is.null(na.strings) ) parseSetup.params$na_strings <- .collapse.array(na.strings)

  parseSetup <- .h2o.__remoteSend(.h2o.__PARSE_SETUP, method = "POST", .params = parseSetup.params)

  # set the column names
  if (!is.null(col.names)) {
    parseSetup$column_names <- if(is.Frame(col.names)) colnames(col.names) else col.names
    if (!is.null(parseSetup$column_names) && (length(parseSetup$column_names) != parseSetup$number_columns)) {
                  stop("length of col.names must equal to the number of columns in dataset") } }

  # set col.types
  if( !is.null(col.types) ) {
    if (typeof(col.types) == "character") {
        parseSetup$column_types <- col.types
    } else if ((typeof(col.types) == "list")) {
        list.names <- names(col.types)
        by.col.name <- ("by.col.name" %in% list.names)
        by.col.idx <- ("by.col.idx" %in% list.names)
        if (!(("types" %in% list.names) && xor(by.col.name,by.col.idx))) stop(.col.type.usage())
        if (by.col.name && typeof(col.types$by.col.name) != "character") stop("`by.col.name` must be character vector.")
        if (by.col.idx  && typeof(col.types$by.col.idx) != "double")     stop("`by.col.idx` must be vector of doubles.")

        if (by.col.name) {
            lapply(col.types$by.col.name, function(n) {
                c <- 1
                valid_col_name <- FALSE
                if (is.null(parseSetup$column_names)) {
                    valid_col_name <- .valid.generated.col(n,parseSetup$number_columns)
                } else {
                    valid_col_name <- n %in% parseSetup$column_names }
                if (!valid_col_name) stop("by.col.name must be a subset of the actual column names")
                if (is.null(parseSetup$column_names)) {
                    parseSetup$column_types[[as.numeric(substring(n,2))]] <<- col.types$types[c]
                } else {
                    parseSetup$column_types[[which(n == parseSetup$column_names)]] <<- col.types$types[c] }
                c <- c + 1 })
        } else {
            c <- 1
            lapply(col.types$by.col.idx, function (i) {
                parseSetup$column_types[[i]] <<- col.types$types[c]
                c <- c + 1 })
        }
    } else { stop("`col.types` must be a character vector or list") }
  }

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

.valid.generated.col <- function(name,ncols) {
     if (!grepl("^C[1-9]+",name)) return(FALSE)
     if (as.numeric(substring(name,2)) > ncols) return(FALSE)
     return(TRUE)
}

.col.type.usage <- function() {
    print("col.types must be a character vector of types (i.e. col.types=c('Numeric','Numeric','Enum')), or")
    print("a named list, where the names are `by.col.names` or `by.col.idx` and `types`. For example:")
    print("col.types=list(by.col.names=c('C1','C3','C99'),types=c('Numeric','Numeric','Enum')), or equivalently")
    print("col.types=list(by.col.idx=c(1,3,99),types=c('Numeric','Numeric','Enum')). Note: `by.col.names` and")
    print("`by.col.idx` cannot be specified simultaneously.")
}
