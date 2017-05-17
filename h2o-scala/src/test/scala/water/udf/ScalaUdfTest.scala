package water.udf

import java.io.File
import java.util.{Date, GregorianCalendar}
import java.{lang, util}

import org.junit.{Assert, Test}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import water.Test0
import water.TestUtil._
import water.udf.MoreColumns._
import water.util.fp.PureFunctions._
import water.util.fp.{Function, Functions, PureFunctions}
import water.util.FileUtils._

import scala.collection.JavaConverters._
import scala.io.Source
import scala.language.postfixOps

/**
  * Scala version of UdfTest
  */
class ScalaUdfTest extends Test0 with BeforeAndAfter with BeforeAndAfterAll {
  val A_LOT: Int = 1 << 20
  
  override def beforeAll: Unit = stall_till_cloudsize(2)

  val sinOpt =
    (i: Long) => Some(i).filter(k => k <= 10 || k >= 20).map(i => math.sin(i.toDouble))

  private def sines: DataColumn[lang.Double] = willDrop(ScalaDoubles.newColumnOpt(1L << 20, sinOpt))

  private def sinesShort: DataColumn[lang.Double] = willDrop(ScalaDoubles.newColumnOpt(1001590, sinOpt))

  private def five_x: DataColumn[lang.Double] = willDrop(ScalaDoubles.newColumn(A_LOT, (i: Long) => i * 5.0))

  val coscos = (i: Long) => math.cos(i * 0.0001) * Math.cos(i * 0.0000001)
  val cossin = (i: Long) => math.cos(i * 0.0001) * Math.sin(i * 0.0000001)
  val sinth  = (i: Long) => Math.sin(i * 0.0001)

  test("IsNA") {
    val c: Column[lang.Double] = sines
    assert(!c(10).isNaN)
    assert(c(11).isNaN)
    assert(c(19).isNaN)
    assert(!c(20).isNaN)
    assert(!c.isNA(10))
    assert(c.isNA(11))
    assert(c.isNA(19))
    assert(!c.isNA(20))
  }

  test("Doubles") {
    val c: Column[lang.Double] = five_x
    assert(0.0 == c(0), 0.000001)
    assert(210.0 == c(42), 0.000001)
    assert(100000.0 == c(20000), 0.000001)
  }

  test("Strings") {
    val c: Column[lang.String] = willDrop(Strings.newColumn(A_LOT,
      (i: Long) => if (i == 42) null else "<<" + i + ">>"))

    assert("<<0>>" == c(0))
    assert(null == c(42))
    assert("<<2016>>" == c(2016))
    val materialized: Column[lang.String] = Strings.materialize(c)
    for {i <- 0 until 100000} assert(c(i) == materialized(i))
  }

  test("Enums") {
    val c: Column[lang.Integer] = willDrop(Enums(Array[String]("Red", "White", "Blue")).newColumn(A_LOT, (i: Long) => (i % 3).toInt))

    assert(0 == c(0))
    assert(0 == c(42))
    assert(1 == c(100))
    assert(2 == c(20000))
    val materialized: Column[lang.Integer] = Enums(Array[String]("Red", "White", "Blue")).materialize(c)
    for {i <- 0 until 100000} {
      assert(c(i) == (i % 3))
      assert(c(i) == materialized(i))
    }
  }

  test("Dates") {
    val longToDate = (i: Long) => new Date(i * 3600000L * 24)
    val c: Column[Date] = willDrop(Dates.newColumn(A_LOT, longToDate))
    assert(new Date(0) == c(0))
    val expected: Date = new GregorianCalendar(1970, 8, 15, 17, 0, 0).getTime
    assert(expected == c(258))
    val materialized: Column[Date] = Dates.materialize(c)
    for (i <- 0 until 100000) assert(c(i) == materialized(i))
  }

  test("EnumFun") {
    val domain: Array[String] = Array("Red", "White", "Blue")
    val x: Column[lang.Integer] = willDrop(Enums(domain).newColumn(A_LOT,
      (i: Long) => (i % 3).toInt))

    val y: Column[lang.String] = new FunColumn[lang.Integer, String](new Function[Integer, String]() {
      def apply(i: Integer): String = domain(i)
    }, x)
    assert("Red" == y(0))
    assert("Red" == y(42))
    assert("White" == y(100))
    assert("Blue" == y(20000))
  }

