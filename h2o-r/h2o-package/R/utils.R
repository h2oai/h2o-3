#"
#" A collection of utility methods for the H2O R package.
#"

#"
#" Get the job key from a job
#"
.get.job <- function(j) j$job$key$name

#"
#" Get the destination key from a job
#"
.get.dest <- function(j) j$job$dest$name

#"
#" Get the key or AST
#"
#" Key points to a bonified object in the H2O cluster
.get <- function(H2OFrame) {
  if(.is.eval(H2OFrame))
    paste0('%', H2OFrame@key)
  else
    H2OFrame@ast
}

#"
#" Check if key points to bonified object in H2O cluster.
#"
.is.eval <- function(H2OFrame) {
  key <- H2OFrame@key
  res <- .h2o.__remoteSend(.retrieveH2O(parent.frame()), paste0(.h2o.__RAPIDS, "/isEval"), ast_key=key)
  res$evaluated
}

#"
#" Cache Frame information on the client side: rows, cols, colnames
#"
.fill <- function(conn, key) {
  res <- .h2o.__remoteSend(conn, .h2o.__RAPIDS, ast=paste0("(%", key, ")"))
  cnames <- if( is.null(res$col_names) ) NA_character_ else res$col_names
  .h2o.parsedData(conn, key, res$num_rows, res$num_cols, cnames)
}

#"
#" Get the raw JSON of a model
#"
.model.view <- function(dest) .h2o.__remoteSend(conn, method="GET", paste0(.h2o.__MODELS, "/", dest))