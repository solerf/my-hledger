package app.frontend.charts.monthly

import app.frontend.Row
import app.frontend.charts.{Colors, Instance}

import scala.scalajs.js

import com.raquo.laminar.api.L.*

object StackedBarChartView:

  val canvasId = "chart-stacked-bar"

  private val handle = new Instance(canvasId)

  def view(dataRowSignal: Signal[List[Row]]): HtmlElement =
    div(
      cls := "card shadow-sm mb-4",
      div(
        cls := "card-body",
        h5(cls := "card-title", "Account breakdown"),
        div(
          cls := "chart-wrap",
          canvasTag(idAttr := canvasId, widthAttr := 960, heightAttr := 360)
        ),
        dataRowSignal --> Observer[List[Row]](render)
      )
    )

  private def render(rows: List[Row]): Unit = {
    val (totals, categories, subaccounts) =
      rows.foldLeft((Map.empty[(String, String), Double], List.empty[String], List.empty[String])) {
        case ((totals, cat, sub), next) =>
          val splitAcc   = next.account.split(":")
          val topAccount =
            if (splitAcc.length > 2) splitAcc.take(2).mkString(":") else splitAcc.head
          val totalKey = (topAccount, next.account)

          val totalAcc = totals.updatedWith(totalKey) {
            current => current.map(_ + next.amount.toDouble).orElse(Some(next.amount.toDouble))
          }
          (totalAcc, topAccount :: cat, next.account :: sub)
      } match {
        case (totals, cat, sub) =>
          (totals, cat.distinct.sorted, sub.distinct.sorted)
      }

    val colors   = Colors.pickColors(subaccounts).toList
    val datasets = subaccounts.zip(colors)
      .map { case (sub, color) =>
        val series = categories.map(c => totals.getOrElse((c, sub), 0d))
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
