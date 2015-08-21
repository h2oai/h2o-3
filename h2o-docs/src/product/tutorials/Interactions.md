#Interaction Features between Factors

Use `h2o.interaction` to create interaction terms between categorical columns of an H2O frame. This feature creates N-th order interaction terms between categorical features of an H2O Frame (N=0,1,2,3,...). 

`h2o.interaction` uses the following parameters in the following form: 

`h2o.interaction(data, destination_frame, factors, pairwise, max_factors,
  min_occurrence)`

- `data`: An H2OParsedData object containing the variables in the model 
- `destination_frame`: A string indicating the destination key. If left blank, H2O automatically generates a name. 
- `factors`: Columns containing the factors used for the computation. If only one is specified, this parameter can be used to reduce the factor levels. 
- `pairwise`: Creates pairwise quadratic interactions between factors; otherwise, create one higher-order interaction. Only applicable if there are three or more factors. 
- `max_factors`: Maximum number of factor levels in pair-wise interaction terms. If enabled, one extra catch-all factor is made. 
- `min_occurrence`: Minimum occurrence threshold for factor levels in pair-wise interaction terms. 




##Example

```
library(h2o)
localH2O <- h2o.init()

# Create some random data
myframe = h2o.createFrame(localH2O, 'framekey', rows = 20, cols = 5,
                         seed = -12301283, randomize = TRUE, value = 0,
                         categorical_fraction = 0.8, factors = 10, real_range = 1,
                         integer_fraction = 0.2, integer_range = 10,
                         binary_fraction = 0, binary_ones_fraction = 0.5,
                         missing_fraction = 0.2,
                         response_factors = 1)
# Turn integer column into a categorical
myframe[,5] <- as.factor(myframe[,5])
head(myframe, 20)

# Create pairwise interactions
pairwise <- h2o.interaction(myframe, destination_frame = 'pairwise',
                            factors = list(c(1,2),c("C2","C3","C4")),
                            pairwise=TRUE, max_factors = 10, min_occurrence = 1)
head(pairwise, 20)
h2o.levels(pairwise,2)

# Create 5-th order interaction
higherorder <- h2o.interaction(myframe, destination_frame = 'higherorder', factors = c(1,2,3,4,5),
                               pairwise=FALSE, max_factors = 10000, min_occurrence = 1)
head(higherorder, 20)

# Limit the number of factors of the "categoricalized" integer column
# to at most 3 factors, and only if they occur at least twice
head(myframe[,5], 20)
trim_integer_levels <- h2o.interaction(myframe, destination_frame = 'trim_integers', factors = "C5",
                                       pairwise = FALSE, max_factors = 3, min_occurrence = 2)
head(trim_integer_levels, 20)

# Put all together
myframe <- h2o.cbind(myframe, pairwise, higherorder, trim_integer_levels)
myframe
head(myframe,20)
summary(myframe)
```