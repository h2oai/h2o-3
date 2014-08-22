# Score: Area Under Curve

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

**Threshold Criterion**
The criterion to be used in calculating the AUC.
Current options include:

- maxixmum F1
- maximum Accuracy
- maximum Precision
- maximum Recall
- maximum Specificity
- minimization of Per Class Error