  test("Squares") {
    val x: Column[lang.Double] = five_x
    val y: Column[lang.Double] = new FunColumn[lang.Double, lang.Double](SQUARE, x)
    assert(0.0 == y(0), 0.000001)
    assert(44100.0 == y(42), 0.000001)
    assert(10000000000.0 == y(20000), 0.000001)
  }

  test("IsFunNA") {
    val x: Column[lang.Double] = sines
    val y: Column[lang.Double] = new FunColumn[lang.Double, lang.Double](PureFunctions.SQUARE, x)
    assert(!y.isNA(10))
    assert(y.isNA(11))
    assert(y.isNA(19))
    assert(!y.isNA(20))
    assert(0.295958969093304 == y(10), 0.0001)
  }

  test("Fun2") {
    val x: Column[lang.Double] = five_x
    val y: Column[lang.Double] = sines
    val y2: Column[lang.Double] = willDrop(new FunColumn[lang.Double, lang.Double](SQUARE, y))
    val z1: Column[lang.Double] = willDrop(new Fun2Column[lang.Double, lang.Double, lang.Double](PLUS, x, y2))
    val z2: Column[lang.Double] = willDrop(new Fun2Column[lang.Double, lang.Double, lang.Double](X2_PLUS_Y2, x, y))
    assert(0.0 == z1(0), 0.000001)
    assert(210.84001174779368 == z1(42), 0.000001)
    assert(100000.3387062632 == z1(20000), 0.000001)
    assert(0.0 == z2(0), 0.000001)
    assert(44100.840011747794 == z2(42), 0.000001)
    assert(10000000000.3387062632 == z2(20000), 0.000001)
    val materialized: Column[lang.Double] = willDrop(ScalaDoubles.materialize(z2))
    for {i <- 0 until 100000} {
      {
        val expected = z2(i)
        assert(z2.isNA(i) == materialized.isNA(i))
        if (expected == null) assert(materialized.isNA(i), "At " + i + ":")
        val actual = materialized(i)
        if (!z2.isNA(i)) assert(expected == actual, 0.0001)
      }
    }
  }



  test("Fun2Compatibility") {
    val x: Column[lang.Double] = five_x
    val y: Column[lang.Double] = sinesShort
    val z: Column[lang.Double] = willDrop(ScalaDoubles.newColumn(A_LOT, (i: Long) => math.sin(i * 0.0001)))

    try {
      val z1: Column[lang.Double] = new Fun2Column[lang.Double, lang.Double, lang.Double](PureFunctions.PLUS, x, y)
      fail("Column incompatibility should be detected")
    }
    catch {
      case ae: AssertionError => // ok 
    }
    try {
      val r: Column[lang.Double] = new Fun3Column[lang.Double, lang.Double, lang.Double, lang.Double](X2_PLUS_Y2_PLUS_Z2, x, y, z)
      fail("Column incompatibility should be detected")
    }
    catch {
      case ae: AssertionError => // ok
    }
    try {
      val r: Column[lang.Double] = new Fun3Column[lang.Double, lang.Double, lang.Double, lang.Double](PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, z, y)
      fail("Column incompatibility should be detected")
    }
    catch {
      case ae: AssertionError => // ok
    }
  }

  def xsOnSphere: DataColumn[lang.Double] = willDrop(ScalaDoubles.newColumn(A_LOT, coscos))
  def ysOnSphere: DataColumn[lang.Double] = willDrop(ScalaDoubles.newColumn(A_LOT, cossin))
  def zsOnSphere: DataColumn[lang.Double] = willDrop(ScalaDoubles.newColumn(A_LOT, sinth))

  test("Fun3") {
    val x: Column[lang.Double] = xsOnSphere
    val y: Column[lang.Double] = ysOnSphere
    val z: Column[lang.Double] = zsOnSphere
    val r: Column[lang.Double] = new Fun3Column[lang.Double, lang.Double, lang.Double, lang.Double](PureFunctions.X2_PLUS_Y2_PLUS_Z2, x, y, z)

    for {i <- 0 until 100000} assert(math.abs(r(i * 10) - 1.0) < 0.0001)

    val materialized: Column[lang.Double] = ScalaDoubles.materialize(r)
    for {i <- 0 until 100000} assert(r(i) == materialized(i), 0.0001)
  }

