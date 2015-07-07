#!/usr/bin/python
import sys, itertools, os, re, glob


# use glob.glob. it uses os.listdir() and fnmatch.fnmatch() ..so it's unix style pattern match
def check_sandbox_for_errors(LOG_DIR=None, python_test_name='',
    cloudShutdownIsError=False, sandboxIgnoreErrors=False, pattern=None, verbose=False):
    # show the parameters
    ### print "check_sandbox_for_errors:", locals()

    # gets set below on error (returned)
    errorFound = False

    if not LOG_DIR:
        LOG_DIR = './sandbox'

    if not os.path.exists(LOG_DIR):
        if verbose:
            print "directory", LOG_DIR, "not found"
        return

    # FIX! wait for h2o to flush to files? how?
    # Dump any assertion or error line to the screen
    # Both "passing" and failing tests??? I guess that's good.
    # if you find a problem, just keep printing till the end, in that file.
    # The stdout/stderr is shared for the entire cloud session?
    # so don't want to dump it multiple times?

    # glob gives full path, so we have to strip to match os.listdir()
    goodLogsList = []

    # if we're using a pattern, ignore the "done" files
    if pattern:
        if verbose:
            print "Only looking at files that match pattern:", pattern
        # search whatever the pattern says
        # need to exclude directories (syn_datasets)
        tempFileList = glob.glob(LOG_DIR + "/" + pattern)
        # have to remove all the line count temp files
        # ignore the json file we copy there also (anything eding in json)
        for filename in tempFileList:
            if os.path.isfile(filename) and not re.search('doneToLine', filename) and not re.search('\.json$', filename):
                goodLogsList.append(os.path.basename(filename))

        if len(goodLogsList)==0:
            raise Exception("Unexpected: h2o_sandbox found 0 files in %s that matched the pattern: %s" % \
                (LOG_DIR, pattern) )
    else:
        tempFileList = os.listdir(LOG_DIR)
        if verbose:
            print "tempFileList:", tempFileList
        # don't search the R stdout/stderr
        # this matches the python h2o captured stdout/stderr, and also any downloaded h2o logs
        # not the commands.log
        for filename in tempFileList:
            # for h2o on hadoop, in the common unit test stuff, we download zipped logs from h2o
            # at the end and expand them. They will be in sandbox like this, because of the names h2o creates
            # in the zip (I flatten it in sandbox): h2o_172.16.2.178_54321.log
            # So look for that pattern too!
            # we log roll so the h2o.*logs can end in a number
            if re.search('h2o.*stdout|h2o.*stderr|h2o.*\.log.*', filename) and not re.search('doneToLine', filename):
                goodLogsList.append(filename)

        if verbose:
            print "goodLogsList:", goodLogsList
        if len(goodLogsList)==0:
            # let this go...sh2junit.py apparently calls h2o_sandbox() looking for h2o logs?
            emsg = "Unexpected: h2o_sandbox found 0 files in %s that matched the stdout/stderr or log pattern" % LOG_DIR
            if sandboxIgnoreErrors:
                print emsg
                return
            else:
                # FIX! have to figure out what to do when there are logs available to check for h2o on hadoop
                # and when to not care if they're not there
                pass
                # raise Exception(emsg)

    if verbose:
        print "h2o_sandbox: checking", len(goodLogsList), "files"

    errLines = []
    for filename in goodLogsList:
        goodLogPath = LOG_DIR + "/" + filename
        goodLog = open(goodLogPath, "r")
        if verbose:
            print "\nScraping this log:", goodLogPath

        # if we've already walked it, there will be a matching file
        # with the last line number we checked
        try:
            with open(LOG_DIR + "/doneToLine." + filename) as f:
                # if multiple processes are checking, this file isn't locked
                # if it's empty, treat it as zero
                r = f.readline().rstrip()
                if not r or r=="":
                    doneToLine = 0
                else:
                    try:
                        doneToLine = int(r)
                    except:
                        raise Exception("%s/doneToLine.%s is corrupted (multiprocess issue?): %s" % \
                            (LOG_DIR, filename, r))
                    
        except IOError:
            # no file
            doneToLine = 0

        # if we're using a pattern, ignore the doneToLine stuff (always start at 0
        if pattern:
            doneToLine = 0

        # just in case error/assert is lower or upper case
        # FIX! aren't we going to get the cloud building info failure messages
        # oh well...if so ..it's a bug! "killing" is temp to detect jar mismatch error
        regex1String = 'found multiple|exception|error|ERRR|assert|killing|killed|required ports|FATAL'
        if cloudShutdownIsError:
            regex1String += '|shutdown command'
        regex1 = re.compile(regex1String, re.IGNORECASE)
        regex2 = re.compile('Caused',re.IGNORECASE)
        # regex3 = re.compile('warn|info|TCP', re.IGNORECASE)
        # FIX! temp to avoid the INFO in jan's latest logging. don't print any info?
        # don't want the tcp_active in the cloud status. Ok to not look for tcp stuff now
        # regex3 = re.compile('warn|TCP', re.IGNORECASE)
        regex3 = re.compile('warn|Retrying after IO error', re.IGNORECASE)

        # many hdfs/apache messages have 'error' in the text. treat as warning if they have '[WARN]'
        # i.e. they start with:
        # [WARN]

        # if we started due to "warning" ...then if we hit exception, we don't want to stop
        # we want that to act like a new beginning. Maybe just treat "warning" and "info" as
        # single line events? that's better
        printing = 0 # "printing" is per file.
        lines = 0 # count per file! errLines accumulates for multiple files.
        currentLine = 0
        log_python_test_name = None

        if verbose:
            print "Using doneToLine line marker %s with %s" % (doneToLine, goodLogPath)

        for line in goodLog:
            currentLine += 1

            m = re.search('(python_test_name:) (.*)', line)
            if m:
                log_python_test_name = m.group(2)
                # if log_python_test_name == python_test_name):
                #    print "Found log_python_test_name:", log_python_test_name

            # don't check if we've already checked
            if currentLine <= doneToLine:
                continue

            # if log_python_test_name and (log_python_test_name != python_test_name):
            #     print "h2o_sandbox.py: ignoring because wrong test name:", currentLine

            # JIT reporting looks like this..don't detect that as an error
            printSingleWarning = False
            foundBad = False
            if not ' bytes)' in line:
                # no multiline FSM on this
                printSingleWarning = regex3.search(line)
                #   13190  280      ###        sun.nio.ch.DatagramChannelImpl::ensureOpen (16 bytes)
                # don't detect these class loader info messags as errors
                #[Loaded java.lang.Error from /usr/lib/jvm/java-7-oracle/jre/lib/rt.jar]
                foundBadPartial = regex1.search(line)
                foundBad = foundBadPartial and not (
                    ('classification error is') or
                    ('STARTING TEST' in line) or # R test named ..._pop_assert..
                    ('INFO:' in line and 'Error' in line) or
                    ('Failed to instantiate schema class:' in line) or
                    ('WARNING: found non-Schema Iced field:' in line) or # arno has 'errors' as a field
                    ('ti-UDP-R ERRR:' in line) or # from Shutdown AIOOBE
                    ('ti-UDP-R FATAL:' in line) or # from Shutdown
                    ('Skipping field that lacks an annotation' in line) or # can have DeepLearningModel$Errors
                    ('python_test_name' in line) or
                    ('Retrying after IO error' in line) or
                    ('Error on' in line) or
                    # temporary hack. getting these on shutdown in multi-machine
                    # ApiWatch  ERRR WATER: ApiPortWatchdog: 
                    #   Failed trying to connect to REST API IP and Port (/10.73.149.39:54323, 30000 ms)
                    ('ApiPortWatchdog' in line) or
                    ('Error reduced' in line) or
                    ('out-of-bag error estimation' in line) or
                    ('reconstruction error' in line) or
                    ('Prediction error' in line) or
                    (('Act/Prd' in line) and ('Error' in line)) or
                    (('AUC' in line) and ('Gini' in line) and ('Precision' in line)) or
                    ('Error on training data' in line) or
                    ('Error on validation data' in line) or
                    # These are real!
                    # ('water.DException' in line) or
                    # the manyfiles data has eRRr in a warning about test/train data
                    ('WARN SCORM' in line) or
                    # ignore the long, long lines that the JStack prints as INFO
                    ('stack_traces' in line) or
                    # shows up as param to url for h2o
                    ('out_of_bag_error_estimate' in line) or
                    # R stdout confusion matrix. Probably need to figure out how to exclude R logs
                    ('Training Error' in line) or
                    # now from GBM
                    ('Mean Squared Error' in line) or
                    ('Error' in line and 'Actual' in line) or
                    # fvec
                    ('prediction error' in line) or 
                    ('errors on' in line) or
                    # R
                    ('class.error' in line) or
                    # original RF
                    ('error rate' in line) or 
                    ('[Loaded ' in line) or
                    ('[WARN]' in line) or 
                    ('CalcSquareErrorsTasks' in line))

            if (printing==0 and foundBad):
                printing = 1
                lines = 1
            elif (printing==1):
                lines += 1
                # if we've been printing, stop when you get to another error
                # keep printing if the pattern match for the condition
                # is on a line with "Caused" in it ("Caused by")
                # only use caused for overriding an end condition
                foundCaused = regex2.search(line)
                # since the "at ..." lines may have the "bad words" in them, we also don't want
                # to stop if a line has " *at " at the beginning.
                # Update: Assertion can be followed by Exception.
                # Make sure we keep printing for a min of 4 lines
                foundAt = re.match(r'[\t ]+at ',line)
                if foundBad and (lines>10) and not (foundCaused or foundAt):
                    printing = 2

            if (printing==1):
                # to avoid extra newline from print. line already has one
                errLines.append(line)
                sys.stdout.write(line)

            # don't double print if warning
            elif (printSingleWarning):
                # don't print these lines
                if not (
                    ('Unable to load native-hadoop library' in line) or
                    ('stack_traces' in line) or
                    ('Multiple local IPs detected' in line) or
                    ('[Loaded ' in line) or
                    ('RestS3Service' in line) ):
                    sys.stdout.write(line)

        goodLog.close()
        # remember what you've checked so far, with a file that matches, plus a suffix
        # this is for the case of multiple tests sharing the same log files
        # only want the test that caused the error to report it. (not flat the subsequent ones as fail)
        # overwrite if exists
        with open(LOG_DIR + "/" + "doneToLine." + filename, "w") as f:
            f.write(str(currentLine) + "\n")

    sys.stdout.flush()

    # already has \n in each line
    # doing this kludge to put multiple line message in the python traceback,
    # so it will be reported by jenkins. The problem with printing it to stdout
    # is that we're in the tearDown class, and jenkins won't have this captured until
    # after it thinks the test is done (tearDown is separate from the test)
    # we probably could have a tearDown with the test rather than the class, but we
    # would have to update all tests.
    if len(errLines)!=0:
        # check if the lines all start with INFO: or have "apache" in them
        justInfo = 0
        for e in errLines:
            # very hacky. try to ignore the captured broken pipe exceptions.
            # if any line has this, ignore the whole group (may miss something)
            if "Broken pipe" in e:
                justInfo = 1
            # if every line has this (beginning of line match)
            elif justInfo==0 and not re.match("INFO:", e):
                justInfo = 2

        if justInfo==2:
            emsg1 = " check_sandbox_for_errors: Errors in sandbox stdout or stderr (or R stdout/stderr).\n" + \
                     "Could have occurred at any prior time\n\n"
            emsg2 = "".join(errLines)
            errorFound = True
            errorMessage = str(python_test_name) + emsg1 + emsg2

            # just print if using the pattern match
            if pattern or sandboxIgnoreErrors:
                print "###############################################################################################"
                print errorMessage
                print "###############################################################################################"
            else: 
                raise Exception(errorMessage)

    if errorFound:
        return errorMessage
    else:
        if verbose:
            print "h2o_sandbox: h2o logs seem okay"
        return

