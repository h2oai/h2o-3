Deep Learning
--------------

Introduction
~~~~~~~~~~~~

H2O’s Deep Learning is based on a multi-layer feed-forward artificial
neural network that is trained with stochastic gradient descent using
back-propagation. The network can contain a large number of hidden
layers consisting of neurons with tanh, rectifier and maxout activation
functions. Advanced features such as adaptive learning rate, rate
annealing, momentum training, dropout, L1 or L2 regularization,
checkpointing and grid search enable high predictive accuracy. Each
compute node trains a copy of the global model parameters on its local
data with multi-threading (asynchronously), and contributes periodically
to the global model via model averaging across the network.

Quick Start
~~~~~~~~~~~~
* H2O + TensorFlow on AWS GPU Tutorial (Python Notebook) `[Blog] <http://blog.h2o.ai/2016/07/h2o-tensorflow-on-aws-gpu/>`__ `[Github] <https://github.com/h2oai/sparkling-water/blob/master/py/examples/notebooks/TensorFlowDeepLearning.ipynb>`__
* The Definitive Performance Tuning Guide for H2O Deep Learning `[Blog] <http://blog.h2o.ai/2015/08/deep-learning-performance-august/>`__
* Deep learning in H2O with Arno Candel (Overview) `[Youtube] <https://www.youtube.com/watch?v=zGdXaRug7LI/>`__
* Top 10 tips and tricks `[Youtube] <https://www.youtube.com/watch?v=LM255qs8Zsk/>`__
* NYC Tour Deep Learning Panel: Tensorflow, Mxnet, Caffe `[Youtube] <https://www.youtube.com/watch?v=KWdkVoKJG3U/>`__
* Deep Water project: `[Github] <http://github.com/h2oai/deepwater/>`__

Defining a Deep Learning Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

H2O Deep Learning models have many input parameters, many of which are
only accessible via the expert mode. For most cases, use the default
values. Please read the following instructions before building extensive
Deep Learning models. The application of grid search and successive
continuation of winning models via checkpoint restart is highly
recommended, as model performance can vary greatly.

-  **model\_id**: (Optional) Specify a custom name for the model to use as
   a reference. By default, H2O automatically generates a destination
   key.

-  **training\_frame**: (Required) Specify the dataset used to build the
   model. **NOTE**: In Flow, if you click the **Build a model** button from the
   ``Parse`` cell, the training frame is entered automatically.

-  **validation\_frame**: (Optional) Specify the dataset used to evaluate
   the accuracy of the model.

-  **nfolds**: Specify the number of folds for cross-validation.
   
    **Note**: Cross-validation is not supported when autoencoder is enabled.

-  **response\_column**: Specify the column to use as the independent
   variable. The data can be numeric or categorical.

-  **ignored\_columns**: (Optional) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column
   name to add it to the list of columns excluded from the model. To add
   all columns, click the **All** button. To remove a column from the
   list of ignored columns, click the X next to the column name. To
   remove all columns from the list of ignored columns, click the
   **None** button. To search for a specific column, type the column
   name in the **Search** field above the column list. To only show
   columns with a specific percentage of missing values, specify the
   percentage in the **Only show columns with more than 0% missing
   values** field. To change the selections for the hidden columns, use
   the **Select Visible** or **Deselect Visible** buttons.

-  **ignore\_const\_cols**: Specify whether to ignore constant
   training columns, since no information can be gained from them. This
   option is enabled by default.

-  **activation**: Specify the activation function (Tahn, Tahn with
   dropout, Rectifier, Rectifier with dropout, Maxout, Maxout with
   dropout).
   
    **Note**: **Maxout** is not supported when **autoencoder** is enabled.

-  **hidden**: Specify the hidden layer sizes (e.g., 100,100). The value
   must be positive.

-  **epochs**: Specify the number of times to iterate (stream) the
   dataset. The value can be a fraction.

-  **variable\_importances**: Specify whether to compute variable
   importance. This option is not enabled by default.

-  **fold\_assignment**: (Applicable only if a value for **nfolds** is
   specified and **fold\_column** is not specified) Specify the
   cross-validation fold assignment scheme. The available options are
   AUTO (which is Random), Random, 
   `Modulo <https://en.wikipedia.org/wiki/Modulo_operation>`__, or Stratified (which will stratify the folds based on the response variable for classification problems).

