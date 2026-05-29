package app.frontend

import app.frontend.charts.Theme
import app.frontend.view.Root
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  import scala.concurrent.ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    Theme.install()
    val api   = new ExpensesApi
    val state = AppState(api)

    // Forward the raw result; AppState decides whether to render rows or
    // surface the error.
    api.fetchExpenses().foreach(state.updateExpensesRows)

    mountApp(state, api)
  }

  private def mountApp(state: AppState, api: ExpensesApi): Unit = {
    def monthObserver = Observer[String] { month =>
      api.fetchExpensesBy(month).foreach(state.updateExpensesRows)
    }

    val component = Root.render(state, monthObserver)
    val container = dom.document.getElementById("app")
    val _         = L.render(container, component)
  }
