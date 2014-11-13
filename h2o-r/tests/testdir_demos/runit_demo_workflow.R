setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

demo_workflow <- function(conn) {
    Log.info("Import small airlines data...")
    iris.hex = as.h2o(conn, iris, key = "iris.hex")
    
    Log.info('Build kmeans model on pedal length and width...')
    my_cols = setdiff(names(iris.hex), "Species")
    iris_model = h2o.kmeans(data = iris.hex, cols = my_cols, centers = 3)
    print(iris_model)
    print(paste('Mean squared error : ', iris_model@model$mse))
    Log.info('Build kmeans model, cheating with species input...')
    iris_model_wSpecies = h2o.kmeans (data = iris.hex, centers = 3)
    print(iris_model_wSpecies)
    print(paste('Mean squared error : ', iris_model_wSpecies@model$mse))
    
    Log.info('Predict on the same iris dataset...')
    pred1 = predict(object = iris_model, newdata = iris.hex)
    pred2 = predict(object = iris_model_wSpecies, newdata = iris.hex)
    
    Log.info('Print confusion matrix...')
    species = iris.hex$Species
    species.R = as.matrix(species)
    
    confusion_matrix <- function(pred){
      assignments = sapply(c(0, 1, 2), function(id) h2o.mode(species[pred == id]))
      foo <- function(x) if(x == assignments[1]) 0 else if(x == assignments[2]) 1 else 2
      species1.R = unlist(lapply(species.R, foo))
      pred.R = as.data.frame(pred)[,1]
      
      cm = matrix(0, nrow = 3, ncol = 3)
      for (i in 1:nrow(species)) {
        row_id = species1.R[i]+1
        col_id = pred.R[i]+1
        cm[row_id, col_id] = cm [row_id, col_id] + 1
      }
      cm = as.data.frame(cm)
      names(cm) = assignments
      row.names(cm) = assignments
      print(cm)
    }
    confusion_matrix(pred1)
    confusion_matrix(pred2)
    testEnd()
}

doTest("Build KMeans model for iris, score and build confusion matrix.", demo_workflow)
