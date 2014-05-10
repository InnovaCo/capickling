import eu.inn.binders.core.{Statement, Rows, Row, Query}
import eu.inn.binders.naming.Converter
import java.util.Date

trait TestRow[C <: Converter] extends Row {
  type nameConverterType = C

  def getInt(name: String): Int

  def getIntNullable(name: String): Option[Int]

  def getDate(name: String): Date

  def getDateNullable(name: String): Option[Date]

  /*def getList[T: ClassTag](name: String) : List[T]
  def getSet[T: ClassTag](name: String) : Set[T]
  def getMap[K: ClassTag, V: ClassTag](name: String) : Map[K,V]*/

  def getList[T](name: String): List[T]

  def getSet[T](name: String): Set[T]

  def getMap[K, V](name: String): Map[K, V]

  def getGenericMap[K, V](name: String): Option[Map[K, V]]
}

trait TestRows[C <: Converter] extends Rows[TestRow[C]] {

}

trait TestStatement[C <: Converter] extends Statement[TestRows[C]] {
  type nameConverterType = C

  def setInt(index: Int, value: Int)

  def setIntNullable(index: Int, value: Option[Int])

  def setInt(name: String, value: Int)

  def setIntNullable(name: String, value: Option[Int])

  def setDate(index: Int, value: Date)

  def setDateNullable(index: Int, value: Option[Date])

  def setDate(name: String, value: Date)

  def setDateNullable(name: String, value: Option[Date])

  def setList[T](index: Int, value: List[T])

  def setList[T](name: String, value: List[T])

  def setSet[T](index: Int, value: Set[T])

  def setSet[T](name: String, value: Set[T])

  def setMap[K, V](name: String, value: Map[K, V])

  def setGenericMap[K, V](name: String, value: Option[Map[K, V]])

  override def execute: TestRows[C] = {
    new Object with TestRows[C] {
      override def iterator = Iterator.empty
    }
  }
}

class TestQuery[C <: Converter](statement: TestStatement[C]) extends Query[TestStatement[C]] {
  override def createStatement: TestStatement[C] = statement
}

case class TestInt(intValue1: Int, nullableValue: Option[Int], intValue2: Int)

case class TestDate(dateValue1: Date, nullableValue: Option[Date], dateValue2: Date)

case class TestCollections(intLst: List[Int], strSet: Set[String], longStrMap: Map[Long, String])

case class TestGenericCollections(genericMap: Option[Map[String, Set[Int]]], genericMapNone: Option[Map[String, Set[Int]]])
