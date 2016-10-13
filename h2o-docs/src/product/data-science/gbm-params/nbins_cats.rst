``nbins_cats``
--------------

- Available in: GBM, DRF
- Hyperparameter: yes

Description
~~~~~~~~~~~

When training models with categorical columns (factors), the ``nbins_cats`` option specifies the number of bins to be included in the histogram and then split at the best point. Because H2O does not perform `one-hot <https://en.wikipedia.org/wiki/One-hot>`__ encoding in the tree algorithms, we look at all the factor levels of a categorical predictor up to the resolution of the histogram, and then decide for each histogram bucket whether it goes left or right during splitting.

When the training data contains columns with categorical levels (factors), these factors are split by assigning an integer to each distinct categorical level, then binning the ordered integers according to the user-specified number of bins (which defaults to 1024 bins), and then picking the optimal split point among the bins. For example, if you have levels A,B,C,D,E,F,G at a certain node to be split, and you specify ``nbins_cats=4``, then the buckets {A,B},{C,D},{E,F},{G} define the grouping during the first split. Only during the next split of {A,B} (down the tree) will GBM separate {A} and {B}.

The value of ``nbins_cats`` for categorical factors has a much greater impact on the generalization error rate than ``nbins`` does for real- or integer-valued columns (where higher values mainly lead to more accurate numerical split points). For columns with many factors, a small ``nbins_cats`` value can add randomness to the split decisions (because the columns are grouped together somewhat arbitrarily), while large values (for example, values as large as the number of factor levels) can lead to perfect splits, resulting in `overfitting <https://en.m.wikipedia.org/wiki/Overfitting>`__ on the training set (AUC=1 in certain datasets). So this option is a very important tuning parameter that can make a big difference on the validation set accuracy. The default value for ``nbins_cats`` is 1024. Note that this default value can lead to large communication overhead for deep distributed tree models. ``nbins_cats`` can go up to 65k, which should be enough for most datasets.

To make a model more general, decrease ``nbins_top_level`` and ``nbins_cats``. To make a model more specific, increase ``nbins`` and/or ``nbins_top_level`` and ``nbins_cats``. Keep in mind that increasing ``nbins_cats`` can have a dramatic effect on the amount of overfitting.

**Note**: Currently in H2O, if the number of categorical values in a dataset exceeds the value specified with ``nbins_cats``, then the values are grouped into bins by `lexical ordering <https://en.wikipedia.org/wiki/Lexicographical_order>`__. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `nbins <nbins.html>`__
- `nbins_top_level <nbins_top_level.html>`__


Example
~~~~~~~

