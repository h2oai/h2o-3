#######################################################################################
#A way to get relevant information from Master Jenkins.
#Plot queries from getData.R
#@author navdeepgill
#######################################################################################
#Plot data from getData.R
source("getDataMySQL.R")

#Pull in ggplot2
library(ggplot2)
library(stringr)
#######################################################################################
#Look at plot of average duration by job name
master_jobname_plot = ggplot(master_jobname, aes(x=JobName, y=AvgDuration, fill = JobName)) + geom_bar(stat="identity") 
master_jobname_plot = master_jobname_plot + ggtitle(paste("Average Duration per Jenkins Job in Master on ", master_jobname$Date)) 
master_jobname_plot = master_jobname_plot + theme(axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Jenkins Job",y="Average Duration") 
#Save plot
ggsave(master_jobname_plot,file = "plotsMySQL/master_jobname.pdf",height = 15,width = 15)

#######################################################################################
#subset master_testname to get top 20 tests in terms of average duration
master_testname_subset = master_testname[1:20,]
master_testname_plot = ggplot(master_testname_subset, aes(x=TestName, y=AvgDuration, fill = TestName)) + geom_bar(stat="identity") 
master_testname_plot = master_testname_plot + ggtitle(paste("Average Duration per Tests in Master (Top 20) on ", master_testname$Date)) 
master_testname_plot = master_testname_plot + theme(axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Test Name",y="Average Duration") 
#save plot
ggsave(master_testname_plot,file = "plotsMySQL/master_testname.pdf",height = 20,width = 20)

#######################################################################################
#subset master_jobname_testname to get top 20 tests in terms of average duration. This is grouped by jobname and testname
master_jobname_testname_subset = master_jobname_testname[1:20,]
master_jobname_testname_plot = ggplot(master_jobname_testname_subset, aes(x=JobName, y=AvgDuration, fill = TestName)) + geom_bar(stat="identity") 
master_jobname_testname_plot = master_jobname_testname_plot + ggtitle(paste("Average Duration per Jenkins Job per Tests in Master (Top 20) on ", master_jobname_testname$Date)) 
master_jobname_testname_plot = master_jobname_testname_plot + theme(axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Jenkins Job",y="Average Duration") 
#save plot
ggsave(master_jobname_testname_plot,file = "plotsMySQL/master_jobname_testname.pdf",height = 20,width = 20) 
