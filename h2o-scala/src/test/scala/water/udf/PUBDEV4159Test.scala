package water.udf

import java.io.IOException
import java.lang
import java.util.Date

import org.joda.time.format.DateTimeFormat
import org.junit.BeforeClass
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import water.TestUtil._
import water.fvec.{Frame, Vec}
import water.parser.{BufferedString, ParseTime}
import water.udf.MoreColumns.{Dates, Doubles, Strings}
import water.udf.fp.{Function, Function2}
import water.util.TwoDimTable
import water.{Test0, TestUtil}

import scala.collection.JavaConversions._
import scala.language.postfixOps
import scala.util.Try

object ThingsWeNeed {

  private val dateFormats = List("YYYY-MM-dd HH:mm:ss.SSS", "YYYYMMdd", "YYYYMM")

  private val formats = dateFormats map DateTimeFormat.forPattern

  private def dateOf(s: String): Option[Date] = {
    val maybeSomething: Option[Long] =
      Some(ParseTime.attemptTimeParse(new BufferedString(s)))

    val optDate = maybeSomething filter (Long.MinValue !=) map (new Date(_))

    optDate orElse {
      val candidates = for {
        fmt <- formats
        dt <- Try {fmt parseDateTime s} toOption
      } yield dt.toDate
      
      candidates.headOption
    }
  }

  val AS_DATE: Function[String, Date] = new Function[String, Date]() {
    def apply(s: String): Date = dateOf(s) orNull
  }

  def dy(from: Date, to: Date): Double = (to.getTime - from.getTime) / 1000.0 / 3600 / 24 / 365.25

  val YEARS_BETWEEN: Function2[Date, Date, lang.Double] = new Function2[Date, Date, lang.Double]() {
    def apply(from: Date, to: Date): lang.Double = dy(from, to)
  }

  val MONTHS_BETWEEN: Function2[Date, Date, lang.Double] = new Function2[Date, Date, lang.Double]() {
    def apply(from: Date, to: Date): lang.Double = dy(from, to) * 12
  }
}

/**
  * test for PUBDEV-4159, create, transform, manipulate dates
  */
class PUBDEV4159Test extends Test0 with BeforeAndAfter with BeforeAndAfterAll {
  import ThingsWeNeed._
  
  val A_LOT: Int = 1 << 20

  override def beforeAll: Unit = stall_till_cloudsize(2)

  test("produce dates") {
    @throws(classOf[IOException])
    val inputDates = List("my first date", "2017-05-28 23:15:42.123", "2017-03-17 10:49:00.567", "192005", "200101", "200202", "20151010", "20151011", "20151122", "my last date")

    def source: DataColumn[String] = willDrop(Strings.newColumn(inputDates))

    val dates: Column[Date] = new FunColumn[String, Date](AS_DATE, source)
    assert(dates.isNA(0))
    assert(dates.isNA(9))
    for {i <- 1 to 8}
      assert(!dates.isNA(i), "@" + i)

    assert(new Date(1489747740567L + 7 * 3600 * 1000L) == dates.apply(2))

    val materialized = Dates.materialize(dates)

    for {i <- inputDates.indices}
      assert(dates.apply(i) == materialized.apply(i))

    assert(Vec.T_TIME == materialized.vec.get_type)
  }

  test("delta years, delta months") {
    val x2: Column[String] = Strings.newColumn(1200, (i: Long) =>{
        val y: Int = i.intValue / 12 + 1917
        val m: Int = i.intValue % 12 + 1
        f"$y%4d-$m%02d-15 04:20:00"
    })

    val d2: Column[Date] = new FunColumn[String, Date](AS_DATE, x2)
    
    for {i <- 0 until d2.size().toInt} assert(!d2.isNA(i), s"failed @$i: ${x2(i)}")

    val d1: Column[Date] = Dates.newColumn(1200, (i: Long) => {
        new Date((i - 365 * 53) * 1000L * 3600 * 24)
    })
    
    val column1: Fun2Column[Date, Date, lang.Double] = new Fun2Column[Date, Date, lang.Double](YEARS_BETWEEN, d1, d2)
    
    for { (y: lang.Double) <- column1 } assert(!y.isNaN) 

    val column2: Fun2Column[Date, Date, lang.Double] = new Fun2Column[Date, Date, lang.Double](MONTHS_BETWEEN, d1, d2)

    for { (m: lang.Double) <- column2 } assert(!m.isNaN)

    val years: Column[lang.Double] = Doubles.materialize(column1)

    val y0 = years(0)
    assert(!y0.isNaN)
    
    for { (y: lang.Double) <- years } assert(!y.isNaN)

    val months: Column[lang.Double] = Doubles.materialize(column2)

    for { (m: lang.Double) <- months } assert(!m.isNaN)

    val yv: Vec = years.vec
    assert(!yv.at(0).isNaN)
    assertEquals(0.004, yv.at(0), 0.0005)
    assertEquals(88.658, yv.at(1100), 0.0005)

    val mv: Vec = months.vec
    assertEquals(0.05, mv.at(0), 0.0005)
    assertEquals(1063.9, mv.at(1100), 0.001)
    val f: Frame = new Frame(d1.vec, d2.vec, years.vec, months.vec)

    val tdt: TwoDimTable = f.toTwoDimTable(1198, 1200, false)
    val expected: String = "Frame null (1200 rows and 4 cols):\n" + "                   C1                   C2                 C3                  C4\n" + "  1920-04-25 16:00:00  2016-11-15 04:20:00  96.55719066088676  1158.6862879306411\n" + "  1920-04-26 16:00:00  2016-12-15 04:20:00  96.63658833371359  1159.6390600045631\n"
    
    assert(expected == tdt.toString)
  }
}

object PUBDEV4159Test extends TestUtil {
  @BeforeClass def setup() = TestUtil.stall_till_cloudsize(2)
}
