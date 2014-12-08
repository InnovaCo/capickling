package eu.inn

import eu.inn.binders.core.{Serializer, Deserializer}

package object binders {

  import eu.inn.internal.BinderProxy
  import language.experimental.macros

  implicit class SerializerOps[S <: Serializer[_]](val serializer: S) {
    def bindParameter[O](index: Int, obj: O) = macro BinderProxy.bindParameter[S, O]

    def bind[O](obj: O) = macro BinderProxy.bind[S, O]

    def bindPartial[O](obj: O) = macro BinderProxy.bindPartial[S, O]

    def bindArgs(t: Any*) = macro BinderProxy.bindArgs
  }

  implicit class DeserializerOps[R <: Deserializer[_]](val deserializer: R) {
    def unbind[O]: O = macro BinderProxy.unbind[R, O]

    def unbindPartial[O](obj: O): O = macro BinderProxy.unbindPartial[R, O]

    def unbindOne[O]: Option[O] = macro BinderProxy.unbindOne[R, O]

    def unbindAll[O]: Iterator[O] = macro BinderProxy.unbindAll[R, O]
  }
}
