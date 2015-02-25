#'
#' Class definitions and their `show` & `summary` methods.
#'
#'
#' To conveniently and safely pass messages between R and H2O, this package relies
#' on S4 objects to capture and pass state. This R file contains all of the h2o
#' package's classes as well as their complementary `show` methods. The end user
#' will typically never have to reason with these objects directly, as there are
#' S3 accessor methods provided for creating new objects.
#'
#' @name ClassesIntro
NULL

#-----------------------------------------------------------------------------------------------------------------------
# Class Defintions
#-----------------------------------------------------------------------------------------------------------------------

setClassUnion("data.frameOrNULL", c("data.frame", "NULL"))

if (inherits(try(getRefClass("H2OConnectionMutableState"), silent = TRUE), "try-error")) {
# TODO: Address issue below
# H2O.ai testing infrastructure sources .R files in addition to loading the h2o package
# avoid redefinition of reference class

#'
#' The H2OConnectionMutableState class
#'
#' This class represents the mutable aspects of a connection to an H2O cloud.
#'
#' @slot session_id A \code{character} string specifying the H2O session identifier.
#' @slot key_count A \code{integer} value specifying count for the number of keys generated for the \code{session_id}.
#' @aliases H2OConnectionMutableState
setRefClass("H2OConnectionMutableState",
            fields = list(session_id = "character", key_count = "integer"),
            methods = list(
              initialize =
              function(..., session_id = NA_character_, key_count = 0L) {
                .self$initFields(session_id = session_id, key_count = key_count)
                callSuper(...)
              }))
}

#'
#' The H2OConnection class.
#'
#' This class represents a connection to an H2O cloud.
#'
#' Because H2O is not a master-slave architecture, there is no restriction on which H2O node
#' is used to establish the connection between R (the client) and H2O (the server).
#'
#' A new H2O connection is established via the h2o.init() function, which takes as parameters
#' the `ip` and `port` of the machine running an instance to connect with. The default behavior
#' is to connect with a local instance of H2O at port 54321, or to boot a new local instance if one
#' is not found at port 54321.
#' @slot ip A \code{character} string specifying the IP address of the H2O cloud.
#' @slot port A \code{numeric} value specifying the port number of the H2O cloud.
#' @slot mutable An \code{H2OConnectionMutableState} object to hold the mutable state for the H2O connection.
#' @aliases H2OConnection
setClass("H2OConnection",
         representation(ip="character", port="numeric", mutable="H2OConnectionMutableState"),
         prototype(ip      = NA_character_,
                   port    = NA_integer_,
                   mutable = new("H2OConnectionMutableState")))
setClassUnion("H2OConnectionOrNULL", c("H2OConnection", "NULL"))

#' @rdname H2OConnection-class
setMethod("show", "H2OConnection", function(object) {
  cat("IP Address:", object@ip,                 "\n")
  cat("Port      :", object@port,               "\n")
  cat("Session ID:", object@mutable$session_id, "\n")
  cat("Key Count :", object@mutable$key_count,  "\n")
})

#'
#' The H2OObject class
#'
#' @slot conn An \code{H2OConnection} object specifying the connection to an H2O cloud.
#' @slot key A \code{character} string specifying the key in the H2O cloud's key-value store.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove keys from the H2O key-value store.
#' @aliases H2OObject
setClass("H2OObject",
         representation(conn="H2OConnectionOrNULL", key="character", finalizers="list"),
         prototype(conn=NULL, key=NA_character_, finalizers=list()),
         contains="VIRTUAL")

#' @rdname H2OObject-class
setMethod("initialize", "H2OObject", function(.Object, ...) {
  .Object <- callNextMethod()
  .Object@finalizers <- .Object@finalizers[!duplicated(unlist(lapply(.Object@finalizers,
                                                                     function(x) capture.output(print(x)))))]
  .Object
})

.keyFinalizer <- function(envir) {
  try(h2o.rm(get("key", envir), get("conn", envir)), silent=TRUE)
}

.newH2OObject <- function(Class, ..., conn = NULL, key = NA_character_, finalizers = list(), linkToGC = FALSE) {
  if (linkToGC && !is.na(key) && is(conn, "H2OConnection")) {
    envir <- new.env()
    assign("key", key, envir)
    assign("conn", conn, envir)
    reg.finalizer(envir, .keyFinalizer, onexit = FALSE)
    finalizers <- c(list(envir), finalizers)
  }
  new(Class, ..., conn = conn, key = key, finalizers = finalizers)
}

#'
#' The Node class.
#'
#' An object of type Node inherits from an H2OFrame, but holds no H2O-aware data. Every node in the abstract syntax tree
#' An object of type Node inherits from an H2OFrame, but holds no H2O-aware data. Every node in the abstract syntax tree
#' has as its ancestor this class.
#'
#' Every node in the abstract syntax tree will have a symbol table, which is a dictionary of types and names for
#' all the relevant variables and functions defined in the current scope. A missing symbol is therefore discovered
#' by looking up the tree to the nearest symbol table defining that symbol.
#' @aliases Node
setClass("Node", contains="VIRTUAL")

