package app.frontend.view

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

/** A single full-width brutalist panel with a heading and a line of copy.
  * Used for loading, error, and "coming soon" placeholder states across views.
  */
object Panel:

  def apply(title: String, message: String): ReactiveHtmlElement[HTMLDivElement] =
    div(
      cls := "row",
      div(cls := "col-12", div(cls := "nb-panel", h2(title), p(cls := "mb-0", message)))
    )
