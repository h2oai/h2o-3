# Data: Quantiles (Request)

**Source Key**
The key associated with the data set of interest.

**Column**
The column of interest.

**Quantile**
A value bounded on the interval (0,1), where X is the value below
which X as a percentage of the data fall. For instance if the
quantile .25 is requested, the value returned will be the value
within the range of the column of data below which 25% of the data
fall.

**Max Qbins**
The number of bins into which the column should be split before the
quantile is calculated. As the number of bins approaches the number
of observations the approximate solution approaches the exact
solution.

**Multiple Pass**
Only 3 possible entries:

- 0: Calculate the best approximation of the requested quantile in
one pass.
- 1: Return the exact result (with a maximum iteration of 16 passes)
- 2: Return both a single pass approximation and multi-pass exact
answer.

**Interpolation Type**
When the quantile falls between two in-data values, it is necessary
to interpolate the true value of the quantile. This can be done by
mean interpolation, or linear interpolation.

- 2: Mean interpolation
- 7: Linear interpolation

# Response

Requesting *Quantile* from the **Data** drop down menu returns the
following information (some information is for development; those
fields have been omitted).

**H2O**
Cloud name

**Node**
Node and cluster information

**Time**
The time in milliseconds taken to complete the computation.

**Status**
Information about whether computation completed.

**Quantile Requested**
The quantile specified in the original request.

**Interpolation type used**
The type of interpolation originally requested where
- 2: Mean interpolation
- 7: Linear interpolation

**Interpolated**
True or False response indicating whether interpolation was used.

**Iterations**
The number of iterations carried out in calculating the quantile.

**Result**
The numeric value of the quantile requested.
