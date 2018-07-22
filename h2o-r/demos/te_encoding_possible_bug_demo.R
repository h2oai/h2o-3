library(h2o)
h2o.init()

print("Category `a` in `ColA` is represented only by one fold.
KFold holdout_type is actually LeaveOneFoldOut and if we don't have this particular te_column's value in other folds then we will have nothing to merge with later.")
x <- data.frame("ColA" = c("a", "b", "b", "b"), "ColB" = c(1, 1, 4, 7), "ColC" = c("2", "6", "6", "6"), "fold_column" = c(1, 2, 2, 3))
xdata <- as.h2o(x)
encoding_map <- h2o.target_encode_create( xdata, list("ColA"), "ColC", "fold_column")
encoding_map

cv_train <- h2o.target_encode_apply(xdata, x = list("ColA"),  y = "ColC", encoding_map, holdout_type = "KFold", fold_column = "fold_column", seed = 1234)
cv_train

print("Here we have added row with `a` in another fold.")
x <- data.frame("ColA" = c("a", "b", "b", "b", "a"), "ColB" = c(1, 1, 4, 7, 4), "ColC" = c("2", "6", "6", "6", "6"), "fold_column" = c(1, 2, 2, 3, 2))
xdata <- as.h2o(x)
encoding_map <- h2o.target_encode_create( xdata, list("ColA"), "ColC", "fold_column")
encoding_map

cv_train <- h2o.target_encode_apply(xdata, x = list("ColA"),  y = "ColC", encoding_map, holdout_type = "KFold", fold_column = "fold_column", seed = 1234)
cv_train


