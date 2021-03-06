/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package scala.math

import java.util.Comparator

/** A trait for representing total orderings.  It is important to 
 *  distinguish between a type that has a total order and a representation 
 *  of total ordering on some type.  This trait is for the latter.
 *
 *  A [[http://en.wikipedia.org/wiki/Total_order total ordering]]
 *  is a binary relation on a type `T` that is also an equivalence relation
 *  and partial ordering on values of type `T`.  This relation is exposed as
 *  the `compare` method of the `Ordering` trait.
 *
 *  This relation must be:
 *
 *  - reflexive:  `x == x`
 *  - antisymmetric:  if `x <= y && y <= x`, then `x == y`
 *  - transitive:  if `x <= y && y <= z`, then `x <= z`
 *
 *  @author Geoffrey Washburn
 *  @version 0.9.5, 2008-04-15
 *  @since 2.7
 */
@annotation.implicitNotFound(msg = "No implicit Ordering defined for ${T}.")
trait Ordering[T] extends Comparator[T] with PartialOrdering[T] with Serializable {
  outer =>

  /** An `Ordering` is defined at all `x` and `y`. */
  def tryCompare(x: T, y: T) = Some(compare(x, y))

 /** Returns a negative integer iff `x` comes before `y` in the ordering,
   * returns 0 iff `x` is the same in the ordering as `y`, and returns a 
   * positive number iff `x` comes after `y` in the ordering.
   */
  def compare(x: T, y: T): Int

  /** @return   true iff `x` comes before `y` in the ordering, or is equal to `y`.
   */
  override def lteq(x: T, y: T): Boolean = compare(x, y) <= 0

  /** @return   true iff `x` comes after `y` in the ordering, or is equal to `y`.
   */
  override def gteq(x: T, y: T): Boolean = compare(x, y) >= 0

  /** @return   true iff `x` comes before `y` in the ordering, and is not equal to `y`.
   */
  override def lt(x: T, y: T): Boolean = compare(x, y) < 0

  /** @return   true iff `y` comes before `x` in the ordering, and is not equal to `x`.
   */
  override def gt(x: T, y: T): Boolean = compare(x, y) > 0

  /** @return   true iff `x` is equivalent to `y` in the ordering.
   */
  override def equiv(x: T, y: T): Boolean = compare(x, y) == 0

  /** Returns the argument which comes later in the ordering. */
  def max(x: T, y: T): T = if (gteq(x, y)) x else y

  /** Returns the argument which comes earlier in the ordering. */
  def min(x: T, y: T): T = if (lteq(x, y)) x else y

  override def reverse: Ordering[T] = new Ordering[T] {
    override def reverse = outer
    def compare(x: T, y: T) = outer.compare(y, x)
  }

  /** Given a function `U => T`, creates `Ordering[U]`. */
  def on[U](f: U => T): Ordering[U] = new Ordering[U] {
    def compare(x: U, y: U) = outer.compare(f(x), f(y))
  }

  class Ops(lhs: T) {
    def <(rhs: T) = lt(lhs, rhs)
    def <=(rhs: T) = lteq(lhs, rhs)
    def >(rhs: T) = gt(lhs, rhs)
    def >=(rhs: T) = gteq(lhs, rhs)
    def equiv(rhs: T) = Ordering.this.equiv(lhs, rhs)
    def max(rhs: T): T = Ordering.this.max(lhs, rhs)
    def min(rhs: T): T = Ordering.this.min(lhs, rhs)
  }
  implicit def mkOrderingOps(lhs: T): Ops = new Ops(lhs)
}

trait LowPriorityOrderingImplicits {
  /** This would conflict with all the nice implicit Orderings
   *  available, but thanks to the magic of prioritized implicits
   *  via subclassing we can make `Ordered[A] => Ordering[A]` only
   *  turn up if nothing else works.  Since `Ordered[A]` extends
   *  `Comparable[A]` anyway, we can throw in some Java interop too.
   */
  implicit def ordered[A <% Comparable[A]]: Ordering[A] = new Ordering[A] {
    def compare(x: A, y: A): Int = x compareTo y
  }
  implicit def comparatorToOrdering[A](implicit cmp: Comparator[A]): Ordering[A] = new Ordering[A] {
    def compare(x: A, y: A) = cmp.compare(x, y)
  }
}

