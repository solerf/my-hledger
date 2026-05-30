package app.frontend.charts.monthly

import app.frontend.Row
import app.frontend.charts.{Colors, Instance}

import scala.scalajs.js

import com.raquo.laminar.api.L.*

/** Pie (or doughnut) chart of amounts grouped by account for a single category
  * (e.g. expenses or liabilities). Instantiated per chart so each owns a
  * distinct canvas id and its own live Chart.js instance. `chartType` is any
  * round Chart.js type — "pie" (default) or "doughnut".
  */
final class PieChartView(canvasId: String, title: String, chartType: String = "pie"):

  private val handle = new Instance(canvasId)

  def view(dataRowsSignal: Signal[List[Row]]): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", title),
        div(
          cls := "chart-wrap chart-wrap-pie",
          canvasTag(idAttr := canvasId, widthAttr := 480, heightAttr := 360)
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
          data = js.Array(values.map(_.toDouble)*),
          backgroundColor = js.Array(colors*)
        )
      )
    )

    val options = js.Dynamic.literal(
      responsive = true,
      maintainAspectRatio = false,
      plugins = js.Dynamic.literal(
        legend = js.Dynamic.literal(position = "left")
      )
    )

    handle.render(js.Dynamic.literal(`type` = chartType, data = data, options = options))
  }
