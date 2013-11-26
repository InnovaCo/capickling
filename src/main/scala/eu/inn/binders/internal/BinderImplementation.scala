package eu.inn.internal

import scala.language.reflectiveCalls
import scala.reflect.macros.Context
import language.experimental.macros

private trait BinderImplementation {
  val c: Context
  import c.universe._

  def bind[S: c.WeakTypeTag, O: c.WeakTypeTag] (index: c.Tree, obj: c.Tree, allFields: Boolean): c.Tree = {

    val setters = extractSetters[S]
    // println("setters: " + setters)

    val thisTerm = TermName(c.fresh("$this"))
    val stmtTerm = newTermName(c.fresh("$stmt"))
    val indexTerm = newTermName(c.fresh("$index"))
    val objTerm = newTermName(c.fresh("$obj"))

    val vals = List(
      ValDef(Modifiers(), thisTerm, TypeTree(), c.prefix.tree),
      ValDef(Modifiers(), stmtTerm, TypeTree(), Select(Ident(thisTerm), TermName("stmt"))),
      ValDef(Modifiers(), indexTerm, TypeTree(), index),
      ValDef(Modifiers(), objTerm, TypeTree(weakTypeTag[O].tpe), obj)
    )

    val (wholeParamSetter,wholeParamTypeArgs) = findSetter(true, setters, obj.symbol, weakTypeTag[O].tpe)

    // println ("wholeParamSetter = " + wholeParamSetter)
    val listOfCalls : List[Tree] = wholeParamSetter match {
      case Some(m) => {
        List(
          makeSetterGetterCall(stmtTerm, (m,wholeParamTypeArgs), List(Ident(indexTerm), Ident(objTerm)))
        )
      }

      case None => {
        val caseClassParams = extractCaseClassParams[O]

        caseClassParams.flatMap { parameter =>
          // println("looking setter for " + parameter + " in " + setters)

          val (sttr, callTypeArgs) = findSetter(false, setters, parameter, parameter.typeSignature)
          val call = sttr.map { setter =>

            // println("found setter for " + parameter + " : " + setter)
            val setterCall =
              makeSetterGetterCall(stmtTerm, (setter,callTypeArgs),
                List(
                  Literal(Constant(parameter.name.decoded)),
                  Select(Ident(objTerm), TermName(parameter.name.decoded))
                )
              )

            val hasCall = Apply(Select(Ident(stmtTerm), "hasParameter"), List(Literal(Constant(parameter.name.decoded))))

            if (allFields)
              setterCall
            else
              If(hasCall, setterCall, Literal(Constant()))
          }
          if (call.isEmpty) {
            c.abort(c.enclosingPosition, "No setter function found for parameter " + parameter)
          }
          call
        }
      }
    }

    val block = Block(vals ++ listOfCalls, Literal(Constant()))
    // println(block)
    block
  }

  def unbind[R: c.WeakTypeTag, O: c.WeakTypeTag](partial: Boolean, obj: c.Tree) : c.Tree = {

    val getters = extractGetters[R]
    // println("getters: " + getters)

    val caseClassParams = extractCaseClassParams[O]
    // println(caseClassParams)

    val thisTerm = TermName(c.fresh("$this"))
    val rowTerm = TermName(c.fresh("$row"))
    val objResultTerm = TermName(c.fresh("$obj"))
    val objOrigTerm = TermName(c.fresh("$objOrig"))

    val applyParams : List[(TermName, Tree, Symbol)] =
      caseClassParams.map { parameter =>
        val getter = findGetter(getters, parameter)
        val apply = makeSetterGetterCall(rowTerm, getter, List(Literal(Constant(parameter.name.decoded))))
        if (partial) {
          val fromObjOrig = Select(Ident(objOrigTerm), TermName(parameter.name.decoded))
          val hasCall = Apply(Select(Ident(rowTerm), "hasField"), List(Literal(Constant(parameter.name.decoded))))
          val iff: Tree = If(hasCall, apply, /*else*/fromObjOrig)
          (TermName(c.fresh("$arg1")), iff, parameter)
        }
        else {
          (TermName(c.fresh("$arg1")), apply, parameter)
        }
      } toList

    val outputCompanionSymbol = weakTypeOf[O].typeSymbol.companionSymbol

    val vals = List(
      ValDef(Modifiers(), thisTerm, TypeTree(), c.prefix.tree),
      ValDef(Modifiers(), rowTerm, TypeTree(), Select(Ident(thisTerm), TermName("row")))
    )

    val applyVals = applyParams.map(p => {
      ValDef(Modifiers(), p._1, TypeTree(p._3.typeSignature), p._2)
    })

    val objOrigVals = if (partial) {
      List(ValDef(Modifiers(), objOrigTerm, TypeTree(), obj))
    }
    else
      List()

    val applyCallParams: List[Ident] = applyParams.map(a => Ident(a._1))
    val applyCall = Apply(Select(Ident(outputCompanionSymbol.name), "apply"), applyCallParams)

    val block = Block(vals ++ objOrigVals ++ applyVals ++
      List(ValDef(Modifiers(), objResultTerm, TypeTree(), applyCall)),
      Ident(objResultTerm))

    // println(block)
    block
  }

