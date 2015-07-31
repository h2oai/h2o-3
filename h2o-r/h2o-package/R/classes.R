#`
#` Class definitions and their `show` & `summary` methods.
#`
#`
#` To conveniently and safely pass messages between R and H2O, this package relies
#` on S4 objects to capture and pass state. This R file contains all of the h2o
#` package's classes as well as their complementary `show` methods. The end user
#` will typically never have to reason with these objects directly, as there are
#` S3 accessor methods provided for creating new objects.
#`

#-----------------------------------------------------------------------------------------------------------------------
# Class Definitions
#-----------------------------------------------------------------------------------------------------------------------

setClassUnion("data.frameOrNULL", c("data.frame", "NULL"))
setClassUnion("listOrNull", c("list", "NULL"))

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
#' @aliases H2OConnectionMutableStat
#' @export
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
#' @slot https Set this to TRUE to use https instead of http.
#' @slot insecure Set this to TRUE to disable SSL certificate checking.
#' @slot username Username to login with.
#' @slot password Password to login with.
#' @slot mutable An \code{H2OConnectionMutableState} object to hold the mutable state for the H2O connection.
#' @aliases H2OConnection
#' @export
setClass("H2OConnection",
         representation(ip="character", port="numeric",
                        https="logical", insecure="logical",
                        username="character", password="character",
                        mutable="H2OConnectionMutableState"),
         prototype(ip       = NA_character_,
                   port     = NA_integer_,
                   https    = FALSE,
                   insecure = FALSE,
                   username = NA_character_,
                   password = NA_character_,
                   mutable  = new("H2OConnectionMutableState")))




setClassUnion("H2OConnectionOrNULL", c("H2OConnection", "NULL"))

#' @rdname H2OConnection-class
#' @param object an \code{H2OConnection} object.
#' @export
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
#' @slot id A \code{character} string specifying the key in the H2O cloud's key-value store.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove keys from the H2O key-value store.
#' @aliases H2OObject
#' @export
setClass("H2OObject",
         representation(conn="H2OConnectionOrNULL", id="character", finalizers="list"),
         prototype(conn=NULL, id=NA_character_, finalizers=list()),
         contains="VIRTUAL")

#' @rdname H2OObject-class
#' @param .Object an \code{H2OObject}
#' @param \dots additional parameters to pass on to functions
#' @export
setMethod("initialize", "H2OObject", function(.Object, ...) {
  .Object <- callNextMethod()
  .Object@finalizers <- .Object@finalizers[!duplicated(unlist(lapply(.Object@finalizers,
                                                                     function(x) utils::capture.output(print(x)))))]
  .Object
})

.keyFinalizer <- function(envir) {
  if( !is.null(envir$model_id) ) h2o.rm(envir$model_id, envir$conn)
  if( !is.null(envir$id)       ) h2o.rm(envir$id, envir$conn)
  if( !is.null(envir$frame_id) ) h2o.rm(envir$frame_id,envir$conn)
  invisible(NULL)
}

.newH2OObject <- function(Class, ..., conn = NULL, id = NA_character_, finalizers = list(), linkToGC = FALSE) {
  if (linkToGC && !is.na(id) && is(conn, "H2OConnection")) {
    envir <- new.env()
    assign("id", id, envir)
    assign("conn", conn, envir)
    reg.finalizer(envir, .keyFinalizer, onexit = FALSE)
    finalizers <- c(list(envir), finalizers)
  }

  if( Class == "H2OFrame" || Class == "H2ORawData" ) new(Class, ..., conn=conn, frame_id=id, finalizers=finalizers)  # frame_id
  else                                               new(Class, ..., conn=conn, model_id=id, finalizers=finalizers)  # model_id
}

#'
#' The Node class.
#'
#' An object of type Node inherits from an H2OFrame, but holds no H2O-aware data. Every node in the abstract syntax tree
#' has as its ancestor this class.
#'
#' Every node in the abstract syntax tree will have a symbol table, which is a dictionary of types and names for
#' all the relevant variables and functions defined in the current scope. A missing symbol is therefore discovered
#' by looking up the tree to the nearest symbol table defining that symbol.
#' @aliases Node
#' @export
setClass("Node", contains="VIRTUAL")