-  **fold\_column**: Specify the column that contains the
   cross-validation fold index assignment per observation.

-  **weights\_column**: Specify a column to use for the observation
   weights, which are used for bias correction. The specified
   ``weights_column`` must be included in the specified
   ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  **offset\_column**: (Applicable for regression only) Specify a column
   to use as the offset. 
   
    **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__.

-  **balance\_classes**: (Applicable for classification only) Specify whether to oversample the minority classes to balance the class distribution. This option is not enabled by default and can increase the data frame size. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **Max\_after\_balance\_size** parameter.

-  **standardize**: If enabled, automatically standardize the data (mean
   0, variance 0). If disabled, the user must provide properly scaled
   input data.

-  **max\_confusion\_matrix\_size**: Specify the maximum size (in number
   of classes) for confusion matrices to be printed in the Logs.

-  **max\_hit\_ratio\_k**: Specify the maximum number (top K) of
   predictions to use for hit ratio computation. Applicable to
   multi-class only. To disable, enter 0.

-  **checkpoint**: Enter a model key associated with a
   previously-trained Deep Learning model. Use this option to build a
   new model as a continuation of a previously-generated model.
   
    **Note**: Cross-validation is not supported during checkpoint restarts.

-  **use\_all\_factor\_levels**: Specify whether to use all factor
   levels in the possible set of predictors; if you enable this option,
   sufficient regularization is required. By default, the first factor
   level is skipped. For Deep Learning models, this option is useful for
   determining variable importances and is automatically enabled if the
   autoencoder is selected.

-  **train\_samples\_per\_iteration**: Specify the number of global
   training samples per MapReduce iteration. To specify one epoch, enter
   0. To specify all available data (e.g., replicated training data),
   enter -1. To use the automatic values, enter -2.

-  **adaptive\_rate**: Specify whether to enable the adaptive
   learning rate (ADADELTA). This option is enabled by default.

-  **input\_dropout\_ratio**: Specify the input layer dropout ratio to
   improve generalization. Suggested values are 0.1 or 0.2.

-  **hidden\_dropout\_ratios**: (Applicable only if the activation type
   is **TanhWithDropout**, **RectifierWithDropout**, or
   **MaxoutWithDropout**) Specify the hidden layer dropout ratio to
   improve generalization. Specify one value per hidden layer. The range
   is >= 0 to <1, and the default is 0.5.

-  **l1**: Specify the L1 regularization to add stability and improve
   generalization; sets the value of many weights to 0.

-  **l2**: Specify the L2 regularization to add stability and improve
   generalization; sets the value of many weights to smaller values.

-  **loss**: Specify the loss function. The options are Automatic,
   CrossEntropy, Quadratic, Huber, or Absolute and the default value is
   Automatic. 
   
    - Use **Absolute**, **Quadratic**, or **Huber** for regression 
    - Use **Absolute**, **Quadratic**, **Huber**, or **CrossEntropy** for classification

-  **distribution**: Specify the distribution type from the drop-down
   list. The options are auto, bernoulli, multinomial, gaussian,
   poisson, gamma, laplace, quantile or tweedie.

-  **quantile\_alpha**: (Only applicable if *Quantile* is specified for
   **distribution**) Specify the quantile to be used for Quantile
   Regression.

-  **tweedie\_power**: (Only applicable if *Tweedie* is specified for
   **distribution**) Specify the Tweedie power. The range is from 1 to 2. 
   
    - For a normal distribution, enter ``0``.
    - For Poisson distribution, enter ``1``. 
    - For a gamma distribution, enter ``2``. 
    - For a compound Poisson-gamma distribution, enter a value greater than 1 but less than 2. 
    
   For more information, refer to `Tweedie distribution <https://en.wikipedia.org/wiki/Tweedie_distribution>`__.

-  **huber\_alpha**: Specify the desired quantile for Huber/M-regression (the threshold between quadratic and linear loss). This value must be between 0 and 1.

-  **score\_interval**: Specify the shortest time interval (in seconds)
   to wait between model scoring.

-  **score\_training\_samples**: Specify the number of training set
   samples for scoring. The value must be >= 0. To use all training
   samples, enter 0.

