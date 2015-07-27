import subprocess;
import signal, os, time, sys;
import argparse;
from threading import Timer;

ARG_PARSER = argparse.ArgumentParser(description='Runs a process with a specified time limit. If the execution time exceeds the limit, then a SIGQUIT signal is sent followed by a SIGKILL.');

SUCCESS, TIMEOUT_ERR = range(2);
EXIT_CODE = SUCCESS;
DIR = "/Users/nkalonia/h2o/h2o-3/h2o-algos";
CMD = ["sh", "testMultiNode.sh"];
OUT_FILE = None;
TIME_LIMIT = 10;

def main():
    print("STARTING PROCESS...");
    process = subprocess.Popen(args=CMD, stdout=OUT_FILE, stderr=subprocess.STDOUT, preexec_fn=os.setpgrp, cwd=DIR);
    wait_timer = Timer(TIME_LIMIT, kill, [process]);
    wait_timer.start();
    process.wait();
    wait_timer.cancel();

    if EXIT_CODE == TIMEOUT_ERR:
        msg = "Process timed out";
    else:
        msg = "Process ran successfully. Darn...";
    print("PROCESS COMPLETE ({msg})".format(msg=msg));
    if EXIT_CODE == TIMEOUT_ERR:
        sys.exit(1);

        
def kill(process):
    os.killpg(process.pid, signal.SIGQUIT);
    time.sleep(1);
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGKILL);
    global EXIT_CODE;
    EXIT_CODE = TIMEOUT_ERR;


def createCMD(cmd):
    return cmd.split();

def parseArgs():
    global CMD, OUT_FILE, DIR, TIME_LIMIT;
    args = ARG_PARSER.parse_args();
    CMD = args.command;
    OUT_FILE = args.outfile;
    DIR = args.directory;
    TIME_LIMIT = args.timeout;

def formatArgParser():
    ARG_PARSER.add_argument("-c", "--command", action="store", default=CMD, type=createCMD, help="the command to be executed", metavar="CMD");
    ARG_PARSER.add_argument("-t", "--timeout", action="store", default=TIME_LIMIT, type=int, help="the time limit (in seconds) given for the process before it is killed", metavar="SEC");
    ARG_PARSER.add_argument("-d", "--directory", action="store", default=DIR, help="the working directory for the process", metavar="DIR");
    ARG_PARSER.add_argument("-o", "--outfile", action="store", default=OUT_FILE, type=argparse.FileType('w'), help="the file to log the process\' output streams", metavar="FILENAME");

    
if __name__ == "__main__":
    formatArgParser();
    parseArgs();
    main();
