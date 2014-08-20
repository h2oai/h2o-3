#'
#' A collection of methods to parse expressions and produce ASTs.
#'
#' R expressions convolved with H2O objects evaluate lazily.

#'
#' Retrieve the slot value from the object given its name and return it as a list.
.slots<-
function(name, object) {
  ret <- list(slot(object, name))
  names(ret) <- name
  ret
}

#'
#' Cast an S4 AST object to a list.
#'
#'
#' For each slot in `object`, create a list entry of name "slotName", with value given by the slot.
#'
#' To unpack this information, .ASTToList depends on a secondary helper function `.slots(...)`.
#' Finally, the result of the lapply is unlisted a single level, such that a vector of lists is returned
#' rather than a list of lists. This helps avoids anonymous lists.
.ASTToList<-
function(object) {
  return( unlist(recursive = FALSE, lapply(slotNames(object), .slots, object)))
}


#'
#' The AST visitor method.
#'
#' This method represents a map between an AST S4 object and a regular R list, which is suitable for rjson::toJSON call.
#'
#' Given a node, the `visitor` function recursively "Lisp"-ifies the node's S4 slots and then returns the list.
#'
#' The returned list has two main pieces: the ast to execute and function defintions:
#'
#'  { '_ast' : { ... }, '_funs' : {[ ... ]} }
#'
#' All ASTNodes have children. All nodes with the @root slot has a list in the @children slot that represent operands.
visitor<-
function(node) {
  res <- ""
  if (.hasSlot(node, "root")) {
    res %<p0-% '('
    res %<p0-% node@root@op
    children <- lapply(node@children, visitor)
    for (child in children) res %<p-% child
    res %<p0-% ')'
    list( ast = res)

  } else if (node %<i-% "ASTSeries") {
    res %<p-% node@op
    children <- unlist(lapply(node@children, visitor))
    children <- paste(children, collapse=";",sep="")
    res %<p0-% children
    res %<p0-% "}"
    res
  } else {
    node
  }
}


#'
#' The old AST visitor method.
#'
#' This method represents a map between an AST S4 object and a regular R list,
#' which is suitable for the rjson::toJSON method.
#'
#' Given a node, the `visitor` function recursively "list"-ifies the node's S4 slots and then returns the list.
#'
#' A node that has a "root" slot is an object of type ASTOp. An ASTOp will always have a "children" slot representing
#' its operands. A root node is the most general type of input, while an object of type ASTFrame or ASTNumeric is the
#' most specific. This method relies on the private helper function .ASTToList(...) to map the AST S4 object to a list.
old.visitor<-
function(node) {
  if (.hasSlot(node, "root")) {
    root_values <- .ASTToList(node@root)
    children <- lapply(node@children, visitor)
    root_values$operands <- children
    list(astop = root_values)
  } else if (.hasSlot(node, "statements")) {
    f_name <- node@name
    arguments <- node@arguments
    children <- lapply(node@statements, visitor)

    l <- list(f_name, arguments, children)
    names(l) <- c("alias", "free_variables", "body")
    l
  } else if (.hasSlot(node, "symbols")) {
    l <- .ASTToList(node)
    l$symbols <- node@symbols
    l
  } else if (.hasSlot(node, "arg_value") && .hasSlot(node@arg_value, "root")) {
    l <- .ASTToList(node)
    l$arg_value <- visitor(node@arg_value)
    l
  } else if (.hasSlot(node, "arg_value") && .hasSlot(node@arg_value, "statements")) {
    l <- .ASTToList(node)
    l$arg_value <- visitor(node@arg_value)
    l
  } else if (.hasSlot(node, "arg_value") && .hasSlot(node@arg_value, "symbols")) {
    l <- .ASTToList(node)
    l$arg_value <- node@arg_value@symbols #visitor(node@arg_value)
    l
  } else {
    .ASTToList(node)
  }
}

#'
#' Check if the call is user defined.
#'
#' A call is user defined if its environment is the Global one.
.isUDF<-
function(fun) {
  e <- environment(eval(fun))
  identical(e, .GlobalEnv)
}

#'
#' Check if operator is infix.
#'
#' .INFIX_OPERATORS is defined in cosntants.R. Used by .exprToAST.
.isInfix<-
function(o) {
  o %in% .INFIX_OPERATORS
}

#'
#' Get the class of the object from the envir.
#'
#' The environment is the parent frame
.eval_class<-
function(i, envir) {
  val <- tryCatch(class(get(as.character(i), envir)), error = function(e) {
    tryCatch(class(i), error = function(e) {
      return(NA)
    })
  })
}

