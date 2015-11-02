# This is a demo of H2O's naive Bayes function
# It imports a data set, parses it, and prints a summary
# Then, it runs naive Bayes with and without laplace smoothing
# Note: This demo runs H2O on localhost:54321
library(h2o)
h2o.init()

# This is a demo of H2O's naive Bayes modeling and prediction with categorical variables
votes.hex = h2o.uploadFile(path = system.file("extdata", "housevotes.csv", package="h2o"), destination_frame = "votes.hex", header = TRUE)
summary(votes.hex)
votes.nb = h2o.naiveBayes(x = 2:17, y = 1, training_frame = votes.hex, laplace = 3)
print(votes.nb)
votes.pred = predict(votes.nb, votes.hex)
head(votes.pred)

# This is a demo of H2O's naive Bayes with continuous predictors
iris.hex = h2o.uploadFile(path = system.file("extdata", "iris.csv", package="h2o"), destination_frame = "iris.hex")
summary(iris.hex)
iris.nb = h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex)
print(iris.nb)
