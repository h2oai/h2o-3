scikit-learn Integration
------------------------

The H2O Python client can be used within scikit-learn pipelines and cross-validation searches.  This extends the capabilities of both H2O and scikit-learn. Note that the sklearn and scipy packages are required to use the H2O Python client with scikit-learn. 

Pipelines
~~~~~~~~~

The following example show how to create a scikit-learn style pipeline using H2O transformers and estimators:

::

	import h2o
	from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	from h2o.transforms.preprocessing import H2OScaler
	from sklearn.pipeline import Pipeline

	# start h2o
	h2o.init()

	# import the iris dataset
	iris_data_path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv"
	iris_df = h2o.import_file(path=iris_data_path)

	# build transformation pipeline using sklearn's Pipeline and H2O transforms
	pipeline = Pipeline([("standardize", H2OScaler()),
	                     ("pca", H2OPrincipalComponentAnalysisEstimator(k=2, impute_missing=True)),
	                     ("gbm", H2OGradientBoostingEstimator(distribution="multinomial"))])

	pipeline.fit(iris_df[:4],iris_df[4])
	Model Details
	=============
	H2OPCA :  Principal Component Analysis
	Model Key:  PCA_model_python_1446220160417_32

	Importance of components:
	                        pc1       pc2
	----------------------  --------  ---------
	Standard deviation      3.22082   0.34891
	Proportion of Variance  0.984534  0.0115538
	Cumulative Proportion   0.984534  0.996088


	ModelMetricsPCA: pca
	** Reported on train data. **

	MSE: NaN
	RMSE: NaN
	Model Details
	=============
	H2OGradientBoostingEstimator :  Gradient Boosting Machine
	Model Key:  GBM_model_python_1446220160417_34

	Model Summary:
	    number_of_trees    number_of_internal_trees  model_size_in_bytes    min_depth    max_depth    mean_depth    min_leaves    max_leaves    mean_leaves
	--  -----------------  ------------------------- ---------------------  -----------  -----------  ------------  ------------  ------------  -------------
	    50                 150                       28170                  1            5            4.84          2             13            9.97333


	ModelMetricsMultinomial: gbm
	** Reported on train data. **

	MSE: 0.00162796447355
	RMSE: 0.0403480417561
	LogLoss: 0.0152718656454
	Mean Per-Class Error: 0.0
	Confusion Matrix: vertical: actual; across: predicted

	Iris-setosa    Iris-versicolor    Iris-virginica    Error    Rate
	-------------  -----------------  ----------------  -------  -------
	50             0                  0                 0        0 / 50
	0              50                 0                 0        0 / 50
	0              0                  50                0        0 / 50
	50             50                 50                0        0 / 150

	Top-3 Hit Ratios:
	k    hit_ratio
	---  -----------
	1    1
	2    1
	3    1

	Scoring History:
	     timestamp            duration    number_of_trees    training_rmse      training_logloss    training_classification_error
	---  -------------------  ----------  -----------------  ----------------  ------------------  -------------------------------
	     2016-08-25 13:50:21  0.006 sec   0.0                0.666666666667    1.09861228867       0.66
	     2016-08-25 13:50:21  0.077 sec   1.0                0.603019288754    0.924249463924      0.04
	     2016-08-25 13:50:21  0.096 sec   2.0                0.545137025745    0.788619346614      0.04
	     2016-08-25 13:50:21  0.110 sec   3.0                0.492902188607    0.679995476522      0.04
	     2016-08-25 13:50:21  0.123 sec   4.0                0.446151758168    0.591313596193      0.04
	---  ---                  ---         ---                ---               ---                 ---
	     2016-08-25 13:50:21  0.419 sec   46.0               0.0489303232171   0.0192767805328     0.0
	     2016-08-25 13:50:21  0.424 sec   47.0               0.0462779490149   0.0180720396825     0.0
	     2016-08-25 13:50:21  0.429 sec   48.0               0.0444689238255   0.0171428314531     0.0
	     2016-08-25 13:50:21  0.434 sec   49.0               0.0423442541538   0.0161938230172     0.0
	     2016-08-25 13:50:21  0.438 sec   50.0               0.0403480417561   0.0152718656454     0.0

	Variable Importances:
	variable    relative_importance    scaled_importance    percentage
	----------  ---------------------  -------------------  ------------
	PC1         448.958                1                    0.982184
	PC2         8.1438                 0.0181393            0.0178162
	Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x1088c6a50>), ('pca', ), ('gbm', )])

Randomized Grid Search Example
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The following example shows how to create a scikit-learn style hyperparameter grid search using k-fold cross validation:

