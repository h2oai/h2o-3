package water.fvec

import water._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

abstract class MapReduce[MapType: ClassTag, E <: MapReduce[MapType,E]] extends Iced { self: E =>
  // Selected numeric columns mapped into 'this'
  def map : (MapType) => Unit
  // Reduce the given operand into 'this'
  def reduce : (E) => Unit

  // Take all columns; they must be of type MapType
  def doAll( fr : DataFrame ) : E = {
    val jc = implicitly[ClassTag[MapType]].runtimeClass
    if( jc.isArray ) { // Array check: all Vecs are compatible with MapType's base type
      jc.getComponentType match {
        case q if q == classOf[Double] => 
          fr.vecs().zip(fr.names()).foreach( x => if( !x._1.isNumeric ) throw new IllegalArgumentException(x._2+"is not of type "+q))
          new MRTask_AD().doAll(fr)
          return this
        case _ => ???                   // Array of String or UUID, etc
      }
    } else {
      ???                               // Struct type matching not implemented
    }
  }

  // Version of MRTask specialized for Array[Double]
  private class MRTask_AD extends MRTask[MRTask_AD] {
    override def map( chks : Array[Chunk] ) : Unit = {
      val start = chks(0)._start
      val len = chks(0).len
      if( len == 0 ) return
      // Specialize user map to double[]
      val map2 = MapReduce.this.map.asInstanceOf[(Array[Double])=>Unit]
      val mr2 : E = MapReduce.this.clone    // Something to reduce into
      val map22 = mr2.map.asInstanceOf[(Array[Double])=>Unit]
      // Temp buffer to hold data without reallocating each row
      val row = new Array[Double](chks.length)
      // No reduce for row 0
      var i=0
      var col = 0 ; while( col < chks.length ) { row(col) = chks(col).at0(i) ; col+=1 }
      map2(row)                         // User map on row 0
      i+=1
      while( i<len ) {       // For all remaining rows, reduce between each map
        var col = 0 ; while( col < chks.length ) { row(col) = chks(col).at0(i) ; col+=1 }
        map22(row)           // Map into mr2
        MapReduce.this.reduce(mr2)      // Reduce mr2 into self
        i+=1
      }
    }

  }


}
