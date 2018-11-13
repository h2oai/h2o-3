#'
#' H2O Data Parsing
#'
#' The second phase in the data ingestion step.
#'
#' Parse the Raw Data produced by the import phase.
#'
#' @param data An H2OFrame object to be parsed.
#' @param pattern (Optional) Character string containing a regular expression to match file(s) in
#'        the folder.
#' @param destination_frame (Optional) The hex key assigned to the parsed file.
#' @param header (Optional) A logical value indicating whether the first row is
#'        the column header. If missing, H2O will automatically try to detect
#'        the presence of a header.
#' @param sep (Optional) The field separator character. Values on each line of
#'        the file are separated by this character. If \code{sep = ""}, the
#'        parser will automatically detect the separator.
#' @param col.names (Optional) An H2OFrame object containing a
#'        single delimited line with the column names for the file.  If skipped_columns are specified,
#'        only list column names of columns that are not skipped.
#' @param col.types (Optional) A vector specifying the types to attempt to force
#'        over columns.  If skipped_columns are specified, only list column types of columns that are not skipped.
#' @param na.strings (Optional) H2O will interpret these strings as missing.
#' @param blocking (Optional) Tell H2O parse call to block synchronously instead
#'        of polling.  This can be faster for small datasets but loses the
#'        progress bar.
#' @param parse_type (Optional) Specify which parser type H2O will use.
#'        Valid types are "ARFF", "XLS", "CSV", "SVMLight"
#' @param decrypt_tool (Optional) Specify a Decryption Tool (key-reference
#'        acquired by calling \link{h2o.decryptionSetup}.
#' @param chunk_size size of chunk of (input) data in bytes
#' @param skipped_columns a list of column indices to be excluded from parsing
#' @seealso \link{h2o.importFile}, \link{h2o.parseSetup}
#' @export
h2o.parseRaw <- function(data, pattern="", destination_frame = "", header=NA, sep = "", col.names=NULL,
                         col.types=NULL, na.strings=NULL, blocking=FALSE, parse_type = NULL, chunk_size = NULL,
                         decrypt_tool = NULL, skipped_columns = NULL) {
  # Check and parse col.types in case col.types is supplied col.name = col.type vec
  if( length(names(col.types)) > 0 & typeof(col.types) != "list" ) {
    parse.params <- h2o.parseSetup(data, pattern="", destination_frame, header, sep, col.names, col.types = NULL,
                                   na.strings = na.strings, parse_type = parse_type, chunk_size = chunk_size,
                                   decrypt_tool = decrypt_tool, skipped_columns=skipped_columns)
    idx = match(names(col.types), parse.params$column_names)
    parse.params$column_types[idx] = as.character(col.types)
  } else {
    parse.params <- h2o.parseSetup(data, pattern="", destination_frame, header, sep, col.names, col.types,
                                   na.strings = na.strings, parse_type = parse_type, chunk_size = chunk_size,
                                   decrypt_tool = decrypt_tool, skipped_columns=skipped_columns)
  }
  for(w in parse.params$warnings){
    cat('WARNING:',w,'\n')
  }
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
            blocking = blocking,
            decrypt_tool = .decrypt_tool_id(parse.params$decrypt_tool),
            skipped_columns = paste0("[", paste(parse.params$skipped_columns, collapse=','), "]")
            )

  # Perform the parse
  res <- .h2o.__remoteSend(.h2o.__PARSE, method = "POST", .params = parse.params)
  hex <- res$job$dest$name

  # Poll on job
  .h2o.__waitOnJob(res$job$key$name)

  # Return a new H2OFrame object
  x <- .newH2OFrame("Parse",id=hex,-1,-1)
  .fetch.data(x,1L) # Fill in nrow and ncol
  x
}


.h2o.get.source.keys <- function(data, destination_frame){
 # Allow single frame or list of frames; turn singleton into a list
  if( is.H2OFrame(data) ) data <- list(data)
  for (d in data) chk.H2OFrame(d)
  .key.validate(destination_frame)
  # Prep srcs: must be of the form [src1,src2,src3,...]
  .collapse.char(sapply(data, function (d) attr(d, "id")))
}

.h2o.readSVMLight <- function(path, pattern = "", destination_frame = "") {
  if(!is.character(path) || is.na(path) || !nzchar(path)) stop("`path` must be a non-empty character string")
  if(!is.character(pattern) || length(pattern) != 1L || is.na(pattern)) stop("`pattern` must be a character string")
  .key.validate(destination_frame)
  res <-.h2o.__remoteSend(.h2o.__IMPORT, path=path, pattern=pattern)
  destFrame <- res$destination_frames
  fails <- res$fails

  if(length(res$fails) > 0L) {
    for(i in seq_len(length(res$fails)))
      cat(res$fails[[i]], "failed to import")
  }
  # Return only the files that successfully imported
  if(length(res$files) <= 0L) stop("all files failed to import")
  data=.newH2OFrame(op="ImportFolder",id=res$destination_frames,-1,-1)
  srcKeys <- .h2o.get.source.keys(data, destination_frame)
  parms = list(source_frames = srcKeys)
  if(!missing(destination_frame)) {
    parms$destination_frame = destination_frame
  }
  parse.job <- .h2o.__remoteSend(.h2o.__PARSE_SVMLIGHT, method = "POST", .params = parms)
  hex <- parse.job$dest$name
  # Poll on job
  .h2o.__waitOnJob(parse.job$key$name)
  # Return a new H2OFrame object
  x <- .newH2OFrame("Parse",id=hex,-1,-1)
  .fetch.data(x,10,100) # Fill in nrow and ncol
  x
}

