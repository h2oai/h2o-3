#Connecting to H2O over LDAP

If your network uses an LDAP protocol, perform the following steps to connect to H2O: 

0. Launch H2O. 
0. Copy the URL that displays in the H2O output. In the following example, the # symbols represent the numbers in the IP address. 

   `Open H2O Flow in your web browser: https://###.##.###.##:54321`
0. Paste the URL into your browser. 
0. Log in using your LDAP credentials.

H2O is now ready to use. 

- To connect to an LDAP-enabled cluster in R, provide the username and password in the `init` call: 

  `h2o.init(ip="###.###.##.##", port = 54321, username = "myusername", password = "mypassword"`

- To kill the cluster and shutdown H2O, click the **Admin** menu in Flow, then click **Shut Down**. 
