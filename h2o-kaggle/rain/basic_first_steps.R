#####################################################
## First steps Kaggle Will it Rain competition
#####################################################

## Point to directory where the Kaggle data is
dir <- paste0(path.expand("~"), "/h2o-kaggle/rain/")

library(data.table)
# Just load RR1 column, the benchmark
system.time(train<-fread(paste0(dir,"train_2013.csv"))[,.(RR1,Expected)]) # 76s
system.time(train<-fread(paste0(dir,"train_2013.csv"),select=c("RR1","Expected"))) # 10s
train # auto-print copy now fixed in R-devel thanks to Luke Tierney (TBC)
print(train)

# a common R idiom to compute mean of each vector in each cell
rr1.mean<-unlist(lapply(train[,RR1], function(x) mean(as.numeric(strsplit(x," ")[[1]]))))

# my inclination to speed up, maybe
train[,splitRR1:=sapply(RR1,strsplit,split=" ")]  # a "list" column
# Hm. Back to basics ...
strsplit("a b c",split=" ")
strsplit("a b c",split=" ")[[1]]
# But then how to do the [[1]] as well per row .. function()? Think again.
strsplit(c("a b c","d e f"), split=" ")
train[,RR1:=strsplit(RR1,split=" ")] # better.  One call to strsplit not N calls
hist(train[,sapply(RR1,length)])
# or use nice piping ...
library(dplyr)
train[,sapply(RR1,length)] %>% hist
train[,sapply(RR1,`==`,0)] %>% hist
# Hm, something wrong.  Make tidyr first maybe?
# splitstackshape maybe?
# Oh I forgot to sum.  Let's define percEq()
percEq = function(x,value) sum(x==value)/length(x)
train[,sapply(RR1,percEq,0.0)] %>% hist
# oops, all blank since forgot as.numeric.
train[,RR1:=sapply(RR1,as.numeric)]
train[,sapply(RR1,percEq,0.0)] %>% hist

# Can we do better?