::

	from sklearn.model_selection import RandomizedSearchCV
	from h2o.cross_validation import H2OKFold
	from h2o.model.regression import h2o_r2_score
	from sklearn.metrics.scorer import make_scorer

	# Parameters to test
	params = {"standardize__center":    [True, False],
	          "standardize__scale":     [True, False],
	          "pca__k":                 [2,3],
	          "gbm__ntrees":            [10,20],
	          "gbm__max_depth":         [1,2,3],
	          "gbm__learn_rate":        [0.1,0.2]}

	custom_cv = H2OKFold(iris_df, n_folds=5, seed=42)

	pipeline = Pipeline([("standardize", H2OScaler()),
	                     ("pca", H2OPrincipalComponentAnalysisEstimator(k=2)),
	                     ("gbm", H2OGradientBoostingEstimator(distribution="gaussian"))])

	random_search = RandomizedSearchCV(pipeline, 
	                                   params,
	                                   n_iter=5,
	                                   scoring=make_scorer(h2o_r2_score),
	                                   cv=custom_cv,
	                                   random_state=42,
	                                   n_jobs=1)
	random_search.fit(iris_df[1:], iris_df[0])
	RandomizedSearchCV(cv=<h2o.cross_validation.H2OKFold instance at 0x10ba413d0>,
	          error_score='raise',
	          estimator=Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x10c0f18d0>), ('pca', ), ('gbm', )]),
	          fit_params={}, iid=True, n_iter=5, n_jobs=1,
	          param_distributions={'pca__k': [2, 3], 'gbm__ntrees': [10, 20], 'standardize__scale': [True, False], 'gbm__max_depth': [1, 2, 3], 'standardize__center': [True, False], 'gbm__learn_rate': [0.1, 0.2]},
	          pre_dispatch='2*n_jobs', random_state=42, refit=True,
	          scoring=make_scorer(h2o_r2_score), verbose=0)

	print random_search.best_estimator_
	Model Details
	=============
	H2OPCA :  Principal Component Analysis
	Model Key:  PCA_model_python_1446220160417_136

	Importance of components:
	                        pc1       pc2         pc3
	----------------------  --------  ----------  ----------
	Standard deviation      9.6974  0.091905     0.031356
	Proportion of Variance  0.9999  8.98098e-05  1.04541e-05
	Cumulative Proportion   0.9999  0.99999      1


	ModelMetricsPCA: pca
	** Reported on train data. **

	MSE: NaN
	RMSE: NaN
	Model Details
	=============
	H2OGradientBoostingEstimator :  Gradient Boosting Machine
	Model Key:  GBM_model_python_1446220160417_138

	Model Summary:
	    number_of_trees    number_of_internal_trees   model_size_in_bytes    min_depth    max_depth    mean_depth    min_leaves    max_leaves    mean_leaves
	--  -----------------  -------------------------  ---------------------  -----------  -----------  ------------  ------------  ------------  -------------
	    20                 20                         2958                   3            3            3             5             8             6.85

	ModelMetricsRegression: gbm
	** Reported on train data. **

	RMSE: 0.193906262445
	MAE: 0.155086582663
	RMSLE: NaN
	Mean Residual Deviance: 0.0375996386155
	Scoring History: 

	     timestamp            duration    number_of_trees    training_rmse   training_mse    training_deviance
	--   -------------------  ----------  -----------------  --------------  --------------  -------------------
	     2016-08-25 13:58:15  0.000 sec   0.0                0.683404046309  0.569341466973  0.467041090512
	     2016-08-25 13:58:15  0.002 sec   1.0                0.571086656306  0.469106400643  0.326139969011
	     2016-08-25 13:58:15  0.003 sec   2.0                0.483508601652  0.395952082872  0.233780567872
	     2016-08-25 13:58:15  0.004 sec   3.0                0.414549015095  0.339981133963  0.171850885916
	     2016-08-25 13:58:15  0.005 sec   4.0                0.362852508373  0.298212416346  0.131661942833
	---  ---                  ---         ---                ---             ---             ---
	     2016-08-25 13:58:15  0.017 sec   16.0               0.204549491682  0.164292158112  0.0418404945473
	     2016-08-25 13:58:15  0.018 sec   17.0               0.201762323368  0.162030458841  0.0407080351307
	     2016-08-25 13:58:15  0.019 sec   18.0               0.199709571992  0.160735480674  0.0398839131454
	     2016-08-25 13:58:15  0.019 sec   19.0               0.196739590066  0.158067452484  0.0387064662994
	     2016-08-25 13:58:15  0.020 sec   20.0               0.193906262445  0.155086582663  0.0375996386155

	Variable Importances:
	variable    relative_importance    scaled_importance    percentage
	----------  ---------------------  -------------------  ------------
	PC1         160.092                1                    0.894701
	PC3         14.8175                0.0925562            0.08281
	PC2         4.0241                 0.0251361            0.0224893
	Pipeline(steps=[('standardize', <h2o.transforms.preprocessing.H2OScaler object at 0x10c1679d0>), ('pca', ), ('gbm', )])

