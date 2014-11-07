\name{h2o.deeplearning}
\alias{h2o.deeplearning}
\title{
H2O: Deep Learning Neural Networks
}
\description{
Performs Deep Learning neural networks on an \code{\linkS4class{H2OParsedData}} object.
}
\usage{
h2o.deeplearning(x, y, data, key = "",override_with_best_model, classification = TRUE,
    nfolds = 0, validation, checkpoint = "", autoencoder, use_all_factor_levels, 
    activation, hidden, epochs, train_samples_per_iteration, seed, adaptive_rate,
    rho, epsilon, rate, rate_annealing, rate_decay, momentum_start,
    momentum_ramp, momentum_stable, nesterov_accelerated_gradient,
    input_dropout_ratio, hidden_dropout_ratios, l1, l2, max_w2,
    initial_weight_distribution, initial_weight_scale, loss,
    score_interval, score_training_samples, score_validation_samples,
    score_duty_cycle, classification_stop, regression_stop, quiet_mode,
    max_confusion_matrix_size, max_hit_ratio_k, balance_classes, class_sampling_factors,
    max_after_balance_size, score_validation_sampling, diagnostics,
    variable_importances, fast_mode, ignore_const_cols, force_load_balance,
    replicate_training_data, single_node_mode, shuffle_training_data,
    sparse, col_major, max_categorical_features, reproducible)
}