-  **score\_validation\_samples**: (Applicable only if
   **validation\_frame** is specified) Specify the number of validation
   set samples for scoring. The value must be >= 0. To use all
   validation samples, enter 0.

-  **score\_duty\_cycle**: Specify the maximum duty cycle fraction for
   scoring. A lower value results in more training and a higher value
   results in more scoring.

-  **stopping\_rounds**: Stops training when the option selected for
   **stopping\_metric** doesn't improve for the specified number of
   training rounds, based on a simple moving average. To disable this
   feature, specify ``0``. The metric is computed on the validation data
   (if provided); otherwise, training data is used. When used with
   **overwrite\_with\_best\_model**, the final model is the best model
   generated for the given **stopping\_metric** option. 
   
   **Note**: If cross-validation is enabled:

     1. All cross-validation models stop training when the validation metric doesn't improve.
     2. The main model runs for the mean number of epochs.
     3. N+1 models do *not* use **overwrite\_with\_best\_model**
     4. N+1 models may be off by the number specified for **stopping\_rounds** from the best model, but the cross-validation metric estimates the performance of the main model for the resulting number of epochs (which may be fewer than the specified number of epochs).

-  **stopping\_metric**: Specify the metric to use for early stopping.
   The available options are:

   -  **AUTO**: Logloss for classification, deviance for regression
   -  **deviance**
   -  **logloss**
   -  **MSE**
   -  **AUC**
   -  **r2**
   -  **misclassification**

-  **stopping\_tolerance**: Specify the relative tolerance for the
   metric-based stopping to stop training if the improvement is less
   than this value.

-  **autoencoder**: Specify whether to enable the Deep Learning
   autoencoder. This option is not enabled by default. 
   
    **Note**: Cross-validation is not supported when autoencoder is enabled.

-  **max\_runtime\_secs**: Maximum allowed runtime in seconds for model
   training. Use 0 to disable.

-  **class\_sampling\_factors**: (Applicable only for classification and
   when **balance\_classes** is enabled) Specify the per-class (in
   lexicographical order) over/under-sampling ratios. By default, these
   ratios are automatically computed during training to obtain the class
   balance.

-  **max\_after\_balance\_size**: Specify the maximum relative size of
   the training data after balancing class counts (**balance\_classes**
   must be enabled). The value can be less than 1.0.

-  **overwrite\_with\_best\_model**: Specify whether to overwrite
   the final model with the best model found during training, based on
   the option specified for **stopping\_metric**. This option is enabled
   by default.

-  **target\_ratio\_comm\_to\_comp**: Specify the target ratio of
   communication overhead to computation. This option is only enabled
   for multi-node operation and if **train\_samples\_per\_iteration**
   equals -2 (auto-tuning).

-  **seed**: Specify the random number generator (RNG) seed for
   algorithm components dependent on randomization. The seed is
   consistent for each H2O instance so that you can create models with
   the same starting conditions in alternative configurations.

-  **rho**: (Applicable only if **adaptive\_rate** is enabled) Specify
   the adaptive learning rate time decay factor.

-  **epsilon**:(Applicable only if **adaptive\_rate** is enabled)
   Specify the adaptive learning rate time smoothing factor to avoid
   dividing by zero.

-  **max\_w2**: Specify the constraint for the squared sum of the
   incoming weights per unit (e.g., for Rectifier).

-  **initial\_weight\_distribution**: Specify the initial weight
   distribution (Uniform Adaptive, Uniform, or Normal).

-  **regression\_stop**: (Regression models only) Specify the stopping
   criterion for regression error (MSE) on the training data. To disable
   this option, enter -1.

-  **diagnostics**: Specify whether to compute the variable
   importances for input features (using the Gedeon method). For large
   networks, enabling this option can reduce speed. This option is
   enabled by default.

-  **fast\_mode**: Specify whether to enable fast mode, a minor
   approximation in back-propagation. This option is enabled by
   default.

-  **force\_load\_balance**: Specify whether to force extra load
   balancing to increase training speed for small datasets and use all
   cores. This option is enabled by default.

-  **single\_node\_mode**: Specify whether to force H2O to run on a
   single node for fine-tuning of model parameters. This option is not
   enabled by default.

-  **shuffle\_training\_data**: Specify whether to shuffle the
   training data. This option is recommended if the training data is
   replicated and the value of **train\_samples\_per\_iteration** is
   close to the number of nodes times the number of rows. This option is
   not enabled by default.

