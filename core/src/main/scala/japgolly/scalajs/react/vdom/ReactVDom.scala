package japgolly.scalajs.react.vdom

import org.scalajs.dom
import scala.annotation.unchecked.uncheckedVariance
import scala.scalajs.js
import scalatags._
import scalatags.generic._
import japgolly.scalajs.react._

object ReactVDom extends Bundle[VDomBuilder, ReactOutput, ReactFragT] {
  object all extends StringTags
             with Attrs
             with Styles
             with ReactTags
             with DataConverters
             with Aggregate
             with ExtraAttrs

  object short extends StringTags
               with Util
               with DataConverters
               with generic.AbstractShort[VDomBuilder, ReactOutput, ReactFragT]
               with Aggregate {
    object * extends StringTags with Attrs with Styles
  }

  object attrs extends StringTags with Attrs with ExtraAttrs
  object tags extends StringTags with ReactTags
  object tags2 extends StringTags with ReactTags2
  object styles extends StringTags with Styles
  object styles2 extends StringTags with Styles2
  object svgTags extends StringTags with ReactSvgTags
  object svgStyles extends StringTags with SvgStyles
  object implicits extends Aggregate

  trait Aggregate extends generic.Aggregate[VDomBuilder, ReactOutput, ReactFragT] {
    def genericAttr[T] = new GenericAttr[T](a => a.toString)
    def genericStyle[T] = new GenericStyle[T]

    implicit def stringFrag(v: String) = new ReactVDom.StringFrag(v)

    val RawFrag = ReactVDom.RawFrag
    val StringFrag = ReactVDom.StringFrag
    type StringFrag = ReactVDom.StringFrag
    type RawFrag = ReactVDom.RawFrag

    def raw(s: String) = new RawFrag(s)

    implicit val jsThisFnAttr = new GenericAttr[js.ThisFunction](f => f)
    implicit val jsFnAttr = new GenericAttr[js.Function](f => f)
    implicit def reactRefAttr[T <: Ref[_]] = new GenericAttr[T](_.name)

    implicit def modifierFromRCU_(c: ReactComponentU_): Modifier = new Modifier {
      override def applyTo(t: VDomBuilder): Unit = t.appendChild(c)
    }

    implicit def modifierFromPropsChildren(c: PropsChildren): Modifier = new Modifier {
      override def applyTo(t: VDomBuilder): Unit = t.appendChild(c)
    }

    implicit def modifierFromSeqRCU_(cs: Seq[ReactComponentU_]): Modifier = new Modifier {
      override def applyTo(t: VDomBuilder): Unit = t.appendChild(cs.asJsArray)
    }

    implicit def modifierFromArrVdom[T <% VDom](c: js.Array[T]): Modifier = new Modifier {
      override def applyTo(t: VDomBuilder): Unit = t.appendChild(c)
    }

    implicit def vdomFromArrVdom[T <% VDom](cs: js.Array[T]): VDom = cs.asInstanceOf[VDom]
    implicit def vdomFromSeqVdom[T <% VDom](cs: Seq[T])     : VDom = cs.asJsArray
    implicit def vdomFromSeqTag            (cs: Seq[TypedTag[ReactOutput]])   : VDom = cs.toJsArray

    @inline final implicit def autoRender(t: TypedTag[ReactOutput]) = t.render
    @inline final implicit def autoRenderS(s: Seq[TypedTag[ReactOutput]]) = s.map(_.render)

    final def compositeAttr[A](k: Attr, f: (A, List[A]) => A, e: => Modifier = Nop) = new {
      def apply(as: Option[A]*)(implicit ev: AttrValue[A]): Modifier =
        as.toList.filter(_.isDefined).map(_.get) match {
          case h :: t => k := f(h, t)
          case Nil => e
        }
    }

    val classSwitch = compositeAttr[String](all.cls, (h,t) => (h::t) mkString " ")

    @inline final def classSet(ps: (String, Boolean)*): Modifier =
      classSwitch(ps.map(p => if (p._2) Some(p._1) else None): _*)

    @inline final def classSet(a: String, ps: (String, Boolean)*): Modifier =
      classSet(((a, true) +: ps):_*)

    @inline final def classSet(ps: Map[String, Boolean]): Modifier =
      classSet(ps.toSeq: _*)

    @inline final def classSet(a: String, ps: Map[String, Boolean]): Modifier =
      classSet(a, ps.toSeq: _*)
  }

