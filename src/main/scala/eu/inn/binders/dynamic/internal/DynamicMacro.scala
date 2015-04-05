package eu.inn.binders.dynamic.internal


import eu.inn.binders.dynamic.DynamicObject

import scala.language.experimental.macros
import scala.language.reflectiveCalls
import scala.reflect.macros.Context

object DynamicMacro {
  def fromDynamic[O: c.WeakTypeTag]
  (c: Context): c.Expr[O] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with DynamicMacroImpl
    c.Expr[O](bundle.fromDynamic[O])
  }

  def toDynamic[O: c.WeakTypeTag]
  (c: Context): c.Expr[DynamicObject] = {
    val c0: c.type = c
    val bundle = new {
      val c: c0.type = c0
    } with DynamicMacroImpl
    c.Expr[DynamicObject](bundle.toDynamic[O])
  }
}