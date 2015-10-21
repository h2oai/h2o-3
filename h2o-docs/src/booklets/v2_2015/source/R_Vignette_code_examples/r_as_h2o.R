# Import the iris data into H2O
data(iris)
iris

# Converts R object "iris" into H2O object "iris.hex"
iris.hex = as.h2o(iris, destination_frame= "iris.hex")

head(iris.hex)