object Ordering extends LowPriorityOrderingImplicits {
  def apply[T](implicit ord: Ordering[T]) = ord

  trait ExtraImplicits {
    /** Not in the standard scope due to the potential for divergence:
     *  For instance `implicitly[Ordering[Any]]` diverges in its presence.
     */
    implicit def seqDerivedOrdering[CC[X] <: collection.Seq[X], T](implicit ord: Ordering[T]): Ordering[CC[T]] =
      new Ordering[CC[T]] {
        def compare(x: CC[T], y: CC[T]): Int = {
          val xe = x.iterator
          val ye = y.iterator

          while (xe.hasNext && ye.hasNext) {
            val res = ord.compare(xe.next, ye.next)
            if (res != 0) return res
          }

          Ordering.Boolean.compare(xe.hasNext, ye.hasNext)
        }
      }

    /** This implicit creates a conversion from any value for which an
     *  implicit `Ordering` exists to the class which creates infix operations.
     *  With it imported, you can write methods as follows:
     *  {{{
     *  def lessThen[T: Ordering](x: T, y: T) = x < y 
     *  }}}
     */
    implicit def infixOrderingOps[T](x: T)(implicit ord: Ordering[T]): Ordering[T]#Ops = new ord.Ops(x) 
  }

  /** An object for implicits which for one reason or another we
   *  aren't ready to put in the default scope.
   */
  object Implicits extends ExtraImplicits { }

  def fromLessThan[T](cmp: (T, T) => Boolean): Ordering[T] = new Ordering[T] {
    def compare(x: T, y: T) = if (cmp(x, y)) -1 else if (cmp(y, x)) 1 else 0
    // overrides to avoid multiple comparisons
    override def lt(x: T, y: T): Boolean = cmp(x, y)
    override def gt(x: T, y: T): Boolean = cmp(y, x)
    override def gteq(x: T, y: T): Boolean = !cmp(x, y)
    override def lteq(x: T, y: T): Boolean = !cmp(y, x)
  }

  def by[T, S](f: T => S)(implicit ord: Ordering[S]): Ordering[T] =
    fromLessThan((x, y) => ord.lt(f(x), f(y)))  

  trait UnitOrdering extends Ordering[Unit] {
    def compare(x: Unit, y: Unit) = 0
  }
  implicit object Unit extends UnitOrdering

  trait BooleanOrdering extends Ordering[Boolean] {
    def compare(x: Boolean, y: Boolean) = (x, y) match {
      case (false, true) => -1
      case (true, false) => 1
      case _ => 0
    }
  }
  implicit object Boolean extends BooleanOrdering

  trait ByteOrdering extends Ordering[Byte] {
    def compare(x: Byte, y: Byte) = x.toInt - y.toInt
  }
  implicit object Byte extends ByteOrdering

  trait CharOrdering extends Ordering[Char] {
    def compare(x: Char, y: Char) = x.toInt - y.toInt
  }
  implicit object Char extends CharOrdering

  trait ShortOrdering extends Ordering[Short] {
    def compare(x: Short, y: Short) = x.toInt - y.toInt
  }
  implicit object Short extends ShortOrdering

  trait IntOrdering extends Ordering[Int] {
    def compare(x: Int, y: Int) =
      if (x < y) -1
      else if (x == y) 0
      else 1
  }
  implicit object Int extends IntOrdering

  trait LongOrdering extends Ordering[Long] {
    def compare(x: Long, y: Long) =
      if (x < y) -1
      else if (x == y) 0
      else 1
  }
  implicit object Long extends LongOrdering

  trait FloatOrdering extends Ordering[Float] {
    def compare(x: Float, y: Float) = java.lang.Float.compare(x, y)
  }
  implicit object Float extends FloatOrdering

  trait DoubleOrdering extends Ordering[Double] {
    def compare(x: Double, y: Double) = java.lang.Double.compare(x, y)
  }
  implicit object Double extends DoubleOrdering

  trait BigIntOrdering extends Ordering[BigInt] {
    def compare(x: BigInt, y: BigInt) = x.compare(y)
  }
  implicit object BigInt extends BigIntOrdering

