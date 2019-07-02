from sklearn.base import BaseEstimator, ClassifierMixin
import h2o
import numpy as np
import sys
import pandas
from h2o.automl import H2OAutoML
import gc


class H2OAutoMLClassifier(BaseEstimator, ClassifierMixin): 
	def __init__(self, 
	max_runtime_secs = 3600, 
	max_models = None, 
    nfolds = 5,
    balance_classes = False, 
    class_sampling_factors = None, 
    max_after_balance_size = 5.0,
    max_runtime_secs_per_model = 0, 
    stopping_metric = 'AUTO', 
    stopping_tolerance = None,   
    stopping_rounds = 3, 
    sort_metric = 'AUTO', 
    seed = None, 
    project_name = None, 
    exclude_algos = None,
    include_algos = None, 
    keep_cross_validation_predictions = False, 
    keep_cross_validation_models = False,
    keep_cross_validation_fold_assignment = False,
    ram = "60G",
    nthread = -1   
      ): 
		assert stopping_metric in ['AUTO', 'deviance', 'logloss', 'MSE','RMSE','MAE','RMSLE','AUC', 'lift_top_group', 'misclassification','mean_per_class_error']
		assert sort_metric in ['AUTO', 'deviance', 'logloss', 'MSE','RMSE','MAE','RMSLE','AUC','mean_per_class_error']
		assert exclude_algos in [None,'GLM', 'DeepLearning', 'DRF', 'XGBoost', 'GBM', 'StackedEnsemble']
		assert include_algos in [None, 'GLM', 'DeepLearning', 'DRF', 'XGBoost', 'GBM', 'StackedEnsemble']
		self.max_runtime_secs = max_runtime_secs
		self.max_models = max_models 
		self.nfolds = nfolds
		self.balance_classes = balance_classes
		self.class_sampling_factors = class_sampling_factors
		self.max_after_balance_size = max_after_balance_size
		self.max_runtime_secs_per_model = max_runtime_secs_per_model
		self.stopping_metric = stopping_metric
		self.stopping_tolerance = stopping_tolerance
		self.stopping_rounds = stopping_rounds
		self.sort_metric = sort_metric
		self.seed = seed
		self.project_name = project_name
		self.exclude_algos = exclude_algos
		self.include_algos = include_algos 
		self.keep_cross_validation_predictions = keep_cross_validation_predictions 
		self.keep_cross_validation_models = keep_cross_validation_models
		self.keep_cross_validation_fold_assignment = keep_cross_validation_fold_assignment
		self.ram = ram
		self.nthread = nthread
		h2o.init(max_mem_size = self.ram ,nthreads = self.nthread)


	def build_matrix(self, X, opt_y=None):
		"""
		build_matrix converts X and y from sklearn format to 
		a matrix in the format required by h2oautoml.

		"""
		if opt_y is None:
			ytemp=np.array([0 for k in range (0,X.shape[0])])
			Xtemp=np.column_stack((ytemp,X))
			Xtemp=h2o.H2OFrame(Xtemp)
		else:
			ytemp=np.array(opt_y)
			Xtemp=np.column_stack((ytemp,X))
			Xtemp=h2o.H2OFrame(Xtemp)        
            
		#covtype_X = Xtemp.col_names[1:]     #last column is Cover_Type, our desired response variable 
		covtype_y = Xtemp.col_names[0]
		Xtemp[covtype_y] = Xtemp[covtype_y].asfactor()    #make factor since it is cassification  
		return Xtemp

	def set_params(self, **parms):
		"""
		Used by sklearn for updating parameters during grid search.
		:param parms: A dictionary of parameters that will be set on this model.
		:returns: self, the current estimator object with the parameters all set as desired.
		"""
		self._parms.update(parms)
		return self

	def get_params(self,deep = True):
		"""
		get_params returns a dictionary containing all parameters and values of the object.

		"""
		return (
	    		{
	    		"max_runtime_secs" : self.max_runtime_secs, 
				"max_models" : self.max_models, 
			    "nfolds" : self.nfolds,
			    "balance_classes" : self.balance_classes, 
			    "class_sampling_factors" : self.class_sampling_factors, 
			    "max_after_balance_size" : self.max_after_balance_size,
			    "max_runtime_secs_per_model" : self.max_runtime_secs_per_model, 
			    "stopping_metric" : self.stopping_metric, 
			    "stopping_tolerance" : self.stopping_tolerance,  
			    "stopping_rounds" : self.stopping_rounds, 
			    "sort_metric" : self.sort_metric, 
			    "seed" : self.seed, 
			    "project_name" : self.project_name, 
			    "exclude_algos" : self.exclude_algos,
			    "include_algos" : self.include_algos, 
			    "keep_cross_validation_predictions" : self.keep_cross_validation_predictions, 
			    "keep_cross_validation_models" : self.keep_cross_validation_models,
			    "keep_cross_validation_fold_assignment" : self.keep_cross_validation_fold_assignment,
			    "ram" : self.ram,
			    "nthread" : self.nthread   
	    		}
	    	)
	     

	def score(self, X, y, sample_weight = None): 
		"""
		score provides an accuracy score on the predictions on X as compared to the true labels y.

		"""
		X = self.build_matrix(X)
		preds = self.model.predict(X).as_data_frame().values[:,0]
		assert (len(preds)==len(y))
		preds = np.array(preds)
		y = np.array(y)
		return (sum(preds == y)/(len(y)+0.0))


	def fit(self, X, y):   
		"""
		fit is an h2oautoml wrapper for the sklearn function fit. It takes in training samples X and
		training labels y in sklearn format and resturn the object.

		"""
		X = self.build_matrix(X, y)
	    
		self.model = H2OAutoML(
			max_runtime_secs = self.max_runtime_secs, 
			max_models = self.max_models, 
		    nfolds = self.nfolds,
		    balance_classes = self.balance_classes, 
		    class_sampling_factors = self.class_sampling_factors, 
		    max_after_balance_size = self.max_after_balance_size,
		    max_runtime_secs_per_model = self.max_runtime_secs_per_model, 
		    stopping_metric = self.stopping_metric, 
		    stopping_tolerance = self.stopping_tolerance,
		    stopping_rounds = self.stopping_rounds, 
		    sort_metric = self.sort_metric, 
		    seed = self.seed, 
		    project_name = self.project_name, 
		    exclude_algos = self.exclude_algos,
		    include_algos = self.include_algos, 
		    keep_cross_validation_predictions = self.keep_cross_validation_predictions, 
			keep_cross_validation_models = self.keep_cross_validation_models,
			keep_cross_validation_fold_assignment = self.keep_cross_validation_fold_assignment
			)


		self.model.train(
			x=X.col_names[1:], 
			y=X.col_names[0], 
			training_frame=X,
			validation_frame = None, 
			leaderboard_frame = None, 
			blending_frame = None,
		    fold_column = None, 
		    weights_column = None, 
		    # ignored_columns = self.ignored_columns 
		) 
		X=None
		gc.collect()
		return self

	def predict(self, X): 
		"""
		predict is an h2oautoml wrapper for the sklearn function predict. It takes in test sample X and
		returns predictions.

		"""
		X = self.build_matrix(X)  
		preds = self.model.predict(X).as_data_frame().values
		preds = preds[:,0]
		X=None
		gc.collect()
		return preds

	def predict_proba(self, X): 
		"""
		predict_proba is an h2oautoml wrapper for the corresponding sklearn function. It takes in 
		test sample X and returns a matrix containing probabilities for the different class predictions.

		"""
		X = self.build_matrix(X)
		preds = self.model.predict(X ).as_data_frame().values[:,1:]     
		X=None
		gc.collect()    
		return preds

	def close_cluster(self): #should be invoked after all preds are done to relase memory  
		h2o.cluster().shutdown()  
		gc.collect()     


