package water.fvec

import java.io.File
import java.net.URI

import water._
import water.parser.ParseSetup

/**
 * Wrapper around H2O Frame to provide more Scala-like API.
 */
class H2OFrame private (key: Key[Frame], names: Array[String], vecs: Array[Vec])
  extends Frame(key, names, vecs)
  with FrameOps {
  // Row type
  type T = Array[Option[Any]]

  // Scala DataFrame from a Frame.  Simple field copy, so the Frames share
  // underlying arrays.  Recommended that the input Java Frame be dead after
  // this call.
  def this(fr : Frame) = this(if (fr._key!=null) fr._key else Key.make("dframe"+Key.rand()).asInstanceOf[Key[Frame]], fr._names, fr.vecs())

  // Create DataFrame from existing Frame
  def this(k : Key[Frame]) = this (DKV.get(k).get.asInstanceOf[Frame])

  def this(s : String) = this (Key.make(s).asInstanceOf[Key[Frame]])

  // Uniform call to load any resource referenced by URI
  def this(uris: URI*) = this(water.util.FrameUtils.parseFrame(
                                Key.make(ParseSetup.createHexName(H2OFrame.baseName(uris(0)))),
                                uris : _*))

  // Scala DataFrame by reading a CSV file
  def this(file : File) = this(file.toURI)

  // No-args public constructor for (de)serialization
  def this() = this(null,null,new Array[Vec](0))

  // Force into K/V store
  assert(key!=null)
  DKV.put(key, new Value(key, this))

  def apply(cols: Array[String]): H2OFrame = new H2OFrame(subframe(cols))

  def apply(cols: Symbol*): H2OFrame = apply(cols.map(_.name).toArray)

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
}
