import mysql.connector
import traceback

'''
A simple check of whether the mysql databases is up and running.
This will run a quick query to get the mysql version. If it returns anything, then
we can assume the connection is healthy. Otherwise, it is not.
'''

#TODO: Will add more checks as time comes.
class CheckDBHealth:

    def __init__(self):
        self = self

    #Check if connection to mysql database is healthy.
    def checkConnection(self):
        try:
            db = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
            cursor = db.cursor()
            cursor.execute("SELECT VERSION()")
            results = cursor.fetchone()
            # Check if anything at all is returned
            if results:
                print "Connection is Healthy to Accuracy Test Database."
            else:
                return "Connection might not be healthy. Nothing returned from query."
        except mysql.connector.Error as err:
            print("Something went wrong: {}".format(err))

if __name__ == '__main__':
    CheckDBHealth().checkConnection()