#'
#' The ASTNode class.
#'
#' This class represents a node in the abstract syntax tree. An ASTNode has a root. The root has children that either
#' point to another ASTNode, or to a leaf node, which may be of type ASTNumeric or ASTFrame.
#' @slot root Object of type \code{Node}
#' @slot children Object of type \code{list}
#' @aliases ASTNode
#' @export
setClass("ASTNode", representation(root="Node", children="list"), contains="Node")

#' @export
setClassUnion("ASTNodeOrNULL", c("ASTNode", "NULL"))

#' @rdname ASTNode-class
#' @param object An \code{ASTNode} class object.
#' @export
setMethod("show", "ASTNode", function(object) cat(.visitor(object), "\n") )

#'
#' The ASTApply class.
#'
#' This class represents an operator between one or more H2O objects. ASTApply nodes are always root nodes in a tree and
#' are never leaf nodes. Operators are discussed more in depth in ops.R.
#' @rdname Node-class
#' @export
setClass("ASTApply", representation(op="character"), contains="Node")
#' @rdname Node-class
#' @export
setClass("ASTEmpty",  representation(key="character"), contains="Node")
#' @rdname Node-class
#' @export
setClass("ASTBody",   representation(statements="list"), contains="Node")
#' @rdname Node-class
#' @export
setClass("ASTFun",    representation(name="character", arguments="character", body="ASTBody"), contains="Node")
#' @rdname Node-class
#' @export
setClass("ASTSpan",   representation(root="Node",    children  = "list"), contains="Node")
#' @rdname Node-class
#' @export
setClass("ASTSeries", representation(op="character", children  = "list"), contains="Node", prototype(op="{"))
#' @rdname Node-class
#' @export
setClass("ASTIf",     representation(op="character", condition = "ASTNode",  body = "ASTBody"), contains="Node", prototype(op="if"))
#' @rdname Node-class
#' @export
setClass("ASTElse",   representation(op="character", body      = "ASTBody"), contains="Node", prototype(op="else"))
#' @rdname Node-class
#' @export
setClass("ASTFor",    representation(op="character", iterator  = "list",  body = "ASTBody"), contains="Node", prototype(op="for"))
#' @rdname Node-class
#' @export
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
#' @export
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
#' @slot frame_id A \code{character} string specifying the identifier for the frame in the H2O cloud.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove objects from the H2O cloud.
#' @slot mutable An \code{H2OFrameMutableState} object to hold the mutable state for the H2O frame.
#' @aliases H2OFrame
#' @export
setClass("H2OFrame",
         representation(conn="H2OConnectionOrNULL", frame_id="character", finalizers="list", mutable = "H2OFrameMutableState"),
         prototype(conn       = NULL,
                   frame_id   = NA_character_,
                   finalizers = list(),
                   mutable    = new("H2OFrameMutableState"))
         )

# TODO: make a more frame-specific constructor
.newH2OFrame <- function(Class, conn = NULL, frame_id = NA_character_, finalizers = list(), linkToGC = FALSE,mutable=new("H2OFrameMutableState")) {
  .newH2OObject("H2OFrame", conn=conn,id=frame_id,finalizers=finalizers,mutable=mutable)
}

#' @rdname H2OFrame-class
#' @param object An \code{H2OConnection} object.
#' @export
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
#' @slot frame_id An object of class \code{"character"}, which is the name of the key assigned to the imported data.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove objects from the H2O cloud.
#' @aliases H2ORawData
#' @export
setClass("H2ORawData", contains="H2OFrame")

.newH2ORawData <- function(Class, ..., conn = NULL, frame_id = NA_character_, finalizers = list(), linkToGC = FALSE) {
  .newH2OObject("H2ORawData", ..., conn=conn,id=frame_id,finalizers=finalizers,linkToGC=linkToGC)
}

#' @rdname H2ORawData-class
#' @param object a \code{H2ORawData} object.
#' @export
setMethod("show", "H2ORawData", function(object) {
  print(object@conn)
  cat("Raw Data Destination Frame:", object@frame_id, "\n")
})

# No show method for this type of object.

#'
#' The H2OW2V object.
#'
#' This class represents a h2o-word2vec object.
#'
#' @aliases H2OW2V
#' @export
setClass("H2OW2V", representation(train.data="H2OFrame"), contains="H2OFrame")

