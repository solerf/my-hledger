package app.frontend.view

import app.frontend.charts.yeartonow.MonthlyCumulativeChartView
import app.frontend.{AppState, Loadable}
import app.shared.dtos.MonthlyExpense

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

/** Year-to-date overview. For now: a single cumulative-by-account-type line
  * chart built from the full (unfiltered) journal feed.
  */
object YearToNow:

  def view(state: AppState): ReactiveHtmlElement[HTMLDivElement] =
    div(
      child <-- state.year.map {
        case Loadable.Loading      => Panel("Loading", "Loading journal…")
        case Loadable.Failed(msg)  => Panel("Journal not found", msg)
        case Loadable.Loaded(rows) => loadedView(rows)
      }
    )

  private def loadedView(rows: List[MonthlyExpense]): ReactiveHtmlElement[HTMLDivElement] =
    div(
      div(
        cls := "row",
        div(cls := "col-12", MonthlyCumulativeChartView.view(Val(rows)))
      )
    )
