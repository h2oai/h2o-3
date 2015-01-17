#----------------------------------------------------------------------
# Purpose:  Create random data and run 20 iterations of GLM on it.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')
options(echo=TRUE)

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

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
  nrow <- rows[i] 
  row_grid[i] <- nrow 
  for(j in 1:length(cols) ){ # changing number of columns 
    ncol <- cols[j] 
    col_grid[j] <- ncol 
    names <- c(names, nrow * ncol) # set the name to be the problem size 
    sst <- system.time(myframe <- h2o.createFrame(conn, 'myframe', rows = nrow, cols = ncol,
                                                 seed = 12345, randomize = T, value = 0, real_range = 100, 
                                                 categorical_fraction = 0.0, factors = 10, 
                                                 integer_fraction = 0.4, integer_range = 100, 
                                                 missing_fraction = 0, response_factors = 1, has_response = TRUE) ) 
    create_frm_time[i,j] <- as.numeric(sst[3]) 
    mem <- h2o.ls(conn,"myframe") 
    frm_size[i,j] <- as.numeric(mem[2]) 
    head(myframe) 
    #str(myframe) 
   
    #warmup
    myframe.glm<-h2o.glm(x=seq(2,ncol+1),y=1,training_frame=myframe,family="gaussian",lambda_search=F,iter_max=1)

    aat <- system.time(myframe.glm<-h2o.glm(x=seq(2,ncol+1),y=1,training_frame=myframe,family="gaussian",lambda_search=F,iter.max=1) ) 
    algo_run_time[i,j] <- aat[3] 
  } 
} 
myframe <- NULL 
gc() 
h2o.rm(conn,"myframe") 
#col_grid 
#row_grid 
#create_frm_time 
#algo_run_time 
frm_size/2^20 #MB 
plot(frm_size[1:3]) 
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

PASS_BANNER()