  test("FoldingColumn") {
    val x: Column[lang.Double] = xsOnSphere
    val y: Column[lang.Double] = ysOnSphere
    val z: Column[lang.Double] = zsOnSphere
    val r: Column[lang.Double] = new FoldingColumn[lang.Double, lang.Double](PureFunctions.SUM_OF_SQUARES, x, y, z)

    for {i <- 0 until 100000} assert(math.abs(r(i * 10) - 1.0) < 0.0001)

    val x1: Column[lang.Double] = new FoldingColumn[lang.Double, lang.Double](PureFunctions.SUM_OF_SQUARES, x)

    for {i <- 0 until 100000} {
      val xi: lang.Double = x(i)
      assert(math.abs(x1(i) - xi * xi) < 0.0002, s"@$i: $xi")
    }

// should not work for now    val x0: Column[lang.Double] = new FoldingColumn[lang.Double, lang.Double](Functions.SUM_OF_SQUARES)
//    for {i <- 0 until 100000} assert(x0(i) == 0.0, 0.0001)

    val materialized: Column[lang.Double] = ScalaDoubles.materialize(r)

    for {i <- 0 until 100000} assert(r(i) == materialized(i), 0.0001)
  }


  test("FoldingColumnCompatibility") {
    val x: Column[lang.Double] = xsOnSphere
    val y: Column[lang.Double] = ysOnSphere
    val z: Column[lang.Double] = sinesShort
    try {
      val r: Column[lang.Double] = new FoldingColumn[lang.Double, lang.Double](PureFunctions.SUM_OF_SQUARES, x, y, z)
      fail("Should have failed on incompatibility")
    }
    catch {
      case ae: AssertionError => 
    }
  }
  
  test("FoldingColumn, Scala") {
    val x: Column[lang.Double] = xsOnSphere
    val y: Column[lang.Double] = ysOnSphere
    val z: Column[lang.Double] = zsOnSphere
    val r: Column[lang.Double] = foldingColumn[lang.Double](
      Magma[lang.Double](0.0, (sum:lang.Double) => (v:lang.Double) => sum+v*v), 
      x, y, z)

    for {i <- 0 until 100000} {
      val idx = i*10
      val act = r(i * 10)
      assert(math.abs(act - 1.0) < 0.001, s"failed at $idx: $act")
    }

  }

  test("UnfoldingColumn") {
    val file: File = getFile("smalldata/chicago/chicagoAllWeather.csv")
    val ss = Source.fromFile(file).getLines().toList
    
    val source: Column[lang.String] = willDrop(Strings.newColumn(ss))
    
    val split: Column[java.util.List[String]] = new UnfoldingColumn[String, String](Functions.splitBy(","), source, 10)

    assert(ss.size == split.size())

    for {i <- ss.indices} {
      val src = ss(i)
      val actual: String = split(i).asScala.filter(null !=).mkString(" ")
      assert(src.replaceAll(",", " ").trim == actual)
    }
  }

  test("UnfoldingColumn, Scala") {
    val file: File = getFile("smalldata/chicago/chicagoAllWeather.csv")
    val ss = Source.fromFile(file).getLines().toList

    val source: Column[lang.String] = willDrop(Strings.newColumn(ss))

    val split: Column[java.util.List[String]] = unfoldingColumn[String, String](_.split(","), source, 10)

    assert(ss.size == split.size())

    for {i <- ss.indices} {
      val src = ss(i)
      val actual: String = split(i).asScala.filter(null !=).mkString(" ")
      assert(src.replaceAll(",", " ").trim == actual)
    }
  }

  test("UnfoldingFrame") {
    val file: File = getFile("smalldata/chicago/chicagoAllWeather.csv")
    val ss:List[String] = Source.fromFile(file).getLines().toList

    val source: Column[String] = willDrop(Strings.newColumn(ss))

    val split: Column[java.util.List[String]] = new UnfoldingColumn[lang.String, String](Functions.splitBy(","), source, 10)

    assert(ss.size == split.size())
    
    val frame: UnfoldingFrame[String] = new UnfoldingFrame[String](Strings, split.size, split, 11)
    
    val columns: util.List[DataColumn[lang.String]] = frame.materialize

    {
      for {i <- ss.indices} {
        val fromColumns = (0 until 10) map (columns.get(_).get(i))
        val actual = fromColumns filter (null !=) mkString " "
        assert(ss(i).replaceAll(",", " ").trim == actual)
      }
    }
    assert(columns.get(5).isCompatibleWith(source), "Need until align the result")
  }

  @Test def testSomethingElse(): Unit = {
    Assert.assertTrue(true)
  }
}
