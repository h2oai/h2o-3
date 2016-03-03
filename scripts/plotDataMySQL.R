#######################################################################################
#' A way to get relevant information from Master Jenkins.
#' Plot queries from getDataMySQL.R
#' @author  navdeepgill
#######################################################################################
#Plot data from getData.R
source("scripts/getDataMySQL.R")

#Pull in ggplot2
library(ggplot2)
library(stringr)
#######################################################################################
#Look at plot of average duration by job name
master_jobname_plot = ggplot(master_jobname, aes(x=JobName, y=AvgDuration, fill = JobName)) + geom_bar(stat="identity") 
master_jobname_plot = master_jobname_plot + ggtitle(paste("Average Duration per Jenkins Job in Master on ", master_jobname$Date)) 
master_jobname_plot = master_jobname_plot + theme(axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Jenkins Job",y="Average Duration") 
#Save plot
ggsave(master_jobname_plot,file = "plots/master_jobname.png",height = 15,width = 15)

#######################################################################################
#subset master_testname to get top 20 tests in terms of average duration
master_testname_subset = master_testname[1:20,]
master_testname_plot = ggplot(master_testname_subset, aes(x=TestName, y=AvgDuration, fill = TestName)) + geom_bar(stat="identity") 
master_testname_plot = master_testname_plot + ggtitle(paste("Average Duration per Tests in Master (Top 20) on ", master_testname$Date)) 
master_testname_plot = master_testname_plot + theme(text = element_text(size=20),axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Test Name",y="Average Duration") 
ggsave(master_testname_plot,file = "plots/master_testname.png",height = 20,width = 20)

#######################################################################################
#subset master_jobname_testname to get top 20 tests in terms of average duration. This is grouped by jobname and testname
master_jobname_testname_subset = master_jobname_testname[1:20,]
master_jobname_testname_plot = ggplot(master_jobname_testname_subset, aes(x=JobName, y=AvgDuration, fill = TestName)) + geom_bar(stat="identity") 
master_jobname_testname_plot = master_jobname_testname_plot + ggtitle(paste("Average Duration per Jenkins Job per Tests in Master (Top 20) on ", master_jobname_testname$Date)) 
master_jobname_testname_plot = master_jobname_testname_plot + theme(text = element_text(size=30),axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Jenkins Job",y="Average Duration") 
#save plot
ggsave(master_jobname_testname_plot,file = "plots/master_jobname_testname.png",height = 30,width = 30) 

#######################################################################################
#Top 20 tests in terms of pass ratio This is grouped by testname
master_testname_failures_subset_plot = ggplot(master_testname_failures_subset[1:20,], aes(x=TestName, y=PassRatio, fill = TestName)) + geom_bar(stat="identity")
master_testname_failures_subset_plot = master_testname_failures_subset_plot + ggtitle(paste("Pass Ratio per Tests in Master (Top 20) on ", master_jobname_testname$Date)) 
master_testname_failures_subset_plot = master_testname_failures_subset_plot + theme(text = element_text(size=30),axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="TestName",y="PassRatio")
master_testname_failures_subset_plot = master_testname_failures_subset_plot + geom_text(aes(label=PassRatio), vjust=1.5, colour="black",size = 10)
#save plot
ggsave(master_testname_failures_subset_plot,file = "plots/master_testname_failures_plot.png",height = 40,width = 40)

#######################################################################################
#Failure rates for Windows and Linux machines
master_os_failures_ts = ggplot(master_os_failures,aes(x=Date,y=PassRatio,colour=OS,group=OS)) + geom_line(size = 2)
master_os_failures_ts = master_os_failures_ts + ggtitle(paste("Pass Ratio by Date per OS"))
master_os_failures_ts = master_os_failures_ts + theme(text = element_text(size=40),axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Date",y="PassRatio")
master_os_failures_ts = master_os_failures_ts + scale_x_date(date_labels = "%b %d %Y")
#save plot
ggsave(master_os_failures_ts,file = "plots/master_os_failures_ts.png",height = 40,width = 40)

#######################################################################################