#'
#' Get a parse setup back for the staged data.
#' @inheritParams h2o.parseRaw
#' @seealso \link{h2o.parseRaw}
#' @export
h2o.parseSetup <- function(data, pattern="", destination_frame = "", header = NA, sep = "", col.names = NULL, col.types = NULL,
                           na.strings = NULL, parse_type = NULL, chunk_size = NULL, decrypt_tool = NULL, skipped_columns=NULL) {

  # Allow single frame or list of frames; turn singleton into a list
  if( is.H2OFrame(data) ) data <- list(data)
  for (d in data) chk.H2OFrame(d)

  .key.validate(destination_frame)
  if(!(is.na(header) || is.logical(header))) stop("`header` cannot be of class ", class(header))
  if(!is.character(sep) || length(sep) != 1L || is.na(sep)) stop("`sep` must a character string")

  # begin the setup
  # setup the parse parameters here
  parseSetup.params <- list()

  if (!is.null(skipped_columns)) {
    skipped_columns = sort(skipped_columns)
  }
  
  # Prep srcs: must be of the form [src1,src2,src3,...]
  parseSetup.params$source_frames <- .collapse.char(sapply(data, function (d) attr(d, "id")))
  parseSetup.params$skipped_columns <- paste0("[", paste (skipped_columns, collapse = ','), "]")

  # check the header
  if( is.na(header) && is.null(col.names) ) parseSetup.params$check_header <-  0
  else if( !isTRUE(header) )                parseSetup.params$check_header <- -1
  else                                      parseSetup.params$check_header <-  1

  # set field sep
  if( nzchar(sep) ) parseSetup.params$separator <- .asc(sep)

  # check the na.strings
  if( !is.null(na.strings) ) parseSetup.params$na_strings <- .collapse.array(na.strings)

  # set decrypt_tool
  if( !is.null(decrypt_tool) ) parseSetup.params$decrypt_tool <- .decrypt_tool_id(decrypt_tool)

  parseSetup <- .h2o.__remoteSend(.h2o.__PARSE_SETUP, method = "POST", .params = parseSetup.params)
  parsedColLength <- parseSetup$number_columns
  if (!is.null(skipped_columns)) {
    parsedColLength <- parsedColLength-length(skipped_columns)
  }

  tempColNames <- parseSetup$column_names
  # set the column names
  if (!is.null(col.names)) {
    parseSetup$column_names <-
      if (is.H2OFrame(col.names))
        colnames(col.names)
    else
      col.names
    if (!is.null(parseSetup$column_names) &&
        (length(parseSetup$column_names) != parsedColLength)) {
      stop("length of col.names must equal to the number of columns in dataset")
    }
    # change column names to what the user specified
    if (!is.null(skipped_columns)) {
      countParsedColumns = 1
      for (cind in c(1:parseSetup$number_columns)) {
        if (!((cind-1) %in% skipped_columns)) {
          tempColNames[cind] = col.names[countParsedColumns]
          countParsedColumns = countParsedColumns + 1
        }
      }
    }
  }

  # set col.types
  if( !is.null(col.types) ) { # list of enums
    if (typeof(col.types) == "character") {
      if (!is.null(skipped_columns)) {
        countParsedColumns = 1
        for (cind in c(1:parseSetup$number_columns)) {
          if ((cind-1) %in% skipped_columns) { # belongs to columns skipped
            parseSetup$col_type[cind]=NA
          } else { #column indices to be parsed
            parseSetup$col_type[cind] = col.types[countParsedColumns]
            countParsedColumns = countParsedColumns+1
          }
        }
      } else {
        parseSetup$column_types <- col.types
      }
    } else if ((typeof(col.types) == "list")) {
        list.names <- names(col.types)
        by.col.name <- ("by.col.name" %in% list.names)
        by.col.idx <- ("by.col.idx" %in% list.names)
        if (!(("types" %in% list.names) && xor(by.col.name,by.col.idx))) stop(.col.type.usage())
        if (by.col.name && typeof(col.types$by.col.name) != "character") stop("`by.col.name` must be character vector.")
        if (by.col.idx  && typeof(col.types$by.col.idx) != "double")     stop("`by.col.idx` must be vector of doubles.")

        c <- 1
        if (by.col.name) {
            lapply(col.types$by.col.name, function(n) {
                valid_col_name <- FALSE
                if (is.null(parseSetup$column_names)) {
                    valid_col_name <- .valid.generated.col(n,parseSetup$number_columns)
                } else {
                    valid_col_name <- n %in% parseSetup$column_names }
                if (!valid_col_name) stop("by.col.name must be a subset of the actual column names")
                if (is.null(parseSetup$column_names)) {
                    parseSetup$column_types[as.numeric(substring(n,2))] <<- col.types$types[[c]]
                } else {
                    parseSetup$column_types[which(n == tempColNames)] <<- col.types$types[[c]] }
                c <<- c + 1 })
        } else {
            lapply(col.types$by.col.idx, function (i) {
                parseSetup$column_types[i]<<- col.types$types[[c]]
                c <<- c + 1 })
        }
    } else { stop("`col.types` must be a character vector or list") }
  }

  # set parse_type
  if( !is.null(parse_type) ) parseSetup$parse_type <- parse_type

  # set chunk_size
  if( !is.null(chunk_size) ) parseSetup$chunk_size <- chunk_size

  # set decrypt_tool
  if( !is.null(decrypt_tool) ) parseSetup$decrypt_tool <- .decrypt_tool_id(decrypt_tool)

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
        delete_on_done     = TRUE,
        warnings           = parseSetup$warnings,
        decrypt_tool       = parseSetup$decrypt_tool,
        skipped_columns    = parseSetup$skipped_columns
        )
}

