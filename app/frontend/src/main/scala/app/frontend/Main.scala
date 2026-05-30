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

    // Gate startup on hledger-web reachability: if it's down the views are
    // replaced by an error panel and there's no point fetching expenses.
    api.checkHealth().foreach { reachable =>
      state.setHledgerReachable(reachable)
      if (reachable)
        // One fetch of the whole journal feeds both views; AppState decides how
        // to render each (monthly filters, year-to-now keeps every month).
        state.refreshExpenses()
    }

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
