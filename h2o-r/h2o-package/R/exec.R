#'
#' This is the front-end of the execution interface between R and H2O.
#'
#' The workhorses of this front end are .h2o.unop, .h2o.binop, and .h2o.varop.
#'
#' Together, these three methods handle all of the available operations that can
#' be done with H2OFrame objects (this includes H2OParsedData objects and ASTNode objects).

.h2o.__exec2 <- function(client, expr) {
  destKey = paste(.TEMP_KEY, ".", .pkg.env$temp_count, sep="")
  .pkg.env$temp_count = (.pkg.env$temp_count + 1) %% .RESULT_MAX
  .h2o.__exec2_dest_key(client, expr, destKey)
  # .h2o.__exec2_dest_key(client, expr, .TEMP_KEY)
}

.h2o.__exec2_dest_key <- function(client, expr, destKey) {
  type = tryCatch({ typeof(expr) }, error = function(e) { "expr" })
  if (type != "character")
    expr = deparse(substitute(expr))
  expr = paste(destKey, "=", expr)
  res = .h2o.__remoteSend(client, .h2o.__PAGE_EXEC2, str=expr)
  if(!is.null(res$response$status) && res$response$status == "error") stop("H2O returned an error!")
  res$dest_key = destKey
  return(res)
}

.h2o.unop<-
function(op, x) {
  if(missing(x)) stop("Must specify data set")
  if(!inherits(x, "H2OFrame")) stop(cat("\nData must be an H2O data set. Got ", class(x), "\n"))

  op <- new("ASTOp", type="UnaryOperator", operator=op, infix=FALSE)
  if (inherits(x, "ASTNode")) {
    return(new("ASTNode", root=op, children=list(arg=x)))
  } else if (inherits(x, "H2OParsedData")) {
   type_list <- list("Frame")
   type_defs <- list(x@key)
   names(type_list) <- names(type_defs) <- deparse(substitute(x))
   arg <- new("ASTFrame", s_table=list(types = type_list, defs = type_defs), type="Frame", value=x@key)
   return(new("ASTNode", root=op, children=list(arg=arg)))
  } else {
    stop("Data must be an H2O data set!")
  }
}

.h2o.binop<-
function(op, e1, e2) {

  # Prep the op
  op <- new("ASTApply", op=.op.map[[op]])

  # Prep the LHS
  if (e1 %<i-% "ASTNode")       lhs <- e1
  if (e1 %<i-% "numeric")       lhs <- paste("#", e1, sep = "")
  if (e1 %<i-% "character")     lhs <- depares(substitute(e1))
  if (e1 %<i-% "H2OParsedData") lhs <- paste("$", e1@key, sep = "")
  # TODO: e1 inherits ASTFun ?

  # Prep the RHS
  if (e2 %<i-% "ASTNode")       rhs <- e2
  if (e2 %<i-% "numeric")       rhs <- paste("#", e2, sep = "")
  if (e2 %<i-% "character")     rhs <- depares(substitute(e2))
  if (e2 %<i-% "H2OParsedData") rhs <- paste("$", e2@key, sep = "")
  # TODO: e2 inherits ASTFun ?

  # Return an ASTNode
  new("ASTNode", root=op, children=list(left = lhs, right = rhs))
}

#'
#' Retrieve values from arguments supplied in a function call.
#'
#' Developer Note: If a method takes a function as an argument and
#'                 you wish to pass arguments to that function by the way of `...`
#'                 then you before passing flowing control to .h2o.varop, you MUST
#'                 label the `...` and list it.
#'
#'                   e.g.: Inside of ddply, we have the following "fun_args" pattern:
#'                      .h2o.varop("ddply", .data, vars, .fun, fun_args=list(...), .progress)
.getValueFromArg<-
function(a, name=NULL) {
  if (inherits(a, "H2OParsedData")) {
    a@key
  } else if (inherits(a, "ASTNode")) {
    a
  } else if (class(a) == "function") {
    ret <- .funToAST(a)
    .pkg.env$formals <- names(formals(a))
    ret
  } else if (!is.null(name) && (name == "fun_args")) {
    .toSymbolTable(a, .pkg.env$formals)
  } else {
    res <- eval(a)
    if (is.vector(res)) {
      if (length(res) > 1) {
        return(toJSON(res))
      }
    } else {
      return(deparse(eval(a)))
    }
  }
}

