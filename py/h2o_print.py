
import getpass

# some fun to match michal's use of green in his messaging in ec2_cmd.py
# generalize like http://stackoverflow.com/questions/287871/print-in-terminal-with-colors-using-python
class bcolors:
    PURPLE = ''
    BLUE = ''
    GREEN = ''
    YELLOW = ''
    RED = ''
    ENDC = ''

    def enable(self):
        self.PURPLE = '\033[95m'
        self.BLUE = '\033[94m'
        self.GREEN = '\033[92m'
        self.YELLOW = '\033[93m'
        self.RED = '\033[91m'
        self.ENDC = '\033[0m'

    def disable(self):
        self.PURPLE = ''
        self.BLUE = ''
        self.GREEN = ''
        self.YELLOW = ''
        self.RED = ''
        self.ENDC = ''

b = bcolors()
b.enable()

def disable_colors():
    b.disable()

def enable_colors():
    b.enable()

# make these compatible with multiple args like print?
def green_print(*args):
    # the , at the end means no eol
    if getpass.getuser()=='jenkins':
        b.disable()
    for msg in args:
        print b.GREEN + str(msg) + b.ENDC,
    print

def blue_print(*args):
    if getpass.getuser()=='jenkins':
        b.disable()
    for msg in args:
        print b.BLUE + str(msg) + b.ENDC,
    print

def yellow_print(*args):
    if getpass.getuser()=='jenkins':
        b.disable()
    for msg in args:
        print b.YELLOW + str(msg) + b.ENDC, 
    print

def red_print(*args):
    if getpass.getuser()=='jenkins':
        b.disable()
    for msg in args:
        print b.RED + str(msg) + b.ENDC,
    print

def purple_print(*args):
    if getpass.getuser()=='jenkins':
        b.disable()
    for msg in args:
        print b.PURPLE + str(msg) + b.ENDC,
    print

