# Model

In general, a model of the user's choosing can be specified either by
finding the list of algorithms at the top of the **Inspect** page when
data are parsed or by selecting the appropriate model from the drop
down menu Model.

- [GLM](glm)
- [K Means](kmeans)
- [Random Forest](rf)
- [PCA](pca)
- [GBM](gbm)
- [Naive Bayes](naive-bayes)
- [Deep Learning](deep-learning)
- [Stochastic Gradient Descent](sgd)

Each model requires that the user provide a .hex key associated with a
data set. Users can often begin typing the name of the original data
source, and select the appropriate .hex key from the auto fill menu
that appears. Users can also find .hex keys for data sets by selecting
View All from the Data drop down menu, or for all H2O actions by
selecting Jobs from the Admin drop down menu.

If a large data set is used in the training and testing of a model,
H2O's capabilities can be bounded by the amount of memory available on
the machine. To utilize H2O's full capability, the amount of memory
available should be about 4 times the file size of the data set, but
not more than the machine's total available memory. For instructions
on how to change the amount of memory allocated to H2O see the Quick
Start Documentation. Advanced users should run H2O on a cloud
computing resource or server.

## Grid Search Models

GLM, and GBM both offer Grid Search Models. In order to access this
option in GLM uses should select GLM Grid Search from the **Model**
drop down menu.

Each grid search modeling option allows users to generate multiple models
simultaneously, and compare model criteria directly, rather than
separately building models one at a time. Users can specify multiple
model configurations by entering different values of tuning parameters
separated by coma. For example, to specify three different values of
lambda, a regularization parameter in GLM Grid search users might
enter: .001, .05, .1.

When multiple values are specified for many tuning
parameters grid search returns one model for each unique
combination. For example, in GBM, if users specify Ntrees as 50, 100,
200, and also specify learning rates of 0.01, and 0.05, six models
will be returned.

Grid search results return a table showing the combination of tuning
parameters used for each model and basic model evaluation information,
as well as a link to each model. Users can access the details of each
model by clicking on the model links in the table.

