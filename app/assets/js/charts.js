// Renders the Chart.js charts for the server-rendered pages. The page embeds
// its chart payload as `window.__CHARTS__ = {pies: [...], lines: [...]}`;
// each entry carries the target canvas id and its labels/datasets.
(function () {
  'use strict';

  var Ink = '#111111';
  var FontFam = '"Space Grotesk", "Inter", system-ui, sans-serif';

  // Applies the neubrutalism look to Chart.js by mutating its global
  // defaults. CSS cannot reach inside a <canvas>, so axis labels, legend
  // chips and tooltips have to be themed via JS.
  function installTheme() {
    if (typeof Chart === 'undefined') return;
    var d = Chart.defaults;

    d.color = Ink;
    d.borderColor = Ink;
    d.font = { family: FontFam, weight: '600', size: 13 };

    // Per-shape defaults — thick ink outlines.
    d.elements.bar.borderColor = Ink;
    d.elements.bar.borderWidth = 2;
    d.elements.arc.borderColor = Ink;
    d.elements.arc.borderWidth = 2;
    d.elements.line.borderWidth = 3;
    d.elements.point.borderColor = Ink;
    d.elements.point.borderWidth = 2;
    d.elements.point.radius = 4;

    // Legend labels — chunky ink text.
    d.plugins.legend.labels.color = Ink;
    d.plugins.legend.labels.font = { family: FontFam, weight: '700', size: 12 };

    // Tooltip — ink background, white text.
    d.plugins.tooltip.backgroundColor = Ink;
    d.plugins.tooltip.titleColor = '#ffffff';
    d.plugins.tooltip.bodyColor = '#ffffff';
    d.plugins.tooltip.borderColor = Ink;
    d.plugins.tooltip.borderWidth = 2;
    d.plugins.tooltip.cornerRadius = 4;
  }

  // Random colour per label, kept stable across pages/reloads within the
  // browser session (the old SPA kept them stable for the page's lifetime).
  var STORE_KEY = 'chart-colors';
  var colors = (function () {
    try {
      return JSON.parse(sessionStorage.getItem(STORE_KEY)) || {};
    } catch (e) {
      return {};
    }
  })();

  function pickColor(label) {
    if (!colors[label]) {
      colors[label] = '#' + Math.floor(Math.random() * 0x1000000).toString(16).padStart(6, '0');
      try {
        sessionStorage.setItem(STORE_KEY, JSON.stringify(colors));
      } catch (e) {
        /* colours just won't persist */
      }
    }
    return colors[label];
  }

  function renderPie(pie) {
    var canvas = document.getElementById(pie.canvasId);
    if (!canvas) return;
    new Chart(canvas, {
      type: pie.type,
      data: {
        labels: pie.labels,
        datasets: [{
          data: pie.values,
          backgroundColor: pie.labels.map(pickColor)
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'left' } }
      }
    });
  }

  function renderLine(line) {
    var canvas = document.getElementById(line.canvasId);
    if (!canvas) return;
    new Chart(canvas, {
      type: 'line',
      data: {
        labels: line.labels,
        datasets: line.datasets.map(function (ds) {
          var color = ds.color || pickColor(ds.label);
          return {
            label: ds.label,
            data: ds.data,
            borderColor: color,
            backgroundColor: color,
            tension: 0,
            fill: false
          };
        })
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        elements: { point: { radius: 2, hoverRadius: 2 } },
        plugins: { legend: { position: 'top' } }
      }
    });
  }

  var data = window.__CHARTS__;
  if (!data || typeof Chart === 'undefined') return;
  installTheme();
  (data.pies || []).forEach(renderPie);
  (data.lines || []).forEach(renderLine);
})();
