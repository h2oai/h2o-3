package water.udf

import java.lang
import java.util.Date
import water.udf.specialized.{Strings, Enums, Dates, Doubles}

import collection.JavaConverters._

import water.udf.DataColumns._

/**
  * Scala adapters for h2o3 udf (which is currently in Java)
  */
object MoreColumns extends DataColumns {

  trait ScalaFactory[JavaType,ScalaType] { self: BaseFactory[JavaType] =>

    import collection.JavaConverters._

    def conv(x:ScalaType): JavaType
    
    def newColumn[U](xs: Iterable[ScalaType]): DataColumn[JavaType] = {
      self.newColumn(xs.size, Functions.onList(xs.toList.map(conv).asJava))
    }

    def newColumn[U](xs: Iterator[ScalaType]): DataColumn[JavaType] = {
      self.newColumn(xs.size, Functions.onList(xs.toList.map(conv).asJava))
    }
  }
  
  val DoubleNan: Double = Double.NaN

  private[MoreColumns] implicit def ff1L[Y](f: Long => Y): water.udf.Function[lang.Long, Y] =
    new water.udf.Function[lang.Long, Y] {
      def apply(x: lang.Long): Y = f(x)
    }

  private[MoreColumns] implicit def ff1LO[Y](f: Long => Option[Y]): water.udf.Function[lang.Long, Y] =
    new water.udf.Function[lang.Long, Y] {
      def apply(x: lang.Long): Y = f(x) getOrElse null.asInstanceOf[Y]
    }

  private[MoreColumns] implicit def ff1LS(f: Long => String): water.udf.Function[lang.Long, lang.String] =
    new water.udf.Function[lang.Long, lang.String] {
      def apply(x: lang.Long): lang.String = f(x)
    }

  private[MoreColumns] implicit def ff1LI(f: Long => Integer): water.udf.Function[lang.Long, lang.Integer] =
    new water.udf.Function[lang.Long, lang.Integer] {
      def apply(x: lang.Long): lang.Integer = f(x)
    }

  private[MoreColumns] implicit def ff1LD(f: Long => Double): water.udf.Function[lang.Long, lang.Double] =
    new water.udf.Function[lang.Long, lang.Double] {
      def apply(x: lang.Long): lang.Double = f(x)
    }

  private[MoreColumns] implicit def ff1LDO(f: Long => Option[Double]): water.udf.Function[lang.Long, lang.Double] =
    new water.udf.Function[lang.Long, lang.Double] {
      def apply(x: lang.Long): lang.Double = f(x).getOrElse(DoubleNan).asInstanceOf[Double]
    }

  private[MoreColumns] implicit def ff1[X](f: Iterable[X] => X): Foldable[X, X] = {
    new Foldable[X, X] {
      override def initial(): X = f(Nil)

      override def apply(y: X, x: X): X = f(y::x::Nil)
    }
  }
  
  val Doubles = new Doubles with ScalaFactory[java.lang.Double, Double] {
    def newColumn(size: Long, f: Long => Double) = super.newColumn(size, ff1LD(f))
    def newColumnOpt(size: Long, f: Long => Option[Double]) = super.newColumn(size, ff1LDO(f))
    override def conv(x: Double): lang.Double = x
  }

  val Dates = new Dates with ScalaFactory[Date, Date] {
    def newColumn(size: Long, f: Long => Date) = super.newColumn(size, ff1L(f))
    def newColumnOpt(size: Long, f: Long => Option[Date]) = super.newColumn(size, ff1LO(f))
    override def conv(x: Date): Date = x
  }

  val Strings = new Strings with ScalaFactory[String, String] {
    def newColumn(size: Long, f: Long => String) = super.newColumn(size, ff1LS(f))
    def newColumnOpt(size: Long, f: Long => Option[String]) = super.newColumn(size, ff1LO(f))
    override def conv(x: String): String = x
  }

  def Enums(domain: Iterable[String]) = new Enums(domain.toArray) with ScalaFactory[java.lang.Integer, Integer] {
    def newColumn(size: Long, f: Long => Integer) = super.newColumn(size, ff1LI(f))
    def newColumnOpt(size: Long, f: Long => Option[Integer]) = super.newColumn(size, ff1LO(f))
    override def conv(x: Integer): java.lang.Integer = x
  }
  
  def foldingColumn[X](f: Iterable[X] => X, components: Column[X]*): Column[X] = {
    new FoldingColumn[X, X] (ff1(f), components:_*)
  }

  private[MoreColumns] def fu1[X, Y](f: X => Iterable[Y]): Unfoldable[X, Y] = {
    new Unfoldable[X, Y] {
      def apply(x: X): java.util.List[Y] = {
        val ys = f(x)
        ys.toList.asJava
      }
      
    }
  }

  def unfoldingColumn[X, Y](f: X => Iterable[Y], source: Column[X], width: Int) = {
    new UnfoldingColumn[X, Y](fu1[X, Y](f), source, width)
  }

//  def unfoldingFrame[X, Y](f: X => Iterable[Y], source: Column[X], width: Int) = {
//    new UnfoldingFrame[X, Y](fu1[X, Y](f), source, width)
//  }
   
}
