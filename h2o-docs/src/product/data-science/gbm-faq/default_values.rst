Default Values
^^^^^^^^^^^^^^

- **Should I use GBM’s out-of-the-box default values? Why were these values chosen? When should you use the default values? When should you change the values?**

 GBM’s default values are chosen to produce fast results with decent accuracy, but the values are not optimized to generate the best model performance. You should use the default values to get a benchmark of your model’s performance, then you should always adjust the tuning parameters using grid search with cross validation. (See "Which parameters are used for tuning" and "How to perform grid search with GBM.")
