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
master_jobname_plot = ggplot(x=JobName, y=AvgDuration, fill=JobName,
                      data=master_jobname, geom="bar", stat = "identity",
                      position="dodge")
master_jobname_plot = master_jobname_plot + theme(axis.text.x = element_text(angle = 90, hjust = 1)) + labs(x="Jenkins Job",y="Average Duration") 
master_jobname_plot = master_jobname_plot + ggtitle(paste("Average Duration per Jenkins Job in Master on ", master_jobname$Date)) 
#save plot
ggsave(master_jobname_plot,file = "plotsMySQL/master_jobname.pdf",height = 15,width = 15)

#######################################################################################
#subset master_testname to get top 20 tests in terms of average duration
master_testname_subset = master_testname[1:20,]
master_testname_plot = ggplot(x=TestName, y=AvgDuration, fill=TestName,
                            data=master_testname_subset, geom="bar", stat="identity",
                            position="dodge")
master_testname_plot = master_testname_plot + theme(axis.text.x = element_text(angle = 90, hjust = 1)) + labs(x="Test Name",y="Average Duration") 
master_testname_plot = master_testname_plot + ggtitle(paste("Average Duration per Tests in Master (Top 20) on ", master_testname$Date)) 
#save plot
ggsave(master_testname_plot,file = "plotsMySQL/master_testname.pdf",height = 20,width = 20)

#######################################################################################
#subset master_jobname_testname to get top 20 tests in terms of average duration. This is grouped by jobname and testname
master_jobname_testname_subset = master_jobname_testname[1:20,]
master_jobname_testname_plot = ggplot(x=JobName, y=AvgDuration, fill=TestName,
                            data=master_jobname_testname_subset, geom="bar", stat="identity",
                            position="dodge")
master_jobname_testname_plot = master_jobname_testname_plot + theme(axis.text.x = element_text(angle = 90, hjust = 1)) + labs(x="Jenkins Job",y="Average Duration") 
master_jobname_testname_plot = master_jobname_testname_plot + ggtitle(paste("Average Duration per Jenkins Job per Tests in Master (Top 20) on ", master_jobname_testname$Date)) 
#save plot
ggsave(master_jobname_testname_plot,file = "plotsMySQL/master_jobname_testname.pdf",height = 20,width = 20) 
