library(h2o)
h2o.init()

#Create Frame
fr = h2o.createFrame(cols=2, real_range=1.0, missing_fraction=0.0)

#Produce a random walk and reassign frame as such
for(i in 1:ncol(fr)){
  fr[,i] = cumsum(fr[,i])
}

#Take a look at a summary of the data
h2o.summary(fr)

#Plot the random walk
df = as.data.frame(fr)
plot.ts(df)

#Transpose frame as Isax expects a single time series per row
fr_t = t(fr)

#Run sax
res = h2o.isax(fr_t,num_words=10,max_cardinality=10,optimize_card = FALSE) #Non optimized cardinalty search
res2 = h2o.isax(fr_t,num_words=10,max_cardinality=10,optimize_card = TRUE) #Optimized cardinality search

#Explore indexes produced
res
res2