# Remove the response column to simulate new data points arriving without the answer being known.
newdata = test
newdata['CAPSULE'] = None
newpred = binomial_fit.predict(newdata)
newpred