#'
#' Helper function to recursively unfurl an expression into a list of statements/exprs/calls/names.
#'
.as_list<-
function(expr) {
  if (is.call(expr)) {
    return(lapply(as.list(expr), .as_list))
  }
  return(expr)
}

#'
#' Check if any item in the expression is an H2OParsedData object.
#'
#' Useful when trying to unravel an expression
.anyH2O<-
function(expr, envir) {
 l <- unlist(recursive = T, lapply(as.list(expr), .as_list))
 a <- any( "H2OParsedData" == unlist(lapply(l, .eval_class, envir)))
 b <- any("H2OFrame" == unlist(lapply(l, .eval_class, envir)))
 any(a | b)
}

#'
#' Check if the expr is in the formals of _any_ method in the call list.
#'
#' It doesn't matter if multiple closures have the same argument names since at execution time
#' the closure will use whatever symbol table it is closest to.
.isFormal<-
function(expr) {
  formals_vec <- function(fun) { names(formals(fun)) }
  expr %in% unlist(lapply(.pkg.env$call_list, formals_vec))
}

#'
#' Walk the R AST directly
#'
.ast.walker<-
function(expr, envir) {
  if (length(expr) == 1) {
    if (is.numeric(expr[[1]])) return('#' %<p0-% (eval(expr[[1]], envir=envir) - 1))
  }
  if (isGeneric(deparse(expr[[1]]))) {

    # Have a vector => ASTSeries
    if ((expr[[1]]) == quote(`c`)) {
    children <- lapply(expr[-1], .ast.walker, envir)
    # ASTSeries single numbers should have no leading '#', so strip it.
    children <- lapply(children, function(x) if (is.character(x)) gsub('#', '', x) else x)
    return(new("ASTSeries", op="{", children = children))
    }
  }

  # Create a new ASTSpan
  if (identical(expr[[1]], quote(`:`))) {
    return(new("ASTNode", root=new("ASTApply", op=":"), children = list('#' %<p0-% (eval(expr[[2]],envir=envir) - 1), '#' %<p0-% (eval(expr[[3]],envir=envir) - 1))))
  }
}