-  **missing\_values\_handling**: Specify how to handle missing values
   (Skip or MeanImputation).

-  **quiet\_mode**: Specify whether to display less output in the
   standard output. This option is not enabled by default.

-  **sparse**: Specify whether to enable sparse data handling, which
   is more efficient for data with many zero values.

-  **col\_major**: Specify whether to use a column major weight
   matrix for the input layer. This option can speed up forward
   propagation but may reduce the speed of backpropagation. This option
   is not enabled by default.

-  **average\_activation**: Specify the average activation for the
   sparse autoencoder. If **Rectifier** is used, the
   **average\_activation** value must be positive.

-  **sparsity\_beta**: (Applicable only if **autoencoder** is enabled)
   Specify the sparsity-based regularization optimization. For more
   information, refer to the following
   `link <http://www.mit.edu/~9.520/spring09/Classes/class11_sparsity.pdf>`__.

-  **max\_categorical\_features**: Specify the maximum number of
   categorical features enforced via hashing. The value must be at least
   one.

-  **reproducible**: Specify whether to force reproducibility on small data. If this option is enabled, the model takes more time to generate because it uses only one thread.

-  **export\_weights\_and\_biases**: Specify whether to export the neural network
   weights and biases as H2O frames.

-  **elastic\_averaging**: Specify whether to enable elastic averaging between computing
   nodes, which can improve distributed model convergence.

-  **rate**: (Applicable only if **adaptive\_rate** is disabled) Specify
   the learning rate. Higher values result in a less stable model, while
   lower values lead to slower convergence.

-  **rate\_annealing**: (Applicable only if **adaptive\_rate** is
   disabled) Specify the rate annealing value. The rate annealing is
   calculated as **rate**\ (1 + **rate\_annealing** \* samples).

-  **rate\_decay**: (Applicable only if **adaptive\_rate** is disabled)
   Specify the rate decay factor between layers. The rate decay is
   calculated as (N-th layer: **rate** \* alpha^(N-1)).

-  **momentum\_start**: (Applicable only if **adaptive\_rate** is
   disabled) Specify the initial momentum at the beginning of training;
   we suggest 0.5.

-  **momentum\_ramp**: (Applicable only if **adaptive\_rate** is
   disabled) Specify the number of training samples for which the
   momentum increases.

-  **momentum\_stable**: (Applicable only if **adaptive\_rate** is
   disabled) Specify the final momentum after the ramp is over; we
   suggest 0.99.

-  **nesterov\_accelerated\_gradient**: (Applicable only if
   **adaptive\_rate** is disabled) Enables the `Nesterov Accelerated
   Gradient <http://premolab.ru/pub_files/pub88/qhkDNEyp8.pdf>`__.

-  **initial\_weight\_scale**: (Applicable only if
   **initial\_weight\_distribution** is **Uniform** or **Normal**)
   Specify the scale of the distribution function. For **Uniform**, the
   values are drawn uniformly. For **Normal**, the values are drawn from
   a Normal distribution with a standard deviation.

Interpreting a Deep Learning Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To view the results, click the View button. The output for the Deep
Learning model includes the following information for both the training
and testing sets:

-  Model parameters (hidden)
-  A chart of the variable importances
-  A graph of the scoring history (training MSE and validation MSE vs
   epochs)
-  Output (model category, weights, biases)
-  Status of neuron layers (layer number, units, type, dropout, L1, L2,
   mean rate, rate RMS, momentum, mean weight, weight RMS, mean bias,
   bias RMS)
-  Scoring history in tabular format
-  Training metrics (model name, model checksum name, frame name, frame
   checksum name, description, model category, duration in ms, scoring
   time, predictions, MSE, R2, logloss)
-  Top-K Hit Ratios (for multi-class classification)
-  Confusion matrix (for classification)

FAQ
~~~

-  **How does the algorithm handle missing values during training?**

 Depending on the selected missing value handling policy, they are either imputed mean or the whole row is skipped. The default behavior is mean imputation. Note that categorical variables are imputed by adding an extra "missing" level. Optionally, Deep Learning can skip all rows with any missing values.

-  **How does the algorithm handle missing values during testing?**

 Missing values in the test set will be mean-imputed during scoring.

