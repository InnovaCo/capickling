package eu.inn.binders.dynamic

import java.util.Date
import eu.inn.binders.dynamic.internal.DynamicMacro
import scala.language.experimental.macros
import scala.language.dynamics

trait Value extends Dynamic {
  def accept[T](visitor: ValueVisitor[T]): T

  def asString: String = {
    accept[String](new ValueVisitor[String] {
      override def visitBool(d: Bool) = d.v.toString
      override def visitText(d: Text) = d.v
      override def visitObj(d: Obj) = d.v.map(kv => kv._1 + "->" + kv._2).mkString(",")
      override def visitNumber(d: Number) = d.v.toString()
      override def visitLst(d: Lst) = d.v.mkString(",")
    })
  }

  def asBoolean: Boolean = {
    accept[Boolean](new ValueVisitor[Boolean] {
      override def visitBool(d: Bool) = d.v
      override def visitText(d: Text) = d.v.toLowerCase match {
        case "true" => true
        case "y" => true
        case "yes" => true
        case "on" => true
        case "1" => true
        case "false" => false
        case "n" => false
        case "no" => false
        case "off" => false
        case "0" => false
        case _ => castUnavailable(s"String(${d.v}) to Boolean")
      }
      override def visitObj(d: Obj) = castUnavailable("Obj to Boolean")
      override def visitNumber(d: Number) = d.v != BigDecimal(0)
      override def visitLst(d: Lst) = castUnavailable("Lst to Boolean")
    })
  }

  def asBigDecimal: BigDecimal = {
    accept[BigDecimal](new ValueVisitor[BigDecimal] {
      override def visitBool(d: Bool) = if(d.v) 1 else 0
      override def visitText(d: Text) = BigDecimal(d.v)
      override def visitObj(d: Obj) = castUnavailable("Obj to BigDecimal")
      override def visitNumber(d: Number) = d.v
      override def visitLst(d: Lst) = castUnavailable("Lst to BigDecimal")
    })
  }

  def asInt: Int = asBigDecimal.toIntExact
  def asLong: Long = asBigDecimal.toLongExact
  def asDouble: Double = asBigDecimal.toDouble
  def asFloat: Float = asBigDecimal.toFloat
  def asDate: Date = new Date(asLong)

  def asMap: Map[String, Value] = {
    accept[Map[String, Value]](new ValueVisitor[Map[String, Value]] {
      override def visitBool(d: Bool) = castUnavailable("Bool to Map[]")
      override def visitText(d: Text) = castUnavailable("Text to Map[]")
      override def visitObj(d: Obj) = d.v
      override def visitNumber(d: Number) = castUnavailable("Number to Map[]")
      override def visitLst(d: Lst) = castUnavailable("Lst to Map[]")
    })
  }

  def asSeq: Seq[Value] = {
    accept[Seq[Value]](new ValueVisitor[Seq[Value]] {
      override def visitBool(d: Bool) = castUnavailable("Bool to Seq[]")
      override def visitText(d: Text) = castUnavailable("Text to Seq[]")
      override def visitObj(d: Obj) = castUnavailable("Obj to Seq[]")
      override def visitNumber(d: Number) = castUnavailable("Number to Seq[]")
      override def visitLst(d: Lst) = d.v
    })
  }

  def castUnavailable(s: String) = throw new ClassCastException(s)

  def selectDynamic[T](name: String) = macro DynamicMacro.selectDynamic[T]
}

trait ValueVisitor[T] {
  def visitNumber(d: Number): T
  def visitText(d: Text): T
  def visitObj(d: Obj): T
  def visitLst(d: Lst): T
  def visitBool(d: Bool): T
//  def visitNull()
}

/*case object Null extends DynamicValue {
  override def accept(visitor: DynamicVisitor): Unit = visitor.visitNull()
}*/

case class Number(v: BigDecimal) extends Value {
  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitNumber(this)
}

case class Text(v: String) extends Value {
  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitText(this)
}

case class Obj(v: Map[String, Value] = Map()) extends Value{
  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitObj(this)
}

case class Lst(v: Seq[Value] = Seq()) extends Value{
  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitLst(this)
}

case class Bool(v: Boolean) extends Value{
  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitBool(this)
}