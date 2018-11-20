``max_depth``
-------------

- Available in: GBM, DRF, XGBoost, Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

This specifies the maximum depth to which each tree will be built. A single tree will stop splitting when there are no more splits that satisfy the ``min_rows`` parameter, if it reaches ``max_depth``, or if there are no splits that satisfy this ``min_split_improvement`` parameter.

In general, deeper trees can seem to provide better accuracy on a training set because deeper trees can overfit your model to your data. Also, the deeper the algorithm goes, the more computing time is required. This is especially true at depths greater than 10. At depth 4, 8 nodes, for example, you need ``8 * 100 * 20`` trials to complete this splitting for the layer.

One way to determine an appropriate value for ``max_depth`` is to run a quick Cartesian grid search. Each model in the grid search will use early stopping to tune the number of trees using the validation set AUC, as before. The examples below are also available in the `GBM Tuning Tutorials <https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/product/tutorials/gbm>`__  folder on GitHub.

The ``max_depth`` default value varies depending on the algorithm.

- GBM: default is 5.
- DRF: default is 20.
- XGBoost: default is 6.
- Isolation Forest: default is 8.


Related Parameters
~~~~~~~~~~~~~~~~~~

- `min_rows <min_rows.html>`__
- `min_split_improvement <min_split_improvement.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r
   
    library(h2o)
    h2o.init()
    # import the titanic dataset
    df <- h2o.importFile(path = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
    dim(df)
    head(df)
    tail(df)
    summary(df,exact_quantiles=TRUE)

    # pick a response for the supervised problem
    response <- "survived"

    # the response variable is an integer.
    # we will turn it into a categorical/factor for binary classification
    df[[response]] <- as.factor(df[[response]])           

    # use all other columns (except for the name) as predictors
    predictors <- setdiff(names(df), c(response, "name")) 
    
    # split the data for machine learning
    splits <- h2o.splitFrame(data = df, 
                             ratios = c(0.6,0.2), 
                             destination_frames = c("train.hex", "valid.hex", "test.hex"), 
                             seed = 1234)
    train <- splits[[1]]
    valid <- splits[[2]]
    test  <- splits[[3]]
    
    # Establish a baseline performance using a default GBM model trained on the 60% training split
    # We only provide the required parameters, everything else is default
    gbm <- h2o.gbm(x = predictors, y = response, training_frame = train)

    # Get the AUC on the validation set
    h2o.auc(h2o.performance(gbm, newdata = valid)) 	
    # The AUC is over 94%, so this model is highly predictive!
    [1] 0.9480135

    # Determine the best max_depth value to use during a hyper-parameter search.
    # Depth 10 is usually plenty of depth for most datasets, but you never know
    hyper_params = list( max_depth = seq(1,29,2) )
    # or hyper_params = list( max_depth = c(4,6,8,12,16,20) ), which is faster for larger datasets

    grid <- h2o.grid(
      hyper_params = hyper_params,

      # full Cartesian hyper-parameter search
      search_criteria = list(strategy = "Cartesian"),
      
      # which algorithm to run
      algorithm="gbm",
      
      # identifier for the grid, to later retrieve it
      grid_id="depth_grid",
      
      # standard model parameters
      x = predictors, 
      y = response, 
      training_frame = train, 
      validation_frame = valid,
      
      # more trees is better if the learning rate is small enough 
      # here, use "more than enough" trees - we have early stopping
      ntrees = 10000,                                                            
      
      # smaller learning rate is better, but because we have learning_rate_annealing,
      # we can afford to start with a bigger learning rate
      learn_rate = 0.05,                                                         
      
      # learning rate annealing: learning_rate shrinks by 1% after every tree 
      # (use 1.00 to disable, but then lower the learning_rate)
      learn_rate_annealing = 0.99,                                               
      
      # sample 80% of rows per tree
      sample_rate = 0.8,                                                       

      # sample 80% of columns per split
      col_sample_rate = 0.8, 
      
      # fix a random number generator seed for reproducibility
      seed = 1234,                                                             

      # early stopping once the validation AUC doesn't improve by at least 
      # 0.01% for 5 consecutive scoring events
      stopping_rounds = 5,
      stopping_tolerance = 1e-4,
      stopping_metric = "AUC", 
     
      # score every 10 trees to make early stopping reproducible 
      # (it depends on the scoring interval)
      score_tree_interval = 10)

    # by default, display the grid search results sorted by increasing logloss 
    # (because this is a classification task)
    grid                                                                       

    # sort the grid models by decreasing AUC
    sortedGrid <- h2o.getGrid("depth_grid", sort_by="auc", decreasing = TRUE)    
    sortedGrid

    # find the range of max_depth for the top 5 models
    topDepths = sortedGrid@summary_table$max_depth[1:5]                       
    minDepth = min(as.numeric(topDepths))
    maxDepth = max(as.numeric(topDepths))
      
    > sortedGrid
    #H2O Grid Details
    Grid ID: depth_grid 
    Used hyper parameters: 
     -  max_depth 
    Number of models: 15 
    Number of failed models: 0 
    Hyper-Parameter Search Summary: ordered by decreasing auc
         max_depth           model_ids                auc
      1         13  depth_grid_model_6 0.9552831783601015
      2         27 depth_grid_model_13 0.9547196393350239
      3         17  depth_grid_model_8 0.9543251620174698
      4         11  depth_grid_model_5 0.9538743307974078
      5          9  depth_grid_model_4 0.9534798534798535
      6         19  depth_grid_model_9 0.9534234995773457
      7         25 depth_grid_model_12 0.9529726683572838
      8         29 depth_grid_model_14 0.9528036066497605
      9         21 depth_grid_model_10 0.9526908988447449
      10        15  depth_grid_model_7 0.9526345449422373
      11         7  depth_grid_model_3  0.951789236404621
      12        23 depth_grid_model_11 0.9505494505494505
      13         3  depth_grid_model_1  0.949084249084249
      14         5  depth_grid_model_2 0.9484361792054099
      15         1  depth_grid_model_0 0.9478162862778248
   
   
   .. code-block:: python
   
    import h2o
    h2o.init()
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    from h2o.grid.grid_search import H2OGridSearch
    
    # import the titanic dataset
    df = h2o.import_file(path = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
    
    # pick a response for the supervised problem
    response = "survived"

    # the response variable is an integer
    # we will turn it into a categorical/factor for binary classification
    df[response] = df[response].asfactor()
    
    # use all other columns as predictors 
    # (except for the name & the response column ("survived")) 
    predictors = df.columns
    del predictors[1:3]

    # split the data for machine learning
    train, valid, test = df.split_frame(
        ratios=[0.6,0.2], 
        seed=1234, 
        destination_frames=['train.hex','valid.hex','test.hex']
    )
    
    # Establish baseline performance
    # We only provide the required parameters, everything else is default
    gbm = H2OGradientBoostingEstimator()
    gbm.train(x=predictors, y=response, training_frame=train)
    
    # Get the AUC on the validation set
    perf = gbm.model_performance(valid)
    print perf.auc()
    # The AUC is over 94%, so this model is highly predictive!
    0.948013524937

    # Determine the best max_depth value to use during a hyper-parameter search
    # Depth 10 is usually plenty of depth for most datasets, but you never know
    hyper_params = {'max_depth' : range(1,30,2)}
    # hyper_params = {max_depth = [4,6,8,12,16,20]} may be faster for larger datasets

    #Build initial GBM Model
    gbm_grid = H2OGradientBoostingEstimator(
        # more trees is better if the learning rate is small enough 
        # here, use "more than enough" trees - we have early stopping
        ntrees=10000,

        # smaller learning rate is better
        # since we have learning_rate_annealing, we can afford to start with a 
        # bigger learning rate
        learn_rate=0.05,

        # learning rate annealing: learning_rate shrinks by 1% after every tree 
        # (use 1.00 to disable, but then lower the learning_rate)
        learn_rate_annealing = 0.99,

        # sample 80% of rows per tree
        sample_rate = 0.8,

        # sample 80% of columns per split
        col_sample_rate = 0.8,

        # fix a random number generator seed for reproducibility
        seed = 1234,

        # score every 10 trees to make early stopping reproducible 
        # (it depends on the scoring interval)
        score_tree_interval = 10, 

        # early stopping once the validation AUC doesn't improve by at least 0.01% for 
        # 5 consecutive scoring events
        stopping_rounds = 5,
        stopping_metric = "AUC",
        stopping_tolerance = 1e-4)

    # Build grid search with previously made GBM and hyper parameters
    grid = H2OGridSearch(gbm_grid,hyper_params,
                         grid_id = 'depth_grid',
                         search_criteria = {'strategy': "Cartesian"})

    # Train grid search
    grid.train(x=predictors, 
               y=response,
               training_frame = train,
               validation_frame = valid)

    # Display the grid search results
    # Sorted by increasing logloss (because this is a classification task)
    print grid

         max_depth            model_ids              logloss
    0           17   depth_grid_model_8  0.20544094075930078
    1           19   depth_grid_model_9  0.20584402503242194
    2           27  depth_grid_model_13  0.20627418156921704
    3           11   depth_grid_model_5   0.2069364255413584
    4           13   depth_grid_model_6   0.2078569528636169
    5           25  depth_grid_model_12  0.20834760530631993
    6            9   depth_grid_model_4  0.20842232867415922
    7           29  depth_grid_model_14  0.20904163538087436
    8           15   depth_grid_model_7  0.20991531457742935
    9           23  depth_grid_model_11   0.2104361858121492
    10          21  depth_grid_model_10  0.21069590143686837
    11           7   depth_grid_model_3  0.21127939637392396
    12           5   depth_grid_model_2  0.21509420086032935
    13           3   depth_grid_model_1  0.21854010261642962
    14           1   depth_grid_model_0  0.23892331983893703

    # Sort the grid models by decreasing AUC
    sorted_grid = grid.get_grid(sort_by='auc',decreasing=True)
    print(sorted_grid)

         max_depth            model_ids                 auc
    0           13   depth_grid_model_6  0.9552831783601015
    1           27  depth_grid_model_13  0.9547196393350239
    2           17   depth_grid_model_8  0.9543251620174698
    3           11   depth_grid_model_5  0.9538743307974078
    4            9   depth_grid_model_4  0.9534798534798535
    5           19   depth_grid_model_9  0.9534234995773457
    6           25  depth_grid_model_12  0.9529726683572838
    7           29  depth_grid_model_14  0.9528036066497605
    8           21  depth_grid_model_10  0.9526908988447449
    9           15   depth_grid_model_7  0.9526345449422373
    10           7   depth_grid_model_3   0.951789236404621
    11          23  depth_grid_model_11  0.9505494505494505
    12           3   depth_grid_model_1   0.949084249084249
    13           5   depth_grid_model_2  0.9484361792054099
    14           1   depth_grid_model_0  0.9478162862778248

It appears that ``max_depth`` values of 9 to 27 are best suited for this dataset, which is unusally deep.
