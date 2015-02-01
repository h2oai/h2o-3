#----------------------------------------------------------------------
# Purpose:  This test exercises building GLM  model 
#           for 186K rows and 3.2K columns 
#----------------------------------------------------------------------
    
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R') 

# Check if we are running inside the 0xdata network by seeing if we can touch
# the cdh3 namenode. (we're not using it though..using automount to the nas)
# stay consistent with the check we use for hdfs tests
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.
running_inside_hexdata = file.exists("/mnt/0xcustomer-datasets/c25")

if (! running_inside_hexdata) {
    stop("Not running on 0xdata internal network.")
}

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

test <- function(conn) {

   data.hex <- h2o.importFile(conn, "/mnt/0xcustomer-datasets/c25/df_h2o.csv", header = T)

   colNames = {}
   for(col in names(data.hex)) {
       colName <- if(is.na(as.numeric(col))) col else paste0("C", as.character(col))
       colNames = append(colNames, colName)
   }

   colNames
   names(data.hex) <- colNames

   ## Examine O/L distribution of Leads column
   #leadsCol = data.hex$Leads
   #summary(leadsCol) ## Resulting 5577 to 181377 ratio

   #data.split = h2o.splitFrame(data=data.hex, ratios=0.8)
   #data.train = data.split[[1]]
   #data.valid = data.split[[2]]

   ## Run a gbm with variable importance
   #myY = "Leads"
   myY = "C1"
   myX = setdiff(names(data.hex), myY)


   # Start modeling
   # GLM
   data1.glm <- h2o.glm(x=myX, y=myY, training_frame = data.hex, family="binomial") 
   data1.glm

   #GBM on original dataset
   data1.gbm = h2o.gbm(x = myX, y = myY, training_frame = data.hex,
                    ntrees = 20, max_depth = 10, variable_importance = T)
   data1.gbm 

  #Deep Learning
  data1.dl <- h2o.deeplearning(x=myX, y=myY, training_frame=data.hex)
  data1.dl 

   testEnd()
}
doTest("Testing glm for dataset 186k rows and 3200 columns", test)
