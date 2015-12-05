library(h2o)
h2o.init()

NROW = 1e9  # for demo of 1bn x 1bn -> 1bn row join
NROW = 1e5  # small for nightly checks that this demo runs
NCOL = 5

X = h2o.createFrame(rows=NROW,cols=NCOL,integer_range=NROW,categorical_fraction = 0,integer_fraction=1,binary_fraction=0,missing_fraction = 0)
X$C1 = abs(X$C1)   # can't handle negatives in join columns yet
colnames(X) = paste0("X.",colnames(X))
colnames(X)[1] = "KEY"  # joins common column names currently, so rename column 1 to be "KEY" in both tables
Y = h2o.createFrame(rows=NROW,cols=NCOL,integer_range=NROW,categorical_fraction = 0,integer_fraction=1,binary_fraction=0,missing_fraction = 0)
Y$C1 = abs(Y$C1)
colnames(Y) = paste0("Y.",colnames(Y))
colnames(Y)[1] = "KEY"
gc()   # currently explicit gc() needed to remove some h2o temps, Cliff aware and working towards not needing

ans1 = h2o.merge(X, Y, method="radix")   # lazy, returns immediately
print(system.time(print(dim(ans1))))     # dim(ans1) is enough to invoke and eval the join
head(ans1)
tail(ans1)



