# Score: Hit Ratio

Hit Ratio is the number of times that a correct prediciton was made in
ratio to the number of total prediciton names. For example if 20
predidtions were made correctly out of 35 predictions, the hit ratio
is 20:35 or .57.


**Actual**
A parsed data set containing the true (known) values of the binomial
data being predicted.

**Vactual**
The column of binomial data from the data set specified in **Actual**.

**Predict**
The parsed data set containing the predicted values for the
dependent variable in question.

**VPredict**
The column in the data set specified in **Predict** containing the
predicted value. This value need not be 0/1, but should be a
probability and not log-likelihood.

