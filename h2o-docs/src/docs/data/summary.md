# Summary

Summary provides information integral to correctly analyzing data,
such as histograms and descriptive statistics for the variables in a
data set. The Summary page can be accessed after parsing your data
into .hex format by going to the drop down menu **Data** and
selecting **Summary**.

## Request

**Source**
The .hex key associated with the data to be summarized.

**Cols**
If a subset of columns is desired, specify that subset
here. Default is to return a summary for all columns.

**Max Ncols**
The maximum number of columns to be summarized.

**Max Qbins**
The number of bins for quantiles. When large data are parsed, they
are also binned and distributed across a cluster. When data are
multimodal (or otherwise distinctly shaped), increasing the number
of bins will allocate fewer data points to each bin and thus
increase the accuracy of the quantiles returned. Increasing the
number of bins for extremely large data can slow results depending
on the memory allocated to computational tasks.


## Inputs

### Key

Enter the .hex key associated with the data set to be summarized.


### X

Select the columns for which summary information is desired. If a
header was not used in the original data set, the columns are indexed
by number beginning at 0 on the left.

### Max Column Display

An alternative to selecting specific X; Max Column Display allows
users to define the maximum number of columns that summary information
will be provided for. For example, if users specify Max Column Display
to be 5, the first 5 columns of the original data set will be
displayed, and all others will be omitted regardless of the subset of
columns selected in the X field.


## Output

### Column Name

The variable name as it was provided in the header when the data
were parsed, or as assigned by H2O if no header was included.

### Base Stats for Numerical Data

#### NAs
Displays a count of the number of elements in the column that are
interpreted as NA by H2O, either because the original element was
given as NA, or because the original element was uninterpretable
when the data were parsed

#### Average: 
Abbreviated **avg**. The arithmetic mean of the data in the column, defined
as the sum of the values in the column divided by the number of
elements in the column.

`$Average\:(X_{i})=\frac{\sum_{k=1}^{N}X_{k}}{N}$`

#### Standard Deviation
Abbreviated **sd**. The standard deviation of the data in the column, defined as the
square root of the sum of the deviance of observed values from the
mean divided by the number of elements in the column less one.
For some columns of data a standard deviation of -0 may be produced.
This outcome is the special case where the standard deviation is a very,
very small negative number. The exact value has not been rounded, but the
number of digits displayed have been truncated.

`$Standard\:deviation\:(X_{i})=\sqrt{\frac{\sum_{k=1}^{N} (X_{k}-\bar X)^2}{N-1}}$`

#### Zeros:
The number of elements in the column that are 0.

#### Min[ ] and Max[ ]
Return a list of the minimum and maximum elements of the
column respectively. The default is the lowest and highest five
numbers, but for binomial data (for example), the minimum are
listed as 0,1 and the maximum as 1,0.

### Percentiles
a table displayed only for Numeric Data
A percentile of value X at P threshold is interpreted as
"X is greater than or equal to P percent of the values
in this column." For example, if 83 is given as the
value at threshold .75, we would say that 83 is greater
than 75% of the other elements in the column.


#### Threshold
Displays the percentile levels. For example .25 corresponds to the
25th percentile, .5 corresponds to the median and .75 corresponds to
the 75th percentile.

#### Value
In H2O the values presented relative to thresholds as percentiles are
obtained through approximation relying on information derived from the
histogram.


### Base Stats for Categorical Data

#### NAs
Displays a count of the number of elements in the column that are
interpreted as NA by H2O, either because the original element was
given as NA, or because the original element was uninterpretable
when the data were parsed.

#### Cardinality
Displays the number of unique categories present in the data. For
example, if a column of 100 elements gives information about the
color of an object, and the possible values for color are red, green,
and blue, cardinality is 3.

### Histogram

#### First Row (top)
Lists the unique values or categorical levels in the column.

#### Second Row (middle)
Lists a corresponding count of the number of observations of the
value given in the top row.

#### Third Row (bottom)
Gives a percentage of the number of observations that are equal to
the corresponding value.

*Users should note that the tables in Summary can become
quite large. When the width of the table exceeds the width of the
display users can view more information by hovering over the table
with their mouse and scrolling left and right.*