  def unbindOne[RS: c.WeakTypeTag, O: c.WeakTypeTag] : c.Tree = {
    val thisTerm = TermName(c.fresh("$this"))
    val rowsTerm = TermName(c.fresh("$rows"))
    val iteratorTerm = TermName(c.fresh("$iterator"))
    val objResultTerm = TermName(c.fresh("$result"))

    val hasCall = Select(Ident(iteratorTerm), "hasNext")

    // get first element
    val nextRowTerm = TermName(c.fresh("$nextRow"))
    val nextRowCall = Apply(Select(Ident(iteratorTerm), "next"), List())
    val valsNextItem = List(
      ValDef(Modifiers(), nextRowTerm, Select(Ident(rowsTerm), TypeName("rowType")), nextRowCall)
    )

    val unbindCall = Block(valsNextItem,
      Apply(Select(Ident(weakTypeOf[Some.type].termSymbol), "apply"),
        List(
          TypeApply(Select(Ident(nextRowTerm), "unbind"), List(Ident(weakTypeOf[O].typeSymbol)))
        )
      )
    )
    
    val none = weakTypeOf[None.type].termSymbol
    val ifCall = If(hasCall, unbindCall, Ident(none))

    val vals = List(
      ValDef(Modifiers(), thisTerm, TypeTree(), c.prefix.tree),
      ValDef(Modifiers(), rowsTerm, TypeTree(), Select(Ident(thisTerm), TermName("rows"))),
      ValDef(Modifiers(), iteratorTerm, TypeTree(), Select(Ident(rowsTerm), TermName("iterator"))),
      ValDef(Modifiers(), objResultTerm, TypeTree(), ifCall)
    )

    val block = Block(vals, Ident(objResultTerm))
    // println(block)
    block
  }

  def unbindAll[RS: c.WeakTypeTag, O: c.WeakTypeTag] : c.Tree = {
    val thisTerm = TermName(c.fresh("$this"))
    val rowsTerm = TermName(c.fresh("$rows"))
    val rowTerm = TermName(c.fresh("$row"))
    val iteratorTerm = TermName(c.fresh("$iterator"))
    val objResultTerm = TermName(c.fresh("$result"))

    val unbindCall = Function(
      List(ValDef(Modifiers(Flag.PARAM), rowTerm, Select(Ident(rowsTerm), TypeName("rowType")), EmptyTree)),
      TypeApply(Select(Ident(rowTerm), "unbind"), List(Ident(weakTypeOf[O].typeSymbol)))
    )

    // map iterator
    val mapCall = Apply(Select(Ident(iteratorTerm), "map"), List(unbindCall))

    val vals = List(
      ValDef(Modifiers(), thisTerm, TypeTree(), c.prefix.tree),
      ValDef(Modifiers(), rowsTerm, TypeTree(), Select(Ident(thisTerm), TermName("rows"))),
      ValDef(Modifiers(), iteratorTerm, TypeTree(), Select(Ident(rowsTerm), TermName("iterator"))),
      ValDef(Modifiers(), objResultTerm, TypeTree(), mapCall)
    )

    val block = Block(vals, Ident(objResultTerm))
    // println(block)
    block
  }

