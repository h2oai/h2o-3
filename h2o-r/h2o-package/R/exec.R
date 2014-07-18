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

  #TODO: REMOVE this VERY BAD preprocessing step --> DOESN'T ACCOUNT FOR NON-COMMUTING OPS!!!!!
  if (inherits(e1, "ASTNumeric")) {
    e1 <- e1@value
  } else if (inherits(e1, "ASTFrame")) {
    e1 <- new("H2OParsedData", key=e1@value)
  } else if (inherits(e2, "ASTNumeric")) {
    e2 <- e2@value
  } else if (inherits(e2, "ASTFrame")) {
    e2 <- new("H2OParsedData", key=e2@value)
  }

  if (inherits(e1, "ASTUnk") || inherits(e2, "ASTUnk")) {
    if (!inherits(e1, "ASTUnk")) {
      tmp <- e1
      e1 <- e2
      e2 <- tmp
    }
  }

  if (inherits(e1, "ASTFun") || inherits(e2, "ASTFun")) {
    if (!inherits(e1, "ASTFun")) {
      tmp <- e1
      e1 <- e2
      e2 <- tmp
    }
  }

  #Case 1: l: ASTOp & r: Numeric
  if (inherits(e1, "ASTNode") && inherits(e2, "numeric")) {
    lhs <- e1
    rhs <-  new("ASTNumeric", type="numeric", value=e2)  #s_table=list(types = type_list, defs = type_defs)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 2: l: ASTOp & r: ASTOp
  } else if (inherits(e1, "ASTNode") && inherits(e2, "ASTNode")) {
    lhs <- e1
    rhs <- e2
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 3: l: ASTOp & r: H2OParsedData
  } else if (inherits(e1, "ASTNode") && inherits(e2, "H2OParsedData")) {
    lhs <- e1
    rhs <- new("ASTFrame", type="Frame", value=e2@key)  #  s_table=list(types = type_list, defs = type_defs)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 4: l: H2OParsedData & r: Numeric
  } else if (inherits(e1, "H2OParsedData") && inherits(e2, "numeric")) {
    lhs <- new("ASTFrame", type="Frame", value=e1@key) # s_table=list(types = type_list, defs = type_defs)
    rhs <- new("ASTNumeric", type="Numeric", value=e2) # s_table=list(types = type_list, defs = type_defs)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 5: l: H2OParsedData & r: ASTOp
  } else if (inherits(e1, "H2OParsedData") && inherits(e2, "ASTNode")) {
    lhs <- new("ASTFrame", type="Frame", value=e1@key) # s_table=list(types = type_list, defs = type_defs)
    rhs <- e2
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 6: l: H2OParsedData & r: H2OParsedData
  } else if (inherits(e1, "H2OParsedData") && inherits(e2, "H2OParsedData")) {
    lhs <- new("ASTFrame", type="Frame", value=e1@key) #s_table=list(types = type_list, defs = type_defs),
    rhs <- new("ASTFrame", type="Frame", value=e2@key) #s_table=list(types = type_list, defs = type_defs),
    op  <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 7: l: Numeric & r: ASTOp
  } else if (inherits(e1, "numeric") && inherits(e2, "ASTNode")) {
    lhs <- new("ASTNumeric", type="numeric", value=e1) #s_table=list(types = type_list, defs = type_defs),
    rhs <- e2
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 8: l: Numeric & r: H2OParsedData
  } else if (inherits(e1, "numeric") && inherits(e2, "H2OParsedData")) {
    lhs <- new("ASTNumeric", type="numeric", value=e1) #s_table=list(types = type_list, defs = type_defs)
    rhs <- new("ASTFrame", type="Frame", value=e2@key) #s_table=list(types = type_list, defs = type_defs)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))


  #Case 9: l: ASTUnk & r: ASTOp
  } else if (inherits(e1, "ASTUnk") && inherits(e2, "ASTNode")) {
    lhs <- e1 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    rhs <- e2 #new("ASTFrame", s_table=list(types = type_list, defs = type_defs), type="Frame", value=e2@key)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))


  #Case 10: l: ASTUnk & r: H2OParsedData
  } else if (inherits(e1, "ASTUnk") && inherits(e2, "H2OParsedData")) {
    lhs <- e1 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    rhs <- new("ASTFrame", type="Frame", value=e2@key) #s_table=list(types = type_list, defs = type_defs),
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))


  #Case 11: l: ASTUnk & r: numeric
  } else if (inherits(e1, "ASTUnk") && inherits(e2, "numeric")) {
    lhs <- e1 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    rhs <- new("ASTNumeric", type="numeric", value=e2) #s_table=list(types = type_list, defs = type_defs),
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))


  #Case 12: l: ASTUnk & r: ASTUnk
  } else if (inherits(e1, "ASTUnk") && inherits(e2, "ASTUnk")) {
    lhs <- e1 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    rhs <- e2 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 13: l: ASTFun & r: ASTOp
  } else if (inherits(e1, "ASTFun") && inherits(e2, "ASTNode")) {
    lhs <- e1 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    rhs <- e2 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 14: l: ASTFun & r: H2OParsedData
  } else if (inherits(e1, "ASTFun") && inherits(e2, "H2OParsedData")) {
    lhs <- e1 #new("ASTNumeric", s_table=list(types = type_list, defs = type_defs), type="numeric", value=e1)
    rhs <- new("ASTFrame", type="Frame", value=e2@key)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 15: l: ASTFun & r: numeric
  } else if (inherits(e1, "ASTFun") && inherits(e2, "numeric")) {
    lhs <- e1
    rhs <- new("ASTNumeric", type="numeric", value=e2)
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 16: l: ASTFun & r: ASTUnk
  } else if (inherits(e1, "ASTFun") && inherits(e2, "ASTUnk")) {
    lhs <- e1
    rhs <- e2
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))

  #Case 17: l: ASTFun & r: ASTFun
  } else if (inherits(e1, "ASTFun") && inherits(e2, "numeric")) {
    lhs <- e1
    rhs <- e2
    op <- new("ASTOp", type="BinaryOperator", operator=op, infix=TRUE)
    return(new("ASTNode", root=op, children=list(left = lhs, right = rhs)))
  }
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

.force.eval<-
function(client, object) {
  expr <- as.character(toJSON(visitor(object)))
  res <- .h2o.__remoteSend(client, .h2o.__PAGE_EXEC3, ast=expr)
  if(!is.null(res$response$status) && res$response$status == "error") stop("H2O returned an error!")
  res$dest_key = destKey
  return(res)
}


#cat(toJSON(visitor(h2o.cut(hex[,1], seq(0,1,0.01)))), "\n")


#cat(toJSON(visitor( h2o.ddply(hex, .("Sepal.Length", "Sepal.Width", "Petal.Length"), f, "hello", "from", "ddply") )), "\n")
