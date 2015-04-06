import sys, psutil, os, stat, tempfile, argparse, time, datetime
sys.path.extend(['.','..','../..','py'])
import h2o_sandbox

# Stripped down, similar to h2o.py has for these functions
# Possible to do this in bash, but the code becomes cryptic.
# You can execute this as sh2junit.py <bash command string>

# sh2junit runs the cmd_string as a subprocess, with stdout/stderr going to files in sandbox
# and stdout to python stdout too.
# When it completes, check the sandbox for errors (using h2o_sandbox.py
# prints interesting things to stdout. Creates the result xml in the current dire
# with name "sh2junit_<name>.xml"

def sandbox_tmp_file(prefix='', suffix=''):
    # this gives absolute path, good!
    dirname = './sandbox'
    if not os.path.exists(dirname):
        print "no ./sandbox. Creating"
        os.makedirs(dirname)

    fd, path = tempfile.mkstemp(prefix=prefix, suffix=suffix, dir=dirname)
    # make sure the file now exists
    # os.open(path, 'a').close()
    # give everyone permission to read it (jenkins running as 
    # 0xcustomer needs to archive as jenkins
    #permissions = stat.S_IRUSR | stat.S_IWUSR | stat.S_IRGRP | stat.S_IROTH
    os.chmod(path, 0644) #'644')  #permissions)
    return (fd, path)

#**************************************************************************
# Example junit xml
#<?xml version="1.0" encoding="UTF-8"?>
#<testsuites disabled="" errors="" failures="" name="" tests="" time="">
#    <testsuite disabled="" errors="" failures="" hostname="" id="" name="" package="" skipped="" tests="" time="" timestamp="">
#        <properties>
#            <property name="" value=""/>
#        </properties>
#        <testcase assertions="" classname="" name="" status="" time="">
#            <skipped/>
#            <error message="" type=""/>
#            <failure message="" type=""/>
#            <system-out/>
#            <system-err/>
#        </testcase>
#        <system-out/>
#        <system-err/>
#    </testsuite>
#</testsuites>
def create_junit_xml(name, out, err, sandboxErrorMessage, errors=0, elapsed=0):
    # http://junitpdfreport.sourceforge.net/managedcontent/PdfTranslation

    # not really nosetests..just trying to mimic the python xml
    content  = '<?xml version="1.0" encoding="UTF-8" ?>\n'
    content += '    <testsuite name="nosetests" tests="1" errors="%s" failures="0" skip="0">\n' % (errors)
    content += '        <testcase classname="%s" name="%s" time="%0.4f">\n' % (name, name, elapsed)
    if errors != 0 and not sandboxErrorMessage:
        content += '            <error type="Non-zero R exit code" message="Non-zero R exit code"></error>\n'
    # may or may not be 2 errors (R exit code plus log error
    if errors != 0 and sandboxErrorMessage:
        content += '            <error type="Error in h2o logs" message="Error in h2o logs"></error>\n'
    content += '            <system-out>\n'
    content += '<![CDATA[\n'
    content += 'spawn stdout' + str(datetime.datetime.now()) + '**********************************************************\n'
    content += out
    content += ']]>\n'
    content += '            </system-out>\n'

    content += '            <system-err>\n'
    content += '<![CDATA[\n'
    content += 'spawn stderr' + str(datetime.datetime.now()) + '**********************************************************\n'
    content += err
    if sandboxErrorMessage:
        content += 'spawn errors from sandbox log parsing*********************************\n'
        # maybe could split this into a 2nd stdout or stder ..see above 
        content += sandboxErrorMessage
    content += ']]>\n'
    content += '            </system-err>\n'

    content += '        </testcase>\n'
    content += '    </testsuite>\n'

    # see if adding nosetests makes michal's stuff pick it up??
    # and "test_" prefix"
    x = './test_' + os.path.basename(name) + '.nosetests.xml'
    with open(x, 'wb') as f:
        f.write(content)
    #f = open(x, 'w')
    #f.write(content)
    #f.close()

#**************************************************************************
# belt and suspenders. Do we really need to worry about this?
def terminate_process_tree(pid, including_parent=True):
    parent = psutil.Process(pid)
    for child in parent.get_children(recursive=True):
        try:
            child.terminate()
        except psutil.NoSuchProcess:
            print "terminate_process_tree:", "NoSuchProcess. couldn't terminate child process with pid %s" % child.pid()
        except psutil.AccessDenied:
            print "terminate_process_tree:", "couldn't terminate child process with pid %s" % child.pid()
        else:
            child.wait(timeout=3)

    if including_parent:
        try:
            parent.terminate()
        except psutil.NoSuchProcess:
            print "terminate_process_tree:", "NoSuchProcess. couldn't terminate parent process with pid %s" % parent.pid()
            pass
        except psutil.AccessDenied:
            print "terminate_process_tree:", "AccessDenied. couldn't terminate parent process with pid %s" % parent.pid()
        else:
            parent.wait(timeout=3)

