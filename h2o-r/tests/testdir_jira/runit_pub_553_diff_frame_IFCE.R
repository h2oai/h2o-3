


test.ifce<- function() {

  hex <- as.h2o(iris)
  zhex <- hex - hex
  # h2o.exec(zhex <- hex - hex)
  
  
}

doTest("test ifce", test.ifce)
