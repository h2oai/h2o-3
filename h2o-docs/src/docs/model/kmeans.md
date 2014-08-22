# K-Means

K-Means falls in the general category of clustering algorithms.

## When to use K-Means

Data are a set of attributes on which the members of the population
likely differ. The objective is classification.
Here are some examples:

- "How do competitors differ from one another on critical dimensions?"
- "How is a particular market segmented?"
- "Which dimensions are most important to differentiating between members of a population of interest?"

## Defining a K-Means model
### Source

The .hex key associated with the data set for use in clustering.

### Ignored Columns

The set of columns to be omitted from modeling.


### Initialization

#### Plus Plus
A modification to the k-means algorithm that impacts the assignment
of K initial cluster centroids. Because of the random process
inherent to K-means, it's possible for the algorithm to converge on
centroids that are not the optimal cluster centers purely by chance
in the choice of starting points. To mitigate this risk, Plus Plus
initialization assigns K initial centers by choosing just one center
at random, computing the Euclidean norm between that point and all
other points in the data set, and using the results to define a
weighted probability distribution from which the next center is
chosen at random. The process repeats until all centers have been
chosen, at which point the algorithm proceeds as usual.

#### Furthest
A modification to the k-means algorithm that impacts the assignment
of K initial cluster centroids. Furthest first initialization
attempts to improve K-means results by selecting the first center,
and then calculating the distance from that point to all other
possible points. The second initial center is chosen to the point
furthest from the first center in terms of Euclidean distance.

### K

The desired  number of clusters. There is no set rule or formula
for defining K, it is up to the user and is
often based on heuristics.

### Max Iter

The maximum number of iterations the algorithm is to go
through if no stopping point is reached before then.

### Normalize

Specifies that each attribute be transformed such that it has a mean
of 0 and standard deviation of 1, and that this transformation be
carried out before the algorithm is applied.

### Seed

A means for specifying the algorithm components
dependent on randomization. Note that the seed stays the same for
each instance of H2O, allowing the user to create models with the
same starting conditions in alternative configurations.

## Interpreting a Model

Output from K-Means is a table with one more column than the
number of attributes used to cluster. The the names of attributes,
and "cluster" appear in the header row. The column cluster gives
an arbitrary number to each cluster built, and the attributes give
the coordinates of the center of that cluster.

|Clusters|Attribute 1|Attribute 2|
|--------|-----------|-----------|
|   0    | centroid  | centroid  |
|        |  value    |  value    |

## References

Xiong, Hui, Junjie Wu, and Jian Chen. "K-means Clustering Versus
Validation Measures: A Data- distribution Perspective." Systems, Man,
and Cybernetics, Part B: Cybernetics, IEEE Transactions on 39.2 (2009): 318-331.

## K-Means Algorithm

The number of clusters `$K$` is user defined and determined a priori.

### Step 1

Choose `$K$` initial cluster centers `$m_{k}$` according to one of the following:

#### Randomization

Choose `$K$` clusters from the set of `$N$` observations at random so that
each observation has an equal chance of being chosen.

#### Plus Plus

0. Choose one center `$m_{1}$` at random.
0. Calculate the difference between `$m_{1}$` and each of the
remaining `$N-1$` observations `$x_{i}$`.  \\( d(x_{i}, m_{1}) \\) = \\( ||(x_{i}-m_{1})||^2 \\)
0. Let `$P(i)$` be the probability of choosing `$x_{i}$` as
`$m_{2}$`. Weight `$P(i)$` by `$d(x_{i}, m_{1})$` so that
those `$x_{i}$` furthest from `$m_{2}$` have  a
higher probability of being selected than those `$x_{i}$`
close to `$m_{1}$`.
0. Choose the next center `$m_{2}$` by drawing at random
according to the weighted probability distribution.

Repeat until `$K$` centers have been chosen.


#### Furthest

0. Choose one center `$m_{1}$` at random.

0. Calculate the difference between `$m_{1}$` and each of the
remaining `$N-1$` observations `$x_{i}$`.

`$d(x_{i}, m_{1})$` = `$||(x_{i}-m_{1})||^2$`

0. Choose `$m_{2}$` to be the `$x_{i}$` that maximizes
`$d(x_{i}, m_{1})$`.

Repeat until `$K$` centers have been chosen.

### Step 2

Once `$K$` initial centers have been chosen calculate the difference
between each observation `$x_{i}$` and each of the centers
`$m_{1},...,m_{K}$`, where difference is the squared Euclidean
distance taken over `$p$` parameters.

`$d(x_{i}, m_{k})=$`

`$\sum_{j=1}^{p}(x_{ij}-m_{k})^2=$`

`$|(x_{i}-m_{k})|^2=$`


### Step 3

Assign `$x_{i}$` to the cluster `$k$` defined by `$m_{k}$` that
minimizes `$d(x_{i}, m_{k})$`

### Step 4

When all observations `$x_{i}$` are assigned to a cluster
calculate the mean of the points in the cluster.

`$\bar{x}(k)=\lbrace\bar{x_{i1}},â€¦\bar{x_{ip}}\rbrace$`

### Step 5

Set the `$\bar{x}(k)$` as the new cluster centers
`$m_{k}$`. Repeat steps 2 through 5 until the specified number
of max iterations is reached or cluster assignments of the
`$x_{i}$` are stable.



## References

Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The
Elements of Statistical Learning.
Vol.1. N.p., Springer New York, 2001.
http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf

Xiong, Hui, Junjie Wu, and Jian Chen. "K-means Clustering Versus
Validation Measures: A Data- distribution Perspective." Systems, Man,
and Cybernetics, Part B: Cybernetics, IEEE Transactions on 39.2 (2009): 318-331.

