# PCA Tutorial

The purpose of this tutorial is to walk the new user through
a PCA analysis beginning to end in H2O.

Those who have never used H2O before should see the quick start guide
for additional instructions on how to run H2O.


### When to Use PCA
PCA is used to reduce dimensions and solve issues of multicollinearity in high dimension data.

### Getting Started

This tutorial uses a publicly available data set that can be found at:
http://archive.ics.uci.edu/ml/datasets/Arrhythmia

The original data are the Arrhythmia data set made available by UCI
Machine Learning Repository. They are composed of 452 observations and
279 attributes.

Before modeling, parse data into H2O as follows:

0. Under the drop down menu **Data** select *Upload* and use the helper to
   upload data.
0. User will be redirected to a page with the header "Request
   Parse". Select whether the first row of the data set is a
   header. All other settings can be left in default. Press Submit.
0. Parsing data into H2O generates a .hex key ("data name.hex")


![Image](PCAparse.png)



### Building a Model

0. Once  data are parsed a horizontal menu will appear at the top
   of the screen reading "Build model using ... ". Select
   PCA here, or go to the drop down menu Model and
   select PCA.
0. In the Key field enter the .hex key for the Arrhythmia data set.
0. In the Ignored Columns field select the set of columns to be
   omitted from the analysis.  Note that PCA ignores categorical variables
   and constant columns. Categoricals can be included by expanding the
   categorical into a set of binomial indicators.
0. Specify MaxPC to be the maximum number of principal components to
   be returned. In this case the maximum number of components is 100.
0. Specify Tolerance so that components exhibiting low standard
   deviation (which indicates a lack of contribution to the overall
   variance observed in the data) are omitted. In this example we set
   Tolerance to .5.
0. Choose whether or not to standardize. Standardizing is highly
   recommended, as choosing to not standardize can produce components
   that are dominated by variables that appear to have larger
   variances relative to other attributes purely as a matter of scale,
   rather than true contribution.

![Image](PCArequest.png)





### PCA Results

PCA output returns a table displaying the number of components
indicated by whichever criteria was more restrictive in this
particular case. In this example, a maximum of 100 components were
requested, and a tolerance set to .5.

Scree and cumulative variance plots for the components are returned as
well. Users can access them by clicking on the black button labeled
"Scree and Variance Plots" at the top left of the results page. A
scree plot shows the variance of each component, while the cumulative
variance plot shows the total variance accounted for by the set of
components.

Users should note that if they wish to replicate results between H2O
and R, it is recommended that standardization and cross validation
either be turned off in H2O, or specified in R.


![Image](PCAoutput.png)



