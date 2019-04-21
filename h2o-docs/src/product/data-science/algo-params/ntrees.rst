``ntrees``
----------

- Available in: GBM, DRF, XGBoost, Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

For tree-based algorithms, this option specifies the number of trees to build in the model. In tree-based models, each node in the tree corresponds to a feature field from a dataset. Except for the top node, each node has an incoming branch. Similarly, except for the bottom node (or leaf node), each node has a number of outgoing branches. A branch represents a possible value for the input field from the originating dataset. A leaf represents the value of the objective field, given all the values for each input field in the chain of branches that go from the root (top) to that leaf.

This option defaults to 50 trees. 

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the titanic dataset: 
	# This dataset is used to classify whether a passenger will survive '1' or not '0'
	# original dataset can be found at https://stat.ethz.ch/R-manual/R-devel/library/datasets/html/Titanic.html
	titanic <-  h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

	# convert response column to a factor
	titanic['survived'] <- as.factor(titanic['survived'])

	# set the predictor names and the response column name
	# predictors include all columns except 'name' and the response column ("survived")
	predictors <- setdiff(colnames(titanic), colnames(titanic)[2:3])
	response <- "survived"

	# split into train and validation
	titanic.splits <- h2o.splitFrame(data =  titanic, ratios = .8, seed = 1234)
	train <- titanic.splits[[1]]
	valid <- titanic.splits[[2]]

	# try a range of ntrees: 
	bin_num = c(20, 50, 80, 110, 140, 170, 200)
	label = c("20", "50", "80", "110", "140", "170", "200")
	lapply(seq_along(1:length(bin_num)),function(num) {
	  titanic.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                          ntrees = bin_num[num], nfolds = 5, seed = 1234)
	  # print the value used and AUC score for train and valid
	  print(paste(label[num], 'training score',  h2o.auc(titanic.gbm, train = TRUE)))
	  print(paste(label[num], 'validation score',  h2o.auc(titanic.gbm, valid = TRUE)))
	})


	# Example of values to grid over for `ntrees`
	hyper_params <- list( ntrees = c(20, 50, 80, 110, 140, 170, 200) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "titanic_grid", hyper_params = hyper_params,
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)  

	## Sort the grid models by AUC
	sortedGrid <- h2o.getGrid("titanic_grid", sort_by = "auc", decreasing = TRUE)    
	sortedGrid


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

	# import the titanic dataset: 
	# This dataset is used to classify whether a passenger will survive '1' or not '0'
	# original dataset can be found at https://stat.ethz.ch/R-manual/R-devel/library/datasets/html/Titanic.html
	titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

	# convert response column to a factor
	titanic['survived'] = titanic['survived'].asfactor() 

	# set the predictor names and the response column name
	# predictors include all columns except 'name' and the response column ("survived")
	predictors = titanic.columns
	del predictors[1:3]
	response = 'survived'

	# split into train and validation sets
	train, valid = titanic.split_frame(ratios = [.8], seed = 1234)

	# try a range of values for `ntrees`
	tree_num = [20, 50, 80, 110, 140, 170, 200]
	label = ["20", "50", "80", "110", "140", "170", "200"]
	for key, num in enumerate(tree_num):
	    # initialize the GBM estimator and set a seed for reproducibility
	    titanic_gbm = H2OGradientBoostingEstimator(ntrees = num, seed = 1234)
	    titanic_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)
	    # print the value and AUC score for train and validation sets
	    print(label[key], 'training score', titanic_gbm.auc(train = True))
	    print(label[key], 'validation score', titanic_gbm.auc(valid = True))


	# Example of values to grid over for `ntrees`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `ntrees` to grid over
	hyper_params = {'ntrees': [20, 50, 80, 110, 140, 170, 200]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	tree_gbm_2 = H2OGradientBoostingEstimator(seed = 1234)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = tree_gbm_2, hyper_params = hyper_params,  
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by='auc', decreasing=True)
	print(sorted_grid)