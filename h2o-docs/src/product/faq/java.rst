Java
----

**How do I use H2O with Java?**

There are two ways to use H2O with Java. The simplest way is to call the
REST API from your Java program to a remote cluster and should meet the
needs of most users.

You can access the REST API documentation within Flow, or on our
`documentation site <../rest-api-reference.html>`__.

Flow, Python, and R all rely on the REST API to run H2O. For example,
each action in Flow translates into one or more REST API calls. The
script fragments in the cells in Flow are essentially the payloads for
the REST API calls. Most R and Python API calls translate into a single
REST API call.

To see how the REST API is used with H2O:

-  Using Chrome as your internet browser, open the developer tab while
   viewing the web UI. As you perform tasks, review the network calls
   made by Flow.

-  Write an R program for H2O using the H2O R package that uses
   ``h2o.startLogging()`` at the beginning. All REST API calls used are
   logged.

The second way to use H2O with Java is to embed H2O within your Java
application, similar to `Sparkling
Water <https://github.com/h2oai/sparkling-water/blob/master/DEVEL.rst>`__.

--------------

**How do I communicate with a remote cluster using the REST API?**

To create a set of bare POJOs for the REST API payloads that can be used
by JVM REST API clients:

1. Clone the sources from GitHub.
2. Start an H2O instance.
3. Enter ``% cd py``.
4. Enter ``% python generate_java_binding.py``.

This script connects to the server, gets all the metadata for the REST
API schemas, and writes the Java POJOs to
``{sourcehome}/build/bindings/Java``.

--------------

**I keep getting a message that I need to install Java. I have the
latest version of Java installed, but I am still getting this message.
What should I do?**

This error message displays if the ``JAVA_HOME`` environment variable is
not set correctly. The ``JAVA_HOME`` variable is likely points to Apple
Java version 6 instead of Oracle Java version 8.

If you are running OS X 10.7 or earlier, enter the following in
Terminal:
``export JAVA_HOME=/Library/Internet\ Plug-Ins/JavaAppletPlugin.plugin/Contents/Home``

If you are running OS X 10.8 or later, modify the launchd.plist by
entering the following in Terminal:

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
