#----------------------------------------------------------------------
# Purpose: Create random data and run randomized SVD on it.
# Compare to timings for R's SVD on same data (#R#)
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')
options(echo=TRUE)

heading("BEGIN TEST")
check.demo_random_svd <- function() {
  
  # Data frame size
  rows <- 1e4
  cols <- c(100,200,500)
  vecs <- c(5,10,20)
  
  create_frm_time <- matrix(NA, nrow = length(vecs), ncol = length(cols))
  frm_size <- matrix(NA, nrow=length(vecs), ncol = length(cols))
  algo_run_time <- matrix(NA, nrow = length(vecs), ncol = length(cols))
  #R# algo_run_time_R = matrix(NA, nrow = length(vecs), ncol = length(cols))
  #R# algo_run_time_cmp = matrix(NA, nrow = length(vecs), ncol = length(cols))
  
  col_grid <- rep(NA,length(cols))
  vec_grid <- rep(NA,length(vecs))
  names <- c()
  
  for(j in 1:length(cols)) {
    ncols <- cols[j]
    col_grid[j] <- ncols
    for(k in 1:length(vecs)) {
      nvecs <- vecs[k]
      vec_grid[k] <- nvecs
      print(paste('Rows:', rows))
      print(paste('Cols:', cols[j]))
      print(paste('Vecs:', vecs[k]))
      names <- c(names, ncols * nvecs)    # set the name to be the problem size
      
      sst <- system.time(myframe <- h2o.createFrame('myframe', rows = rows, cols = ncols, seed = SEED, 
                                                    randomize = TRUE, real_range = 100, categorical_fraction = 0.0, 
                                                    integer_fraction = 0.4, integer_range = 100, 
                                                    missing_fraction = 0, has_response = FALSE) )
      
      create_frm_time[k,j] = as.numeric(sst[3])
      mem <- h2o.ls()
      frm_size[k,j] <- as.numeric(mem[1,1])
      head(myframe)

      aat <- system.time(myframe.svd <- h2o.svd(training_frame=myframe, nv=nvecs, svd_method="Randomized"))
      algo_run_time[k,j] <- aat[3]
      print(myframe.svd)
      
      # Run R's SVD on same data
      #R# myframe.R <- as.data.frame(myframe)
      #R# aat.R = system.time(myframe.Rsvd <- svd(myframe.R, nv=nvecs))
      #R# algo_run_time_R[k,j] = aat.R[3]
      
      # Record differences between R and h2o
      #R# algo_run_time_cmp[k,j] = algo_run_time[k,j] - algo_run_time_R[k,j]
    }
  }
  myframe <- NULL
  gc()
  h2o.rm("myframe")
  
  # format timing data from h2o
  data <- algo_run_time
  dimnames(data) <- list(vec_grid,col_grid)
  #R# dimnames(algo_run_time_cmp) <- list(vec_grid,col_grid)
  
  # format timing data from R
  #R# data_R = algo_run_time_R
  #R# dimnames(data_R)<-list(row_grid,col_grid)
  
  # Report timing results
  print("H2O Runtime:")
  print(data)
  #R# print("R Runtime:")
  #R# data_R
  
  # Report comparision, relative to h2o (h2o - R)
  #R# print("(H2O - R) Runtime:")
  #R# print(algo_run_time_cmp)
  
  
}

doTest("Randomized subspace iteration SVD using random data", check.demo_random_svd)
