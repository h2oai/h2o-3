import sys, os
import csv
import mysql.connector
import traceback

class CreateMysqlTables:
    def __init__(self):
        self = self

    def drop_testcases_table(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        try:
            drop_testcases_query = """
                         DROP TABLE IF EXISTS TestCases;
                         """

            cursor.execute(drop_testcases_query)
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to drop TestCases table!"

        cursor.close()
        h2o.close()

    def drop_acc_datasets_tables(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        try:
            drop_accuracydata_query = """
                        DROP TABLES IF EXISTS AccuracyDatasets;
                        """

            cursor.execute(drop_accuracydata_query)
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to drop AccuracyDatasets table!"

        cursor.close()
        h2o.close()

    def create_testcases_table(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        #Drop table first before creation
        self.drop_testcases_table()

        #Create test cases table to be imputed into h2o database
        try:
            test_cases_query = """
                                CREATE TABLE TestCases(
                                test_case_id int(100) NOT NULL AUTO_INCREMENT,
                                algorithm varchar(100) NOT NULL,
                                algo_parameters varchar(200) NOT NULL,
                                tuned int(100) NOT NULL,
                                regression int(100) NOT NULL,
                                training_data_set_id int(100) NOT NULL,
                                testing_data_set_id int(100) NOT NULL,
                                PRIMARY KEY (`test_case_id`)
                                )"""

            cursor.execute(test_cases_query)
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to build TestCases table for h2o database!"

        cursor.close()
        h2o.close()

    def create_accuracy_datasets(self):

        #Connect to mysql database
        h2o = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor = h2o.cursor()

        #Drop table first before creation
        self.drop_acc_datasets_tables()

        #Create accuracy datasets table to be imputed into h2o database
        try:
            acc_data_query = """
                                CREATE TABLE IF NOT EXISTS AccuracyDatasets(
                                data_set_id int(100) NOT NULL AUTO_INCREMENT,
                                uri varchar(100) NOT NULL,
                                respose_col_idx int(100) NOT NULL,
                                PRIMARY KEY (`data_set_id`)
                                )"""

            cursor.execute(acc_data_query)
        except:
            traceback.print_exc()
            h2o.rollback()
            assert False, "Failed to build AccuracyDatasets table for h2o database!"

        cursor.close()
        h2o.close()

if __name__ == '__main__':
    CreateMysqlTables().create_testcases_table()
    CreateMysqlTables().create_accuracy_datasets()
