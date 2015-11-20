package water.fvec

import java.io.File
import java.net.URI

import water._
import water.parser.ParseSetup._
import water.parser.{ParseSetup, ParserType}
import water.util.FrameUtils

/**
 * Wrapper around Java H2O Frame to provide more Scala-like API.
 *
 * @param frameKey  reference of new frame
 * @param names  column names for new frame
 * @param vecs  vectors composing new frame
 */
class H2OFrame private (frameKey: Key[Frame], names: Array[String], vecs: Array[Vec])
  extends Frame(frameKey, names, vecs) with FrameOps {

  /** Create a new H2OFrame based on existing Java Frame.
    *
    * Simple field copy, so the Frames share
    * underlying arrays.  Recommended that the input Java Frame be dead after
    * this call.
    *
    * @param fr  Java frame
    * @return new H2O frame with parsed data
    */
  def this(fr : Frame) = this(if (fr._key!=null) fr._key else Key.make("dframe"+Key.rand()).asInstanceOf[Key[Frame]], fr._names, fr.vecs())

  /**
   * Create a new H2OFrame based on existing Java Frame referenced by its key.
   * @param key  reference to Java Frame
   * @return new H2O frame
   */
  def this(key : Key[Frame]) = this (DKV.get(key).get.asInstanceOf[Frame])

  /**
   * Create a new H2OFrame based on existing Java Frame referenced by its key.
   *
   * @param key  string representation of a reference to Java Frame
   * @return new H2O frame
   */
  def this(key : String) = this (Key.make(key).asInstanceOf[Key[Frame]])

  /**
   * Create a new frame by parsing given files.
   *
   * @param parseSetup  setup for parser
   * @param uris  URIs of files to parse
   * @return new H2O frame containing parsed data
   */
  def this(parseSetup: ParseSetup, uris: URI*) = this(water.util.FrameUtils.parseFrame(
      Key.make(ParseSetup.createHexName(H2OFrame.baseName(uris(0)))),
      parseSetup,
      uris: _*))

  /**
   * Create a new frame by parsing given files.
   *
   * @param uris  URIs of files to parse
   * @return new H2O frame containing parsed data
   */
  def this(uris: URI*) = this(water.util.FrameUtils.parseFrame(
                                Key.make(ParseSetup.createHexName(H2OFrame.baseName(uris(0)))),
                                uris : _*))

  /**
   * Create a new frame by parsing given file.
   *
   * @param file  cluster-local file to parse (has to be available on each node)
   * @return  a new frame containing parsed file data
   */
  def this(file : File) = this(file.toURI)

  /** Create a new frame by parsing given file.
    *
    * @param parseSetup  setup for parser
    * @param file  cluster-local file to parse (has to be available on each node)
    * @return  a new frame containing parsed file data
    */
  def this(parseSetup: ParseSetup, file : File) = this(parseSetup, file.toURI)

  // No-args public constructor for (de)serialization
  def this() = this(null,null,new Array[Vec](0))

  /* Constructor */
  // Force into K/V store
  assert(frameKey != null)
  DKV.put(frameKey, new Value(frameKey, this))
  /* ---- */

  /** Expose internal key via a method.
    *
    * The motivation is to simplify manipulation with frame from Py4J (pySparkling)
    */
  def key: Key[Frame] = _key

  override def toString(): String = super[Frame].toString()

  override def hashCode(): Int = super[Frame].hashCode()
}

/** Companion object providing factory methods to create frame
  * from different sources.
  */
object H2OFrame {
  def apply(key : Key[Frame]) = new H2OFrame(key)
  def apply(f : Frame) = new H2OFrame(f)
  def apply(s : String) = new H2OFrame(s)
  def apply(file : File) = new H2OFrame(file)
  def apply(uri : URI) = new H2OFrame(uri)
  def baseName(uri: URI) = {
    val s = uri.toString
    s.substring(s.lastIndexOf('/')+1)
  }

  /** Return default parser setup */
  def defaultParserSetup(singleQuotes: Boolean = true) =
    new ParseSetup(ParserType.GUESS, GUESS_SEP, singleQuotes, GUESS_HEADER, GUESS_COL_CNT,
                   null, null, null, null, null)

  /** Return guessed parser setup for given file.
    *
    * @param file  file to parse
    * @return  guessed parser setup
    */
  def parserSetup(file: File): ParseSetup = parserSetup(file.toURI)

  /**
   * Return guessed parser setup for given file.
   *
   * @param userSetup  user-specified hint for parser setup
   * @param file  file to parse
   * @return  guessed parser setup
   */
  def parserSetup(userSetup: ParseSetup, file: File): ParseSetup = parserSetup(file.toURI)

  /**
   * Return guessed parser setup for given files.
   *
   * @param uris  URIs of files to parse
   * @return  guessed parser setup
   */
  def parserSetup(uris: URI*): ParseSetup = parserSetup(defaultParserSetup(), uris:_*)

  /**
   * Return guessed parser setup for given files.
   *
   * @param userSetup  user-specified hint for parser setup
   * @param uris  URIs of files to parse
   * @return  guessed parser setup
   */
  def parserSetup(userSetup: ParseSetup, uris: URI*) = FrameUtils.guessParserSetup(defaultParserSetup(), uris:_*)

}