.toSymbolTable<-
function(arguments, free_vars) {
  if (length(free_vars) != length(arguments)) stop("Number of free variables does not match number of arguments.")
  names(arguments) <- free_vars
  new("ASTSymbolTable", symbols=arguments)
}

.toASTArg<-
function(col) {
  new("ASTArg", arg_name=col$arg_names, arg_number=col$arg_numbers, arg_value=col$arg_values, arg_type=col$arg_types)
}

.argsToAST<-
function(...) {
  arg.names <- names(as.list(substitute(list(...)))[-1])
  if ("fun_args" %in% arg.names) {
    arg_names  <- unlist(lapply(as.list(substitute(list(...)))[-1], as.character))
    to_keep <- which(names(arg_names) == "")
    idx_to_change <- which(arg.names != "")
    lapply(seq_along(arg.names),
      function(i) {
        if (arg.names[i] == "") {
          arg.names[i] <<- arg_names[to_keep[1]]
          to_keep <<- to_keep[-1]
        }
      }
    )
    to_keep   <- NULL
    arg_names <- arg.names
    arg_ts <- lapply(list(...), .evalClass)
    arg_ts$fun_args <- "ASTSymbolTable"
    names(arg_ts) <- NULL
    arg_types <- arg_ts
  } else {
    arg_names  <- unlist(lapply(as.list(substitute(list(...)))[-1], as.character))
    arg_types  <- lapply(list(...), .evalClass)
  }
  arg_values <- lapply(seq_along(list(...)), function(i) { .getValueFromArg(list(...)[[i]], names(list(...))[i]) })
  args <- as.data.frame(rbind(arg_names, arg_types, arg_values, arg_numbers = 1:length(arg_names)))
  .pkg.env$formals <- NULL
  names(args) <- paste("Arg", 1:length(arg_names), sep ="")
  unlist(apply(args, 2, .toASTArg))
}

.h2o.varop<-
function(op, ...) {

  ASTargs <- .argsToAST(...)
  op <- new("ASTOp", type="PrefixOperator", operator=op, infix=FALSE)
  new("ASTNode", root=op, children=ASTargs)
}

#'
#' Force the evaluation of an AST
#'
#' This function is never called directly. The object shall never be a phrase!
.force.eval<-
function(client, Last.value, ID, rID = NULL, env = parent.frame()) {
  ret <- ""
  if(length(as.list(substitute(Last.value))) > 1)
    stop(paste("Found phrase: ", substitute(Last.value), ". Illegal usage.", sep = ""))

  Last.value <- ID %<-% Last.value
  expr <- visitor(Last.value)

  # Have H2O evaluate the AST
  res <- .h2o.__remoteSend(client, .h2o.__CASCADE, ast=expr$ast)
  ID <- ifelse(ID == "Last.value", ID, as.character(as.list(match.call())$Last.value))
  if (!is.null(rID)) ID <- rID
  if (!is.null(res$string)) ret <- res$string
  else if (res$result == "") {
    ret <- .h2o.parsedData(client, res$key$name, res$num_rows, res$num_cols, res$col_names)
  } else {
    ret <- res$scalar
  }
  assign(ID, ret, env = env)
}




#cat(toJSON(visitor(h2o.cut(hex[,1], seq(0,1,0.01)))), "\n")


#cat(toJSON(visitor( h2o.ddply(hex, .("Sepal.Length", "Sepal.Width", "Petal.Length"), f, "hello", "from", "ddply") )), "\n")
