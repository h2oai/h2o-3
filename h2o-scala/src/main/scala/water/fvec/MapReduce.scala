package water.fvec

import water._
import scala.reflect.ClassTag

abstract class MapReduce[MapType: ClassTag, E <: MapReduce[MapType,E]] extends Iced { self: E =>
  type maptype=MapType
  type self <: this.type
  // Selected numeric columns mapped into 'this'
  def map(row : MapType) : Unit
  // Reduce the given operand into 'this'
  def reduce(that : self) : Unit
  // Skip the whole row if any value is missing
  def skipNA = true

  // Take all columns; they must be of type MapType
  def doAll( fr : DataFrame ) : this.type = {
    val jc = implicitly[ClassTag[MapType]].runtimeClass
    if( jc.isArray ) { // Array check: all Vecs are compatible with MapType's base type
      jc.getComponentType match {
        case q if q == classOf[Double] => 
          fr.vecs().zip(fr.names()).foreach( x => if( !x._1.isNumeric ) throw new IllegalArgumentException(x._2+"is not of type "+q))
          new MRTask_AD[E](this).doAll(fr).res.asInstanceOf[this.type]
        case _ => ???                   // Array of String or UUID, etc
      }
    } else {
      ???                               // Struct type matching not implemented
    }
  }
}
