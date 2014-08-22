# Generalized Linear Model

Generalized Linear Models (GLM) estimates regression models for
outcomes following exponential distributions in general. In addition
to the Gaussian (i.e. normal) distribution, these include Poisson, binomial, gamma
and Tweedie distributions. Each serves a different purpose, and
depending on distribution and link function choice, it can be used
either for prediction or classification.

**The GLM suite includes**

- Gaussian regression
- Poisson regression
- Binomial regression
- Gamma regression
- Tweedie regression

## Defining a GLM Model

### Response
Response is the model dependent variable, often noted as Y.
The specific features of a dependent variable should be considered
when choosing the appropriate distribution for estimating a model.

*Gaussian*
Y variables must be continuous and real valued.

*Binomial*
Y variables are discrete and valued only at 0 or 1.

*Poisson*
Y variables are discrete and valued strictly greater than 0. Poisson
models are used to model count data.

*Gamma*
Y variables are discrete and valued strictly greater than 0.

*Tweedie*
Y variables follow a Poisson-Gamma mixed compound distribution. This
is often also called a zero-inflated Poisson, and is used when Y
variables follow a distribution with a large mass at 0, and integer valued
counts for all non-zero observations.


### Ignored Columns

This field will auto populate a list of the columns from the data
set in use. The user selected set of columns will be omitted from
the modeling process. H2O omits the dependent
variable specified in Y, as well as any columns with a
constant value. Constant columns are omitted because the variances
of such columns are 0. In this case Y is independent of X, and X
is not an explanatory variable.

H2O factors (also called categorical variables or
enumerators) as if they are collapsed columns of binomial
variables at each  factor level. When a factor is encountered, H2 O determines the  cardinality of the variable, and
generates a unique regression coefficient for all but one of the
factor levels. The omitted  factor level becomes the reference
level. H2O omits the first level in the ordered
set. For instance, if factor levels are A, B, and C, level A will
be omitted.

Please note that H2O does not currently return a warning when
users predict on data outside of the range on which the model was
originally specified. For example, H2O allows a model
to be  trained on data with X between (-1, 10), and then applied
to predicting  on data where the range of X is (-10, 10) without
warning. This is also true in the analogous case for predicting and
training on factors. It is the user's responsibility to ensure
that out of data prediction is undertaken with caution, as the
veracity of the original results are often constrained to the
data range used in the original model.


### Max Iter

The maximum number of iterations to be performed for training the
model via gradient descent. . If Max Iter is set to 100, the
algorithm will repeat the gradient descent 100 times, or until
the model converges, whichever comes first. If the model will not
converge after 100 cycles, modeling will stop.

### Standardize

An option that transforms variables into
standardized variables, each with mean 0 and unit
variance. Variables and coefficients are now expressed in terms
of their relative position to 0, and in standard units.

### N Folds

N folds specifies the number of cross validation models to be
generated simultaneously to training a model on the full data
set. If N folds is sent to 10, additional models will be generated
with 1/10 of the data used to train each. The purpose of N folds
is to evaluate the stability of the parameter estimates produced.



### Family and Link

Each of the given options differs in the
assumptions made about the Y variable - the target of
prediction. Each family is associated with a default link function,
which defines the specialized transformation on the set of X
variables chosen to  predict Y. 	

#### Gaussian (identity)

Y are quantitative, continuous (or discrete
predicted values can be meaningfully interpreted as approximately
continuos).

#### Binomial (logit)

Dependent variables take on two values, coded as 0 and 1, and
follow a binomial distribution.  Binomial dependent variables
can be understood as a categorical Y with two possible outcomes

#### Poisson (log)

Dependent variable is a count - a quantitative,
discrete value that expresses the number of times some event
occurred.

#### Gamma (inverse)

Dependent variable is a survival measure, or is distributed as
Poisson where variance is greater than the mean of the distribution.

### Tweedie Variance Power

Tweedie distributions are distributions of the dependent variable Y where $ var(Y)=a[E(Y)]^{p} $

where a and p are constants, and p is determined on the basis of
the distribution of Y. Guidelines for selecting Tweedie power are
given below.

Tweedie power is chosen based on the distribution of the dependent variable.

p      | Response distribution*
------ | ---------
0	     | Normal
1	     | Poisson
(1, 2) | Compound Poisson, non-negative with mass at zero
2	     | Gamma
3	     | Inverse-Gaussian
> 2	   | Stable, with support on the positive reals


### Alpha

A user defined tuning regularization parameter.  H2O sets Alpha
to 0.5 by default, but the parameter can take any value between
0 and 1, inclusive. It functions such that there is an added
penalty taken against the estimated fit of the model as the
number of parameters increases. An Alpha of 1 is the lasso
penalty, and an alpha of 0 is the ridge penalty.


### Lambda

H2O provides a default value, but this can also be user
defined. Lambda is a regularization parameter that is designed to
prevent overfitting. The best value(s) of lambda depends on the
desired level of agreement.


### Beta Epsilon

Precision of the vector of coefficients. Computation
stops when the maximum difference between two beta vectors is
below than beta epsilon.

