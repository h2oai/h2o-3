library(h2o)
h2o.init

hex <- as.h2o(iris, destination_frame = "iris")
k <- 3
setSeed <- 148008988978

print('Build kmeans model on petal length and width...')
iris_model <- h2o.kmeans(training_frame = hex, x = 1:4, k = k, seed = setSeed)
print(iris_model)
print(paste('Mean squared error : ', iris_model@model$mse))
print('Build kmeans model, cheating with species input...')
# expect_error(h2o.kmeans (training_frame = hex, x = 1:5, k = k, seed = setSeed))
iris_model_wSpecies <- h2o.kmeans (training_frame = hex, x = 1:5, k = k, seed = setSeed)
print(iris_model_wSpecies)
print(paste('Mean squared error : ', iris_model_wSpecies@model$mse))

print('Predict on the same iris dataset...')
pred1.R <- as.data.frame(predict(object = iris_model, newdata = hex))
# pred2.R <- as.data.frame(predict(object = iris_model_wSpecies, newdata = hex))

print('Print confusion matrix...')
species.R <- iris$Species

Mode <- function(x) {
      ux <- unique(x)
      ux[which.max(tabulate(match(x, ux)))]
}

confusion_matrix <- function(pred){
      assignments <- sapply(c(0, 1, 2), function(id) Mode(species.R[pred == id]))
      foo <- function(x) {
                if(x == assignments[1]) 0
                else if(x == assignments[2]) 1
                else 2
              }
      species1.R <- unlist(lapply(species.R, foo))

      cm <- matrix(0, nrow = 3, ncol = 3)
      for (i in 1:length(species.R)) {
        row_id <- species1.R[i]+1
        col_id <- pred[,1][i]+1
        cm[row_id, col_id] <- cm[row_id, col_id] + 1
      }
      cm <- as.data.frame(cm)
      names(cm) <- assignments
      row.names(cm) <- assignments
      print(cm)
}
confusion_matrix(pred1.R)
# confusion_matrix(pred2.R)