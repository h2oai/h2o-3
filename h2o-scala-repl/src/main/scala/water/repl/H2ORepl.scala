package water.repl

import water.H2O

import scala.tools.nsc.interpreter._
import water.fvec.NFSFileVec
import scala.tools.nsc._

/** Custom H2O REPL.
  *
  *  It reconfigures default REPL and
  *  @see http://www.michaelpollmeier.com/create-your-custom-scala-repl/
  *
  *  It expects to be run on regular H2O node and has access to
  *
  */
object H2ORepl {
  // Simple REPL launcher - expect to be executed from water.Boot 
  //  - hence it configures REPL classpath according the Boot
  def main(args: Array[String]): Unit = {
    // Launch Boot and and then H2O and ShalalaRepl
    H2O.main(args)
    launchRepl()
  }

  def launchRepl() = {
	  val repl = new H2OILoop
	  val settings = new Settings
	  //settings.Xnojline.value = true // Use SimpleLine library by default
	  //settings.Yreplsync.value = true
	  // FIXME we should provide CP via classloader via resource app.class.path 
	  settings.usejavacp.value = true
	  // setup the classloader of some H2O class
	  settings.embeddedDefaults[NFSFileVec]
	  //settings.embeddedDefaults[Boot] // serve as application loader
      // Uncomment to configure DEBUG
	  //debug(settings)
	  
	  repl.process(settings)
  }
  
  def debug(settings: Settings) = {
    //settings.Yrepldebug.value = true
    settings.Ylogcp.value = true
	  settings.verbose.value = true
  }
  
  /** H2O Repl Configuration */
  class H2OILoop extends ILoop {
    // override default shell prompt
    override def prompt = "h2o> "
    // implicitly import H2ODsl
    addThunk(intp.beQuietDuring {
      intp.addImports("water.api.dsl.H2ODsl._")
    })
    
    override def printWelcome() {
      echo("""
  _    _ ___   ____        _
 | |  | |__ \ / __ \      (_)
 | |__| |  ) | |  | | __ _ _
 |  __  | / /| |  | |/ _` | |
 | |  | |/ /_| |__| | (_| | |
 |_|  |_|____|\____(_)__,_|_|

Type `help` or `example` to begin...
          
Your are now in  """ + System.getProperty("user.dir") + """

Enjoy!
"""
)
    }
  }
}

// Companion class
class H2ORepl {
}
