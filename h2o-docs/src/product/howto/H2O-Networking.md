# H2O Networking

## Internal Communication

By default, H2O selects the IP and PORT for internal communication automatically using the following this process (if not specified):

  1. Retrieve a list of available interfaces (which are up).
  2. Sort them with "bond" interfaces put on the top.
  3. For each interface, extract associated IPs.
  4. Pick only reachable IPs (that filter IPs provided by interfaces such as awdl):

   - If there is a site IP, use it.
   - Otherwise, if there is a link local IP, use it. (For IPv6, the link IP 0xfe80/96 is associated with each interface.)
   - Or finally, try to find a local IP. (Use loopback or try to use Google DNS to find IP for this machine.)

The port is selected by looking for a free port starting with port 54322. This can be modified using the `-port` and `-baseport` options.

### Options
The IP, PORT and network selection can be changed by the following options

  - `-ip` specifies IP for the machine, for example:
    - IPv4: `-ip 178.16.2.223` 
    - IPv6: `-ip 2001:db8:1234:0:0:0:0:1` (short version of IPv6 with `::` is not supported). Note: if you are selecting a link-local address fe80::/96 it is necessary to specify _zone index_ - e.g., `%en0` for `fe80::2acf:e9ff:fe15:e0f3%en0` to select the right interface.
  - `network` limits selection of IP to a specified subnet
    - IPv4: `-network 178.0.0.0/8`
    - IPv6: `-network 2001:db8:1234:0:0:0:0:0/48` (short version of IPv6 with `::` is not supported)
  - `-port` specifies PORT used for REST API, the communication port will be the port with value +1 higher
  - `-baseport` specifies starting port to find a free port for REST API, the internal communication port will be port with value +1 higher

## Clouding Up: Cluster Creation

H2O provides two modes for cluster creation:

  1. Multicast based
  2. Flatfile based

### Multicast 
In this mode, H2O is using IP multicast to announce existence of H2O nodes. Each node selects the same multicast group and port based on specified shared cloud name (see `-name` option). For example, for IPv4/PORT a generated multicast group is `228.246.114.236:58614` (for cloud name `michal`), 
for IPv6/PORT a generated multicast group is `ff05:0:3ff6:72ec:0:0:3ff6:72ec:58614` (for cloud name `michal` and link-local address which enforce link-local scope).

For IPv6 the scope of multicast address is enforced by a selected node IP. For example, if IP the selection process selects link-local address, then the scope of multicast will be link-local. This can be modified by specifying JVM variable `sys.ai.h2o.network.ipv6.scope` which enforces addressing scope use in multicast group address (for example, `-Dsys.ai.h2o.network.ipv6.scope=0x0005000000000000` enforces the site local scope. For more details please consult the
class `water.util.NetworkUtils`).

For more information about scopes, see <a href="http://www.tcpipguide.com/free/diagrams/ipv6scope.png" target="_blank">http://www.tcpipguide.com/free/diagrams/ipv6scope.png</a>. 

### Flatfile
The flatfile describes a topology of a H2O cluster. The flatfile definition is passed via `-flatfile` option. 
It needs to be passed at each node in the cluster, but definition does not be the same at each node. However, transitive closure of all definitions should contains all nodes. For example, for the following definition

Nodes    | nodeA | nodeB | nodeC 
---------|-------|-------|-------
Flatfile | A,B   | A, B  | B, C  

The resulting cluster will be formed by nodes A, B, C. The node A transitively sees node C via node B flatfile definition, and vice versa.

The flatfile contains a list of nodes in the form `IP:PORT` (each node on separated line, everythin prefixed by `#` is ignored) which are going to compose a resulting cluster.
For example:

**IPv4**:

```
# run two nodes on 108
10.10.65.108:54322
10.10.65.108:54325
```
**IPv6**:

```
0:0:0:0:0:0:0:1:54321
0:0:0:0:0:0:0:1:54323
```


## Web Server
The web server IP is auto-configured in the same way as internal communication IP, nevertheless the created socket listens on all available interfaces. A specific API can be specified by the `-web_ip` option.

### Options
  - `-web_ip`: specifies IP for web server to expose REST API

## Dual Stacks
Dual stack machines support IPv4 and IPv6 network stacks.
Right now, H2O always prefer IPV4, however the preference can be changed via JVM system options `java.net.preferIPv4Addresses` and `java.net.preferIPv6Addresses` options.

For example:

  - `-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Addresses=true` - H2O will try to select IPv4

  - `-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Addresses=false` - H2O will try to select IPv6


