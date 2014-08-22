# Naive Bayes

Naive Bayes (NB) is a classification algorithm that relies on strong
assumptions of the independence of covariates in applying Bayes
Theorem. NB models are commonly used as an alternative to decision
trees for classification problems.



## Defining a Naive Bayes Model
### Source:

The .hex key associated with the data to be modeled.

### Response:

The dependent variable to be predicted by the model.

### Ignored Columns:

The set of features in the training data set to be omitted from
model training.

### Laplace:

Laplace smoothing is used to circumvent the modeling issues that can
arise when conditional probabilities are 0. In particular this can
occur when a rare event appears in holdout or prediction data, but
did not appear in the training data. Smoothing modifies the maximum
likelihood estimates used to generate classification probabilities
even when unknown cases are encountered.

## Naive Bayes Algorithm and Implementation
The algorithm is presented for the simplified binomial case without
loss of generality.

Under the Naive Bayes assumption of independence, given a training set
for a set of discrete valued features X
`${(X^{(i)},\ y^{(i)};\ i=1,...m)}$`

The joint likelihood of the data can be expressed as:

`$\mathcal{L} \: (\phi(y),\: \phi_{i|y=1},\: \phi_{i|y=0})=\Pi_{i=1}^{m} p(X^{(i)},\: y^{(i)})$`

The model can be parameterized by:

`$\phi_{i|y=0}=\ p(x_{i}=1|\ y=0);\: \phi_{i|y=1}=\ p(x_{i}=1|y=1);\: \phi(y)$`

Where `$\phi_{i|y=0}=\ p(x_{i}=1|\ y=0)$` can be thought of as the
fraction of the observed instances where feature `$x_{i}$` is
observed, and the outcome is `$y=0$`, `$\phi_{i|y=1}=\ p(x_{i}=1|\ y=1)$` is the fraction of the observed instances where feature `$x_{i}$` is
observed, and the outcome is `$y=1$`, and so on.

The objective of the algorithm is then to maximize with respect to
`$\phi_{i|y=0}, \ \phi_{i|y=1},\ and \ \phi(y)$`

Where the maximum likelihood estimates are:

`$\phi_{j|y=1}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 1)}{\Sigma_{i=1}^{m}(y^{(i)}=1}$`

`$\phi_{j|y=0}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 0)}{\Sigma_{i=1}^{m}(y^{(i)}=0}$`

`$\phi(y)= \frac{(y^{i} = 1)}{m}$`


Once all parameters `$\: \phi_{j|y}$` are fit, the model can be
used to predict new examples with features `$X_{(i^*)}$`.

This is carried out by calculating

`$p(y=1|x)=\frac{\Pi p(x_i|y=1) p(y=1)}{\Pi p(x_i|y=1)p(y=1) \: + \: \Pi p(x_i|y=0)p(y=0)}$`


`$p(y=0|x)=\frac{\Pi p(x_i|y=0) p(y=0)}{\Pi p(x_i|y=1)p(y=1) \: + \: \Pi p(x_i|y=0)p(y=0)}$`

and predicting the class with the highest probability.


It is possible that predictions sets contain features not originally
seen in the training set. When this occurs the maximum likelihood
estimates for these features will predict a probability of 0 for all
cases of y.

Laplace smoothing allows a model to predict on out of training data
features by adjusting the maximum likelihood estimates to be:


`$\phi_{j|y=1}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 1) \: + \: 1}{\Sigma_{i=1}^{m}(y^{(i)}=1 \: + \: 2}$`

`$\phi_{j|y=0}= \frac{\Sigma_{i}^m 1(x_{j}^{(i)}=1 \ \bigcap y^{i} = 0) \: + \: 1}{\Sigma_{i=1}^{m}(y^{(i)}=0 \: + \: 2}$`

Note that in the general case where y takes on k values, there are k+1
modified parameter estimates, and the added in the denominator is k
(rather than 2, as shown in the 2 level classifier shown here.)

Laplace smoothing should be used with care; it is generally intended
to allow for predictions in rare events. As prediction data becomes
increasingly distinct from training data, new models should be
trained when possible to account for the broader set of possible X
values.