def terminate_child_processes():
    me = os.getpid()
    terminate_process_tree(me, including_parent=False)

#**************************************************************************
def rc_if_exists_and_done(ps):
    try:
        rc = ps.wait(0)
    except psutil.TimeoutExpired:
        # not sure why I'm getting this
        print "Got TimeoutExpired on the R subprocess, may be legal"
        rc = None
    except psutil.NoSuchProcess:
        raise Exception("The R subprocess disappeared when we thought it should still be there")
    except psutil.AccessDenied:
        raise Exception("The R subprocess gave us AccessDenied")

    # rc = None means it already completed? 
    # FIX! Is it none if we get a timeout exception on this python ..how is that captured?
    if rc:
        # increment the global errors count if we get a non-zero rc. non-zero rc should only happen once?
        error = 1
        print "rc_if_exists_and_done: got non-zero rc: %s" % rc
    else:
        error = 0
    return (rc, error)

#**************************************************************************
def sh2junit(name='NoName', cmd_string='/bin/ls', timeout=300, shdir=None, **kwargs):
    # split by arbitrary strings of whitespace characters (space, tab, newline, return, formfeed)
    print "cmd_string:", cmd_string
    cmdList = cmd_string.split()
    # these are absolute paths
    outfd, outpath = sandbox_tmp_file(prefix=name + '.stdout.', suffix='.log')
    errfd, errpath = sandbox_tmp_file(prefix=name + '.stderr.', suffix='.log')

    # make outpath and errpath full paths, so we can redirect
    print "outpath:", outpath
    print "errpath:", errpath

    start = time.time()
    print "psutil.Popen:", cmdList, outpath, errpath
    import subprocess
    # start the process in the target dir, if desired
    if shdir:
        currentDir = os.getcwd()
        os.chdir(shdir)
    ps = psutil.Popen(cmdList, stdin=None, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    if shdir:
        os.chdir(currentDir)

    comment = 'PID %d, stdout %s, stderr %s' % (
        ps.pid, os.path.basename(outpath), os.path.basename(errpath))
    print "spawn_cmd", cmd_string, comment

    # Reads the subprocess stdout until it is closed and 
    # ...echo it our python stdout and also the R stdout file in sandbox
    # Then wait for the program to exit. 
    # Read before wait so that you don't risk the pipe filling up and hanging the program. 
    # You wait after read for the final program exit and return code. 
    # If you don't wait, you'll get a zombie process (at least on linux)

    # this might not do what we want..see:
    # http://stackoverflow.com/questions/2804543/read-subprocess-stdout-line-by-line
    # I suppose we'll stop early?

    # shouldn't need a delay before checking this?
    if not ps.is_running():
        raise Exception("sh2junit: not immediate ps.is_running after start")

    # Until we get the rc, it can be a zombie process.
    # A zombie process is not a real process. 
    # it's just a remaining entry in the process table until the parent process requests the child's return code. 
    # The actual process has ended and requires no other resources but said process table entry.
    linesMayExist = True
    errors = 0 
    timeoutError = False
    while linesMayExist:
        # get whatever accumulated, up to nothing returned 
        # only do up to 20 lines before we check timeout again
        # why was R processes not completing on centos?
        # linesMayExist = ps.is_running() and not ps.status() == psutil.STATUS_ZOMBIE
        linesMayExist = ps.is_running()
        lineBurstCnt = 0
        # stdout from subprocess
        line = ps.stdout.readline()

        # R apparently uses stderr a lot, so want to mix that in. We don't grab it until we hit a stall in R stdout though.
        while line:
            lineBurstCnt += 1
            # maybe I should use p.communicate() instead. have to keep it to stdout? or do stdout+stderr here
            sys.stdout.write("R->" + line) # to our python stdout, with a prefix so it's obviously from R
            sys.stdout.flush()
            os.write(outfd, line) # to sandbox R stdout
            elapsed = time.time() - start
            if elapsed > timeout:
                timeoutError = True
                errors += 1
                print "ERROR: sh2junit: elapsed: %0.2f timeout: %s (secs) while echoing subprocess stdout" % (elapsed, timeout)
                #kill R subprocess but don't kill me
                terminate_process_tree(ps.pid, including_parent=False)
                break
            line = ps.stdout.readline()
        if timeoutError:
            print "\n\n\nERROR: timeout"
            break
        # stderr from subprocess
        line = ps.stderr.readline()
        while line:
            lineBurstCnt += 1
            sys.stdout.write("Re->" + line) # to our python stdout, with a prefix so it's obviously from R stderr
            sys.stdout.flush()
            os.write(errfd, line) # to sandbox R stderr
            line = ps.stderr.readline()
        print "lineBurstCnt:", lineBurstCnt

        # Check. may have flipped to not running, and we just got the last bit.
        # shouldn't be a race on a transition here, if ps.wait(0) completion syncs the transition
        if linesMayExist:
            print "ps.is_running():", ps.is_running(), ps.pid, ps.name, ps.status, ps.create_time
            # unload the return code without waiting..so we don't have a zombie!

        (lastrc, error) = rc_if_exists_and_done(ps)
        errors += error

        elapsed = time.time() - start
        # forever if timeout is None
        #if timeout and elapsed > timeout:
        if elapsed > timeout:
            timeoutError = True
            errors += 1
            # we don't want to exception here, because we're going to print the xml that says there's an error
            # I guess we'll end up terminating the R process down below
            # could we have lines in stdout we didn't catch up on? maybe, but do we care?
            print "ERROR: sh2junit: elapsed: %0.2f timeout: %s (secs) while echoing subprocess stdout" % (elapsed, timeout)
            #kill R subprocess but don't kill me
            #terminate_process_tree(ps.pid, including_parent=False)
            break
        # wait for some more output to accumulate
        time.sleep(0.25)
        
    # It shouldn't be running now?

    # timeout=None waits forever. timeout=0 returns immediately.
    # default above is 5 minutes
    # Wait for process termination. Since child:  return the exit code. 
    # If the process is already terminated does not raise NoSuchProcess exception 
    # but just return None immediately. 
    # If timeout is specified and process is still alive raises psutil.TimeoutExpired() exception. 
    # old
    # rc = ps.wait(timeout)
    (lastrc, error) = rc_if_exists_and_done(ps)
    errors += error
    elapsed = time.time() - start

    # Prune h2o logs to interesting lines and detect errors.
    # Error lines are returned. warning/info are printed to our (python stdout)
    # so that's always printed/saved?
    # None if no error
    sandboxErrorMessage = h2o_sandbox.check_sandbox_for_errors(
        LOG_DIR='./sandbox', 
        python_test_name=name, 
        cloudShutdownIsError=True, 
        sandboxIgnoreErrors=True) # don't take exception on error

    if sandboxErrorMessage:
        errors += 1

    out = file(outpath).read()
    err = file(errpath).read()
    create_junit_xml(name, out, err, sandboxErrorMessage, errors=errors, elapsed=elapsed)

    if not errors:
        return (errors, outpath, errpath)
    else:
        # dump all the info as part of the exception? maybe too much
        # is this bad to do in all cases? do we need it? 
        hline = "\n===========================================BEGIN DUMP=============================================================\n"
        hhline = "\n===========================================END DUMP=============================================================\n"
        out = '[stdout->err]: '.join(out.splitlines(True))
        err = '[sterr->err]: '.join(err.splitlines(True))
        if ps.is_running():
            print "Before terminate:", ps.pid, ps.is_running()
            terminate_process_tree(ps.pid, including_parent=True)
        if sandboxErrorMessage:
            print "\n\n\nError in Sandbox. Ending test. Dumping sub-process output.\n"
            print hline
            raise Exception("%s %s \n\tlastrc:%s \n\terrors:%s \n\tErrors found in ./sandbox log files?.\nR stdout:\n%s\n\nR stderr:\n%s\n%s" % 
                (name, cmd_string, lastrc, errors, out, err, hhline))
        # could have already terminated?
        elif timeoutError:
            print "\n\n\nTimeout Error. Ending test. Dumping sub-process output.\n"
            print hline
            raise Exception("%s %s \n\tlastrc:%s \n\terrors:%s \n\ttimed out after %d secs. \nR stdout:\n%s\n\nR stderr:\n%s\n%s" %
                (name, cmd_string, lastrc, errors, timeout or 0, out, err, hhline))
        else:
            print "\n\n\nCaught exception. Ending test. Dumping sub-process output.\n"
            print hline
            raise Exception("%s %s \n\tlastrc:%s \n\terrors:%s \n\tLikely non-zero exit code from R.\nR stdout:\n%s\n\nR stderr:\n%s\n%s" % 
                (name, cmd_string, lastrc, errors, out, err, hhline))


#**************************************************************************
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('-shdir', type=str, default=None, help='executes the $cmd in the target dir, but the logs stay in sandbox here')
    parser.add_argument('-name', type=str, default='NoName', help='used to help name the xml/stdout/stderr logs created')
    parser.add_argument('-timeout', type=int, default=5, help='secs timeout for the shell subprocess. Fail if timeout')
    parser.add_argument('-cmd', '--cmd_string', type=str, default=None, help="cmd string to pass to shell subprocess. Better to just use'--' to start the cmd (everything after that is sucked in)")
    parser.add_argument('Rargs', nargs=argparse.REMAINDER)
    args = parser.parse_args()

    if args.cmd_string:
        cmd_string = args.cmd_string
    else:
        # easiest way to handle multiple tokens for command
        # end with -- and this grabs the rest
        # drop the leading '--' if we stopped parsing the rest that way
        if args.Rargs:
            print "args.Rargs:", args.Rargs
            if args.Rargs[0]=='--':
                args.Rargs[0] = ''
            cmd_string = ' '.join(args.Rargs)
        else:
            # placeholder for test
            cmd_string = '/bin/ls'
        
    sh2junit(name=args.name, cmd_string=cmd_string, timeout=args.timeout, shdir=args.shdir)

