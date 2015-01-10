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

#'
#' The Node class.
#'
#' An object of type Node inherits from an H2OFrame, but holds no H2O-aware data. Every node in the abstract syntax tree
#' An object of type Node inherits from an h2o.frame, but holds no H2O-aware data. Every node in the abstract syntax tree
#' has as its ancestor this class.
#'
#' Every node in the abstract syntax tree will have a symbol table, which is a dictionary of types and names for
#' all the relevant variables and functions defined in the current scope. A missing symbol is therefore discovered
#' by looking up the tree to the nearest symbol table defining that symbol.
setClass("Node", contains="VIRTUAL")

#'
#' The ASTNode class.
#'
#' This class represents a node in the abstract syntax tree. An ASTNode has a root. The root has children that either
#' point to another ASTNode, or to a leaf node, which may be of type ASTNumeric or ASTFrame.
#' @slot root Object of type \code{Node}
#' @slot children Object of type \code{list}
setClass("ASTNode", representation(root="Node", children="list"), contains="Node")

#' @rdname ASTNode-class
setMethod("show", "ASTNode", function(object) cat(visitor(object), "\n") )

#'
#' The ASTApply class.
#'
#' This class represents an operator between one or more H2O objects. ASTApply nodes are always root nodes in a tree and
#' are never leaf nodes. Operators are discussed more in depth in ops.R.
setClass("ASTApply", representation(op="character"), contains="Node")

setClass("ASTEmpty",  representation(key="character"), contains="Node")
setClass("ASTBody",   representation(statements="list"), contains="Node")
setClass("ASTFun",    representation(name="character", arguments="character", body="ASTBody"), contains="Node")
setClass("ASTSpan",   representation(root="Node",    children  = "list"), contains="Node")
setClass("ASTSeries", representation(op="character", children  = "list"), contains="Node")
setClass("ASTIf",     representation(op="character", condition = "ASTNode",  body = "ASTBody"), contains="Node", prototype(op="if"))
setClass("ASTElse",   representation(op="character", body      = "ASTBody"), contains="Node", prototype(op="else"))
setClass("ASTFor",    representation(op="character", iterator  = "list",  body = "ASTBody"), contains="Node", prototype(op="for"))
setClass("ASTReturn", representation(op="character", children  = "ASTNode"), contains="Node", prototype(op="return"))

#'
#' The H2OConnection class.
#'
#' This class represents a connection to the H2O Cloud.
#'
#' Because H2O is not a master-slave architecture, there is no restriction on which H2O node
#' is used to establish the connection between R (the client) and H2O (the server).
#'
#' A new H2O connection is established via the h2o.init() function, which takes as parameters
#' the `ip` and `port` of the machine running an instance to connect with. The default behavior
#' is to connect with a local instance of H2O at port 54321, or to boot a new local instance if one
#' is not found at port 54321.
#' @slot ip Object of class \code{character} representing the IP address of the H2O server.
#' @slot port Object of class \code{numeric} representing the port number of the H2O server.
#' @aliases H2OConnection
setClass("H2OConnection",
         representation(ip="character", port="numeric", session_key="character"),
         prototype(ip=NA_character_, port=NA_integer_, session_key=NA_character_)
         )

#' @rdname H2OConnection-class
setMethod("show", "H2OConnection", function(object) {
  cat("IP Address:", object@ip,   "\n")
  cat("Port      :", object@port, "\n")
})

setClassUnion("H2OConnectionOrNULL", c("H2OConnection", "NULL"))
setClassUnion("ASTNodeOrNULL", c("ASTNode", "NULL"))
setClassUnion("data.frameOrNULL", c("data.frame", "NULL"))


#'
#' The H2OFrame class
#'
setClass("H2OFrame",
         representation(h2o="H2OConnectionOrNULL", key="character", ast="ASTNodeOrNULL",
         col_names="character", nrows="numeric", ncols="numeric", scalar="numeric",
         factors="data.frameOrNULL"),
         prototype(h2o       = NULL,
                   key       = NA_character_,
                   ast       = NULL,
                   col_names = NA_character_,
                   nrows     = NA_integer_,
                   ncols     = NA_integer_,
                   factors   = NULL,
                   scalar    = NA_integer_)
         )

