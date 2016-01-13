import sys, os
import csv
import mysql.connector
from mysql.connector.constants import ClientFlag
import traceback

class SendDataToMysql:
    def __init__(self):
        self = self

    def add_test_cases_to_h2o(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(client_flags=[ClientFlag.LOCAL_FILES],user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        #Send data to mysql database
        try:
            #Sending accuracyTestCases.csv
            cursor.execute("LOAD DATA LOCAL INFILE '../h2o-test-accuracy/src/test/resources/accuracyTestCases.csv' INTO "
                        "TABLE TestCases COLUMNS TERMINATED BY ',' LINES TERMINATED BY '\n' IGNORE 1 LINES;")
            #Commit query
            h2o.commit()
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to add accuracy test cases to h2o database!"

    def add_accuracy_data(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(client_flags=[ClientFlag.LOCAL_FILES],user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        #Send data to mysql database
        try:
            #Sending accuracyDatasets
            cursor.execute("LOAD DATA LOCAL INFILE '../h2o-test-accuracy/src/test/resources/accuracyDataSets.csv' INTO "
                           "TABLE AccuracyDatasets COLUMNS TERMINATED BY ',' LINES TERMINATED BY '\n' IGNORE 1 LINES;")
            #Commit query
            h2o.commit()
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to add accuracy test cases to h2o database!"

        cursor.close()
        h2o.close()

if __name__ == '__main__':
    SendDataToMysql().add_test_cases_to_h2o()
    SendDataToMysql().add_accuracy_data()
