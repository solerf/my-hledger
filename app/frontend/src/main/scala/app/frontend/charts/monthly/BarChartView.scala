package app.frontend.charts.monthly

import app.frontend.Row
import app.frontend.charts.{Colors, Instance}
import com.raquo.laminar.api.L.*

import scala.scalajs.js

object BarChartView:

  val canvasId = "chart-bar"

  private val handle = new Instance(canvasId)

  def view(dataRowsSignal: Signal[List[Row]]): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", "By account"),
        div(
          cls := "chart-wrap",
          canvasTag(idAttr := canvasId, widthAttr := 960, heightAttr := 360)
        ),
        dataRowsSignal --> Observer[List[Row]](render)
      )
    )

  private def render(rows: List[Row]): Unit = {
    val (labels, values) =
      rows
        .groupMapReduce(_.account)(_.amount)(_ + _)
        .toList
        .sortBy { case (_, total) => -total }
        .unzip

    val colors = Colors.pickColors(labels).toList

    val data = js.Dynamic.literal(
      labels = js.Array(labels*),
      datasets = js.Array(
        js.Dynamic.literal(
          label = "Amount",
          data = js.Array(values.map(_.toDouble)*),
          backgroundColor = js.Array(colors*)
        )
      )
    )

    val options = js.Dynamic.literal(
      responsive = true,
      maintainAspectRatio = false,
      plugins = js.Dynamic.literal(
        legend = js.Dynamic.literal(display = false)
      )
    )

    handle.render(js.Dynamic.literal(`type` = "bar", data = data, options = options))
  }
