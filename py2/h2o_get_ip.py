# Hackery: find the ip address that gets you to Google's DNS
# Trickiness because you might have multiple IP addresses (Virtualbox), or Windows.
# we used to not like giving ip 127.0.0.1 to h2o?
import sys, socket, os, getpass
import h2o_args

# print "h2o_get_ip"

# copied here from h2o_test.py to eliminate a circular import 
def verboseprint(*args, **kwargs):
    if h2o_args.verbose:
        for x in args: # so you don't have to create a single string
            print x,
        for x in kwargs: # so you don't have to create a single string
            print x,
        print
        # so we can see problems when hung?
        sys.stdout.flush()

def get_ip_address(ipFromCmdLine=None):
    if ipFromCmdLine:
        verboseprint("get_ip case 1:", ipFromCmdLine)
        return ipFromCmdLine

    ip = '127.0.0.1'
    socket.setdefaulttimeout(0.5)
    hostname = socket.gethostname()
    # this method doesn't work if vpn is enabled..it gets the vpn ip
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 0))
        ip = s.getsockname()[0]
        verboseprint("get_ip case 2:", ip)
    except:
        pass

    try:
        if ip.startswith('127'):
            # drills down into family
            ip = socket.getaddrinfo(hostname, None)[0][4][0]
            verboseprint("get_ip case 3:", ip)
    except:
        pass

    ipa = None
    # we had some hosts that didn't support gethostbyname_ex().
    # hopefully we don't need a hack to exclude
    # the gethostbyname_ex can be slow. the timeout above will save us quickly
    try:
        # Translate a host name to IPv4 address format, extended interface.
        # This should be resolve by dns so it's the right ip for talking to this guy?
        # Return a triple (hostname, aliaslist, ipaddrlist)
        # where hostname is the primary host name responding to the given ip_address,
        # aliaslist is a (possibly empty) list of alternative host names for the same address,
        # ipaddrlist is a list of IPv4 addresses for the same interface on the same host
        ghbx = socket.gethostbyname_ex(hostname)
        for ips in ghbx[2]:
            # only take the first
            if ipa is None and not ips.startswith("127."):
                ipa = ips[:]
                verboseprint("get_ip case 4:", ipa)
                if ip != ipa:
                    print "\nAssuming", ip, "is the ip address h2o will use but", ipa,\
                        "is probably the real ip?"
                    print "You might have a vpn active. Best to use '-ip", ipa,\
                        "'to get python and h2o the same."
    except:
        pass
        # print "Timeout during socket.gethostbyname_ex(hostname)"

    verboseprint("get_ip_address:", ip)
    # set it back to default higher timeout (None would be no timeout?)
    socket.setdefaulttimeout(5)
    return ip