#'
#' Setup a Decryption Tool
#'
#' If your source file is encrypted - setup a Decryption Tool and then provide
#' the reference (result of this function) to the import functions.
#'
#' @param keystore An H2OFrame object referencing a loaded Java Keystore (see example).
#' @param keystore_type (Optional) Specification of Keystore type, defaults to JCEKS.
#' @param key_alias Which key from the keystore to use for decryption.
#' @param password Password to the keystore and the key.
#' @param decrypt_tool (Optional) Name of the decryption tool.
#' @param decrypt_impl (Optional) Java class name implementing the Decryption Tool.
#' @param cipher_spec Specification of a cipher (eg.: AES/ECB/PKCS5Padding).
#' @seealso \link{h2o.importFile}, \link{h2o.parseSetup}
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' ksPath <- system.file("extdata", "keystore.jks", package = "h2o")
#' keystore <- h2o.importFile(path = ksPath, parse = FALSE) # don't parse, keep as a binary file
#' cipher <- "AES/ECB/PKCS5Padding"
#' pwd <- "Password123"
#' kAlias <- "secretKeyAlias"
#' dt <- h2o.decryptionSetup(keystore, key_alias = kAlias, password = pwd, cipher_spec = cipher)
#' dataPath <- system.file("extdata", "prostate.csv.aes", package = "h2o")
#' data <- h2o.importFile(dataPath, decrypt_tool = dt)
#' summary(data)
#' }
#' @export
h2o.decryptionSetup <- function(keystore, keystore_type = "JCEKS", key_alias = NA_character_, password = NA_character_,
                                decrypt_tool = "", decrypt_impl = "water.parser.GenericDecryptionTool", cipher_spec = NA_character_) {

  # Validate inputs
  chk.H2OFrame(keystore)

  if (!is.character(keystore_type) || is.na(keystore_type) || !nzchar(keystore_type))
    stop("`keystore_type` must be a non-empty character string")
  if (!is.character(key_alias) || is.na(key_alias) || !nzchar(key_alias))
    stop("`key_alias` must be a non-empty character string")
  if (!is.character(password) || is.na(password) || !nzchar(password))
    stop("`password` must be a non-empty character string")
  if (!is.character(decrypt_impl) || is.na(decrypt_impl) || !nzchar(decrypt_impl))
    stop("`decrypt_impl` must be a non-empty character string")
  if (!is.character(cipher_spec) || is.na(cipher_spec) || !nzchar(cipher_spec))
    stop("`cipher_spec` must be a non-empty character string")

  .key.validate(decrypt_tool)

  # Prepare Decryption Setup
  setup <- list(
    decrypt_impl = decrypt_impl,
    keystore_id = attr(keystore, "id"),
    keystore_type = keystore_type,
    key_alias = key_alias,
    password = password,
    cipher_spec = cipher_spec
  )
  if (! nzchar(decrypt_tool))
    setup$decrypt_tool_id <- decrypt_tool

  .h2o.__remoteSend(.h2o.__DECRYPTION_SETUP, method = "POST", .params = setup)
}

#'
#' Helper Collapse Function
#'
#' Collapse a character vector into a ','-sep array of the form: [thing1,thing2,...]
#'
#' @param v Character vector.
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

.decrypt_tool_id <- function(decrypt_tool) {
    if (! is.null(decrypt_tool) && is(decrypt_tool, "list"))
      return(decrypt_tool$decrypt_tool_id$name)
    return(decrypt_tool)
}
