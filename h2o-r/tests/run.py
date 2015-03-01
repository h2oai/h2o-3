#!/usr/bin/python

import os
import sys


#
# Change this path when copying and pasting this script.
#

g_root_dir = os.path.join("..", "..")


#
# Do not change anything below this line.
#

def main():
    global g_root_dir
    absolute_this_script_dir = os.path.dirname(os.path.realpath(__file__))
    relative_run_py = os.path.join(g_root_dir, os.path.join("scripts", "run.py"))
    absolute_run_py = os.path.join(absolute_this_script_dir, relative_run_py)
    args = []
    args.append(sys.executable)
    args.append(absolute_run_py)
    for i in range (1, len(sys.argv)):
        args.append(sys.argv[i])
    os.execv(sys.executable, args)


if __name__ == "__main__":
    main()
