package app.frontend.view

import app.frontend.{AppState, NavView}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

/** Top-level page chrome. Renders the navbar and swaps in the view selected by
  * the nav menu. Each view owns its own loading/error/content states.
  */
object Root:

  def render(
    state: AppState,
    monthChangeObserver: Observer[String]
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      navbar(state),
      child <-- state.navView.distinct.map {
        case NavView.Monthly     => Monthly.view(state, monthChangeObserver)
        case NavView.YearToNow   => YearToNow.view
        case NavView.ManualEntry => ManualEntry.view
      }
    )

  private def navbar(state: AppState): HtmlElement =
    navTag(
      cls := "navbar navbar-expand-md navbar-dark bg-dark mb-4 rounded",
      div(
        cls := "container-fluid",
        span(cls := "navbar-brand mb-0 h1", "my-hledger"),
        button(
          tpe                   := "button",
          cls                   := "navbar-toggler",
          dataAttr("bs-toggle") := "collapse",
          dataAttr("bs-target") := "#nav-menu",
          aria.controls         := "nav-menu",
          aria.expanded         := false,
          aria.label            := "Toggle navigation",
          span(cls := "navbar-toggler-icon")
        ),
        div(
          cls    := "collapse navbar-collapse",
          idAttr := "nav-menu",
          ul(
            cls := "navbar-nav ms-auto",
            NavView.values.toList.map(navItem(state, _))
          )
        )
      )
    )

  private def navItem(state: AppState, view: NavView): HtmlElement =
    li(
      cls := "nav-item",
      a(
        href := "#",
        cls  := "nav-link",
        cls("active") <-- state.navView.map(_ == view),
        onClick.preventDefault.mapTo(view) --> state.navViewWriter,
        view.label
      )
    )
