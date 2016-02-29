master_jobname_plot = ggplot(master_jobname, aes(x=JobName, y=AvgDuration, fill = JobName)) + geom_bar(stat="identity") 
master_jobname_plot = master_jobname_plot + ggtitle(paste("Average Duration per Jenkins Job in Master on ", master_jobname$Date)) 
master_jobname_plot = master_jobname_plot + theme(axis.text.x=element_text(angle=90,hjust=1,vjust=0.5)) + labs(x="Jenkins Job",y="Average Duration") 
ggsave(master_jobname_plot,file = "plotsMySQL/master_jobname.pdf",height = 15,width = 15)
