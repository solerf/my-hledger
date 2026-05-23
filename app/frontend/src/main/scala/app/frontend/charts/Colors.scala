package app.frontend.charts

import scala.collection.mutable

object Colors {

  private val colors: mutable.Map[String, String] = mutable.Map.empty

  def pickColors(labels: List[String]): Vector[String] =
    labels.map(l => colors.getOrElseUpdate(l, randomColor())).toVector

  private def randomColor(): String = "#%06x".format(scala.util.Random.nextInt(1 << 24))
}