setMethod("show", "H2OFrame", function(object) {
  print(object@h2o)
  cat("Key:", object@key, "\n")
  tmp_R_value <- object
  print(head(tmp_R_value))
  h2o.gc()
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
#' @slot h2o An \code{H2OConnection} object containing the IP address and port number of the H2O server.
#' @slot key An object of class \code{"character"}, which is the hex key assigned to the imported data.
#' @aliases H2ORawData
setClass("H2ORawData", representation(h2o="H2OConnection", key="character"))

#' @rdname H2ORawData-class
setMethod("show", "H2ORawData", function(object) {
  print(object@h2o)
  cat("Raw Data Key:", object@key, "\n")
})

# No show method for this type of object.

#'
#' The H2OW2V object.
#'
#' This class represents a h2o-word2vec object.
#'
setClass("H2OW2V", representation(h2o="H2OConnection", key="character", train.data="H2OFrame"))

#'
#' The H2OModel object.
#'
#' This virtual class represents a model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OFrame).

#' @slot h2o Object of class \code{H2OConnection}, which is the client object that was passed into the function call.
#' @slot key Object of class \code{character}, representing the unique hex key that identifies the model
#' @slot model Object of class \code{list} containing the characteristics of the model returned by the algorithm.
#' @aliases H2OModel
setClass("H2OModel",
         representation(h2o="H2OConnection", key="character", algorithm="character", parameters="list", model="list"),
         contains="VIRTUAL")

setMethod("show", "H2OModel", function(object) {
  cat(class(object), ": ", object@algorithm, "\n\n", sep = "")
  cat("Model Details:\n")
  # print(object@model)
  sub = intersect(names(object@model), names(object@model$help))
  val = object@model[sub]; lab = object@model$help[sub]
  val = val[match(names(val), names(lab))]
  val = val[names(val) != "help"]; lab = lab[names(lab) != "help"]
  tmp = mapply(function(val, lab) { cat("\n", lab, "\n"); print(val) }, val, lab)
})

setClass("H2OUnknownModel",     contains="H2OModel")
setClass("H2OBinomialModel",    contains="H2OModel")
setClass("H2OMultinomialModel", contains="H2OModel")
setClass("H2ORegressionModel",  contains="H2OModel")
setClass("H2OClusteringModel",  contains="H2OModel")

#-----------------------------------------------------------------------------------------------------------------------
# Class Utils
#-----------------------------------------------------------------------------------------------------------------------

.retrieveH2O<-
function(env) {
  e_list <- unlist(lapply(ls(env), function(x) {
    tryCatch(is(get(x, envir=env), "H2OConnection"), error = function(e) FALSE)
             }))
  if (any(e_list)) {
    if (sum(e_list) > 1L) {
      x <- e_list[1L]
      for (y in e_list[1L])
        if (!identical(x, y)) stop("Found multiple H2OConnection objects. Please specify the preferred H2O connection.")
    }
    return(get(ls(env)[which(e_list)[1L]], envir=env))
  }
  g_list <- unlist(lapply(ls(globalenv()), function(x) is(get(x, envir=globalenv()), "H2OConnection")))
  if (any(g_list)) {
    if (sum(g_list) > 1L) {
      x <- g_list[1L]
      for (y in g_list[1L])
        if (!identical(x, y)) stop("Found multiple H2OConnection objects. Please specify the preferred H2O connection.")
    }
    return(get(ls(globalenv())[which(g_list)[1L]], envir=globalenv()))
  }
  stop("Could not find any active H2OConnection objects. Please specify an H2O connection.")
}

.get.session.id <- function() {
  h <- .retrieveH2O(parent.frame())
  if (is.na(h@session_key)) stop("Missing session_key! Please perform h2o.init.")
  h@session_key
}