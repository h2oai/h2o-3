
import subprocess
import getpass

def kill_process_tree(pid, including_parent=True):
    parent = psutil.Process(pid)
    for child in parent.get_children(recursive=True):
        child.kill()
    if including_parent:
        parent.kill()

def kill_child_processes():
    me = os.getpid()
    kill_process_tree(me, including_parent=False)

# since we hang if hosts has bad IP addresses, thought it'd be nice
# to have simple obvious feedback to user if he's running with -v 
# and machines are down or his hosts definition has bad IPs.
# FIX! currently not used
def ping_host_if_verbose(host):
    # if (h2o.verbose) 
    if 1==1:
        username = getpass.getuser()
        # if username=='jenkins' or username=='kevin' or username=='michal':
        if username=='jenkins' or username=='kevin':
            ping = subprocess.Popen( ["ping", "-c", "4", host])
            ping.communicate()

def check_port_group(base_port):
    # Only enable if useful for debug
    if 1==1:
        username = getpass.getuser()
        # if username=='jenkins' or username=='kevin' or username=='michal':
        if username=='jenkins':
            # assumes you want to know about 3 ports starting at base_port
            # can't use p, not root
            command1Split = ['netstat', '-an']
            command2Split = ['egrep']
            # colon so only match ports. space at end? so no submatches
            command2Split.append("(%s | %s)" % (base_port, base_port+1) )
            command3Split = ['wc','-l']

            print "Checking 2 ports starting at ", base_port
            print ' '.join(command2Split)

            # use netstat thru subprocess
            p1 = subprocess.Popen(command1Split, stdout=subprocess.PIPE)
            p2 = subprocess.Popen(command2Split, stdin=p1.stdout, stdout=subprocess.PIPE)
            output = p2.communicate()[0]
            print output

# I suppose we should use psutil here. since everyone has it installed?
# and it should work on windows?
def show_h2o_processes():
    # Only enable if useful for debug
    if 1==0:
        username = getpass.getuser()
        h2oFound = False
        users = set()
        h2oUsers = set()
        # if username=='jenkins' or username=='kevin' or username=='michal':
        if username=='jenkins' or username=='kevin':
            import psutil
            # print "get_users:", psutil.get_users()
            print "total physical dram:" , (psutil.TOTAL_PHYMEM+0)/(1024*1024), "GB"
            print "max cpu threads:", psutil.NUM_CPUS

            print "\nReporting on h2o"
            users = set()
            h2oUsers = set()
            h2oFound = False
            for p in psutil.process_iter():
                h2oProcess = False
                # hack.
                # psutil 2.x needs function reference
                # psutil 1.x needs object reference
                if hasattr(p.name, '__call__'):
                    pname = p.name()
                    pcmdline = p.cmdline()
                    # the user name might be uknown here, due to LXC?
                    try:
                        pusername = p.username()
                    except:
                        pusername = "Unknown-maybe-LXC-user"
                    pstatus = p.status()
                else:
                    pname = p.name
                    pcmdline = p.cmdline
                    try:
                        pusername = p.username
                    except:
                        pusername = "Unknown-maybe-LXC-user"
                    pstatus = p.status

                if hasattr(p.pid, '__call__'):
                    ppid = p.pid()
                else:
                    ppid = p.pid

                if 'java' in pname:
                    users.add(pusername)
                    # now iterate through the cmdline, to see if it's got 'h2o
                    for c in pcmdline:
                        if 'h2o' in c: 
                            h2oProcess = True
                            h2oUsers.add(pusername)
                            break

                if h2oProcess:
                    h2oFound = True
                    print "\n#**********************************************"
                    print p
                    # process could disappear while we're looking? (fast h2o version java process?)
                    try:
                        print "pid:", ppid
                        print "cmdline:", pcmdline
                        # AccessDenied problem?
                        # print p.getcwd()
                        print "status:", pstatus
                        print "username:", pusername
                        print "cpu_percent:", p.get_cpu_percent(interval=1.0)
                        print "memory_percent:", p.get_memory_percent()
                        print p.get_memory_info()
                        # AccessDenied problem
                        # print p.get_io_counters()
                        # AccessDenied problem
                        # p.get_open_files()
                        # AccessDenied problem
                        # print p.get_connections()
                    except:
                        pass
                

        if h2oFound:
            print "\n\n#**********************************************************************************************"
        else:
            print "No h2o processes found."
        print "\nusers running java:", list(users)
        print "users running h2o java:", list(h2oUsers)

