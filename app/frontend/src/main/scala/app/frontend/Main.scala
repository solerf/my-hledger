package app.frontend

import app.frontend.charts.Theme
import cats.syntax.all.catsSyntaxEither
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    Theme.install()
    val api   = new ExpensesApi
    val state = AppState(api)

    api
      .fetchExpenses()
      .foreach(
        _.bimap(
          err => dom.console.error(s"failed: $err"),
          state.updateExpensesRows
        )
      )

    val observerMonth = Observer[String] { month =>
      api.fetchExpensesBy(month)
        .foreach(
          _.bimap(
            err => dom.console.error(s"failed: $err"),
            state.updateExpensesRows
          )
        )
    }

    mountApp(state, observerMonth)
  }

  private def mountApp(state: AppState, monthObserver: Observer[String]): Unit = {
    val component = Views.render(state, monthObserver)
    val container = dom.document.getElementById("app")
    val _         = L.render(container, component)
  }
