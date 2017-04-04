package water.util.fp

import java.lang

import water.udf.fp.{Function => UFunction}

/**
  * Created by vpatryshev on 4/3/17.
  */
object JavaInterop {
  implicit def ff1I[Y](f: Int => Y): UFunction[lang.Integer, Y] =
    new UFunction[lang.Integer, Y] {
      def apply(x: lang.Integer): Y = f(x)
    }
  
  implicit def ff1L[Y](f: Long => Y): UFunction[lang.Long, Y] =
    new UFunction[lang.Long, Y] {
      def apply(x: lang.Long): Y = f(x)
    }

  implicit def ff1LO[Y](f: Long => Option[Y]): UFunction[lang.Long, Y] =
    new UFunction[lang.Long, Y] {
      def apply(x: lang.Long): Y = f(x) getOrElse null.asInstanceOf[Y]
    }

  implicit def ff1LS(f: Long => String): UFunction[lang.Long, lang.String] =
    new UFunction[lang.Long, lang.String] {
      def apply(x: lang.Long): lang.String = f(x)
    }

  implicit def ff1LI(f: Long => Integer): UFunction[lang.Long, lang.Integer] =
    new UFunction[lang.Long, lang.Integer] {
      def apply(x: lang.Long): lang.Integer = f(x)
    }

}