-  **What happens if the response has missing values?**

 No errors will occur, but nothing will be learned from rows containing missing the response.

-  **What happens when you try to predict on a categorical level not
   seen during training?**

 For an unseen categorical level in the test set, Deep Learning makes an extra input neuron that remains untrained and contributes some random amount to the subsequent layer.

-  **Does it matter if the data is sorted?**

 Yes, since the training set is processed in order. Depending whether ``train_samples_per_iteration`` is enabled, some rows will be skipped. If ``shuffle_training_data`` is enabled, then each thread that is processing a small subset of rows will process rows randomly, but it is not a global shuffle.

-  **Should data be shuffled before training?**

 Yes, the data should be shuffled before training, especially if the dataset is sorted.

-  **How does the algorithm handle highly imbalanced data in a response
   column?**

 Specify ``balance_classes``, ``class_sampling_factors`` and ``max_after_balance_size`` to control over/under-sampling.

-  **What if there are a large number of columns?**

 The input neuron layer's size is scaled to the number of input features, so as the number of columns increases, the model complexity increases as well.

-  **What if there are a large number of categorical factor levels?**

 This is something to look out for. Say you have three columns: zip code (70k levels), height, and income. The resulting number of internally one-hot encoded features will be 70,002 and only 3 of them will be activated (non-zero). If the first hidden layer has 200 neurons, then the resulting weight matrix will be of size 70,002 x 200, which can take a long time to train and converge. In this case, we recommend either reducing the number of categorical factor levels upfront (e.g., using ``h2o.interaction()`` from R), or specifying ``max_categorical_features`` to use feature hashing to reduce the dimensionality.

-  **How does your Deep Learning Autoencoder work? Is it deep or
   shallow?**

 H2O’s DL autoencoder is based on the standard deep (multi-layer) neural net architecture, where the entire network is learned together, instead of being stacked layer-by-layer. The only difference is that no response is required in the input and that the output layer has as many neurons as the input layer. If you don’t achieve convergence, then try using the *Tanh* activation and fewer layers. We have some example test scripts `here <https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/deeplearning/>`__, and even some that show `how stacked auto-encoders can be implemented in R <https://github.com/h2oai/h2o-3/blob/master/h2o-r/tests/testdir_algos/deeplearning/runit_deeplearning_stacked_autoencoder_large.R>`__.

-  **When building the model, does Deep Learning use all features or a
   selection of the best features?**

 For Deep Learning, all features are used, unless you manually specify that columns should be ignored. Adding an L1 penalty can make the model sparse, but it is still the full size.

-  **What is the relationship between iterations, epochs, and the
   ``train_samples_per_iteration`` parameter?**

 Epochs measures the amount of training. An iteration is one MapReduce (MR) step - essentially, one pass over the data. The ``train_samples_per_iteration`` parameter is the amount of data to use for training for each MR step, which can be more or less than the number of rows.

-  **When do ``reduce()`` calls occur, after each iteration or each
   epoch?**

 Neither; ``reduce()`` calls occur after every two ``map()`` calls, between threads and ultimately between nodes. There are many ``reduce()`` calls, much more than one per MapReduce step (also known as an "iteration"). Epochs are not related to MR iterations, unless you specify ``train_samples_per_iteration`` as ``0`` or ``-1`` (or to number of rows/nodes). Otherwise, one MR iteration can train with an arbitrary number of training samples (as specified by ``train_samples_per_iteration``).

-  **Does each Mapper task work on a separate neural-net model that is
   combined during reduction, or is each Mapper manipulating a shared
   object that's persistent across nodes?**

 Neither; there's one model per compute node, so multiple Mappers/threads share one model, which is why H2O is not reproducible unless a small dataset is used and ``force_load_balance=F`` or ``reproducible=T``, which effectively rebalances to a single chunk and leads to only one thread to launch a ``map()``. The current behavior is simple model averaging; between-node model averaging via "Elastic Averaging" is currently `in progress <https://0xdata.atlassian.net/browse/HEXDEV-206>`__.

-  **Is the loss function and backpropagation performed after each
   individual training sample, each iteration, or at the epoch level?**

 Loss function and backpropagation are performed after each training sample (mini-batch size 1 == online stochastic gradient descent).