### Higher Accuracy

The higher accuracy option implements line search
optimization. Line search is an optimization approach that
calculates an adaptive step size at each iteration of the
gradient descent. Because line search is a direct search
algorithm it can improve model convergence without specification
of additional regularization. Line search can slow model
training.

### Lambda Search

The lambda search option allows users to start at 0.90*Lambda
max, where lambda max is the value for lambda at which the model
returned estimates all coefficients as zero. An additional 50 values of
lambda are estimated. These values are successively smaller, and
are log scaled. Models for each are returned, along with the
ratio of the explained deviance to nonzero parameter estimates.

## GLMgrid Models

GLMgrid models can be generated for sets of regularization parameters by
entering the parameters either as a list of comma separated
values, or ranges in steps. For example, if users wish to
evaluate a model for alpha=(0, .5, 1), entering 0, .5, 1 or
0:1:.5 will achieve the desired outcome.



## Interpreting a Model

### Degrees of Freedom

#### Null (total)
Defined as (n-1), where n is the number of observations or rows
in the data set. Quantity (n-1) is used rather than n to account
for the condition that the residuals must sum to zero, which
calls for a loss of one degree of freedom.

#### Residual
Defined as  (n-1)-p. This is the null degrees of freedom less the
number of parameters being estimated in the model.

### Residual Deviance

The difference between the predicted value and the observed value
for each example or observation in the data. Deviance is
a function of the specific model in question. Even when the same
data set is used between two models, deviance statistics will
change, because the predicted values of Y are model dependent.

### Null Deviance

The deviance associated with the full model (also known as the
saturated model). Heuristically, this can be thought of as the
disturbance representing stochastic processes when all of
determinants of Y are known and accounted for.

### Residual Deviance

The deviance associated with the reduced model, a model defined
by some subset of explanatory variables.

### AIC

A model selection criterion that penalizes models having large
numbers of predictors. AIC stands for Akiaike Information
Criterion. It is defined as

`$AIC = 2k + n Log(\frac{RSS}{n})$`

Where `$k$` is the number of model parameters, `$n$` is
the number of observations, and `$RSS$` is the residual sum
of squares.

### AUC

Area Under Curve. The curve in question is the
receiver operating characteristic curve. The criteria is a
commonly  used metric for evaluating the performance of
classifier models. It  gives the probability that a randomly
chosen positive observation is correctly ranked greater than a
randomly chosen negative observation. In machine learning, AUC is
usually seen as the preferred evaluative criteria for a model
(over accuracy) for classification models. AUC is not an output
for Gaussian regression, but is output for classification models
like binomial.

### Confusion Matrix

The accuracy of the classifier can be evaluated
from the confusion matrix, which reports actual versus predicted
classifications, and the error rates of both.



## Validate GLM

After running the GLM Model, a .hex key associated with the model is
generated.

0.  Select the "Validate on Another Dataset" option in the horizontal
menu at the top of your results page. You can also access this at
a later time by going to the drop down menu **Score** and
selecting **GLM**.
0.  In the validation generation page enter the .hex key for the model
you wish to validate in the Model Key field.
0.  In the key field enter the .hex for a testing data set matching
the structure of your training data set.
0.  Push the **Submit** button.


## Cross Validation

The model resulting from a GLM analysis in H2O can be
presented with cross validated models at the user's request. The
coefficients presented in the result model are independent of
those in  any of the cross validated models, and are generated
via least squares on the full data set. Cross validated models
are generated by taking a 90% random subsample of the data,
training a model, and testing that model on the remaining
10%. This process is repeated as many times as the  user
specifies in the Nfolds field during model specification.


## Cost of Computation

H2O is able to process large data sets because it relies on
paralleled processes. Large data sets are divided into smaller
data sets and processed simultaneously, with results being
communicated between computers as needed throughout the process.

In GLM data are split by rows, but not by columns because the
predicted Y values depend on information in each of the predictor
variable vectors. If we let O be a complexity function, N be the
number of observations (or rows), and P be the number of
predictors (or columns) then

$$ Runtime\propto p^3+\frac{(N*p^2)}{CPUs} $$

Distribution reduces the time it takes an algorithm to process
because it decreases N.


Relative to P, the larger that (N/CPUs) becomes, the more trivial
p becomes to the overall computational cost. However, when p is
greater than (N/CPUs), O is dominated by p.

$$ Complexity = O(p^3 + N*p^2) $$

## GLM Algorithm

Following the definitive text by P. McCullagh and J.A. Nelder (1989)
on the generalization of linear models to non-linear distributions of
the response variable Y, H2O fits GLM models based on the maximum
likelihood estimation via iteratively reweighed least squares.

Let `$y_{1},...,y_{n}$` be n observations of the independent, random
response variable `$Y_{i}$`

Assume that the observations are distributed according to a function
from the exponential family and have a probability density function of
the form:

`$f(y_{i})=exp[\frac{y_{i}\theta_{i} - b(\theta_{i})}{a_{i}(\phi)} + c(y_{i}; \phi)]$`

