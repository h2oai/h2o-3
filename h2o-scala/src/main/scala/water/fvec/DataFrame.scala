package water.fvec

import water._
import water.fvec._
import java.io.File

class DataFrame private ( key : Key, names : Array[String], vecs : Array[Vec] ) 
  extends Frame(key,names,vecs) 
  with Map[Long,Array[Any]] {

  // Scala DataFrame from a Frame.  Simple field copy, so the Frames share
  // underlying arrays.  Recommended that the input Java Frame be dead after
  // this call.
  def this(fr : Frame) = this(fr._key,fr._names,fr.vecs())

  // Scala DataFrame by reading a CSV file
  def this(file : File) = this(water.util.FrameUtils.parseFrame(Key.make(water.parser.ParseSetup.hex(file.getName)),file))

  // Operators for the Map and MapLike
  override def get( row : Long ) : Option[Array[Any]] = ???
  override def iterator: Iterator[(Long, Array[Any])] = ???
  override def + [B1 >: Array[Any]](kv: (Long, B1)): Map[Long,Array[Any]] = ???
  override def -(key: Long): Map[Long,Array[Any]] = ???

  override def empty : Map[Long,Array[Any]] = ???
  override def foreach[U](f: ((Long, Array[Any])) => U): Unit = ???
  override def size: Int = ???
  

}

//val fr = new DataFrame("airlines.csv")
//fr.map((Year,IsDelayed) => (...if( Year.isNA ) Year = 1977...))
