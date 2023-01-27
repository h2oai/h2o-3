Generated Metrics
^^^^^^^^^^^^^^^^^

What evaluation metrics are available for GBM?
##############################################

- MSE
- RMSE
- MAE
- RMSLE
- Mean Residual Deviance
- Mean Residual Deviance Not Optimized

**Notes**: MSE = Mean Residual Deviance for Gaussian distributions. Also, look at Mean Residual Deviance when using quantile distributions. Mean Residual Deviance Not Optimized is currently only implemented for Poisson distribution. Mean Residual Deviance Not Optimized is calculated from the original deviance formula. Mean Residual Deviance uses an optimized deviance formula for some distributions which provides a different output than the original deviance formula would. 


How is variable importance determined?
######################################

Variable importance is determined by calculating the relative influence of each variable: whether that variable was selected during splitting in the tree building process and how much the squared error (over all trees) improved (decreased) as a result.
