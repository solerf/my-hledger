package app.frontend

import app.frontend.charts.ChartTheme
import app.shared.dtos.MonthlyExpense
import cats.syntax.all.catsSyntaxEither
import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    ChartTheme.install()
    val api   = new ExpensesApi
    val state = AppState(api)

    def updateView(exp: List[MonthlyExpense]): Unit = {
      state.updateExpensesRows(exp)
      val view      = Views.app(state)
      val container = dom.document.getElementById("app")
      val _         = render(container, view)
    }

    // init view
    api
      .fetchExpenses()
      .foreach(
        _.bimap(
          err => dom.console.error(s"failed: $err"),
          updateView
        )
      )

    registerMonthChange(state, api, updateView)
  }

  private def registerMonthChange(
    state: AppState,
    api: ExpensesApi,
    callback: List[MonthlyExpense] => Unit
  ): Unit =
    state
      .selectedMonthSignal
      .changes
      .foreach {
        m =>
          api.fetchExpensesBy(m)
            .foreach(
              _.bimap(
                err => dom.console.error(s"failed: $err"),
                callback
              )
            )
      }(using unsafeWindowOwner): Unit