#'
#' The H2OModel object.
#'
#' This virtual class represents a model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OFrame).
#'
#' @slot conn Object of class \code{H2OConnection}, which is the client object that was passed into the function call.
#' @slot model_id A \code{character} string specifying the key for the model fit in the H2O cloud's key-value store.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove keys from the H2O key-value store.
#' @slot algorithm A \code{character} string specifying the algorithm that were used to fit the model.
#' @slot parameters A \code{list} containing the parameter settings that were used to fit the model that differ from the defaults.
#' @slot allparameters A \code{list} containg all parameters used to fit the model.
#' @slot model A \code{list} containing the characteristics of the model returned by the algorithm.
#' @aliases H2OModel
#' @export
setClass("H2OModel",
         representation(conn="H2OConnectionOrNULL", model_id="character", algorithm="character", parameters="list", allparameters="list", model="list", finalizers="list"),
                        prototype(conn=NULL, model_id=NA_character_, finalizers=list()),
                        contains=c("VIRTUAL"))

# TODO: make a mode model-specific constructor
.newH2OModel <- function(Class, ..., conn = NULL, model_id = NA_character_, finalizers = list(), linkToGC = FALSE) {
  .newH2OObject(Class, ..., conn=conn,id=model_id,finalizers=finalizers,linkToGC=linkToGC)
}

#' @rdname H2OModel-class
#' @param object an \code{H2OModel} object.
#' @export
setMethod("show", "H2OModel", function(object) {
  o <- object
  model.parts <- .model.parts(o)
  m <- model.parts$m
  cat("Model Details:\n")
  cat("==============\n\n")
  cat(class(o), ": ", o@algorithm, "\n", sep = "")
  cat("Model ID: ", o@model_id, "\n")

  # summary
  print(m$model_summary)

  # if glm, print the coefficeints
  cat("\n")
  if( !is.null(m$coefficients_table) ) print(m$coefficients_table)

  # metrics
  cat("\n")
  if( !is.null(model.parts$tm) ) print(model.parts$tm)
  cat("\n")
  if( !is.null(model.parts$vm) ) print(model.parts$vm)
  cat("\n")
  if( !is.null(model.parts$xm) ) print(model.parts$xm)
})

#'
#' Print the Model Summary
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param ... further arguments to be passed on (currently unimplemented)
#' @export
setMethod("summary", "H2OModel", function(object, ...) {
  o <- object
  model.parts <- .model.parts(o)
  m <- model.parts$m
  cat("Model Details:\n")
  cat("==============\n\n")
  cat(class(o), ": ", o@algorithm, "\n", sep = "")
  cat("Model Key: ", o@model_id, "\n")

  # summary
  print(m$model_summary)

  # metrics
  cat("\n")
  if( !is.null(model.parts$tm) ) print(model.parts$tm)
  cat("\n")
  if( !is.null(model.parts$vm) ) print(model.parts$vm)

  # History
  cat("\n")
  print(h2o.scoreHistory(o))

  # Varimp
  cat("\n")

  # VI could be real, true variable importances or GLM coefficients
  haz_varimp <- !is.null(m$variable_importances) || !is.null(m$standardized_coefficients_magnitude)
  if( haz_varimp ) {
    cat("Variable Importances: (Extract with `h2o.varimp`) \n")
    cat("=================================================\n\n")
    print(h2o.varimp(o))
  }
})