#*********************************************************************************************
# for use from the command line
if __name__ == "__main__":

    arg_names = ['me', 'LOG_DIR', 'python_test_name', 'cloudShutdownIsError', 'sandboxIgnoreErrors', 'verbose']
    # https://docs.python.org/2/library/itertools.html
    # Nice..Learning itertools stuff:
    # izip_longest: Make an iterator that aggregates elements from each of the iterables. 
    # If the iterables are of uneven length, missing values are filled-in with fillvalue. 
    # Iteration continues until the longest iterable is exhausted. 
    args = dict(itertools.izip_longest(arg_names, sys.argv))
    # if you're running this from the command line, remove any existing doneToLine markers
    if not args['LOG_DIR']:
        LOG_DIR = './sandbox'
    else:
        LOG_DIR = args['LOG_DIR']

    if os.path.exists(LOG_DIR):
        print "Checking for any marker files to remove first.." +\
             "(multi-test cloud log scrape uses and we always leave the droppings)"
        for f in glob.glob(LOG_DIR + '/*doneToLine*'):
            print "cleaning marker file:", f
            os.remove(f)

    # if you call from the command line, we'll just pass the first two positionally.
    # here's a low budget argsparse :) (args are optional!)
    errorMessage = check_sandbox_for_errors(
        LOG_DIR=LOG_DIR,
        python_test_name=args['python_test_name'],
        cloudShutdownIsError=args['cloudShutdownIsError'], 
        sandboxIgnoreErrors=True,
        verbose=True, 
        )
        # sandboxIgnoreErrors=args['sandboxIgnoreErrors'],
        # verbose=args['verbose'],


