.. _Parameters:

Appendix A - Parameters
=======================

This Appendix provides detailed descriptions of parameters that can be specified in the H2O algorithms. In addition, each parameter also includes the algorithms that support the parameter, whether the parameter is a hyperparameter (can be used in grid search), links to any related parameters, and R and Python examples showing the parameter in use. 

**Notes**:

- This Appendix is a work in progress.
- For parameters that are supported in multiple algorithms, the included example uses the GBM or GLM algorithm.

.. toctree::
   :maxdepth: 2

   data-science/algo-params/alpha
   data-science/algo-params/balance_classes
   data-science/algo-params/beta_constraints
   data-science/algo-params/beta_epsilon
   data-science/algo-params/binomial_double_trees
   data-science/algo-params/build_tree_one_node
   data-science/algo-params/calibrate_model
   data-science/algo-params/calibration_frame
   data-science/algo-params/categorical_encoding
   data-science/algo-params/checkpoint
   data-science/algo-params/class_sampling_factors
   data-science/algo-params/col_sample_rate
   data-science/algo-params/col_sample_rate_change_per_level
   data-science/algo-params/col_sample_rate_per_tree
   data-science/algo-params/compute_metrics
   data-science/algo-params/compute_p_values
   data-science/algo-params/distribution
   data-science/algo-params/early_stopping
   data-science/algo-params/eps_prob
   data-science/algo-params/eps_sdev
   data-science/algo-params/estimate_k
   data-science/algo-params/family
   data-science/algo-params/fold_assignment
   data-science/algo-params/fold_column
   data-science/algo-params/gradient_epsilon
   data-science/algo-params/histogram_type
   data-science/algo-params/huber_alpha
   data-science/algo-params/ignore_const_cols
   data-science/algo-params/ignored_columns
   data-science/algo-params/impute_missing
   data-science/algo-params/init
   data-science/algo-params/interaction_pairs
   data-science/algo-params/interactions
   data-science/algo-params/intercept
   data-science/algo-params/k
   data-science/algo-params/keep_cross_validation_fold_assignment
   data-science/algo-params/keep_cross_validation_models
   data-science/algo-params/keep_cross_validation_predictions
   data-science/algo-params/lambda
   data-science/algo-params/lambda_min_ratio
   data-science/algo-params/lambda_search
   data-science/algo-params/laplace
   data-science/algo-params/learn_rate
   data-science/algo-params/learn_rate_annealing
   data-science/algo-params/link
   data-science/algo-params/max_abs_leafnode_pred
   data-science/algo-params/max_active_predictors
   data-science/algo-params/max_after_balance_size
   data-science/algo-params/max_depth
   data-science/algo-params/max_hit_ratio_k
   data-science/algo-params/max_iterations
   data-science/algo-params/max_runtime_secs
   data-science/algo-params/metalearner_algorithm
   data-science/algo-params/metalearner_params
   data-science/algo-params/min_prob
   data-science/algo-params/min_rows
   data-science/algo-params/min_sdev
   data-science/algo-params/min_split_improvement
   data-science/algo-params/missing_values_handling
   data-science/algo-params/model_id
   data-science/algo-params/mtries
   data-science/algo-params/nbins
   data-science/algo-params/nbins_cats
   data-science/algo-params/nbins_top_level
   data-science/algo-params/nfolds
   data-science/algo-params/nlambdas
   data-science/algo-params/non_negative
   data-science/algo-params/ntrees
   data-science/algo-params/objective_epsilon
   data-science/algo-params/offset_column
   data-science/algo-params/pca_impl
   data-science/algo-params/pca_method
   data-science/algo-params/pred_noise_bandwidth
   data-science/algo-params/prior
   data-science/algo-params/quantile_alpha
   data-science/algo-params/remove_collinear_columns
   data-science/algo-params/sample_rate
   data-science/algo-params/sample_rate_per_class
   data-science/algo-params/sample_size
   data-science/algo-params/score_each_iteration
   data-science/algo-params/score_tree_interval
   data-science/algo-params/seed
   data-science/algo-params/solver
   data-science/algo-params/sort_metric
   data-science/algo-params/standardize
   data-science/algo-params/start_column
   data-science/algo-params/stop_column
   data-science/algo-params/stopping_metric
   data-science/algo-params/stopping_rounds
   data-science/algo-params/stopping_tolerance
   data-science/algo-params/ties
   data-science/algo-params/training_frame
   data-science/algo-params/transform
   data-science/algo-params/tweedie_link_power
   data-science/algo-params/tweedie_power
   data-science/algo-params/tweedie_variance_power
   data-science/algo-params/use_all_factor_levels
   data-science/algo-params/user_points
   data-science/algo-params/validation_frame
   data-science/algo-params/weights_column
   data-science/algo-params/x
   data-science/algo-params/y