  def execute (args: Seq[c.Tree]): c.Tree = {

    val thisTerm = TermName(c.fresh("$this"))
    val rowsTerm = TermName(c.fresh("$rows"))
    val stmtTerm = TermName(c.fresh("$stmt"))

    var bindAllParameters =
      args.zipWithIndex.map { arg =>
        val t = arg._1
        val index = arg._2
        val term = TermName(c.fresh("$t0"))
        val vdef = ValDef(Modifiers(), term, TypeTree(), t)
        val bindCall = Apply(Select(Ident(stmtTerm), "bind"), List(Literal(Constant(index)),Ident(term)))
        List(vdef,bindCall)
      } flatten

    val bindCallback = Function(
      List(ValDef(Modifiers(Flag.PARAM), stmtTerm, Select(Ident(thisTerm), TypeName("statementType")), EmptyTree)),
      Block(bindAllParameters.toList, Literal(Constant()))
    )

    val callExecute = Apply(
      Select(Select(Ident(thisTerm), "query"), "bindAndExecute"), List(bindCallback)
    )

    val vals = List(
      ValDef(Modifiers(), thisTerm, TypeTree(), c.prefix.tree),
      ValDef(Modifiers(), rowsTerm, TypeTree(), callExecute)
    )

    val block = Block(vals, Ident(rowsTerm))
    // println(block)
    block
  }

  private def applyTypeArgs(select: Select, typeArgs: List[Type]) = {
    if (typeArgs.isEmpty)
      select
    else
      TypeApply(select, typeArgs.map(a => Ident(a.typeSymbol)))
  }

  private def makeSetterGetterCall(rowTerm: TermName, getter: (MethodSymbol, List[Type]), parameters: List[Tree]) : Apply = {
    val inner = Apply(applyTypeArgs(Select(Ident(rowTerm), getter._1), getter._2), parameters)

    getter._1.paramss.tail.foldLeft(inner){(a:Apply, params:List[Symbol]) =>
      Apply(a,
        params.map(p =>
          Select(
            Select(Ident(newTermName("scala")), newTermName("Predef")),
            newTermName("implicitly")
          )
        )
      )
    }
  }

  private def findSetter(byIndex: Boolean, setters: List[MethodSymbol], parSym: Symbol, parSymType: Type) : (Option[MethodSymbol], List[Type]) = {
    var rMax: Int = 0
    var mRes: Option[MethodSymbol] = None
    var mTypeArgs: List[Type] = List()
    setters.map({ m =>

      val idxSymbol = m.paramss.head(0); // parSym 1 (index/name)
      val methodParSym = m.paramss.head(1); // parSym 2 (value)

      if (if (byIndex) idxSymbol.typeSignature =:= typeOf[Int] else idxSymbol.typeSignature =:= typeOf[String]) {

        val (r, typeArgs) = compareTypes(parSymType, methodParSym.typeSignature)
        // println("Comparing " + m + " with arg type " + methodParSym.typeSignature + " for parameter " + parSym + " with type " + parSymType + " RES = " + r + " --- " + math.random)

        if (r > rMax) {
          rMax = r
          mRes = Some(m)
          mTypeArgs = typeArgs
        }
      }
    })
    (mRes, mTypeArgs)
  }

  private def compareTypes(left: Type, right: Type) : (Int, List[Type]) = {
    if (left =:= right)
      (100, List())
    else
    if (left <:< right)
      (90, List())
    else
    if (left weak_<:< right)
      (80, List())
    else {
      // println("left = " + left + " " + left.getClass + " right = " + right + " " + right.getClass)

      left match {
        case TypeRef(leftTpe,leftSym,leftArgs) => {
          right match {
            case TypeRef(rightTpe,rightSym,rightArgs) => {
              // println("leftSym = " + leftTpe + " " + leftSym + " " + leftArgs + " right = " + rightTpe + " " + rightSym + " " + rightArgs)
              var r =
                if (leftSym.typeSignature =:= rightSym.typeSignature) // Outer type is matched fully
                  50
                else
                if (leftSym.typeSignature <:< rightSym.typeSignature) // Outer type inherits
                  30
                else
                if (rightTpe == NoPrefix) // Right symbol is generic type parameter
                  20
                else
                  0

              if (r > 0) { // now check generic type args
                // println("Checking generic type args of : " + left + " " + right)
                if (leftArgs.size == rightArgs.size) {
                  for (i <- 0 until leftArgs.size) {
                    val lefT = leftArgs(i)
                    val rightT = rightArgs(i)
                    val tR = compareTypes(lefT, rightT)
                    if (tR == 0) {
                      r = 0
                    }
                  }
                }
              }
              (r, leftArgs)
            }
            case _ => (0, List())
          }
        }
        case _ => (0, List())
      }
    }
  }

  private def extractTypeParameters(t:Type) = t match {
    case TypeRef(_,_,args) => args
    case _ => List()
  }

