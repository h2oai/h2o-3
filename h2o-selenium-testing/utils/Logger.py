import datetime
import sys


class Logger(object):
    def __init__(self, filename="testlog.log"):
        temp = open(filename, "w")  # reset content file
        temp.close()

        self.filename = filename
        self.terminal = sys.stdout
        self.log = open(filename, "a")

    def write(self, message):
        prefix = str(datetime.datetime.now().strftime("%m-%d %H:%M:%S.%f")) + " "

        # message is not empty
        if len(message) > 2:
            message = prefix + message

        self.terminal.write(message)
        self.log.write(message)

        # todo: for debug. we should delete it when release
        self.flush()

    def read(self):
        self.flush()
        temp = open(self.filename, "r")
        content_log_file = temp.read()
        temp.close()

        self.close()

        return content_log_file

    def flush_all(self):
        self.terminal.flush()
        self.log.flush()

    def flush(self):
        self.log.flush()

    def close(self):
        # reset stdout stream
        sys.stdout = sys.__stdout__
        self.flush_all()
        self.log.close()
