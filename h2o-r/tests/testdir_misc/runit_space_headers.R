setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.bad.headers <- function() {
  citibikePath <- locate("smalldata/jira/citibike_head.csv")
  # summary use to fail on datasets that had spaces in the headers during import.
  f <- h2o.importFile(citibikePath , destination_frame = "citibike.hex")  
  f.df <- read.csv(citibikePath)

  print(colnames(f))

  print(f$starttime)

  r.median <- median(f.df$start.station.id)

  if(!is.Frame(f[,"start station id"])) stop("Didn't subset column correctly")

  h2o.median <- median(f[,"start station id"])
  h2o.median <- as.data.frame(h2o.median)[1,1] 
   
  if(h2o.median != r.median) stop("Medians in R and H2O unequal!!")

  testEnd()
}

doTest("Run summary on dataset with spaces : ", test.bad.headers)
