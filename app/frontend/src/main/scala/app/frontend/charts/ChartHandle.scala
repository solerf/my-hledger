package app.frontend.charts

import org.scalajs.dom

import scala.scalajs.js

/** Tiny helper around a Chart.js instance bound to a single canvas. Holds the
  * current instance so re-renders can dispose it before creating a new one.
  */
final class ChartHandle(canvasId: String):

  private var instance: Option[js.Dynamic] = None

  def render(cfg: js.Dynamic): Unit =
    val canvas = dom.document.getElementById(canvasId)
    if canvas == null then return
    instance.foreach(_.destroy())
    val Chart = js.Dynamic.global.Chart
    instance = Some(js.Dynamic.newInstance(Chart)(canvas, cfg))