  trait BigDecimalOrdering extends Ordering[BigDecimal] {
    def compare(x: BigDecimal, y: BigDecimal) = x.compare(y)
  }
  implicit object BigDecimal extends BigDecimalOrdering

  trait StringOrdering extends Ordering[String] {
    def compare(x: String, y: String) = x.compareTo(y)
  }
  implicit object String extends StringOrdering

  trait OptionOrdering[T] extends Ordering[Option[T]] {
    def optionOrdering: Ordering[T]
    def compare(x: Option[T], y: Option[T]) = (x, y) match {
      case (None, None)       => 0
      case (None, _)          => -1
      case (_, None)          => 1
      case (Some(x), Some(y)) => optionOrdering.compare(x, y)
    }
  }
  implicit def Option[T](implicit ord: Ordering[T]): Ordering[Option[T]] =
    new OptionOrdering[T] { val optionOrdering = ord }

  implicit def Iterable[T](implicit ord: Ordering[T]): Ordering[Iterable[T]] = 
    new Ordering[Iterable[T]] {
      def compare(x: Iterable[T], y: Iterable[T]): Int = {
        val xe = x.iterator
        val ye = y.iterator

        while (xe.hasNext && ye.hasNext) {
          val res = ord.compare(xe.next, ye.next)
          if (res != 0) return res
        }

        Boolean.compare(xe.hasNext, ye.hasNext)
      }
    }

