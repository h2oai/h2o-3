setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.as.h2o.destination_frame <- function() {
  
  #default
  dummy_list <- list(1, 2, 3)
  #with dest 
  converted <- as.h2o(dummy_list, "list_dest")
  expect_equal(attr(converted, 'id'), "list_dest")
  #no dest 
  converted <- as.h2o(dummy_list)
  expect_match(attr(converted, 'id'), "^dummy_list_\\w+$")
  
  #default no var
  #with dest 
  converted <- as.h2o(list(1, 2, 3), "novar_dest")
  expect_equal(attr(converted, 'id'), "novar_dest")
  #no dest   
  converted <- as.h2o(list(1, 2, 3))
  expect_match(attr(converted, 'id'), "^list_\\w+$")
  
  #data.frame
  #with dest 
  converted <- as.h2o(iris, "iris_dest")
  expect_equal(attr(converted, 'id'), "iris_dest")
  #no dest 
  converted <- as.h2o(iris)
  expect_match(attr(converted, 'id'), "^iris_\\w+$")
  
  #H2OFrame
  dummy_h2oframe <- as.h2o(list(1, 2, 3))
  #with dest 
  converted <- as.h2o(dummy_h2oframe, "h2oframe_dest")
  expect_equal(attr(converted, 'id'), "h2oframe_dest")
  #no dest 
  converted <- as.h2o(dummy_h2oframe)
  expect_match(attr(converted, 'id'), "^dummy_h2oframe_\\w+$")
  
  #Matrix
  dummy_matrix <- Matrix::Matrix(c(1, 2, 3, 4, 5, 6), nrow=2)
  #with dest 
  converted <- as.h2o(dummy_matrix, "matrix_dest")
  expect_equal(attr(converted, 'id'), "matrix_dest")
  #no dest 
  converted <- as.h2o(dummy_matrix)
  expect_match(attr(converted, 'id'), "^dummy_matrix_\\w+$")
  
}

doTest("Test as.h2o methods with/without destination frame", test.as.h2o.destination_frame)

