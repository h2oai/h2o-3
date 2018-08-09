library(h2o)
h2o.init()

# Import Airlines training dataset
filePath <- "smalldata/airlines/AirlinesTrain.csv.zip"
airlines.data <- h2o.importFile(h2o:::.h2o.locate(filePath))
# Traing a Gradient Boosting Machine model on the dataset. The model predicts delayed departure best on the travel distance, origin and destination of the flight.
# There are 10 trees built
gbm.model <- h2o.gbm(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 10)

# An example of fetching a single tree from H2O. H2O needs to know about model the tree is fetched from. Then the number of the tree and tree class.
# Trees are numbered starting from 1 to ntrees, where ntrees is the variable in h2o.gbm() function. If not specified, the default number of trees is used.
# Number of classes corresponds to the numbero of categorical levels, if the response variable is categorical. In case of regression, the number is 1.
gbm.tree <- h2o.getModelTree(gbm.model, 1,1)
# Show the structure of the three fetched. The tree contains lists of left and right child nodes. A list with description of each edge in the graph is also present.
# The length of the descriptions list equals to the number of nodes in the tree. For each node's index, there is a description to be found.
# The descriptions consists of categorical levels/thresholds related to the edge leading from node's parent to the node itself.
print(gbm.tree)

# Iterate over the nodes in the tree, printing numbers (identifiers) of nodes and the split rules.
# Each split rule represents the decision path from node's parent. May contain threshold in case numerical column or categorical levels for categorical columns.
for(node_index in 1:length(gbm.tree@left_children)){
  left_child <- gbm.tree@left_children[node_index]
  right_child <- gbm.tree@right_children[node_index]
  node_description <- gbm.tree@descriptions[node_index]
  
  if(left_child == -1 && right_child == -1){
    sprintf("Node %d is a leaf node.", node_index)
    next
  }
  
  cat(sprintf("Left child of node %d has number %d, right child has number %d. \n", node_index, left_child, right_child))
  cat(sprintf("Split rule for node %d is: %s.\n\n", node_index, node_description))
  
}


# An example of fetching all the trees in a model
# First, a list is allocated. Size of the equals the number of trees built in the model.
gbm.trees <- list(gbm.model@parameters$ntrees)
for (tree_number in 1:gbm.model@parameters$ntrees) {
  gbm.trees[[tree_number]] <- h2o.getModelTree(gbm.model,tree_number,1)
}