  trait StringTags extends Util { self =>
    type ConcreteHtmlTag[T <: ReactOutput] = TypedTag[T]

    protected[this] implicit val stringAttrX: AttrValue[String] = new GenericAttr[String](s => s)
    protected[this] implicit val stringStyleX: StyleValue[String] = new GenericStyle[String]

    def makeAbstractTypedTag[T <: ReactOutput](tag: String, void: Boolean): TypedTag[T] =
      TypedTag(tag, Nil, void)

    implicit class SeqFrag[A <% Frag](xs: Seq[A]) extends Frag{
      def applyTo(t: VDomBuilder): Unit = xs.foreach(_.applyTo(t))
      def render: ReactOutput = {
        val b = new VDomBuilder()

        applyTo(b)

        b.render("")
      }
    }
  }

  import implicits._

  object StringFrag extends Companion[StringFrag]
  case class StringFrag(v: String) extends ReactDomFrag {
    def render: ReactFragT = v
  }

  def raw(s: String) = new RawFrag(s)

  object RawFrag extends Companion[RawFrag]
  case class RawFrag(v: String) extends Modifier {
    def render: ReactFragT = v
    def applyTo(t: VDomBuilder): Unit = t.appendChild(this.render)
  }

  class GenericAttr[T](f: T => js.Any) extends AttrValue[T]{
    def apply(t: VDomBuilder, a: Attr, v: T): Unit =
      t.addAttr(a.name, f(v))
  }

  class GenericStyle[T] extends StyleValue[T]{
    def apply(b: VDomBuilder, s: Style, v: T): Unit = {
      b.addStyle(s.cssName, v.toString)
    }
  }

  case class TypedTag[+Output <: ReactOutput](tag: String = "",
                                              modifiers: List[Seq[Modifier]],
                                              void: Boolean = false)
                     extends generic.TypedTag[VDomBuilder, Output, ReactFragT] with ReactDomFrag {
    // unchecked because Scala 2.10.4 seems to not like this, even though
    // 2.11.1 works just fine. I trust that 2.11.1 is more correct than 2.10.4
    // and so just force this.
    protected[this] type Self = TypedTag[Output @uncheckedVariance]

    def render: Output = {
      val b = new VDomBuilder()
      build(b)
      b.render(tag).asInstanceOf[Output]
    }

    def apply(xs: Modifier*): TypedTag[Output] =
      this.copy(tag=tag, void = void, modifiers = xs :: modifiers)

    /** Converts an ScalaTag fragment into an html string */
    override def toString = render.toString
  }

  val Nop: Modifier = new Modifier {
    override def applyTo(t: VDomBuilder): Unit = ()
  }

  trait ReactDomFrag extends generic.Frag[VDomBuilder, ReactFragT]{
    def render: ReactFragT
    def applyTo(b: VDomBuilder): Unit = b.appendChild(this.render)
  }

  trait ExtraAttrs extends Util {
    val className = "className".attr
    val refAttr = "ref".attr
    val keyAttr = "key".attr
    @inline final def key = keyAttr
    @inline final def ref = refAttr
    val draggable   = "draggable".attr
    val onDragStart = "onDragStart".attr
    val onDragEnd   = "onDragEnd".attr
    val onDragEnter = "onDragEnter".attr
    val onDragOver  = "onDragOver".attr
    val onDragLeave = "onDragLeave".attr
    val onDrop      = "onDrop".attr
    val onBeforeInput = "onBeforeInput".attr
  }

  implicit final class ReactAttrExt(val a: Attr) extends AnyVal {
    @inline def runs(thunk: => Unit) = a := ((() => thunk): js.Function)
    @inline def -->(thunk: => Unit) = a runs thunk
    @inline def ==>[N <: dom.Node, E <: SyntheticEvent[N]](eventHandler: E => Unit) = a := (eventHandler: js.Function)
  }

  implicit final class ReactBoolExt(val a: Boolean) extends AnyVal {
    @inline def &&(m: => Modifier): Modifier = if (a) m else Nop
    // @inline def :=>[V](v: => V): Option[V] = if (a) Some(v) else None
  }

  implicit final class ArrayChildrenExt[A](val as: Seq[A]) extends AnyVal {
    @inline def asJsArray = js.Array(as: _*)
    @inline def toJsArray(implicit ev: A =:= TypedTag[ReactOutput]) = js.Array(as.map(_.render): _*)
  }
}