  private def extractSetters[T: c.WeakTypeTag] : List[MethodSymbol] = {
    weakTypeOf[T].members.filter(member => member.isMethod &&
      member.name.decoded.startsWith("set") &&
      member.isPublic && {
        val m = member.asInstanceOf[MethodSymbol]
        m.paramss.nonEmpty &&
        (m.paramss.tail.isEmpty || allImplicits(m.paramss.tail)) &&
        m.paramss.head.size == 2 && // only 2 parameters
        ( m.paramss.head(0).typeSignature =:= typeOf[Int] ||
          m.paramss.head(0).typeSignature =:= typeOf[String] )
      }
    ).map(_.asInstanceOf[MethodSymbol]).toList
  }

  private def findGetter(getters: List[MethodSymbol], parameter: c.Symbol) : (MethodSymbol, List[Type]) = {
    var rMax: Int = 0
    var mRes: Option[MethodSymbol] = None
    var mTypeArgs: List[Type] = List()

    getters.map({ m =>

      val (r, typeArgs) = compareTypes(parameter.typeSignature, m.returnType)
      // println("Comparing " + m + " with arg type " + m.returnType + " for parameter " + parameter + " with type " + parameter.typeSignature + " RES = " + r + " --- " + math.random)

      if (r > rMax) {
        rMax = r
        mRes = Some(m)
        mTypeArgs = typeArgs
      }
    })
    mRes

    if (mRes.isDefined)
      (mRes.get, mTypeArgs)
    else
      c.abort(c.enclosingPosition, "No getter function found for parameter " + parameter)
  }

  private def extractGetters[T: c.WeakTypeTag] : List[MethodSymbol] = {

    weakTypeOf[T].members.filter(member => member.isMethod &&
      member.name.decoded.startsWith("get") &&
      member.isPublic && {
        val m = member.asInstanceOf[MethodSymbol]
        // println(m.paramss)
        m.paramss.nonEmpty &&
        (m.paramss.tail.isEmpty || allImplicits(m.paramss.tail)) && // 1 group of parameters or 2 group with all implicits
        m.paramss.head.size == 1 && // only 1 parSym
        m.paramss.head(0).typeSignature =:= typeOf[String]
      }
    ).map(_.asInstanceOf[MethodSymbol]).toList
  }

  private def allImplicits(symbols: List[List[Symbol]]): Boolean = symbols.flatten.filter(!_.isImplicit).isEmpty

  private def extractCaseClassParams[T: c.WeakTypeTag] : List[c.Symbol] = {

    val companioned = weakTypeOf[T].typeSymbol
    val companionSymbol = companioned.companionSymbol
    val companionType = companionSymbol.typeSignature

    companionType.declaration(stringToTermName("unapply")) match {
      case NoSymbol => c.abort(c.enclosingPosition, s"No setter or unapply function found for ${companioned.fullName}")
      case s =>
        val unapply = s.asMethod
        val unapplyReturnTypes = unapply.returnType match {
          case TypeRef(_, _, Nil) =>
            c.abort(c.enclosingPosition, s"Apply of ${companionSymbol} has no parameters. Are you using an empty case class?")
          case TypeRef(_, _, args) =>
            args.head match {
              case t @ TypeRef(_, _, Nil) => Some(List(t))
              case t @ TypeRef(_, _, args) =>
                if (t <:< typeOf[Option[_]]) Some(List(t))
                else if (t <:< typeOf[Seq[_]]) Some(List(t))
                else if (t <:< typeOf[Set[_]]) Some(List(t))
                else if (t <:< typeOf[Map[_, _]]) Some(List(t))
                else if (t <:< typeOf[Product]) Some(args)
              case _ => None
            }
          case _ => None
        }

        companionType.declaration(stringToTermName("apply")) match {
          case NoSymbol => c.abort(c.enclosingPosition, "No apply function found")
          case s =>
            // searches apply method corresponding to unapply
            val applies = s.asMethod.alternatives
            val apply = applies.collectFirst {
              case (apply: MethodSymbol) if (apply.paramss.headOption.map(_.map(_.asTerm.typeSignature)) == unapplyReturnTypes) => apply
            }
            apply match {
              case Some(apply) =>

                // println("apply found:" + apply)
                if (!apply.paramss.tail.isEmpty)
                  c.abort(c.enclosingPosition, "Couldn't use apply method with more than a single parameter group")

                apply.paramss.head

              case None => c.abort(c.enclosingPosition, "No apply function found matching unapply parameters")
            }
        }

    }
  }

  private object TermName {
    def apply(s: String) = newTermName(s)
    def unapply(name: TermName): Option[String] = Some(name.toString)
  }

  private object TypeName {
    def apply(s: String) = newTypeName(s)
    def unapply(name: TypeName): Option[String] = Some(name.toString)
  }
}