#'
#' The ASTNode class.
#'
#' This class represents a node in the abstract syntax tree. An ASTNode has a root. The root has children that either
#' point to another ASTNode, or to a leaf node, which may be of type ASTNumeric or ASTFrame.
#' @slot root Object of type \code{Node}
#' @slot children Object of type \code{list}
#' @aliases ASTNode
setClass("ASTNode", representation(root="Node", children="list"), contains="Node")

setClassUnion("ASTNodeOrNULL", c("ASTNode", "NULL"))

#' @rdname ASTNode-class
setMethod("show", "ASTNode", function(object) cat(.visitor(object), "\n") )

#'
#' The ASTApply class.
#'
#' This class represents an operator between one or more H2O objects. ASTApply nodes are always root nodes in a tree and
#' are never leaf nodes. Operators are discussed more in depth in ops.R.
#' @rdname Node-class
setClass("ASTApply", representation(op="character"), contains="Node")
#' @rdname Node-class
setClass("ASTEmpty",  representation(key="character"), contains="Node")
#' @rdname Node-class
setClass("ASTBody",   representation(statements="list"), contains="Node")
#' @rdname Node-class
setClass("ASTFun",    representation(name="character", arguments="character", body="ASTBody"), contains="Node")
#' @rdname Node-class
setClass("ASTSpan",   representation(root="Node",    children  = "list"), contains="Node")
#' @rdname Node-class
setClass("ASTSeries", representation(op="character", children  = "list"), contains="Node", prototype(op="{"))
#' @rdname Node-class
setClass("ASTIf",     representation(op="character", condition = "ASTNode",  body = "ASTBody"), contains="Node", prototype(op="if"))
#' @rdname Node-class
setClass("ASTElse",   representation(op="character", body      = "ASTBody"), contains="Node", prototype(op="else"))
#' @rdname Node-class
setClass("ASTFor",    representation(op="character", iterator  = "list",  body = "ASTBody"), contains="Node", prototype(op="for"))
#' @rdname Node-class
setClass("ASTReturn", representation(op="character", children  = "ASTNode"), contains="Node", prototype(op="return"))

if (inherits(try(getRefClass("H2OFrameMutableState"), silent = TRUE), "try-error")) {
# TODO: Address issue below
# H2O.ai testing infrastructure sources .R files in addition to loading the h2o package
# avoid redefinition of reference class

#'
#' The H2OFrameMutableState class
#'
#' This class represents the mutable aspects of an H2OFrame object.
#'
#' @slot ast Either an abstract syntax tree defining the H2O frame or NULL.
#' @slot nrows A \code{numeric} value specifying the number of rows in the H2O frame.
#' @slot ncols A \code{numeric} value specifying the number of columns in the H2O frame.
#' @slot col_names A \code{character} vector specifying the column names in the H2O frame.
#' @aliases H2OFrameMutableState
setRefClass("H2OFrameMutableState",
            fields = list(ast = "ASTNodeOrNULL", nrows = "numeric", ncols = "numeric", col_names = "character"),
            methods = list(
              initialize =
              function(..., ast = NULL, nrows = NA_integer_, ncols = NA_integer_, col_names = NA_character_) {
                .self$initFields(ast = ast, nrows = nrows, ncols = ncols, col_names = col_names)
                callSuper(...)
              }))
}

#'
#' The H2OFrame class
#'
#' @slot conn An \code{H2OConnection} object specifying the connection to an H2O cloud.
#' @slot key A \code{character} string specifying the key for the frame in the H2O cloud's key-value store.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove keys from the H2O key-value store.
#' @slot mutable An \code{H2OFrameMutableState} object to hold the mutable state for the H2O frame.
#' @aliases H2OFrame
setClass("H2OFrame",
         representation(mutable = "H2OFrameMutableState"),
         prototype(conn       = NULL,
                   key        = NA_character_,
                   finalizers = list(),
                   mutable    = new("H2OFrameMutableState")),
         contains ="H2OObject")

#' @rdname H2OFrame-class
setMethod("show", "H2OFrame", function(object) {
  .byref.update.frame(object)

  nr <- nrow(object)
  nc <- ncol(object)
  cat(class(object), " with ",
      nr, ifelse(!is.na(nr) && nr == 1L, " row and ", " rows and "),
      nc, ifelse(!is.na(nc) && nc == 1L, " column\n", " columns\n"), sep = "")
  if (!is.na(nr)) {
    if (nr > 10L)
      cat("\nFirst 10 rows:\n")
    print(head(object, 10L))
  }
  invisible(object)
})