.showMultiMetrics <- function(o, which="Training") {
  arg <- "train"
  if( which == "Validation" ) { arg <- "valid"
  } else if ( which == "Cross-Validation" ) { arg <- "xval" }
  tm <- o@metrics
  cat(which, "Set Metrics: \n")
  cat("=====================\n")
  if( !is.null(tm$description)     )  cat(tm$description, "\n")
  if( !is.null(tm$frame) && !is.null(tm$frame$name) )  cat("\nExtract", tolower(which),"frame with", paste0("`h2o.getFrame(\"",tm$frame$name, "\")`"))
  if( !is.null(tm$MSE)                              )  cat("\nMSE: (Extract with `h2o.mse`)", tm$MSE)
  if( !is.null(tm$r2)                               )  cat("\nR^2: (Extract with `h2o.r2`)", tm$r2)
  if( !is.null(tm$logloss)                          )  cat("\nLogloss: (Extract with `h2o.logloss`)", tm$logloss)
  if( !is.null(tm$AUC)                              )  cat("\nAUC: (Extract with `h2o.auc`)", tm$AUC)
  if( !is.null(tm$Gini)                             )  cat("\nGini: (Extract with `h2o.gini`)", tm$Gini)
  if( !is.null(tm$null_deviance)                    )  cat("\nNull Deviance: (Extract with `h2o.nulldeviance`)", tm$null_deviance)
  if( !is.null(tm$residual_deviance)                )  cat("\nResidual Deviance: (Extract with `h2o.residual_deviance`)", tm$residual_deviance)
  if( !is.null(tm$AIC)                              )  cat("\nAIC: (Extract with `h2o.aic`)", tm$AIC)
  if( !is.null(tm$cm)                               )  { if ( arg != "xval" ) { cat(paste0("\nConfusion Matrix: Extract with `h2o.confusionMatrix(<model>,", arg, "=TRUE)`)\n")); } }
  if( !is.null(tm$cm)                               )  { if ( arg != "xval" ) { cat("=========================================================================\n"); print(data.frame(tm$cm$table)) } }
  if( !is.null(tm$hit_ratio_table)                  )  cat(paste0("\nHit Ratio Table: Extract with `h2o.hit_ratio_table(<model>,", arg, "=TRUE)`\n"))
  if( !is.null(tm$hit_ratio_table)                  )  { cat("=======================================================================\n"); print(h2o.hit_ratio_table(tm$hit_ratio_table)); }
  cat("\n")
  invisible(tm)
}

