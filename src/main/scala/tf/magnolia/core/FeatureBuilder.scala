package tf.magnolia.core

import org.tensorflow.example._
import magnolia._

import scala.language.experimental.macros
import scala.collection.JavaConverters._

trait FeatureBuilder[T] {
  def toFeatures(record: T): Features.Builder
}

object FeatureBuilder {
  type Typeclass[T] = FeatureBuilder[T]

  def of[T]: FeatureBuilderFrom[T] = new FeatureBuilderFrom[T]

  class FeatureBuilderFrom[T] {
    def apply[U](f: T => Iterable[U])(implicit fb: FeatureBuilder[Iterable[U]]): FeatureBuilder[T] =
      new FeatureBuilder[T] {
        override def toFeatures(record: T): Features.Builder = fb.toFeatures(f(record))
      }
  }

  def combine[T](caseClass: CaseClass[FeatureBuilder, T]): FeatureBuilder[T] = (record: T) => {
    val features = caseClass.parameters.map { p =>
      p.repeated
      composeWithName(p.label, p.typeclass).toFeatures(p.dereference(record))
    }
    features.reduce { (fb1, fb2) =>
      fb1.putAllFeature(fb2.getFeatureMap)
      fb1
    }
  }

  def dispatch[T](sealedTrait: SealedTrait[Typeclass, T]): Typeclass[T] = ???

  implicit def gen[T]: Typeclass[T] = macro Magnolia.gen[T]

  private def composeWithName[T](name: String, instance: FeatureBuilder[T]): FeatureBuilder[T] =
    (record: T) => {
      val features = instance.toFeatures(record)
      // If the parameter is -not- a nested case class, it should be treated as an anonymous
      // (un-named) single feature. In this case, we name the feature using the param name.
      // Instead of checking whether the param is a case class directly (via reflection),
      // we can instead just check whether the generated features already have names or not.
      // If the parameter -is- a nested case class, we prepend its feature names with this parameter
      // name to prevent potential ambiguities.
      if (features.getFeatureCount == 1 && features.containsFeature("")) {
        val feature = features.getFeatureOrThrow("")
        features.removeFeature("")
        features.putFeature(name, feature)
        features
      }
      else {
        // TODO: fix
        val fMap = features.getFeatureMap.asScala.map { case (fName, f) =>
          (s"${name}_$fName", f)
        }.asJava
        Features.newBuilder().putAllFeature(fMap)
      }
    }
}
