deprecated = ['offset_column', 'distribution']

doc = dict(
    __class__="""
Builds a Distributed Random Forest (DRF) on a parsed dataset, for regression or classification.
DRF is a powerful classification and regression tool. When given a set of data,
DRF generates a forest of classification or regression trees, rather than a single classification or regression tree.
Each of these trees is a weak learner built on a subset of rows and columns.
More trees will reduce the variance. Both classification and regression take the average prediction over all of their trees to make a final prediction,
whether predicting for a class or numeric value. (Note: For a categorical response column,
DRF maps factors (e.g. ‘dog’, ‘cat’, ’mouse) in lexicographic order to a name lookup array with integer indices
(e.g. ’cat -> 0, ‘dog’ -> 1, ‘mouse’ -> 2.)
"""
)
