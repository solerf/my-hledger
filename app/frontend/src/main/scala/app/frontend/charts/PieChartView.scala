package app.frontend.charts

import app.frontend.Views.Row
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** Pie chart: share of total spend per account for the selected month. */
object PieChartView:

  val canvasId = "chart-pie"

  private val handle = new ChartHandle(canvasId)

  def view(rowsSignal: Signal[List[Row]]): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", "Account share"),
        div(
          cls := "chart-wrap pie-wrap",
          canvasTag(idAttr := canvasId, widthAttr := 360, heightAttr := 360)
        ),
        rowsSignal --> Observer[List[Row]](render)
      )
    )

  private def render(rows: List[Row]): Unit = {
    val (labels, values) = rows
      .groupMapReduce(r => r.account.split(':').take(2).mkString(":"))(_.amount)(_ + _)
      .toList
      .sortBy { case (_, total) => -total }
      .unzip

    val colors = pickColors(labels.size).toList

    val data = js.Dynamic.literal(
      labels = js.Array(labels*),
      datasets = js.Array(
        js.Dynamic.literal(
          data = js.Array(values*),
          backgroundColor = js.Array(colors*)
        )
      )
    )

    val options = js.Dynamic.literal(
      responsive = true,
      maintainAspectRatio = false,
      plugins = js.Dynamic.literal(
        legend = js.Dynamic.literal(position = "bottom")
      )
    )

    handle.render(js.Dynamic.literal(`type` = "pie", data = data, options = options))
  }
