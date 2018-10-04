Java
----

**How do I use H2O with Java?**

There are two ways to use H2O with Java. The simplest way is to call the REST API from your Java program to a remote cluster and should meet the needs of most users.

You can access the REST API documentation within `Flow <http://docs.h2o.ai/h2o/latest-stable/h2o-docs/flow.html#viewing-rest-api-documentation>`__, or on our `documentation site <../rest-api-reference.html>`__.

Flow, Python, and R all rely on the REST API to run H2O. For example, each action in Flow translates into one or more REST API calls. The script fragments in the cells in Flow are essentially the payloads for the REST API calls. Most R and Python API calls translate into a single REST API call.

To see how the REST API is used with H2O:

-  Using Chrome as your internet browser, open the developer tab while viewing the web UI. As you perform tasks, review the network calls made by Flow.

-  Write an R program for H2O using the H2O R package that uses ``h2o.startLogging()`` at the beginning. All REST API calls used are logged.

The second way to use H2O with Java is to embed H2O within your Java application, similar to `Sparkling Water <https://github.com/h2oai/sparkling-water/blob/master/DEVEL.rst>`__.

--------------

**How do I communicate with a remote cluster using the REST API?**

To create a set of bare POJOs for the REST API payloads that can be used by JVM REST API clients:

1. Clone the sources from GitHub.
2. Start an H2O instance.
3. Enter ``% cd py``.
4. Enter ``% python generate_java_binding.py``.

This script connects to the server, gets all the metadata for the REST API schemas, and writes the Java POJOs to ``{sourcehome}/build/bindings/Java``.

--------------

**I keep getting a message that I need to install Java. I have a supported version of Java installed, but I am still getting this message. What should I do?**

This error message displays if the ``JAVA_HOME`` environment variable is not set correctly. The ``JAVA_HOME`` variable is likely points to Apple Java version 6 instead of Oracle Java version 8.

If you are running OS X 10.7 or earlier, enter the following in Terminal:

::

    export JAVA_HOME=/Library/Internet\ Plug-Ins/JavaAppletPlugin.plugin/Contents/Home``

If you are running OS X 10.8 or later, modify the launchd.plist by entering the following in Terminal:

::

    cat << EOF | sudo tee /Library/LaunchDaemons/setenv.JAVA_HOME.plist
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
      <plist version="1.0">
      <dict>
      <key>Label</key>
      <string>setenv.JAVA_HOME</string>
      <key>ProgramArguments</key>
      <array>
        <string>/bin/launchctl</string>
        <string>setenv</string>
        <string>JAVA_HOME</string>
        <string>/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home</string>
      </array>
      <key>RunAtLoad</key>
      <true/>
      <key>ServiceIPC</key>
      <false/>
    </dict>
    </plist>
    EOF

--------------

**I recently upgraded to Java 9, and now H2O no longer works. How can I switch to a supported version of Java?**

Java 9 is not yet supported but will be in an upcoming release. In the meantime, you can tell H2O which version of Java you'd like to use by setting the ``JAVA_HOME`` environment variable. 

 **On Mac OS X**

 First, run the following to get the location of a previous version of Java (for example, 1.8).

 ::
  
   /usr/libexec/java_home -v 1.8

 This will return the path for Java. For example:

 ::

   /Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home

 Next, add the path to your ``.bash_profile``.

 :: 

   export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home

 **In RStudio**

 In some cases, after you've set your JAVA_HOME environment variable to a supported version, H2O still fails in RStudio. If this is the case, add the path (for example, ``JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_25.jdk/Contents/Home``) to ``~/.Renviron``, and then restart RStudio.
