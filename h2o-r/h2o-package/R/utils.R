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
.get <- function(h2o.frame) {
  if(.is.eval(h2o.frame)) return('$' %p0% h2o.frame@key)
  h2o.frame@ast
}

#"
#" Check if key points to bonified object in H2O cluster.
#"
.is.eval <- function(h2o.frame) {
  key <- h2o.frame@key
  res <- .h2o.__remoteSend(.retrieveH2O(parent.frame()), .h2o.__RAPIDS %p0% "/isEval", ast_key=key)
  res$evaluated
}

#"
#" Cache Frame information on the client side: rows, cols, colnames
#"
.fill <- function(h2o, key) {
  res <- .h2o.__remoteSend(h2o, .h2o.__RAPIDS, ast="($" %p0% key %p0% ")")
  .h2o.parsedData(h2o, key, res$num_rows, res$num_cols, res$col_names)
}

#"
#" Get the raw JSON of a model
#"
.model.view <- function(dest) .h2o.__remoteSend(client, method="GET",  .h2o.__MODELS %p0% "/" %p0% dest)