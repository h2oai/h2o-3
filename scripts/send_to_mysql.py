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

    def drop_join_test_cases_tables(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        try:
            drop_join_test_cases_query = """
                        DROP TABLES IF EXISTS TestCasesResults;
                        """

            cursor.execute(drop_join_test_cases_query)
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to drop TestCasesResults table!"

    def join_test_cases_results(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(client_flags=[ClientFlag.LOCAL_FILES],user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        #Drop table if exists before re creating
        self.drop_join_test_cases_tables()

        try:
            join_query = """
                         CREATE TABLE TestCasesResults AS(
                         SELECT *
                         FROM AccuracyTestCaseResults
                         LEFT JOIN TestCases
                         ON AccuracyTestCaseResults.testcase_id = TestCases.test_case_id
                         LEFT JOIN AccuracyDatasets
                         ON TestCases.training_data_set_id = AccuracyDatasets.data_set_id);
                         """

            cursor.execute(join_query)
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to join AccuracyTestCaseResults, TestCases, and AccuracyDatasets!"

        cursor.close()
        h2o.close()

if __name__ == '__main__':
    #SendDataToMysql().add_test_cases_to_h2o()
    #SendDataToMysql().add_accuracy_data()
    SendDataToMysql().join_test_cases_results()