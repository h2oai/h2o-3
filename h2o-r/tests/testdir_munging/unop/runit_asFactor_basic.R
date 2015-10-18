


test.as.factor.basic <- function() {
  hex <- h2o.importFile(locate("smalldata/junit/cars.csv"), destination_frame = "cars.hex")

  Log.info("Printing out the head of the cars datasets") 

  print(hex)

  print(hex[, "cylinders"])

  asdf <- hex[,"cylinders"]
  print(asdf)
  
  print(as.factor(hex[,"cylinders"]))

  meow <- as.factor(hex[,"cylinders"])
  print(meow)

  
  hex[,"cylinders"] <- as.factor(hex[,"cylinders"])

  print(hex)


  Log.info("doing the Log.info")


  #print(is.factor(hex[,"cylinders"]))  

  print(hex[,"cylinders"])



  #expect_true(is.factor(hex[,"cylinders"])[1])
  
}

doTest("Test the as.factor unary operator", test.as.factor.basic)