#' @rdname H2OModel-class
#' @export
setClass("H2OUnknownModel",     contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OBinomialModel",    contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OMultinomialModel", contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2ORegressionModel",  contains="H2OModel")
#'
#' The H2OClusteringModel object.
#'
#' This virtual class represents a clustering model built by H2O.
#'
#' This object has slots for the key, which is a character string that points to the model key existing in the H2O cloud,
#' the data used to build the model (an object of class H2OFrame).
#'
#' @slot conn Object of class \code{H2OConnection}, which is the client object that was passed into the function call.
#' @slot model_id A \code{character} string specifying the key for the model fit in the H2O cloud's key-value store.
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove keys from the H2O key-value store.
#' @slot algorithm A \code{character} string specifying the algorithm that was used to fit the model.
#' @slot parameters A \code{list} containing the parameter settings that were used to fit the model that differ from the defaults.
#' @slot allparameters A \code{list} containing all parameters used to fit the model.
#' @slot model A \code{list} containing the characteristics of the model returned by the algorithm.
#'        \describe{
#'          \item{size }{The number of points in each cluster.}
#'          \item{totss }{Total sum of squared error to grand mean.}
#'          \item{withinss }{A vector of within-cluster sum of squared error.}
#'          \item{tot_withinss }{Total within-cluster sum of squared error.}
#'          \item{betweenss }{Between-cluster sum of squared error.}
#'        }
#' @slot finalizers A \code{list} object containing environments with finalizers that
#'                  remove keys from the H2O key-value store.
#' @export
setClass("H2OClusteringModel",  contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2OAutoEncoderModel", contains="H2OModel")
#' @rdname H2OModel-class
#' @export
setClass("H2ODimReductionModel", contains="H2OModel")

#'
#' Accessor Methods for H2OModel Object
#'
#' Function accessor methods for various H2O output fields.
#'
#' @param object an \linkS4class{H2OModel} class object.
#' @name ModelAccessors
NULL

#' @rdname ModelAccessors
#' @export
setGeneric("getParms", function(object) { standardGeneric("getParms") })
#' @rdname ModelAccessors
#' @export
setMethod("getParms", "H2OModel", function(object) { object@parameters })

#' @rdname ModelAccessors
#' @export
setGeneric("getCenters", function(object) { standardGeneric("getCenters") })
#' @rdname ModelAccessors
#' @export
setGeneric("getCentersStd", function(object) { standardGeneric("getCentersStd") })
#' @rdname ModelAccessors
#' @export
setGeneric("getWithinSS", function(object) { standardGeneric("getWithinSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getTotWithinSS", function(object) { standardGeneric("getTotWithinSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getBetweenSS", function(object) { standardGeneric("getBetweenSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getTotSS", function(object) { standardGeneric("getTotSS") })
#' @rdname ModelAccessors
#' @export
setGeneric("getIterations", function(object) { standardGeneric("getIterations") })
#' @rdname ModelAccessors
#' @export
setGeneric("getClusterSizes", function(object) { standardGeneric("getClusterSizes") })

#' @rdname ModelAccessors
#' @export
setMethod("getCenters", "H2OClusteringModel", function(object) { as.data.frame(object@model$centers)[,-1] })
#' @rdname ModelAccessors
#' @export
setMethod("getCentersStd", "H2OClusteringModel", function(object) { as.data.frame(object@model$centers_std)[,-1] })
#' @rdname ModelAccessors
#' @export
setMethod("getWithinSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$centroid_stats$within_cluster_sum_of_squares })
#' @rdname ModelAccessors
#' @export
setMethod("getTotWithinSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$tot_withinss })
#' @rdname ModelAccessors
#' @export
setMethod("getBetweenSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$betweenss })
#' @rdname ModelAccessors
#' @export
setMethod("getTotSS", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$totss } )
#' @rdname ModelAccessors
#' @export
setMethod("getIterations", "H2OClusteringModel", function(object) { object@model$model_summary$number_of_iterations })
#' @rdname ModelAccessors
#' @export
setMethod("getClusterSizes", "H2OClusteringModel", function(object) { object@model$training_metrics@metrics$centroid_stats$size })

#'
#' The H2OModelMetrics Object.
#'
#' A class for constructing performance measures of H2O models.
#'
#' @aliases H2OModelMetrics
#' @export
setClass("H2OModelMetrics",
         representation(algorithm="character", on_train="logical", on_valid="logical", on_xval="logical", metrics="listOrNull"),
         prototype(algorithm=NA_character_, on_train=FALSE, metrics=NULL),
         contains="VIRTUAL")

#' @rdname H2OModelMetrics-class
#' @param object An \code{H2OModelMetrics} object
#' @export
setMethod("show", "H2OModelMetrics", function(object) {
    cat(class(object), ": ", object@algorithm, "\n", sep="")
    if( object@on_train ) cat("** Reported on training data. **\n")
    if( object@on_valid ) cat("** Reported on validation data. **\n")
    if( object@on_xval ) cat("** Reported on cross-validation data. **\n")
    if( !is.null(object@metrics$description) ) cat("Description: ", object@metrics$description, "\n\n", sep="")
    else                                       cat("\n")
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OUnknownMetrics",     contains="H2OModelMetrics")

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OBinomialMetrics",    contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OBinomialMetrics", function(object) {
    callNextMethod(object)  # call to the super
    cat("MSE:  ", object@metrics$MSE, "\n", sep="")
    cat("R^2:  ", object@metrics$r2, "\n", sep="")
    cat("LogLoss:  ", object@metrics$logloss, "\n", sep="")
    cat("AUC:  ", object@metrics$AUC, "\n", sep="")
    cat("Gini:  ", object@metrics$Gini, "\n", sep="")
    if(object@algorithm == "glm") {
      cat("Null Deviance:  ", object@metrics$null_deviance,"\n", sep="")
      cat("Residual Deviance:  ", object@metrics$residual_deviance,"\n", sep="")
      cat("AIC:  ", object@metrics$AIC,"\n", sep="")
    }
    cat("\n")
    cm <- h2o.confusionMatrix(object)
    if( is.null(cm) ) print(NULL)
    else {
      attr(cm, "header") <- "Confusion Matrix for F1-optimal threshold"
      print(cm)
      cat("\n")
    }
    print(object@metrics$max_criteria_and_metric_scores)
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OMultinomialMetrics", contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OMultinomialMetrics", function(object) {
  if( !is.null(object@metrics) ) {
    callNextMethod(object)  # call super
    if( object@on_train ) .showMultiMetrics(object, "Training")
    if( object@on_valid ) .showMultiMetrics(object, "Validation")
    if( object@on_xval ) .showMultiMetrics(object, "Cross-Validation")
  } else print(NULL)
})
#' @rdname H2OModelMetrics-class
#' @export
setClass("H2ORegressionMetrics",  contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2ORegressionMetrics", function(object) {
  callNextMethod(object)
  cat("MSE:  ", object@metrics$MSE, "\n", sep="")
  cat("R2 :  ", h2o.r2(object), "\n", sep="")
  cat("Mean Residual Deviance :  ", h2o.mean_residual_deviance(object), "\n", sep="")
  null_dev <- h2o.null_deviance(object)
  res_dev  <- h2o.residual_deviance(object)
  null_dof <- h2o.null_dof(object)
  res_dof  <- h2o.residual_dof(object)
  aic      <- h2o.aic(object)
  if( !is.null(null_dev) ) cat("Null Deviance :", null_dev, "\n", sep="")
  if( !is.null(null_dof) ) cat("Null D.o.F. :",   null_dof, "\n", sep="")
  if( !is.null(res_dev ) ) cat("Residual Deviance :", res_dev, "\n", sep="")
  if( !is.null(res_dof ) ) cat("Residual D.o.F. :",   res_dof, "\n", sep="")
  if( !is.null(aic     ) ) cat("AIC :", aic, "\n", sep="")
  cat("\n")
})
#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OClusteringMetrics",  contains="H2OModelMetrics")
#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OClusteringMetrics", function(object) {
  if( !is.null(object@metrics) ) {
    callNextMethod(object)
    m <- object@metrics
    cat("\nTotal Within SS: ", m$tot_withinss)
    cat("\nBetween SS: ", m$betweenss)
    cat("\nTotal SS: ", m$totss, "\n")
    print(m$centroid_stats)
  } else print(NULL)
})

#' @rdname H2OModelMetrics-class
#' @export
setClass("H2OAutoEncoderMetrics", contains="H2OModelMetrics")

#' @rdname H2OModelMetrics-class
#' @export
setMethod("show", "H2OAutoEncoderMetrics", function(object) {
  if( !is.null(object@metrics) ) {
    callNextMethod(object)  # call super
    object@metrics$frame$name <- NULL
    if( object@on_train ) .showMultiMetrics(object, "Training")
    if( object@on_valid ) .showMultiMetrics(object, "Validation")
    if( object@on_xval ) .showMultiMetrics(object, "Cross-Validation")
  } else print(NULL)
})
#' @rdname H2OModelMetrics-class
#' @export
setClass("H2ODimReductionMetrics", contains="H2OModelMetrics")

#' H2O Future Model
#'
#' A class to contain the information for background model jobs.
#' @slot conn an \linkS4class{H2OConnection}
#' @slot job_key a character key representing the identification of the job process.
#' @slot model_id the final identifier for the model
#' @seealso \linkS4class{H2OModel} for the final model types.
#' @export
setClass("H2OModelFuture", representation(conn="H2OConnection", job_key="character", model_id="character"))

#'
#' Describe an H2OFrame object
#'
#' @param object An H2OFrame object.
#' @param cols Logical indicating whether or not to do the str for all columns.
#' @param \dots Extra args
#' @export
str.H2OFrame <- function(object, cols=FALSE, ...) {
  if (length(l <- list(...)) && any("give.length" == names(l)))
    invisible(NextMethod("str", ...))
  else if( !cols ) invisible(NextMethod("str", give.length = FALSE, ...))

  if( cols ) {
    nc <- ncol(object)
    nr <- nrow(object)
    cc <- colnames(object)
    width <- max(nchar(cc))
    df <- as.data.frame(object[1L:10L,])
    isfactor <- as.data.frame(is.factor(object))[,1]
    num.levels <- as.data.frame(h2o.nlevels(object))[,1]
    lvls <- as.data.frame(h2o.levels(object))
    # header statement
    cat("\nH2OFrame '", object@frame_id, "':\t", nr, " obs. of  ", nc, " variable(s)", "\n", sep = "")
    l <- list()
    for( i in 1:nc ) {
      cat("$ ", cc[i], rep(' ', width - max(stats::na.omit(c(0,nchar(cc[i]))))), ": ", sep="")
      first.10.rows <- df[,i]
      if( isfactor[i] ) {
        nl <- num.levels[i]
        lvls.print <- lvls[1L:min(nl,2L),i]
        cat("Factor w/ ", nl, " level(s) ", paste(lvls.print, collapse='","'), "\",..: ", sep="")
        cat(paste(match(first.10.rows, lvls[,i]), collapse=" "), " ...\n", sep="")
      } else
        cat("num ", paste(first.10.rows, collapse=' '), if( nr > 10L ) " ...", "\n", sep="")
    }
  }
}