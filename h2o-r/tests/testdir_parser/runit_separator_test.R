
#Read in small datasets with uncommon delimiters to see how it is parsed:

test.separator <- function(){
  #Define file paths to test files of particular delimiters
  path.tab = "smalldata/parser/tabs.tsv"
  path.pipe = "smalldata/parser/pipes.psv"
  path.hive = "smalldata/parser/test.hive"
  path.semi = "smalldata/parser/semi.scsv"
  path.caret = "smalldata/parser/caret.csv"
  path.asterisk = "smalldata/parser/asterisk.asv"
  
  #Tab delimited
  tab.test = h2o.importFile(path = normalizePath(locate(path.tab)), destination_frame = "tab.hex",sep = "\t")
  expect_that(dim(tab.test), equals(c(3,3)))
  
  #Pipe delimited
  pipe.test = h2o.importFile(path = normalizePath(locate(path.pipe)), destination_frame = "pipe.hex",sep = "|")
  expect_that(dim(pipe.test), equals(c(3,3)))
  
  #Hive delimited
  hive.test = h2o.importFile(path = normalizePath(locate(path.hive)), destination_frame = "hive.hex",sep = "\001")
  expect_that(dim(hive.test), equals(c(3,3)))
  
  #Semi colon delimited
  semi.test = h2o.importFile(path = normalizePath(locate(path.semi)), destination_frame = "semi.hex",sep = ";")
  expect_that(dim(semi.test), equals(c(3,3)))
  
  #Caret delimited
  caret.test = h2o.importFile(path = normalizePath(locate(path.caret)), destination_frame = "caret.hex",sep = "^")
  expect_that(dim(caret.test), equals(c(3,3)))
  
  #Asterisk delimited
  asterisk.test = h2o.importFile(path = normalizePath(locate(path.asterisk)), destination_frame = "asterisk.hex",sep = "*")
  expect_that(dim(asterisk.test), equals(c(3,3)))
}

doTest("Separator Test", test.separator)
