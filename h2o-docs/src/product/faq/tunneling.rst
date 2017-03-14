Tunneling between Servers with H2O
----------------------------------

To tunnel between servers (for example, due to firewalls):

1. Use ssh to log in to the machine where H2O will run.
2. Start an instance of H2O by locating the working directory and
   calling a java command similar to the following example.

The port number chosen here is arbitrary; yours may be different.

``$ java -jar h2o.jar -port  55599``

This returns output similar to the following:

::

    irene@mr-0x3:~/target$ java -jar h2o.jar -port 55599
    04:48:58.053 main      INFO WATER: ----- H2O started -----
    04:48:58.055 main      INFO WATER: Build git branch: master
    04:48:58.055 main      INFO WATER: Build git hash: 64fe68c59ced5875ac6bac26a784ce210ef9f7a0
    04:48:58.055 main      INFO WATER: Build git describe: 64fe68c
    04:48:58.055 main      INFO WATER: Build project version: 1.7.0.99999
    04:48:58.055 main      INFO WATER: Built by: 'Irene'
    04:48:58.055 main      INFO WATER: Built on: 'Wed Sep  4 07:30:45 PDT 2013'
    04:48:58.055 main      INFO WATER: Java availableProcessors: 4
    04:48:58.059 main      INFO WATER: Java heap totalMemory: 0.47 gb
    04:48:58.059 main      INFO WATER: Java heap maxMemory: 6.96 gb
    04:48:58.060 main      INFO WATER: ICE root: '/tmp'
    04:48:58.081 main      INFO WATER: Internal communication uses port: 55600
    +                                  Listening for HTTP and REST traffic on
    +                                  http://192.168.1.173:55599/
    04:48:58.109 main      INFO WATER: H2O cloud name: 'irene'
    04:48:58.109 main      INFO WATER: (v1.7.0.99999) 'irene' on
    /192.168.1.173:55599, discovery address /230 .252.255.19:59132
    04:48:58.111 main      INFO WATER: Cloud of size 1 formed [/192.168.1.173:55599]
    04:48:58.247 main      INFO WATER: Log dir: '/tmp/h2ologs'

3. Log into the remote machine where the running instance of H2O will be
   forwarded using a command similar to the following. (Your specified
   port numbers and IP address will be different.)

   ``ssh -L 55577:localhost:55599 irene@192.168.1.173``

4. Check the cluster status.

You are now using H2O from localhost:55577, but the instance of H2O is
running on the remote server (in this case the server with the ip
address 192.168.1.xxx) at port number 55599.

To see this in action note that the web UI is pointed at
localhost:55577, but that the cluster status shows the cluster running
on 192.168.1.173:55599.
