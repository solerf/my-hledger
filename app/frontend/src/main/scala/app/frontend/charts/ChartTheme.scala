package app.frontend.charts

import scala.scalajs.js

/** Applies the neubrutalism look to Chart.js by mutating its global
  * defaults. CSS cannot reach inside a <canvas>, so axis labels,
  * legend chips and tooltips have to be themed via JS.
  *
  * Call once at app startup, before any chart is rendered.
  *
  * Notes on Chart.js v4 default paths used here:
  *   - `defaults.color` / `defaults.font` — base text styling.
  *   - `defaults.borderColor` — dataset border fallback.
  *   - `defaults.elements.{bar,arc,line,point}` — per-shape defaults.
  *   - `defaults.plugins.{legend,tooltip}` — plugin defaults.
  * Per-scale grid colours live under `defaults.scales[type]` and
  * are intentionally not touched here (would require iterating each
  * scale type and is brittle across Chart.js versions).
  */
object ChartTheme:

  private val Ink     = "#111111"
  private val FontFam = "\"Space Grotesk\", \"Inter\", system-ui, sans-serif"

  def install(): Unit = {
    val Chart = js.Dynamic.global.Chart
    if js.isUndefined(Chart) then return

    val d = Chart.defaults

    // Chart.js doesn't pre-create empty sub-objects (font, plugin
    // option groups, …), so we assign whole literals rather than
    // poking individual properties — otherwise we'd hit
    // `Cannot set properties of undefined`.

    d.color       = Ink
    d.borderColor = Ink
    d.font        = js.Dynamic.literal(
      family = FontFam,
      weight = "600",
      size = 13
    )

    // Per-shape defaults — thick ink outlines.
    d.elements.bar.borderColor   = Ink
    d.elements.bar.borderWidth   = 2
    d.elements.arc.borderColor   = Ink
    d.elements.arc.borderWidth   = 2
    d.elements.line.borderWidth  = 3
    d.elements.point.borderColor = Ink
    d.elements.point.borderWidth = 2
    d.elements.point.radius      = 4

    // Legend labels — chunky ink text.
    d.plugins.legend.labels.color = Ink
    d.plugins.legend.labels.font  = js.Dynamic.literal(
      family = FontFam,
      weight = "700",
      size = 12
    )

    // Tooltip — ink background, white text.
    d.plugins.tooltip.backgroundColor = Ink
    d.plugins.tooltip.titleColor      = "#ffffff"
    d.plugins.tooltip.bodyColor       = "#ffffff"
    d.plugins.tooltip.borderColor     = Ink
    d.plugins.tooltip.borderWidth     = 2
    d.plugins.tooltip.cornerRadius    = 4
  }
