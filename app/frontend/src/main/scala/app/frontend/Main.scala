package app.frontend

import app.frontend.charts.ChartTheme
import cats.syntax.all.catsSyntaxEither
import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    ChartTheme.install()
    val api       = new ExpensesApi
    val state     = AppState(api)
    val container = dom.document.getElementById("app")
    val _         = render(container, Views.app(state))

    init(state, api)
    registerMonthChange(state, api)
  }

  private def init(state: AppState, api: ExpensesApi): Unit =
    api.fetchExpenses()
      .foreach(
        _.bimap(
          err => dom.console.error(s"failed: $err"),
          state.updateExpensesRows
        )
      )

  private def registerMonthChange(state: AppState, api: ExpensesApi): Unit =
    state
      .selectedMonthSignal
      .changes
      .foreach {
        // Refetch whenever the user changes the selected month. The initial
        // population done previously already sets rows for the latest month, so
        // we drop the leading default emission via `changes`.
        m =>
          api.fetchExpensesBy(m)
            .foreach(
              _.bimap(
                err => dom.console.error(s"failed: $err"),
                state.updateExpensesRows
              )
            )
      }(using unsafeWindowOwner): Unit