.. example-code::
   .. code-block:: r
	
	library(h2o)
	h2o.init()
	# import the airlines dataset:
	# This dataset is used to classify whether a flight will be delayed 'YES' or not "NO"
	# original data can be found at http://www.transtats.bts.gov/
	airlines <-  h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

	# convert columns to factors
	airlines["Year"] <- as.factor(airlines["Year"])
	airlines["Month"] <- as.factor(airlines["Month"])
	airlines["DayOfWeek"] <- as.factor(airlines["DayOfWeek"])
	airlines["Cancelled"] <- as.factor(airlines["Cancelled"])
	airlines['FlightNum'] <- airlines['FlightNum'].asfactor()

	# set the predictor names and the response column name
	predictors <- c("Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum")
	response <- "IsDepDelayed"

	# split into train and validation
	airlines.splits <- h2o.splitFrame(data =  airlines, ratios = .8, seed = 1234)
	train <- airlines.splits[[1]]
	valid <- airlines.splits[[2]]

	# number of factor levels range from 2 to 2439
	# ('FlightNum', [2439])
	# ('Origin', [132])
	# ('Dest', [134])
	# ('Year', [22])
	# ('UniqueCarrier', [10])
	# ('DayOfWeek', [7])
	# ('Month', [2])

	# try a range of nbins_cats: 
	bin_num = c(8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096)
	label = c("8", "16" ,"32", "64", "128", "256", "512", "1024", "2048", "4096")
	lapply(seq_along(1:length(bin_num)),function(num) {
	  airlines.gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                          nbins_cats = bin_num[num], nfolds = 5, seed = 1234)
	  # print the value used and AUC score for train and valid
	  print(paste(label[num], 'training score',  h2o.auc(airlines.gbm, train = TRUE)))
	  print(paste(label[num], 'validation score',  h2o.auc(airlines.gbm, valid = TRUE)))
	})


	# Example of values to grid over for `nbins_cats`
	hyper_params <- list( nbins_cats = c(8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096) )

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: list(strategy = "RandomDiscrete")
	# this GBM uses early stopping once the validation AUC doesn't improve by at least 0.01% for 
	# 5 consecutive scoring events
	grid <- h2o.grid(x = predictors, y = response, training_frame = train, validation_frame = valid,
	                 algorithm = "gbm", grid_id = "air_grid", hyper_params = hyper_params,
	                 stopping_rounds = 5, stopping_tolerance = 1e-4, stopping_metric = "AUC",
	                 search_criteria = list(strategy = "Cartesian"), seed = 1234)  

	## Sort the grid models by AUC
	sortedGrid <- h2o.getGrid("air_grid", sort_by = "auc", decreasing = TRUE)    
	sortedGrid

	  
   .. code-block:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

	# import the airlines dataset:
	# This dataset is used to classify whether a flight will be delayed 'YES' or not "NO"
	# original data can be found at http://www.transtats.bts.gov/
	airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

	# convert columns to factors
	airlines["Year"]= airlines["Year"].asfactor()
	airlines["Month"]= airlines["Month"].asfactor()
	airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
	airlines["Cancelled"] = airlines["Cancelled"].asfactor()
	airlines['FlightNum'] = airlines['FlightNum'].asfactor()

	# set the predictor names and the response column name
	predictors = ["Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum"]
	response = "IsDepDelayed"

	# split into train and validation sets 
	train, valid= airlines.split_frame(ratios = [.8], seed = 1234)

	# number of factor levels range from 2 to 2439
	# ('FlightNum', [2439])
	# ('Origin', [132])
	# ('Dest', [134])
	# ('Year', [22])
	# ('UniqueCarrier', [10])
	# ('DayOfWeek', [7])
	# ('Month', [2])

	# try a range of nbins_cats: 
	bin_num = [8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096]
	label = ["8", "16", "32", "64", "128", "256", "512", "1024", "2048", "4096"]
	for key, num in enumerate(bin_num):
	    # initialize the GBM estimator and set a seed for reproducibility
	    airlines_gbm = H2OGradientBoostingEstimator(nbins_cats = num, seed =1234)
	    airlines_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)
	    # print the value used and AUC score for train and valid
	    print(label[key], 'training score', airlines_gbm.auc(train = True))
	    print(label[key], 'validation score', airlines_gbm.auc(valid = True))


	# Example of values to grid over for `nbins_cats`
	# import Grid Search
	from h2o.grid.grid_search import H2OGridSearch

	# select the values for nbins_cats to grid over
	hyper_params = {'nbins_cats': [8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096]}

	# this example uses cartesian grid search because the search space is small
	# and we want to see the performance of all models. For a larger search space use
	# random grid search instead: {'strategy': "RandomDiscrete"}
	# initialize the GBM estimator
	# use early stopping once the validation AUC doesn't improve by at least 0.01% for 
	# 5 consecutive scoring events
	airlines_gbm_2 = H2OGradientBoostingEstimator(seed = 1234, stopping_rounds = 5,
	                     stopping_metric = "AUC", stopping_tolerance = 1e-4)

	# build grid search with previously made GBM and hyper parameters
	grid = H2OGridSearch(model = airlines_gbm_2, hyper_params = hyper_params,
	                     search_criteria = {'strategy': "Cartesian"})

	# train using the grid
	grid.train(x = predictors, y = response, training_frame = train, validation_frame = valid, seed = 1234)

	# sort the grid models by decreasing AUC
	sorted_grid = grid.get_grid(sort_by = 'auc', decreasing = True)
	print(sorted_grid)