%- maybe also 'usage' for other objects documented here.
\arguments{
        \item{x}{ A vector containing the names of the predictors in the model. }
        \item{y}{ The name of the response variable in the model. }
        \item{data}{ An \code{\linkS4class{H2OParsedData}} object containing the variables in the model. }
        \item{key}{ (Optional) The unique hex key assigned to the resulting model. If none is given, a key will automatically be generated.}
        \item{override_with_best_model}{ If enabled, override the final model with the best model found during training. Defaults to true.}
        \item{classification}{ (Optional) A logical value indicating whether the algorithm should conduct classification. }
        \item{nfolds}{(Optional) Number of folds for cross-validation. If \code{nfolds >= 2}, then \code{validation} must remain empty.}
        \item{validation}{(Optional) An \code{\linkS4class{H2OParsedData}} object indicating the validation dataset used to construct confusion matrix. If left blank, this defaults to the training data when \code{nfolds = 0}.}
        \item{checkpoint}{"Model checkpoint (either key or H2ODeepLearningModel) to resume training with."}
        \item{activation}{A string indicating the activation function to use. Must be either "Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout" or "MaxoutWithDropout".}
        \item{hidden}{ Hidden layer sizes (e.g. c(100,100)}

        %- BEGIN AUTO-GENERATED CODE
        \item{autoencoder}{ Enable auto-encoder for model building.}
        \item{use_all_factor_levels}{ Use all factor levels of categorical variables. Otherwise, the first factor level is omitted (without loss of accuracy). Useful for variable importances and auto-enabled for autoencoder. }
        \item{epochs}{ How many times the dataset should be iterated (streamed), can be fractional}
        \item{train_samples_per_iteration}{ Number of training samples (globally) per MapReduce iteration. Special values are 0: one epoch, -1: all available data (e.g., replicated training data), -2: auto-tuning (default)}
        \item{seed}{ Seed for random numbers (affects sampling) - Note: only reproducible when running single threaded}
        \item{adaptive_rate}{ Adaptive learning rate (ADADELTA)}
        \item{rho}{ Adaptive learning rate time decay factor (similarity to prior updates)}
        \item{epsilon}{ Adaptive learning rate smoothing factor (to avoid divisions by zero and allow progress)}
        \item{rate}{ Learning rate (higher => less stable, lower => slower convergence)}
        \item{rate_annealing}{ Learning rate annealing: rate / (1 + rate_annealing * samples)}
        \item{rate_decay}{ Learning rate decay factor between layers (N-th layer: rate*alpha^(N-1))}
        \item{momentum_start}{ Initial momentum at the beginning of training (try 0.5)}
        \item{momentum_ramp}{ Number of training samples for which momentum increases}
        \item{momentum_stable}{ Final momentum after the ramp is over (try 0.99)}
        \item{nesterov_accelerated_gradient}{ Use Nesterov accelerated gradient (recommended)}
        \item{input_dropout_ratio}{ Input layer dropout ratio (can improve generalization, try 0.1 or 0.2)}
        \item{hidden_dropout_ratios}{ Hidden layer dropout ratios (can improve generalization), specify one value per hidden layer, defaults to 0.5}
        \item{l1}{ L1 regularization (can add stability and improve generalization, causes many weights to become 0)}
        \item{l2}{ L2 regularization (can add stability and improve generalization, causes many weights to be small}
        \item{max_w2}{ Constraint for squared sum of incoming weights per unit (e.g. for Rectifier)}
        \item{initial_weight_distribution}{ Initial Weight Distribution}
        \item{initial_weight_scale}{ Uniform: -value...value, Normal: stddev}
        \item{loss}{ Loss function}
        \item{score_interval}{ Shortest time interval (in secs) between model scoring}
        \item{score_training_samples}{ Number of training set samples for scoring (0 for all)}
        \item{score_validation_samples}{ Number of validation set samples for scoring (0 for all)}
        \item{score_duty_cycle}{ Maximum duty cycle fraction for scoring (lower: more training, higher: more scoring).}
        \item{classification_stop}{ Stopping criterion for classification error fraction on training data (-1 to disable)}
        \item{regression_stop}{ Stopping criterion for regression error (MSE) on training data (-1 to disable)}
        \item{quiet_mode}{ Enable quiet mode for less output to standard output}
        \item{max_confusion_matrix_size}{ Max. size (number of classes) for confusion matrices to be shown}
        \item{max_hit_ratio_k}{ Max. number (top K) of predictions to use for hit ratio computation (for multi-class only, 0 to disable)}
        \item{balance_classes}{ Balance training data class counts via over/under-sampling (for imbalanced data)}
        \item{class_sampling_factors}{ Desired over/under-sampling ratios per class (lexicographic order). }
        \item{max_after_balance_size}{ Maximum relative size of the training data after balancing class counts (can be less than 1.0)}
        \item{score_validation_sampling}{ Method used to sample validation dataset for scoring}
        \item{diagnostics}{ Enable diagnostics for hidden layers}
        \item{variable_importances}{ Compute variable importances for input features (Gedeon method) - can be slow for large networks}
        \item{fast_mode}{ Enable fast mode (minor approximation in back-propagation)}
        \item{ignore_const_cols}{ Ignore constant training columns (no information can be gained anyway)}
        \item{force_load_balance}{ Force extra load balancing to increase training speed for small datasets (to keep all cores busy)}
        \item{replicate_training_data}{ Replicate the entire training dataset onto every node for faster training on small datasets}
        \item{single_node_mode}{ Run on a single node for fine-tuning of model parameters}
        \item{shuffle_training_data}{ Enable shuffling of training data (recommended if training data is replicated and train_samples_per_iteration is close to #nodes x #rows)}
        \item{sparse}{ Sparse data handling (Experimental)}
        \item{col_major}{ Use a column major weight matrix for input layer. Can speed up forward propagation, but might slow down backpropagation (Experimental)}
        \item{max_categorical_features}{ Max. number of categorical features, enforced via hashing (Experimental) }
        \item{reproducible}{ Force reproducibility on small data (will be slow - only uses 1 thread) }
        %- END AUTO-GENERATED CODE
  }

\value{
An object of class \code{\linkS4class{H2ODeepLearningModel}} with slots key, data, valid (the validation dataset) and model, where the last is a list of the following components:
  \item{confusion }{The confusion matrix of the response, with actual observations as rows and predicted values as columns.}
  \item{train_class_err }{Classification error on the training dataset.}
  \item{train_sqr_err }{Mean-squared error on the training dataset.}
  \item{valid_class_err }{Classification error on the validation dataset.}
  \item{valid_sqr_err }{Mean-squared error on the validation dataset.}
}

\examples{
# -- CRAN examples begin --
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
h2o.deeplearning(x = 1:4, y = 5, data = iris.hex, activation = "Tanh", 
                 hidden = c(10, 10), epochs = 5)
# -- CRAN examples end --

\dontrun{
# DeepLearning variable importance
# Also see:
#   https://github.com/0xdata/h2o/blob/master/R/tests/testdir_demos/runit_demo_VI_all_algos.R
data.hex = h2o.importFile(
  localH2O,
  path = "https://raw.github.com/0xdata/h2o/master/smalldata/bank-additional-full.csv",
  key = "data.hex")
myX = 1:20
myY="y"
my.dl = h2o.deeplearning(x=myX,y=myY,data=data.hex,classification=T,activation="Tanh",
                         hidden=c(10,10,10),epochs=12,variable_importances=T)
dl.VI =my.dl@model$varimp
print(dl.VI)
}
}
