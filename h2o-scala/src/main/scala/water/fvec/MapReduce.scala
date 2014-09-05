package water.fvec

import water._
import scala.reflect.ClassTag

abstract class MapReduce[MapType: ClassTag, E <: MapReduce[MapType,E]] extends Iced { self: E =>
  // Selected numeric columns mapped into 'this'
  def map : (MapType) => Unit
  // Reduce the given operand into 'this'
  def reduce : (E) => Unit
  // Skip the whole row if any value is missing
  def skipNA = true

  // Take all columns; they must be of type MapType
  def doAll( fr : DataFrame ) : E = {
    val jc = implicitly[ClassTag[MapType]].runtimeClass
    if( jc.isArray ) { // Array check: all Vecs are compatible with MapType's base type
      jc.getComponentType match {
        case q if q == classOf[Double] => 
          fr.vecs().zip(fr.names()).foreach( x => if( !x._1.isNumeric ) throw new IllegalArgumentException(x._2+"is not of type "+q))
          new MRTask_AD().doAll(fr).outer
        case _ => ???                   // Array of String or UUID, etc
      }
    } else {
      ???                               // Struct type matching not implemented
    }
  }

  // Version of MRTask specialized for Array[Double]
  private class MRTask_AD extends MRTask[MRTask_AD] {
    val outer = MapReduce.this
    override def map( chks : Array[Chunk] ) : Unit = {
      val start = chks(0)._start
      val len = chks(0).len
      if( len == 0 ) return
      // Specialize user map to double[]
      val map2 = outer.map.asInstanceOf[(Array[Double])=>Unit]
      val mr2 : E = outer.clone    // Something to reduce into
      val map22 = mr2.map.asInstanceOf[(Array[Double])=>Unit]
      // Temp buffer to hold data without reallocating each row
      val row = new Array[Double](chks.length)
      // No reduce for first map
      var needreduce = false
      // For all rows in Chunk
      var i = 0
      // Find first available row & map it
      while( i < len && !fill(row,chks,i) ) i += 1
      map2(row); i += 1
      // For all remaining rows, find available, map & reduce
      while( i < len ) {         // For all rows
        if( fill(row,chks,i) ) { // Fill all cols into 'row'
          map22(row)             // Map into mr2
          outer.reduce(mr2)      // Reduce mr2 into self
        }
        i += 1
      }
    }
    // Call user reduce
    override def reduce( mrt : MRTask_AD ) = outer.reduce(mrt.outer)
    // Fill reused temp array from Chunks.  Returns false is any value is NaN & skipNA true
    private def fill(row : Array[Double], chks : Array[Chunk], i : Int) : Boolean = {
      var col = 0
      while( col < chks.length ) {
        val d = chks(col).at0(i); row(col) = d
        if( skipNA && d.isNaN ) return false
        col += 1
      }
      true
    }
  }
}
