#'
#' H2O Package Constants
#'
#' The API endpoints for interacting with H2O via REST are named here.
#'
#' Additionally, environment variables for the H2O package are named here.


#'
#' The H2O Package Environment
#'
.pkg.env              <- new.env()

# These may no longer be needed...
.pkg.env$result_count <- 0
.pkg.env$temp_count   <- 0
.pkg.env$IS_LOGGING   <- FALSE
.pkg.env$call_list    <- NULL
.TEMP_KEY <- "Last.value"

# Some handy infix utilities
"%i%"    <- function(x,y) inherits(x, y)                                                         # instanceof
"%p0%"   <- function(x,y) assign(deparse(substitute(x)), paste(x, y, sep = ""), parent.frame())  # paste0 infix
"%p%"    <- function(x,y) assign(deparse(substitute(x)), paste(x, y), parent.frame())            # paste  infix
"%<-%"   <- function(x,y) {
  if ( x %i% "h2o.frame" ) x <- x@key
  new("ASTNode", root= new("ASTApply", op="="), children = list(left = '!' %p0% x, right = y))   # assignment node
}

.uniq.id <- function(prefix = "") {
  hex_digits <- c(as.character(0:9), letters[1:6])
  y_digits <- hex_digits[9:12]
  tempA <- paste(sample(hex_digits, 8, replace=TRUE), collapse='')
  tempB <- paste(sample(hex_digits, 4, replace=TRUE), collapse='')
  tempC <- '4'
  tempD <- paste(sample(hex_digits, 3, replace=TRUE), collapse='')
  tempE <- paste(sample(y_digits,1), collapse='')
  tempF <- paste(sample(hex_digits, 3, replace=TRUE), collapse='')
  tempG <- paste(sample(hex_digits, 12, replace=TRUE), collapse='')
  temp <- tempA %p0% tempB %p0% tempC %p0% tempD %p0% tempE %p0% tempF %p0% tempG
  prefix %p0% '_' %p0% temp
}
.key.make <- function() .uniq.id("rapids")

#'
#' Map of binary operators to their "AST" operator value.
#'
.op.map <- c('>'  = 'g',
             '>=' = 'G',
             '<'  = 'l',
             '<=' = 'L',
             '==' = 'n',
             '!=' = 'N',
             '%%' = '%',
             '**' = '^',
             '!'  = '_',
             '|'  = '|',
             '&'  = '&',
             "&&" = "&&",
             '+'  = '+',
             '-'  = '-',
             '*'  = '*',
             '/'  = '/',
             '^'  = '^',
             "%/%"="%/%")

.binop.map <- c('>'  = 'g',
                '>=' = 'G',
                '<'  = 'l',
                '<=' = 'L',
                '==' = 'n',
                '!=' = 'N',
                '%%' = '%',
                '**' = '^',
                '|'  = '|',
                '&'  = '&',
                "&&" = "&&",
                '+'  = '+',
                '-'  = '-',
                '*'  = '*',
                '/'  = '/',
                '^'  = '^',
                "%/%"="%/%")

#'
#' Map of unary operators to their "AST" operator value.
#'
.unop.map <- c('!' = '_',
               '$' = '[',
               '[' = '[')

#'
#' A list of prefix operations. 1:1 map for now, may add more to the map as needed.
#'
.prefix.map <-  c("abs"  = "abs",
                  "sign" = "sign",
                  "sqrt" = "sqrt",
                  "ceiling" = "ceiling",
                  "floor" = "floor",
                  "trunc" = "trunc",
                  "cummax" = "cummax",
                  "cummin" = "cummin",
                  "cumprod" = "cumprod",
                  "cumsum" = "cumsum",
                  "log" = "log",
                  "log10" = "log10",
                  "log2" = "log2",
                  "log1p" = "log1p",
                  "acos" = "acos",
                  "acosh" = "acosh",
                  "asin" = "asin",
                  "asinh" = "asinh",
                  "atan" = "atan",
                  "atanh" = "atanh",
                  "exp" = "exp",
                  "expm1" = "expm1",
                  "cos" = "cos",
                  "cosh" = "cosh",
                  "cospi" = "cospi",
                  "sin" = "sin",
                  "sinh" = "sinh",
                  "sinpi" = "sinpi",
                  "tan" = "tan",
                  "tanh" = "tanh",
                  "tanpi" = "tanpi",
                  "gamma" = "gamma",
                  "lgamma" = "lgamma",
                  "digamma" = "digamma",
                  "trigamma" = "trigamma",
                  "round" = "round",
                  "signif" = "signif",
                  "max" = "max",
                  "nrow"="nrow",
                  "ncol"="ncol",
                  "min" = "min",
                  "range" = "range",
                  "prod" = "prod",
                  "sum" = "sum",
                  "any" = "any",
                  "all" = "all",
                  "is.na" = "is.na",
                  "trunc" = "trunc",
                  "is.factor" = "is.factor")

#'
#' The variable args operations
#'
.varop.map <- c("round" = "round",
                "signif" = "signif",
                "max" = "max",
                "min" = "min",
                "range" = "range",
                "prod" = "prod",
                "sum" = "sum",
                "any" = "any",
                "all" = "all",
                "mean"  = "mean",
                "var"   = "var",
                "log" = "log",
                "sd"    = "sd",
                "scale" = "scale",
                "tail" = "tail",
                "head" = "head",
                "match" = "match",
                "cut" = "cut",
                "table" = "table",
                "xorsum" = "xorsum",
                "trunc" = "trunc")


.type.map <- c("boolean" = "logical",
               "boolean[]" = "barray",
               "enum" = "character",
               "enum[]" = "sarray",
               "double" = "numeric",
               "double[]" = "narray",
               "float" = "numeric",
               "float[]" = "narray",
               "Frame" = "h2o.frame",
               "int" = "numeric",
               "int[]" = "narray",
               "Key" = "character",
               "long" = "numeric",
               "long[]" = "narray",
               "string" = "character",
               "string[]" = "sarray")

.algo.map <- c("deeplearning" = ".deeplearning.builder",
               "gbm" = ".gbm.builder",
               "kmeans" = ".kmeans.builder",
               "glm" = ".glm.builder")
#'
#' Inspect/Summary Endpoints
#'
.h2o.__INSPECT      <- "Inspect.json"       # Inspect.json?key=asdfasdf
.h2o.__FRAMES       <- "3/Frames.json"      # Frames.json/<key>    example: http://localhost:54321/3/Frames.json/meow.hex

#'
#' Administrative Endpoints
#'
.h2o.__JOBS         <- "Jobs.json"          # Jobs/$90w3r52hfej_JOB_KEY_12389471jsdfs
.h2o.__CLOUD        <- "Cloud.json"

#'
#' Algorithm Endpoints
#'
.h2o.__MODEL_BUILDERS <- function(algo) "2/ModelBuilders/" %p0% algo %p0% '.json'

#'
#' Algorithm Parameter Endpoints
#'

#'
#' Model Endpoint
#'
.h2o.__MODELS       <- "3/Models.json/"

#'
#' Removal
#'
.h2o.__REMOVE       <- "Remove.json"
.h2o.__REMOVEALL    <- "RemoveAll.json"

#'
#' Log and Echo
#'
.h2o.__LOGANDECHO   <- "LogAndEcho.json"

#'
#' Word To Vec
#'
.h2o.__W2V          <- "Word2Vec.json"
.h2o.__SYNONYMS     <- "Synonyms.json"
.h2o.__TRANSFORM    <- "Transform.json"