-  **When using Hinton's dropout and specifying an input dropout ratio
   of ~20% and ``train_samples_per_iteration`` is set to 50, will each
   of the 50 samples have a different set of the 20% input neurons
   suppressed?**

 Yes - suppression is not done at the iteration level across as samples in that iteration. The dropout mask is different for each training sample.

-  **When using dropout parameters such as ``input_dropout_ratio``, what
   happens if you use only ``Rectifier`` instead of
   ``RectifierWithDropout`` in the activation parameter?**

 The amount of dropout on the input layer can be specified for all activation functions, but hidden layer dropout is only supported is set to ``WithDropout``. The default hidden dropout is 50%, so you don't need to specify anything but the activation type to get good results, but you can set the hidden dropout values for each layer separately.

-  **When using the ``score_validation_sampling`` and
   ``score_training_samples`` parameters, is scoring done at the end of
   the Deep Learning run?**

 The majority of scoring takes place after each MR iteration. After the iteration is complete, it may or may not be scored, depending on two criteria: the time since the last scoring and the time needed for scoring.

 The maximum time between scoring (``score_interval``, default = 5 seconds) and the maximum fraction of time spent scoring (``score_duty_cycle``) independently of loss function, backpropagation, etc.

 Of course, using more training or validation samples will increase the time for scoring, as well as scoring more frequently. For more information about how this affects runtime, refer to the `Deep Learning Performance Guide <http://h2o.ai/blog/2015/02/deep-learning-performance/>`__.

-  **How does the validation frame affect the built neuron network?**

 The validation frame is only used for scoring and does not directly affect the model. However, the validation frame can be used stopping the model early if ``overwrite_with_best_model = T``, which is the default. If this parameter is enabled, the model with the lowest validation error is displayed at the end of the training.

 By default, the validation frame is used to tune the model parameters (such as number of epochs) and will return the best model as measured by the validation metrics, depending on how often the validation metrics are computed (``score_duty_cycle``) and whether the validation frame itself was sampled.

 Model-internal sampling of the validation frame (``score_validation_samples`` and ``score_validation_sampling`` for optional stratification) will affect early stopping quality. If you specify a validation frame but set ``score_validation_samples`` to more than the number of rows in the validation frame (instead of 0, which represents the entire frame), the validation metrics received at the end of training will not be reproducible, since the model does internal sampling.

-  **Are there any best practices for building a model using
   checkpointing?**

 In general, to get the best possible model, we recommend building a model with ``train_samples_per_iteration = -2`` (which is the default value for auto-tuning) and saving it.

 To improve the initial model, start from the previous model and add iterations by building another model, setting the checkpoint to the previous model, and changing ``train_samples_per_iteration``, ``target_ratio_comm_to_comp``, or other parameters.

 If you don't know your model ID because it was generated by R, look it up using ``h2o.ls()``. By default, Deep Learning model names start with ``deeplearning_`` To view the model, use ``m <- h2o.getModel("my_model_id")`` or ``summary(m)``.

 There are a few ways to manage checkpoint restarts:

  *Option 1*: (Multi-node only) Leave ``train_samples_per_iteration = -2``, increase ``target_comm_to_comp`` from 0.05 to 0.25 or 0.5, which provides more communication. This should result in a better model when using multiple nodes. **Note:** This does not affect single-node performance.

  *Option 2*: (Single or multi-node) Set ``train_samples_per_iteration`` to (N), where (N) is the number of training samples used for training by the entire cluster for one iteration. Each of the nodes then trains on (N) randomly-chosen rows for every iteration. The number defined as (N) depends on the dataset size and the model complexity.

  *Option 3*: (Single or multi-node) Change regularization parameters such as ``l1, l2, max_w2, input_droput_ratio`` or ``hidden_dropout_ratios``. We recommend build the first mode using ``RectifierWithDropout``, ``input_dropout_ratio = 0`` (if there is suspected noise in the input), and ``hidden_dropout_ratios=c(0,0,0)`` (for the ability to enable dropout regularization later).

