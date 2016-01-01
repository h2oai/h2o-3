setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.string.manipulation <- function() {
  s1 <- as.character(as.h2o(" this is a string "))
  s2 <- as.character(as.h2o("this is another string"))
  s3 <- as.character(as.h2o("this is a longer string"))
  s4 <- as.character(as.h2o("this is tall, this is taller"))

  h2oTest.logInfo("Single and all substitutions...")
  s4 <- h2o.sub("this", "that", s4)
  print(s4)
  expect_identical(s4[1,1], "that is tall, this is taller")
  s4 <- h2o.gsub("tall", "fast", s4)
  print(s4)
  expect_identical(s4[1,1], "that is fast, this is faster")

  h2oTest.logInfo("Trimming...")
  print(s1[1,1])
  expect_identical(s1[1,1], " this is a string ")
  s1 <- h2o.trim(s1)
  expect_identical(as.character(s1[1,1]), "this is a string")

  ds <- h2o.rbind(s1, s2, s3)
  print(ds)
  #splits <- h2o.strsplit(ds, " ")
  #print(splits)
  #expect_equal(ncol(splits), 5)

  
}

h2oTest.doTest("Testing Various String Manipulations", test.string.manipulation)
