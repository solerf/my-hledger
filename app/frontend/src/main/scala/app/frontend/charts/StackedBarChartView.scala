package app.frontend.charts

import app.frontend.Row
import com.raquo.laminar.api.L.*

import scala.scalajs.js

object StackedBarChartView:

  val canvasId = "chart-stacked-bar"

  private val handle = new Instance(canvasId)

  def view(rowsSignal: Signal[List[Row]]): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", "Account breakdown"),
        div(
          cls := "chart-wrap",
          canvasTag(idAttr := canvasId, widthAttr := 960, heightAttr := 360)
        ),
        rowsSignal --> Observer[List[Row]](render)
      )
    )

  private def topLevel(account: String): String =
    account.split(':').take(2).mkString(":")

  private def render(rows: List[Row]): Unit = {
    val categories  = rows.map(r => topLevel(r.account)).distinct.sorted
    val subaccounts = rows.map(_.account).distinct.sorted
    val colors      = Colors.pickColors(subaccounts).toList

    val totals: Map[(String, String), BigDecimal] =
      rows.groupMapReduce(r => (r.account, topLevel(r.account)))(_.amount)(_ + _)

    val datasets = subaccounts.zip(colors).map { case (sub, color) =>
      val series = categories.map(c => totals.getOrElse((sub, c), BigDecimal(0)).toDouble)
      js.Dynamic.literal(
        label = sub,
        data = js.Array(series*),
        backgroundColor = color
      )
    }

    val data = js.Dynamic.literal(
      labels = js.Array(categories*),
      datasets = js.Array(datasets*)
    )

    val options = js.Dynamic.literal(
      responsive = true,
      maintainAspectRatio = false,
      plugins = js.Dynamic.literal(
        legend = js.Dynamic.literal(position = "bottom")
      ),
      scales = js.Dynamic.literal(
        x = js.Dynamic.literal(stacked = true),
        y = js.Dynamic.literal(stacked = true)
      )
    )

    handle.render(js.Dynamic.literal(`type` = "bar", data = data, options = options))
  }
