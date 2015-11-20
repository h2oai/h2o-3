##
# Parse airlines_all
##




# setupRandomSeed(1994831827)

test <- function() {
	hex = h2o.importFile(locate("bigdata/server/airlines_all.csv"), "hex")
  print(hex)
      
    
}

doTest("Parse 2008 airlines dataset from NAS", test)

