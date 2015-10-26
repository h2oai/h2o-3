package water;

import groovy.lang.Binding;

import org.codehaus.groovy.tools.shell.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

/**
 * Created by michal on 10/25/15.
 */
public class H2OAppWithShell extends H2OStarter {

  public static void main(String[] args) {
    start(args, System.getProperty("user.dir"));
    startShell();
  }

  private static void startShell() {
    Binding binding = new Binding();
    // Configure your bindings here.

    Groovysh shell = new Groovysh(binding, new IO());
    shell.run("");
  }

}
