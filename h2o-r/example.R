# Step 1: gradle build  -or- gradle build -x test (to skip tests)
# Step 2: cd to R/src/contrib and run R CMD INSTALL h2o_0.1.6.99999.tar.gz  (versioning may change)
# Step 3: Start the h2o-app jar by hand
# Step 4: Start an R session and load the h2o package:

library(h2o)

h <- h2o.init()  # connects to the h2o-app

hex <- as.h2o(h, iris)  # loads the R iris data using importFile

hex[,5]  #produces an AST --> this will change to forcing the eval later on

head(hex[,5])  # forces the eval

a <- hex[hex[,5] == "setosa",]  # store the AST into variable a

head(a)  # force eval of a amd store H2OParsedData into a (drops the ast)

print(a)  # prints the head of a


# To see what else will work see ops.R and methods.R -- some are still in progress