-  **How does class balancing work?**

 The ``max_after_balance_size`` parameter defines the maximum size of the over-sampled dataset. For example, if ``max_after_balance_size = 3``, the over-sampled dataset will not be greater than three times the size of the original dataset.

 For example, if you have five classes with priors of 90%, 2.5%, 2.5%, and 2.5% (out of a total of one million rows) and you oversample to obtain a class balance using ``balance_classes = T``, the result is all four minor classes are oversampled by forty times and the total dataset will be 4.5 times as large as the original dataset (900,000 rows of each class). If ``max_after_balance_size = 3``, all five balance classes are reduced by 3/5 resulting in 600,000 rows each (three million total).

 To specify the per-class over- or under-sampling factors, use ``class_sampling_factors``. In the previous example, the default behavior with ``balance_classes`` is equivalent to ``c(1,40,40,40,40)``, while when ``max_after_balance\size = 3``, the results would be ``c(3/5,40*3/5,40*3/5,40*3/5)``.

 In all cases, the probabilities are adjusted to the pre-sampled space, so the minority classes will have lower average final probabilities than the majority class, even if they were sampled to reach class balance.

-  **How is variable importance calculated for Deep Learning?**

 For Deep Learning, variable importance is calculated using the Gedeon method.

--------------

Deep Learning Algorithm
~~~~~~~~~~~~~~~~~~~~~~~

To compute deviance for a Deep Learning regression model, the following
formula is used:

 Loss = Quadratic -> MSE==Deviance For Absolute/Laplace or Huber -> MSE != Deviance

For more information about how the Deep Learning algorithm works, refer
to the `Deep Learning booklet <http://h2o.ai/resources>`__.

References
~~~~~~~~~~

`"Deep Learning." *Wikipedia: The free encyclopedia*. Wikimedia
Foundation, Inc. 1 May 2015. Web. 4 May
2015. <http://en.wikipedia.org/wiki/Deep_learning>`__

`"Artificial Neural Network." *Wikipedia: The free encyclopedia*.
Wikimedia Foundation, Inc. 22 April 2015. Web. 4 May
2015. <http://en.wikipedia.org/wiki/Artificial_neural_network>`__

`Zeiler, Matthew D. 'ADADELTA: An Adaptive Learning Rate Method'.
Arxiv.org. N.p., 2012. Web. 4 May
2015. <http://arxiv.org/abs/1212.5701>`__

`Sutskever, Ilya et al. "On the importance of initialization and
momementum in deep learning." JMLR:W&CP vol. 28.
(2013). <http://www.cs.toronto.edu/~fritz/absps/momentum.pdf>`__

`Hinton, G.E. et. al. "Improving neural networks by preventing
co-adaptation of feature detectors." University of Toronto.
(2012). <http://arxiv.org/pdf/1207.0580.pdf>`__

`Wager, Stefan et. al. "Dropout Training as Adaptive Regularization."
Advances in Neural Information Processing Systems.
(2013). <http://arxiv.org/abs/1307.1493>`__

`Gedeon, TD. "Data mining of inputs: analysing magnitude and functional
measures." University of New South Wales.
(1997). <http://www.ncbi.nlm.nih.gov/pubmed/9327276>`__

`Candel, Arno and Parmar, Viraj. "Deep Learning with H2O." H2O.ai, Inc.
(2015). <https://leanpub.com/deeplearning>`__

`Deep Learning
Training <http://learn.h2o.ai/content/hands-on_training/deep_learning.html>`__

`Slideshare slide
decks <http://www.slideshare.net/0xdata/presentations?order=latest>`__

`Youtube channel <https://www.youtube.com/user/0xdata>`__

`Candel, Arno. "The Definitive Performance Tuning Guide for H2O Deep
Learning." H2O.ai, Inc.
(2015). <http://h2o.ai/blog/2015/02/deep-learning-performance/>`__

`Niu, Feng, et al. "Hogwild!: A lock-free approach to parallelizing
stochastic gradient descent." Advances in Neural Information Processing
Systems 24 (2011): 693-701. (algorithm implemented is on
p.5) <https://papers.nips.cc/paper/4390-hogwild-a-lock-free-approach-to-parallelizing-stochastic-gradient-descent.pdf>`__

`Hawkins, Simon et al. "Outlier Detection Using Replicator Neural
Networks." CSIRO Mathematical and Information
Sciences <http://neuro.bstu.by/ai/To-dom/My_research/Paper-0-again/For-research/D-mining/Anomaly-D/KDD-cup-99/NN/dawak02.pdf>`__

