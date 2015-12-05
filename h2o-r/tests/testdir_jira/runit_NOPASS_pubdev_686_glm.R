##
# Test for JIRA PUBDEV-686 
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function(conn) {
  
  print("Read allyears2k_headers.zip into R.")
  data.hex <-  h2o.importFile(conn, locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="airlines.data")
  
  s = h2o.runif(data.hex)
  train = data.hex[s <= 0.8,]
  valid = data.hex[s > 0.8,]

  myY = "IsDepDelayed"
  myX = setdiff(names(data.hex), myY)

  # GLM
  mdl.glm <- h2o.glm(x = myX, y = myY, training_frame = data.hex, validation_frame = valid, family = "gaussian")
  mdl.glm  

  testEnd()
}

doTest("GLM PUBDEV-686", test)
