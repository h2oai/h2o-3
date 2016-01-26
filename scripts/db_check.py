import mysql.connector

'''A Class to get metrics and health of the mysql database located at 172.16.2.178'''
class CheckDB:

    def __init__(self):
        self = self

    '''
    Check of whether the mysql databases is up and running.
    This will run a quick query to get the mysql version. If it returns anything, then
    we can assume the connection is healthy. Otherwise, it is not.
    '''
    def check_connection(self):
        try:
            db = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
            cursor = db.cursor()
            cursor.execute("SELECT VERSION()")
            results = cursor.fetchone()
            # Check if anything at all is returned
            if results:
                print "\n*****Connection is Healthy to Accuracy Test Database.*****\n"
            else:
                return "\n*****Connection might not be healthy. Nothing returned from query.*****\n"
        except mysql.connector.Error as err:
            print("Something went wrong: {}".format(err))

    '''
    Get size of each database in MySQL server for reference
    '''
    def get_db_size(selfs):
        db = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='information_schema')
        cursor_h2o = db.cursor()

        cursor_h2o.execute("SELECT table_schema as 'Database', "
                           "sum(round(((data_length + index_length) / 1024 / 1024 / 1024), 5)) as 'Size in GB' "
                           "FROM TABLES group by table_schema;")

        results = cursor_h2o.fetchall()
        widths = []
        columns = []
        pipe = '|'
        separator = '+'

        for cd in cursor_h2o.description:
            widths.append(max(cd[2], len(cd[0])))
            columns.append(cd[0])

        #Reallocate width length
        widths[0] = 30

        for w in widths:
            pipe += " %-"+"%ss |" % (w,)
            separator += '-'*w + '--+'

        print "\n*****Size of each database in MySQL Server in GB:*****"
        print(separator)
        print(pipe % tuple(columns))
        print(separator)
        for row in results:
            print(pipe % row)
        print(separator)

    '''
    Display of tables in the h2o database for reference.
    Will be displayed in Jenkins logs.
    '''
    def get_tables_h2o(self):
            db = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
            cursor_h2o = db.cursor()

            cursor_h2o.execute("select table_name as h2o_tables from information_schema.tables where table_schema = 'h2o';")

            results = cursor_h2o.fetchall()
            widths = []
            columns = []
            pipe = '|'
            separator = '+'

            for cd in cursor_h2o.description:
                widths.append(max(cd[2], len(cd[0])))
                columns.append(cd[0])

            #Reallocate width length
            widths[0] = 30

            for w in widths:
                pipe += " %-"+"%ss |" % (w,)
                separator += '-'*w + '--+'

            print "\n*****Tables currently in the h2o database:*****"
            print(separator)
            print(pipe % tuple(columns))
            print(separator)
            for row in results:
                print(pipe % row)
            print(separator)

    '''
    Display of tables in the mr_unit database for reference.
    Will be displayed in Jenkins logs.
    '''
    def get_tables_mrunit(self):
        db = mysql.connector.connect(user='root', password='0xdata', host='172.16.2.178', database='h2o')
        cursor_mrunit = db.cursor()
        cursor_mrunit.execute("select table_name as mr_unit_tables from information_schema.tables where table_schema = 'mr_unit';")

        results = cursor_mrunit.fetchall()
        widths = []
        columns = []
        pipe = '|'
        separator = '+'

        for cd in cursor_mrunit.description:
            widths.append(max(cd[2], len(cd[0])))
            columns.append(cd[0])

        for w in widths:
            pipe += " %-"+"%ss |" % (w,)
            separator += '-'*w + '--+'

        print "\n*****Tables currently in the mr_unit database:*****"
        print(separator)
        print(pipe % tuple(columns))
        print(separator)
        for row in results:
            print(pipe % row)
        print(separator)

    '''
    Get sizes of every table in mysql server
    '''
    def get_table_size(self):
        db = mysql.connector.connect(user='root', password = '0xdata', host = '172.16.2.178', database = 'h2o')
        cursor = db.cursor()
        cursor.execute(" SELECT table_schema as `Database`,table_name AS `Table`,round(((data_length + index_length) / 1024 / 1024), 2) `Size in MB` "
                       "FROM information_schema.TABLES ORDER BY (data_length + index_length) DESC;")

        results = cursor.fetchall()
        widths = []
        columns = []
        pipe = '|'
        separator = '+'

        for cd in cursor.description:
            widths.append(max(cd[2], len(cd[0])))
            columns.append(cd[0])

        #Reallocate width length
        widths[0] = widths[1] = widths[2] = 50

        for w in widths:
            pipe += " %-"+"%ss |" % (w,)
            separator += '-'*w + '--+'

        print "\n*****Size of every table in mysql server in MB (desc order):*****"
        print(separator)
        print(pipe % tuple(columns))
        print(separator)
        for row in results:
            print(pipe % row)
        print(separator)

    '''
    Get current and total connections per host
    '''
    def get_host(self):
        db = mysql.connector.connect(user='root', password = '0xdata', host = '172.16.2.178', database = 'performance_schema')
        cursor = db.cursor()
        cursor.execute(" SELECT * FROM accounts;")

        results = cursor.fetchall()
        print cursor.description
        widths = []
        columns = []
        pipe = '|'
        separator = '+'

        for cd in cursor.description:
            widths.append(max(cd[2], len(cd[0])))
            columns.append(cd[0])

        #Reallocate width length
        widths[0] = widths[1] = widths[2] = widths[3] = 50
        
        for w in widths:
            pipe += " %-"+"%ss |" % (w,)
            separator += '-'*w + '--+'

        print "\n*****Current and Total Connections per Host:*****"
        print(separator)
        print(pipe % tuple(columns))
        print(separator)
        for row in results:
            print(pipe % row)
        print(separator)

if __name__ == '__main__':
    CheckDB().check_connection()
    CheckDB().get_db_size()
    CheckDB().get_tables_h2o()
    CheckDB().get_tables_mrunit()
    CheckDB().get_table_size()
    CheckDB().get_host()