#'
#' The H2ORawData class.
#'
#' This class represents data in a post-import format.
#'
#' Data ingestion is a two-step process in H2O. First, a given path to a data source is _imported_ for validation by the
#' user. The user may continue onto _parsing_ all of the data into memory, or the user may choose to back out and make
#' corrections. Imported data is in a staging area such that H2O is aware of the data, but the data is not yet in
#' memory.
#'
#' The H2ORawData is a representation of the imported, not yet parsed, data.
#' @slot conn An \code{H2OConnection} object containing the IP address and port number of the H2O server.
#' @slot key An object of class \code{"character"}, which is the hex key assigned to the imported data.
#' @aliases H2ORawData
setClass("H2ORawData", contains="H2OObject")

#' @rdname H2ORawData-class
setMethod("show", "H2ORawData", function(object) {
  print(object@conn)
  cat("Raw Data Key:", object@key, "\n")
})

# No show method for this type of object.

#'
#' The H2OW2V object.
#'
#' This class represents a h2o-word2vec object.
#'
#' @aliases H2OW2V
setClass("H2OW2V", representation(train.data="H2OFrame"), contains="H2OObject")

#'
#' The H2OModel object.
#'
#' This virtual class represents a model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OFrame).
#'
#' @slot conn Object of class \code{H2OConnection}, which is the client object that was passed into the function call.
#' @slot key A \code{character} string specifying the key for the model fit in the H2O cloud's key-value store.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove keys from the H2O key-value store.
#' @slot algorithm A \code{character} string specifying the algorithm that were used to fit the model.
#' @slot parameters A \code{list} containing the parameter settings that were used to fit the model.
#' @slot model A \code{list} containing the characteristics of the model returned by the algorithm.
#' @aliases H2OModel
setClass("H2OModel",
         representation(algorithm="character", parameters="list", model="list"),
         contains=c("VIRTUAL", "H2OObject"))

#' @rdname H2OModel-class
setMethod("show", "H2OModel", function(object) {
  cat(class(object), ": ", object@algorithm, "\n\n", sep = "")
  cat("Model Details:\n")
  sub <- intersect(names(object@model), names(object@model$help))
  val <- object@model[sub]
  lab <- object@model$help[sub]
  lab <- lab[names(lab) != "help"]
  val <- val[names(lab)]
  mapply(function(val, lab) { cat("\n", lab, "\n"); print(val) }, val, lab)
  invisible(object)
})

#' @rdname H2OModel-class
setClass("H2OUnknownModel",     contains="H2OModel")
#' @rdname H2OModel-class
setClass("H2OBinomialModel",    contains="H2OModel")
#' @rdname H2OModel-class
setClass("H2OMultinomialModel", contains="H2OModel")
#' @rdname H2OModel-class
setClass("H2ORegressionModel",  contains="H2OModel")
#' @rdname H2OModel-class
setClass("H2OClusteringModel",  contains="H2OModel")
#' @rdname H2OModel-class
setClass("H2OAutoEncoderModel", contains="H2OModel")
#' @rdname H2OModel-class
setClass("H2ODimReductionModel", contains="H2OModel")

#' 
#' The H2OModelMetrics Object.
#'
#' A class for constructing performance measures of H2O models.
#'
#' @aliases H2OModelMetrics
setClass("H2OModelMetrics",
         representation(algorithm="character", metrics="list"),
         contains="VIRTUAL")

#' @rdname H2OModelMetrics-class
setMethod("show", "H2OModelMetrics", function(object) {
    cat(class(object), ": ", object@algorithm, "\n\n", sep="")
})

#' @rdname H2OModelMetrics-class
setClass("H2OUnknownMetrics",     contains="H2OModelMetrics")

#' @rdname H2OModelMetrics-class
setClass("H2OBinomialMetrics",    contains="H2OModelMetrics")
setMethod("show", "H2OBinomialMetrics", function(object) {
    cat(class(object), ": ", object@algorithm, "\n\n", sep="")
    cat("Metric Details:\n\n")
    if(object@algorithm == "glm") {
      cat("Null Deviance:     ", object@metrics$nullDeviance,"\n", sep="")
      cat("Residual Deviance: ", object@metrics$residualDeviance,"\n", sep="")
      cat("aic:               ", object@metrics$aic,"\n\n", sep="")
    }
    cat("AUC:  ", object@metrics$AUC, "\n", sep="")
    cat("Gini: ", object@metrics$Gini, "\n", sep="")
    cat("MSE:  ", object@metrics$mse, "\n\n", sep="")    
    print(object@metrics$maxCriteriaAndMetricScores)
})

#' @rdname H2OModelMetrics-class
setClass("H2OMultinomialMetrics", contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
setClass("H2ORegressionMetrics",  contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
setClass("H2OClusteringMetrics",  contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
setClass("H2OAutoEncoderMetrics", contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
setClass("H2ODimReductionMetrics", contains="H2OModelMetrics")