#'
#' Walk the R AST directly.
#'
#' This walks the AST for some arbitrary R expression and produces an "S4"-ified AST.
#'
#' This function has lots of twists and turns mainly for involving h2o S4 objects.
#' We have to "prove" that we can safely eval an expression by showing that the
#' intersection of the classes in the expression with a vector of some of the H2O object types is non empty.
#'
#' The calls to eval here currently redirect to .h2o.binop() and .h2o.unop(). In the future,
#' this call may redirect to .h2o.varop() to handle multiple arg methods.
.exprToAST<-
function(expr) {

  # Assigning to the symbol or this is a symbol appearing in the formals of a UDF. If the latter, tag it.
  if (is.symbol(expr)) {
    sym <- as.character(expr)
    if (.isFormal(sym)) {
      return(new("ASTUnk", key=sym, isFormal=TRUE))
    }
    return(new("ASTUnk", key=as.character(expr), isFormal=FALSE))
  }

  # Got an atomic numeric. Plain old numeric value. #TODO: What happens to logicals?
  if (is.atomic(expr) && class(expr) == "numeric") {
    new("ASTNumeric", type="numeric", value=expr)

  # Got an atomic string. #TODO: What to do with print/cat statements in h2o? Ignore them in UDFs?
  } else if (is.atomic(expr) && class(expr) == "character") {
    new("ASTString", type="character", value=expr)

  # Got a left arrow assignment statement
  } else if (identical(expr[[1]], quote(`<-`))) {
    lhs <- new("ASTUnk", key=as.character(expr[[2]]), isFormal=FALSE)
    rhs <- .exprToAST(expr[[3]])
    op <- new("ASTOp", type="LAAssignOperator", operator="<-", infix=TRUE)
    new("ASTNode", root=op, children=list(left = lhs, right = rhs))

  # Got an equals assignment statement
  } else if (identical(expr[[1]], quote(`=`))) {
    lhs <- new("ASTUnk", key=as.character(expr[[2]]))
    rhs <- .exprToAST(expr[[3]])
    op <- new("ASTOp", type="EQAssignOperator", operator="=", infix=TRUE)
    new("ASTNode", root=op, children=list(left = lhs, right = rhs))

  # Got a named function
  } else if (is.name(expr[[1]])) {

    # The named function is user defined
    if (.isUDF(expr[[1]])) {
      return(.funToAST(expr))
    }
    o <- deparse(expr[[1]])

    # Is the function generic? (see getGenerics() in R)
    if (isGeneric(o)) {

      # Operator is infix?
      if (.isInfix(o)) {

        lhs <- .exprToAST(expr[[2]])
        rhs <- .exprToAST(expr[[3]])

        expr[[2]] <- lhs
        expr[[3]] <- rhs

        # Prove that we have _h2o_ infix:
        if (length( intersect(c("H2OParsedData", "H2OFrame", "ASTNode", "ASTUnk", "ASTFrame", "ASTFrame", "ASTNumeric"), unlist(lapply(expr, .evalClass)))) > 0) {

          # Calls .h2o.binop
          return(eval(expr))
        } else {
          # Regular R infix... recurse down the left and right arguments
          op <- new("ASTOp", type="InfixOperator", operator=as.character(expr[[1]]), infix=TRUE)
          args <- lapply(expr[-1], .exprToAST)
          return(new("ASTNode", root=op, children=args))
        }
      # Function is not infix, but some prefix method. #TODO: Must ensure a _single_ argument here: No .h2o.varop yet!
      } else {

        # Prove that we have _h2o_ prefix:
        if (length( intersect(c("H2OParsedData", "H2OFrame", "ASTNode"), unlist(lapply(expr, .evalClass)))) > 0) {

          # Calls .h2o.unop
          return(eval(expr))
       }
      }
    }

    # Not an R generic, operator is some other R method. Recurse down the arguments.
    op <- new("ASTOp", type="PrefixOperator", operator=as.character(expr[[1]]), infix=FALSE)
    args <- lapply(expr[-1], .exprToAST)
    new("ASTNode", root=op, children=args)

  # Got an H2O object back: Must inherit from H2OFrame or error (NB: ASTNode inherits H2OFrame)
  } else if (is.object(expr)) {
    if (inherits(expr, "ASTNode")) {
      expr
    } else if (inherits(expr, "H2OFrame")) {
      new("ASTFrame", type="Frame", value=expr@key)
    } else {
      stop("Unfamiliar object. Got: ", class(expr), ". This is unimplemented.")
    }
  }
}

#'
#' Helper function for .funToAST
#'
#' Recursively discover other user defined functions and hand them to .funToAST and
#' hand the *real* R expressions over to .exprToAST.
.funToASTHelper<-
function(piece) {
  f_call <- piece[[1]]

  # Check if user defined function
  if (.isUDF(f_call)) {

    if (is.call(piece)) {
      return(.funToAST(piece))
    }

    # Keep a global eye on functions we have definitions for to prevent infinite recursion
    if (! (any(f_call == .pkg.env$call_list)) || is.null(.pkg.env$call_list)) {
      .pkg.env$call_list <- c(.pkg.env$call_list, f_call)
      .funToAST(eval(f_call))
    }
  } else {
    .exprToAST(piece)
  }
}

#'
#' Translate a function's body to an AST.
#'
#' Recursively build an AST from a UDF.
#'
#' This method is the entry point for producing an AST from a closure.
.funToAST<-
function(fun) {
  if (is.call(fun)) {

    res <- tryCatch(eval(fun), error = function(e) {
      FALSE
      }
    )
    # This is a fairly slimey conditional.
#    if (is.object(res)) { return(res) }
    if ( (!is.object(res) && res == FALSE) || (is.object(res)) ) {
      return(.exprToAST(fun[[2]]))
    } else {
      return(.exprToAST(eval(fun)))
    }
  }
  if(is.null(body(fun)) && !(is.call(fun))) fun <- eval(fun)
  if (.isUDF(fun)) {
    .pkg.env$call_list <- c(.pkg.env$call_list, fun)
    l <- as.list(body(fun))

    statements <- lapply(l[-1], .funToASTHelper)
    if (length(l[-1]) == 1) {

      statements <- .funToASTHelper(eval(parse(text=deparse(eval(l[-1])))))
    }
    if (length(statements) == 1 && is.null(statements[[1]])) { return(NULL) }
    .pkg.env$call_list <- NULL
    print(fun)
    arguments <- names(formals(fun))
    if (is.null(formals(fun))) arguments <- "none"
    new("ASTFun", type="UDF", name=deparse(substitute(fun)), statements=statements, arguments=arguments)
  } else {
    substitute(fun)
  }
}
