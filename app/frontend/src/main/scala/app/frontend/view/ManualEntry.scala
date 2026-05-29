package app.frontend.view

import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

/** Add a journal entry by hand. Placeholder for now — shows an info panel only. */
object ManualEntry:

  def view: ReactiveHtmlElement[HTMLDivElement] =
    Panel("Manual Entry", "Add a journal entry by hand — coming soon.")
