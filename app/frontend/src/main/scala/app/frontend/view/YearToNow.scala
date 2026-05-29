package app.frontend.view

import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

/** Year-to-date overview. Placeholder for now — shows an info panel only. */
object YearToNow:

  def view: ReactiveHtmlElement[HTMLDivElement] =
    Panel("Year To Now", "Year-to-date overview is coming soon.")
