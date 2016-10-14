``nbins_top_level``
-------------------

- Available in: GBM, DRF
- Hyperparameter: yes

Description
~~~~~~~~~~~

For numerical columns (real/int), the ``nbins_top_level`` option is the number of bins to use at the top of each tree. It then divides by 2 at each ensuing level to find a new number. This option defaults to 1024 and is used with `nbins <nbins.html>`_, which controls when the algorithm stops dividing by 2.

To make a model more general, decrease ``nbins_top_level`` and ``nbins_cats``. To make a model more specific, increase ``nbins`` and/or ``nbins_top_level`` and ``nbins_cats``. Keep in mind that increasing ``nbins_cats`` can lead to in `overfitting <https://en.m.wikipedia.org/wiki/Overfitting>`__ on the training set.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `nbins <nbins.html>`__
- `nbins_cats <nbins_cats.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()
	# import the EEG dataset: 
	# All data is from one continuous EEG measurement with the Emotiv EEG Neuroheadset. 
	# The duration of the measurement was 117 seconds. The eye state was detected via a camera during the 
	# EEG measurement and added later manually to the file after analysing the video frames. 
	# '1' indicates the eye-closed and '0' the eye-open state. All values are in chronological 
	# order with the first measured value at the top of the data.
	# original dataset can be found at the UCI Machine Learning Repository http://archive.ics.uci.edu/ml/datasets/EEG+Eye+State
	eeg <-  h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/eeg/eeg_eyestate.csv")

	# convert response column to a factor
	eeg['eyeDetection'] <- as.factor(eeg['eyeDetection'])

	# set the predictor names and the response column name
	predictors <- colnames(eeg)[1:(length(eeg)-1)]
	response <- "eyeDetection"

	# split into train and validation
	eeg.splits <- h2o.splitFrame(data =  eeg, ratios = .8, seed = 1234)
	train <- eeg.splits[[1]]
	valid <- eeg.splits[[2]]

	# try a range of nbins_top_level: 
	bin_num = c(32, 64, 128, 256, 512, 1024, 2048, 4096)
	label = c("32", "64", "128", "256", "512", "1024", "2048", "4096")
	lapply(seq_along(1:length(bin_num)),function(num) {
	  eeg.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                          nbins_top_level = bin_num[num], nfolds = 5, seed = 1234)
	  # print the value used and AUC score for train and valid
	  print(paste(label[num], 'training score',  h2o.auc(eeg.gbm, train = TRUE)))
	  print(paste(label[num], 'validation score',  h2o.auc(eeg.gbm, valid = TRUE)))
	})


	# Example of values to grid over for `nbins_top_level`
	hyper_params <- list( nbins_top_level = c(32, 64, 128, 256, 512, 1024, 2048, 4096) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	# this GBM uses early stopping once the validation AUC doesn't improve by at least 0.01% for 
	# 5 consecutive scoring events
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "eeg_grid", hyper_params = hyper_params,
	                 stopping_rounds = 5, stopping_tolerance = 1e-4, stopping_metric = "AUC",
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)  

	## Sort the grid models by AUC
	sortedGrid <- h2o.getGrid("eeg_grid", sort_by = "auc", decreasing = TRUE)    
	sortedGrid


   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

	# import the EEG dataset: 
	# All data is from one continuous EEG measurement with the Emotiv EEG Neuroheadset. 
	# The duration of the measurement was 117 seconds. The eye state was detected via a camera during the 
	# EEG measurement and added later manually to the file after analysing the video frames. 
	# '1' indicates the eye-closed and '0' the eye-open state. All values are in chronological 
	# order with the first measured value at the top of the data.
	# original dataset can be found at the UCI Machine Learning Repository http://archive.ics.uci.edu/ml/datasets/EEG+Eye+State
	eeg <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/eeg/eeg_eyestate.csv")

	# convert response column to a factor
	eeg['eyeDetection'] = eeg['eyeDetection'].asfactor() 

	# set the predictor names and the response column name
	predictors = eeg.columns[:-1]
	response = 'eyeDetection'

	# split into train and validation sets
	train, valid = eeg.split_frame(ratios = [.8], seed = 1234)

	# try a range of values for `nbins_top_level`
	# we start at 32 because the default for nbins is 20, and nbins_top_level
	# must be greater than nbins
	bin_num = [32, 64, 128, 256, 512, 1024, 2048, 4096]
	label = ["32", "64", "128", "256", "512", "1024", "2048", "4096"]
	for key, num in enumerate(bin_num):
	    # initialize the GBM estimator and set a seed for reproducibility
	    eeg_gbm = H2OGradientBoostingEstimator(nbins_top_level = num, seed = 1234)
	    eeg_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)
	    # print the value used and AUC score for train and validation sets
	    print(label[key], 'training score', eeg_gbm.auc(train = True))
	    print(label[key], 'validation score', eeg_gbm.auc(valid = True))


	# Example of values to grid over for `nbins_top_level`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for `nbins_top_level` to grid over
	hyper_params = {'nbins_top_level': [32, 64, 128, 256, 512, 1024, 2048, 4096]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	# use early stopping once the validation AUC doesn't improve by at least 0.01% for 
	# 5 consecutive scoring events
	eeg_gbm_2 = H2OGradientBoostingEstimator(stopping_rounds = 5, stopping_metric = "AUC",
	                                         stopping_tolerance = 1e-4, seed = 1234)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = eeg_gbm_2, hyper_params = hyper_params,  
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid, seed = 1234)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by='auc', decreasing=True)
	print(sorted_grid)