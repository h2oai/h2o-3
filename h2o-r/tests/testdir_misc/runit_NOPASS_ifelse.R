##
# Test: ifelse is added as method for H2OFrame properly
# Description: push a dataset into H2O and convert column using ifelse
##

source('../h2o-runit.R')

test.ifelse <- function() {
  
  Log.info("Importing heart dataset into H2O...")
  heart.hex <- as.h2o(object = heart, "heart.hex")
  Log.info("Change Surgery Column in R using bases' ifelse...")
  heart$surgery <- ifelse(heart$surgery == 0, "N", "Y")
  Log.info("Change Surgery Column in H2O using H2O's ifelse...")
  heart.hex$surgery <- ifelse(heart.hex$surgery == 0, "N", "Y")
  
  if(!all(heart$surgery == as.data.frame(heart.hex$surgery))) stop("Conversion of column different between h2o and base ifelse function!")
  
  testEnd()
}

doTest("R and H2O ifelse Function", test.ifelse)

