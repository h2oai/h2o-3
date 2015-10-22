#----------------------------------------------------------------------
# Purpose:  Create random data and run 20 iterations of GLM on it.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')
options(echo=TRUE)

heading("BEGIN TEST")
check.demo_random_glm <- function() {

  # Data frame size 
  rows <- c(1e4,1e5) 
  cols <- c(1e1,1e2) 
  #rows = c(1e4,1e6) 
  #cols = c(1e1,1e2,300,400,1e3) 
  rows 
  cols 

  create_frm_time <- matrix(NA, nrow = length(rows), ncol = length(cols)) 
  frm_size <- matrix(NA, nrow=length(rows), ncol = length(cols)) 
  algo_run_time <- matrix(NA, nrow = length(rows), ncol = length(cols)) 
  col_grid <- rep(NA,length(cols)) 
  row_grid <- rep(NA,length(rows)) 
  names <- c() 


  for(i in 1:length(rows)){ # changing number of rows 
    nrows <- rows[i] 
    row_grid[i] <- nrows 
    for(j in 1:length(cols) ){ # changing number of columns 
      print(paste("Rows:", rows[i]))
      print(paste("Cols:", cols[j]))
      ncols <- cols[j] 
      col_grid[j] <- ncols 
      names <- c(names, nrows * ncols) # set the name to be the problem size 
      print("frame")
      sst <- system.time(myframe <- h2o.createFrame( 'myframe', rows = nrows, cols = ncols,
                                                   seed = 12345, randomize = T, value = 0, real_range = 100, 
                                                   categorical_fraction = 0.0, factors = 10, 
                                                   integer_fraction = 0.4, integer_range = 100, 
                                                   missing_fraction = 0, response_factors = 1, has_response = TRUE) ) 
      create_frm_time[i,j] <- as.numeric(sst[3]) 
      # mem <- h2o.ls("myframe") 
      # frm_size[i,j] <- as.numeric(mem[2]) 
      # head(myframe) 
      #str(myframe)  
      #warmup
      print("first")
      myframe.glm<-h2o.glm(x=seq(2,ncols+1),y=1,training_frame=myframe,family="gaussian",lambda_search=F,max_iterations=1)
      print(i)
        myframe.glm<-h2o.glm(x=seq(2,ncols+1),y=1,training_frame=myframe,family="gaussian",lambda_search=F,max_iterations=1)
      print("second")
      # algo_run_time[i,j] <- aat[3] 
    } 
  } 
  myframe <- NULL 
  gc() 
  h2o.rm("myframe") 
  #col_grid 
  #row_grid 
  #create_frm_time 
  #algo_run_time 
  #frm_size/2^20 #MB 
  #plot(frm_size[1:3]) 
  #col_grid 
  #row_grid 

  data <- algo_run_time 
  dimnames(data)<-list(row_grid,col_grid) 

  # Report timing results 
  data 


  # Visualization 
  #library(rgl) 
  #open3d() 
  #cf3d.dat <- data.frame(cols=as.vector(col(data)), rows=as.vector(row(data)), value=as.vector(data)) 
  #plot3d(cf3d.dat$cols,cf3d.dat$rows,cf3d.dat$value,type='h',lwd=3,xlab="col",ylab="row",zlab="",) 
  #text3d(cf3d.dat$cols,cf3d.dat$rows,cf3d.dat$value,texts=sprintf("%.2f",cf3d.dat$value)) 

  
}

doTest("20 iteration of GLM using random data", check.demo_random_glm)
