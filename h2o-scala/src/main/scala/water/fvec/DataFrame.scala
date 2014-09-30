package water.fvec

import java.io.File

import water._

class DataFrame private ( key : Key, names : Array[String], vecs : Array[Vec] )
  extends Frame(key,names,vecs) 
  with Map[Long,Array[Option[Any]]] {
  type T = Array[Option[Any]]

  // Scala DataFrame from a Frame.  Simple field copy, so the Frames share
  // underlying arrays.  Recommended that the input Java Frame be dead after
  // this call.
  def this(fr : Frame) = this( if (fr._key!=null) fr._key else Key.make(), fr._names, fr.vecs())

  // Create DataFrame from existing Frame
  def this(k : Key) = this ( DKV.get(k).get[Frame] )

  def this(s : String) = this ( Key.make(s) )

  // Scala DataFrame by reading a CSV file
  def this(file : File) = this(water.util.FrameUtils.parseFrame(Key.make(water.parser.ParseSetup.hex(file.getName)),file))

  // No-args public constructor for (de)serialization
  def this() = this(null,null,new Array[Vec](0))

  // Force into K/V store
  assert(key!=null)
  DKV.put(key,new Value(key,this))

  def apply( cols: Array[String] ) : DataFrame = new DataFrame(subframe(cols))

  def apply( cols: Symbol* ) : DataFrame = apply(cols.map(_.name).toArray)

  // Operators for the Map and MapLike
  override def iterator: Iterator[(Long, T)] = ???
  override def + [B1 >: T](kv: (Long, B1)): Map[Long,T] = ???
  override def - (key: Long): Map[Long,T] = ???

  override def empty : Map[Long,T] = ???
  override def size: Int = numRows.asInstanceOf[Int]

  // If the row is outside the range, return None.
  // Else return an Array of Option; None for any NA values.
  // Else return Option[Double] or Option[Long] or Option[String].
  override def get( row : Long ) : Option[T] = {
    if( 0 <= row && row < numRows ) 
      Some(for( vec <- vecs ) yield if( vec.isNA(row) ) None else Some(vec.at(row)))
    else None
  }

  // Map a function over each Row.  Reuse the Row array, but still allocates Options & Doubles
  override def foreach[U](f: ((Long, T)) => U): Unit = {
    new MRTask {
      override def map( chks : Array[Chunk] ) = {
        val start = chks(0)._start
        val row = new T(chks.length)
        val len = chks(0).len
        (0 until len).foreach{ i =>
          (0 until chks.length).foreach{ col => row(col) = if( chks(col).isNA0(i) ) None else Some(chks(col).at0(i)) }
          f(start+i,row)
        }
      }
    }.doAll(this)
  }

  override def toString(): String = super[Frame].toString()

  override def hashCode(): Int = super[Frame].hashCode()
}
