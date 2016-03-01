#######################################################################################
#A way to get relevant information from Master Jenkins.
#All is query based and mainly show average duration of a job/test from the previous day.
#@author navdeepgill
#######################################################################################

#Get data from MySQL DB that stores information about Master Jenkins:
library(RMySQL)

#Connect to MySQL DB
mr_unit = dbConnect(MySQL(), user='root', password='0xdata', dbname='mr_unit', host='172.16.2.178')

#List out tables in the database mr_unit:
dbListTables(mr_unit)

#List out fields in the table perf:
dbListFields(mr_unit, 'perf')

#######################################################################################
#Some notes on naming conventions of fields;

  #Naming convention: master_groupbyvariable (this can expand based on # of group by vars)
  #Ordered by AvgDuration for now
#######################################################################################

#######################################################################################
#First pass it to group by JobName, TestName, and a combination of JobName + TestName
#Ordered by AvgDuration for now
#Time frame is the previous date.
#######################################################################################

#Query to get summary statistics about tests that ran yesterday and group by JobName. Order by AvgDuration in descending order.:
master_jobname = dbSendQuery(mr_unit, "SELECT date as Date, job_name as JobName, 
                             COUNT(*) as SampleSize,
                             AVG(end_time-start_time) as AvgDuration, 
                             STDDEV_SAMP(end_time-start_time) as STDDevDuration,
                             MIN(end_time-start_time) as MinDuration,
                             MAX(end_time-start_time) as MaxDuration
                             FROM perf 
                             WHERE git_branch = 'master' and date = CURDATE() - INTERVAL 1 DAY
                             GROUP BY JobName
                             ORDER BY AvgDuration DESC;")

#Send query to a dataframe
master_jobname = fetch(master_jobname, n = -1)

#######################################################################################

#Query to get summary statistics about tests that ran yesterday and group by TestName. Order by AvgDuration in descending order.:
master_testname = dbSendQuery(mr_unit, "SELECT date as Date, test_name as TestName, 
                             COUNT(*) as SampleSize,
                             AVG(end_time-start_time) as AvgDuration, 
                             STDDEV_SAMP(end_time-start_time) as STDDevDuration,
                             MIN(end_time-start_time) as MinDuration,
                             MAX(end_time-start_time) as MaxDuration
                             FROM perf 
                             WHERE git_branch = 'master' and date = CURDATE() - INTERVAL 1 DAY
                             GROUP BY TestName
                             ORDER BY AvgDuration DESC;")

#Send query to a dataframe
master_testname = fetch(master_testname, n = -1)

#######################################################################################

#Query to get summary statistics about tests that ran yesterday and group by JobName & TestName. Order by AvgDuration in descending order.:
master_jobname_testname = dbSendQuery(mr_unit, "SELECT date as Date, job_name as JobName, test_name as TestName, 
                              COUNT(*) as SampleSize,
                              AVG(end_time-start_time) as AvgDuration, 
                              STDDEV_SAMP(end_time-start_time) as STDDevDuration,
                              MIN(end_time-start_time) as MinDuration,
                              MAX(end_time-start_time) as MaxDuration
                              FROM perf 
                              WHERE git_branch = 'master' and date = CURDATE() - INTERVAL 1 DAY
                              GROUP BY JobName, TestName 
                              ORDER BY AvgDuration DESC;")

#Send query to a dataframe
master_jobname_testname = fetch(master_jobname_testname, n = -1)