  implicit def Tuple2[T1, T2](implicit ord1: Ordering[T1], ord2: Ordering[T2]): Ordering[(T1, T2)] = 
    new Ordering[(T1, T2)]{
      def compare(x: (T1, T2), y: (T1, T2)): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        0
      }
    }

  implicit def Tuple3[T1, T2, T3](implicit ord1: Ordering[T1], ord2: Ordering[T2], ord3: Ordering[T3]) : Ordering[(T1, T2, T3)] = 
    new Ordering[(T1, T2, T3)]{
      def compare(x: (T1, T2, T3), y: (T1, T2, T3)): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        val compare3 = ord3.compare(x._3, y._3)
        if (compare3 != 0) return compare3
        0
      }
    }

  implicit def Tuple4[T1, T2, T3, T4](implicit ord1: Ordering[T1], ord2: Ordering[T2], ord3: Ordering[T3], ord4: Ordering[T4]) : Ordering[(T1, T2, T3, T4)] = 
    new Ordering[(T1, T2, T3, T4)]{
      def compare(x: (T1, T2, T3, T4), y: (T1, T2, T3, T4)): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        val compare3 = ord3.compare(x._3, y._3)
        if (compare3 != 0) return compare3
        val compare4 = ord4.compare(x._4, y._4)
        if (compare4 != 0) return compare4
        0
      }
    }

  implicit def Tuple5[T1, T2, T3, T4, T5](implicit ord1: Ordering[T1], ord2: Ordering[T2], ord3: Ordering[T3], ord4: Ordering[T4], ord5: Ordering[T5]): Ordering[(T1, T2, T3, T4, T5)] = 
    new Ordering[(T1, T2, T3, T4, T5)]{
      def compare(x: (T1, T2, T3, T4, T5), y: Tuple5[T1, T2, T3, T4, T5]): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        val compare3 = ord3.compare(x._3, y._3)
        if (compare3 != 0) return compare3
        val compare4 = ord4.compare(x._4, y._4)
        if (compare4 != 0) return compare4
        val compare5 = ord5.compare(x._5, y._5)
        if (compare5 != 0) return compare5
        0
      }
    }

  implicit def Tuple6[T1, T2, T3, T4, T5, T6](implicit ord1: Ordering[T1], ord2: Ordering[T2], ord3: Ordering[T3], ord4: Ordering[T4], ord5: Ordering[T5], ord6: Ordering[T6]): Ordering[(T1, T2, T3, T4, T5, T6)] = 
    new Ordering[(T1, T2, T3, T4, T5, T6)]{
      def compare(x: (T1, T2, T3, T4, T5, T6), y: (T1, T2, T3, T4, T5, T6)): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        val compare3 = ord3.compare(x._3, y._3)
        if (compare3 != 0) return compare3
        val compare4 = ord4.compare(x._4, y._4)
        if (compare4 != 0) return compare4
        val compare5 = ord5.compare(x._5, y._5)
        if (compare5 != 0) return compare5
        val compare6 = ord6.compare(x._6, y._6)
        if (compare6 != 0) return compare6
        0
      }
    }

  implicit def Tuple7[T1, T2, T3, T4, T5, T6, T7](implicit ord1: Ordering[T1], ord2: Ordering[T2], ord3: Ordering[T3], ord4: Ordering[T4], ord5: Ordering[T5], ord6: Ordering[T6], ord7: Ordering[T7]): Ordering[(T1, T2, T3, T4, T5, T6, T7)] = 
    new Ordering[(T1, T2, T3, T4, T5, T6, T7)]{
      def compare(x: (T1, T2, T3, T4, T5, T6, T7), y: (T1, T2, T3, T4, T5, T6, T7)): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        val compare3 = ord3.compare(x._3, y._3)
        if (compare3 != 0) return compare3
        val compare4 = ord4.compare(x._4, y._4)
        if (compare4 != 0) return compare4
        val compare5 = ord5.compare(x._5, y._5)
        if (compare5 != 0) return compare5
        val compare6 = ord6.compare(x._6, y._6)
        if (compare6 != 0) return compare6
        val compare7 = ord7.compare(x._7, y._7)
        if (compare7 != 0) return compare7
        0
      }
    }

  implicit def Tuple8[T1, T2, T3, T4, T5, T6, T7, T8](implicit ord1: Ordering[T1], ord2: Ordering[T2], ord3: Ordering[T3], ord4: Ordering[T4], ord5: Ordering[T5], ord6: Ordering[T6], ord7: Ordering[T7], ord8: Ordering[T8]): Ordering[(T1, T2, T3, T4, T5, T6, T7, T8)] = 
    new Ordering[(T1, T2, T3, T4, T5, T6, T7, T8)]{
      def compare(x: (T1, T2, T3, T4, T5, T6, T7, T8), y: (T1, T2, T3, T4, T5, T6, T7, T8)): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        val compare3 = ord3.compare(x._3, y._3)
        if (compare3 != 0) return compare3
        val compare4 = ord4.compare(x._4, y._4)
        if (compare4 != 0) return compare4
        val compare5 = ord5.compare(x._5, y._5)
        if (compare5 != 0) return compare5
        val compare6 = ord6.compare(x._6, y._6)
        if (compare6 != 0) return compare6
        val compare7 = ord7.compare(x._7, y._7)
        if (compare7 != 0) return compare7
        val compare8 = ord8.compare(x._8, y._8)
        if (compare8 != 0) return compare8
        0
      }
    }

  implicit def Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9](implicit ord1: Ordering[T1], ord2: Ordering[T2], ord3: Ordering[T3], ord4: Ordering[T4], ord5: Ordering[T5], ord6: Ordering[T6], ord7: Ordering[T7], ord8 : Ordering[T8], ord9: Ordering[T9]): Ordering[(T1, T2, T3, T4, T5, T6, T7, T8, T9)] = 
    new Ordering[(T1, T2, T3, T4, T5, T6, T7, T8, T9)]{
      def compare(x: (T1, T2, T3, T4, T5, T6, T7, T8, T9), y: (T1, T2, T3, T4, T5, T6, T7, T8, T9)): Int = {
        val compare1 = ord1.compare(x._1, y._1)
        if (compare1 != 0) return compare1
        val compare2 = ord2.compare(x._2, y._2)
        if (compare2 != 0) return compare2
        val compare3 = ord3.compare(x._3, y._3)
        if (compare3 != 0) return compare3
        val compare4 = ord4.compare(x._4, y._4)
        if (compare4 != 0) return compare4
        val compare5 = ord5.compare(x._5, y._5)
        if (compare5 != 0) return compare5
        val compare6 = ord6.compare(x._6, y._6)
        if (compare6 != 0) return compare6
        val compare7 = ord7.compare(x._7, y._7)
        if (compare7 != 0) return compare7
        val compare8 = ord8.compare(x._8, y._8)
        if (compare8 != 0) return compare8
        val compare9 = ord9.compare(x._9, y._9)
        if (compare9 != 0) return compare9
        0
      }
    }

}