`$where\: \theta \:and \: \phi \:are \: location \: and \: scale\: parameters,$`
`$and \: a_{i}(\phi), \:b_{i}(\theta_{i}),\: c_{i}(y_{i}; \phi)\:are\:known\:functions.$`

`$a_{i}\:is\:of\:the\: form: \:a_{i}=\frac{\phi}{p_{i}}; p_{i}\: is\: a\: known\: prior\: weight.$`

When `$Y$` has a pdf from the exponential family:

`$E(Y_{i})=\mu_{i}=b^{\prime}$`
`$var(Y_{i})=\sigma_{i}^2=b^{\prime\prime}(\theta_{i})a_{i}(\phi)$`

Let `$g(\mu_{i})=\eta_{i}$` be a monotonic, differentiable
transformation of the expected value of `$y_{i}$`. The function
`$\eta_{i}$` is the link function and follows a linear model.
`$g(\mu_{i})=\eta_{i}=\mathbf{x_{i}^{\prime}}\beta$`

When inverted:
`$\mu=g^{-1}(\mathbf{x_{i}^{\prime}}\beta)$`


### Maximum Likelihood Estimation

Suppose some initial rough estimate of the parameters `$\hat{\beta}$`.
Use the estimate to generate fitted values:
`$\mu_{i}=g^{-1}(\hat{\eta_{i}})$`

Let `$z$` be a working dependent variable such that
`$z_{i}=\hat{\eta_{i}}+(y_{i}-\hat{\mu_{i}})\frac{d\eta_{i}}{d\mu_{i}}$`

where `$\frac{d\eta_{i}}{d\mu_{i}}$` is the derivative of the link
function evaluated at the trial estimate.

Calculate the iterative weights:
`$w_{i}=\frac{p_{i}}{[b^{\prime\prime}(\theta_{i})\frac{d\eta_{i}}{d\mu_{i}}^{2}]}$`

Where `$b^{\prime\prime}$` is the second derivative of
`$b(\theta_{i})$` evaluated at the trial estimate.


Assume `$a_{i}(\phi)$` is of the form
`$\frac{\phi}{p_{i}}$`. The weight `$w_{i}$` is inversely
proportional to the variance of the working dependent variable
`$z_{i}$` for current parameter estimates and proportionality
factor `$\phi$`.

Regress `$z_{i}$` on the predictors `$x_{i}$` using the
weights `$w_{i}$` to obtain new estimates of `$\beta$`.
`$\hat{\beta}=(\mathbf{X}^{\prime}\mathbf{W}\mathbf{X})^{-1}\mathbf{X}^{\prime}\mathbf{W}\mathbf{z}$`
Where `$\mathbf{X}$` is the model matrix, `$\mathbf{W}$` is a
diagonal matrix of `$w_{i}$`, and `$\mathbf{z}$` is a vector of
the working response variable `$z_{i}$`.

This process is repeated until the estimates `$\hat{\beta}$` change by less than a specified amount.



## References

Breslow, N E. "Generalized Linear Models: Checking Assumptions and
Strengthening Conclusions." Statistica Applicata 8 (1996): 23-41.

Frome, E L. "The Analysis of Rates Using Poisson Regression Models."
Biometrics (1983): 665-674.
http://www.csm.ornl.gov/~frome/BE/FP/FromeBiometrics83.pdf

Goldberger, Arthur S. "Best Linear Unbiased Prediction in the
Generalized Linear Regression Model." Journal of the American
Statistical Association 57.298 (1962): 369-375.
http://people.umass.edu/~bioep740/yr2009/topics/goldberger-jasa1962-369.pdf

Guisan, Antoine, Thomas C Edwards Jr, and Trevor Hastie. "Generalized
Linear and Generalized Additive Models in Studies of Species
Distributions: Setting the Scene." Ecological modeling
157.2 (2002): 89-100.
http://www.stanford.edu/~hastie/Papers/GuisanEtAl_EcolModel-2003.pdf

Nelder, John A, and Robert WM Wedderburn. "Generalized Linear Models."
Journal of the Royal Statistical Society. Series A (General) (1972): 370-384.
http://biecek.pl/MIMUW/uploads/Nelder_GLM.pdf

Niu, Feng, et al. "Hogwild!: A lock-free approach to parallelizing
stochastic gradient descent." Advances in Neural Information
Processing Systems 24 (2011): 693-701.*implemented algorithm on p.5
http://www.eecs.berkeley.edu/~brecht/papers/hogwildTR.pdf

Pearce, Jennie, and Simon Ferrier. "Evaluating the Predictive
Performance of Habitat Models Developed Using Logistic Regression."
Ecological modeling 133.3 (2000): 225-245.
http://www.whoi.edu/cms/files/Ecological_Modelling_2000_Pearce_53557.pdf

Press, S James, and Sandra Wilson. "Choosing Between Logistic
Regression and Discriminant Analysis." Journal of the American
Statistical Association 73.364 (April, 2012): 699â€“705.
http://www.statpt.com/logistic/press_1978.pdf

Snee, Ronald D. "Validation of Regression Models: Methods and
Examples." Technometrics 19.4 (1977): 415-428.

