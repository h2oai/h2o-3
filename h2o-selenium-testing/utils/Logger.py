import sys

class Logger(object):
    def __init__(self, filename="testlog.log"):
        temp = open(filename, "w")  # reset content file
        temp.close()

        self.filename = filename
        self.terminal = sys.stdout
        self.log = open(filename, "a")

    def write(self, message):
        self.terminal.write(message)
        self.log.write(message)

    def flush_all(self):
        self.terminal.flush()
        self.log.flush()

    def flush(self):
        self.log.flush()

    def read(self):
        self.flush()
        temp = open(self.filename, "r")
        content_log_file = temp.read()
        temp.close()

        # reset stdout stream
        sys.stdout = sys.__stdout__
        self.flush_all()
        self.log.close()

        return content_log_file
