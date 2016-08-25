In [32]: ntrees_opt = [5, 10, 15]

In [33]: max_depth_opt = [2, 3, 4]

In [34]: learn_rate_opt = [0.1, 0.2]

In [35]: hyper_parameters = {"ntrees": ntrees_opt, "max_depth":max_depth_opt, "learn_rate":learn_rate_opt}

In [36]: from h2o.grid.grid_search import H2OGridSearch

In [37]: gs = H2OGridSearch(H2OGradientBoostingEstimator(distribution="multinomial"), hyper_params=hyper_parameters)

In [38]: gs.train(x=range(0,iris_df.ncol-1), y=iris_df.ncol-1, training_frame=iris_df, nfolds=10)

gbm Grid Build Progress: [########################################] 100%

In [39]: print gs.sort_by('logloss', increasing=True)

Grid Search Results:
Model Id                         Hyperparameters: ['learn_rate', 'ntrees', 'max_depth']    logloss
-------------------------------  --------------------------------------------------------  ---------
Grid_GBM_model_1446220160417_30  ['0.2, 15, 4']                                            0.05105
Grid_GBM_model_1446220160417_27  ['0.2, 15, 3']                                            0.0551088
Grid_GBM_model_1446220160417_24  ['0.2, 15, 2']                                            0.0697714
Grid_GBM_model_1446220160417_29  ['0.2, 10, 4']                                            0.103064
Grid_GBM_model_1446220160417_26  ['0.2, 10, 3']                                            0.106232
Grid_GBM_model_1446220160417_23  ['0.2, 10, 2']                                            0.120161
Grid_GBM_model_1446220160417_21  ['0.1, 15, 4']                                            0.170086
Grid_GBM_model_1446220160417_18  ['0.1, 15, 3']                                            0.171218
Grid_GBM_model_1446220160417_15  ['0.1, 15, 2']                                            0.181186
Grid_GBM_model_1446220160417_28  ['0.2, 5, 4']                                             0.275788
Grid_GBM_model_1446220160417_25  ['0.2, 5, 3']                                             0.27708
Grid_GBM_model_1446220160417_22  ['0.2, 5, 2']                                             0.280413
Grid_GBM_model_1446220160417_20  ['0.1, 10, 4']                                            0.28759
Grid_GBM_model_1446220160417_17  ['0.1, 10, 3']                                            0.288293
Grid_GBM_model_1446220160417_14  ['0.1, 10, 2']                                            0.292993
Grid_GBM_model_1446220160417_16  ['0.1, 5, 3']                                             0.520591
Grid_GBM_model_1446220160417_19  ['0.1, 5, 4']                                             0.520697
Grid_GBM_model_1446220160417_13  ['0.1, 5, 2']                                             0.524777