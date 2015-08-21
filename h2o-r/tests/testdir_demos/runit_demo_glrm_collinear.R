setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Helper functions for testing collinearity between columns
collinear_cols_r <- function(y) {
  ee <- eigen(crossprod(y), symmetric = TRUE)
  collinear_cols(ee$values, ee$vectors)
}

collinear_cols <- function(values, vectors) {
  evals <- zapsmall(values)
  evecs <- split(zapsmall(vectors), col(vectors))  # Split eigenvector matrix into list by columns
  
  # For eigenvalues equal to zero, list non-zero eigenvector components
  res <- mapply(function(val, vec) { if (val != 0) NULL else which(vec != 0) }, evals, evecs)
  res[!sapply(res, is.null)]
}

correlated_cols <- function(y, threshold = 0.95) {
  y_cor <- cor(y)
  which(abs(y_cor) >= threshold & lower.tri(y_cor), arr.ind = TRUE)
}

# Helper functions for printing out collinear columns
collapse_list <- function(x) {
  if(is.null(x) || length(x) == 0) return("None")
  x_uniq <- unique(x)
  pairs <- lapply(x_uniq, function(row) { paste0("(", paste(row, collapse = ","), ")") })
  paste(pairs, collapse = " ")
}

collapse_mat <- function(x) {
  if(is.null(x) || nrow(x) == 0) return("None")
  x_uniq <- unique(x)
  pairs <- apply(x_uniq, 1, function(row) { paste0("(", paste(row, collapse = ","), ")") })
  paste(pairs, collapse = " ")
}

# Generate a standard normal matrix with collinear columns
gen_collinear_mat <- function(m, n, ndep = 0, shuffle = TRUE) {
  if(n <= ndep) stop("n must be strictly greater than ", ndep)
  nind <- n-ndep
  mat_ind <- matrix(rnorm(m*nind), nrow = m, ncol = nind)
  if(ndep == 0) return(mat_ind)
  
  # Generate collinear columns using matrix of weights on independent columns
  dep <- matrix(0, nrow = nind, ncol = ndep)
  for(i in 1:ndep) {
    idx <- sample(1:nind, size = sample(1:i,1))
    dep[idx,i] <- rnorm(length(idx))
  }
  mat_dep <- mat_ind %*% dep
  
  mat <- cbind(mat_ind, mat_dep)
  if(shuffle) mat <- mat[,sample(1:n,n)]    # Shuffle columns (weights applies to unshuffled data)
  return(list(data = mat, weights = dep))
}

test.glrm.collinear <- function(conn) {
  m <- 1000; n <- 25; ndep <- 5; k <- 20
  Log.info(paste("Uploading random collinear matrix with rows =", m, "and cols =", n))
  M <- gen_collinear_mat(m, n, ndep)
  train <- M$data
  train.h2o <- as.h2o(conn, train)
  
  Log.info(paste("Run GLRM with k =", k, "and quadratic loss"))
  fitH2O <- h2o.glrm(train.h2o, k = k, init = "PlusPlus", loss = "Quadratic", regularization_x = "None", regularization_y = "None", min_step_size = 1e-5)
  Log.info(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))
  fitY <- as.matrix(fitH2O@model$archetypes)
  
  # Note: This method is slower (SVD) and doesn't identify subsets of collinear columns
  Log.info("Identify collinear columns from eigenvectors and eigenvalues")
  train_ccol  <- collinear_cols_r(train)
  fitY_ccol   <- collinear_cols_r(fitY)
  Log.info(paste("Collinear columns of training data:", collapse_list(train_ccol)))
  Log.info(paste("Collinear columns of Y (R SVD)    :", collapse_list(fitY_ccol)))
  expect_equivalent(train_ccol, fitY_ccol)
  
  # Note: This method only identifies pairwise correlation between columns and misses some dependencies
  threshold <- 0.95
  Log.info(paste("Identify pairs of columns with correlation >=", threshold, "from correlation matrix"))
  train_ccor <- correlated_cols(train, threshold)
  fitY_ccor  <- correlated_cols(fitY, threshold)
  Log.info(paste("Correlated columns of training data:", collapse_mat(train_ccor)))
  Log.info(paste("Correlated columns of Y (R cor)    :", collapse_mat(fitY_ccor)))
  expect_equivalent(train_ccor, fitY_ccor)
  testEnd()
}

doTest("GLRM Test: Detect Collinear Columns", test.glrm.collinear)
