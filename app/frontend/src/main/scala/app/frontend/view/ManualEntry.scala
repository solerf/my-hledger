package app.frontend.view

import app.frontend.AppState
import app.shared.dtos.NewTransaction

import scala.util.Try

import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement

/** Add journal entries by hand. The form drafts one entry at a time; "Add"
  * appends it to the pending table, "Save" posts the whole table to the backend,
  * which groups entries by date into one hledger transaction per day (each entry
  * contributing a to/from posting pair, in input order).
  */
object ManualEntry:

  import scala.concurrent.ExecutionContext.Implicits.global

  private val DefaultCurrency = "EUR"

  // Field keys used to flag invalid inputs with a red border.
  private val DateKey     = "date"
  private val FromKey     = "from"
  private val ToKey       = "to"
  private val AmountKey   = "amount"
  private val CurrencyKey = "currency"

  // A draft form is just empty strings; the amount is parsed on add.
  private case class Draft(
    date: String = "",
    from: String = "",
    to: String = "",
    amount: String = "",
    currency: String = DefaultCurrency,
    description: String = "",
    comment: String = ""
  )

  def view(state: AppState): ReactiveHtmlElement[HTMLDivElement] = {
    val draft     = Var(Draft())
    val pending   = Var(List.empty[NewTransaction])
    val accounts  = Var(List.empty[String])
    val feedback  = Var(Option.empty[(Boolean, String)]) // save result only
    val attempted = Var(false)                           // has Add been pressed?

    // Account names power the autocomplete datalist for the from/to fields.
    state.api.fetchAccounts().foreach {
      case Right(names) => accounts.set(names)
      case Left(_)      => () // autocomplete is best-effort
    }

    // Invalid fields are derived from the draft, so a red border clears as soon
    // as the user fixes the value — but only after the first Add attempt.
    val invalidFields: Signal[Set[String]] =
      draft.signal.combineWith(attempted.signal).map {
        case (d, true)  => invalidKeys(d)
        case (_, false) => Set.empty
      }

    val addEntry = Observer[Unit] { _ =>
      val d = draft.now()
      if (invalidKeys(d).isEmpty) {
        pending.update(_ :+ toTransaction(d))
        // Keep date and currency so a run of similar entries is quick to add.
        draft.set(Draft(date = d.date, currency = d.currency))
        attempted.set(false)
        feedback.set(None)
      } else
        // Don't show a message — just light up the offending field borders.
        attempted.set(true)
    }

    val save = Observer[Unit] { _ =>
      val txns = pending.now()
      if (txns.nonEmpty)
        state.api.addTransactions(txns).foreach {
          case Right(_) =>
            pending.set(Nil)
            feedback.set(Some((
              false,
              s"Saved ${txns.size} entr${plural(txns.size)} to the journal."
            )))
            // Refetch the journal
            state.refreshExpenses()
          case Left(err) =>
            feedback.set(Some((true, s"Save failed: $err")))
        }
    }

    div(
      accountsDatalist(accounts.signal),
      entryForm(draft, invalidFields, addEntry),
      child.maybe <-- feedback.signal.map(_.map(feedbackBanner)),
      pendingTable(pending),
      saveBar(pending.signal, save)
    )
  }

  /** Mandatory fields only — description and comment are optional. */
  private def invalidKeys(d: Draft): Set[String] = {
    val amountValid = Try(BigDecimal(normalizeAmount(d.amount))).toOption.exists(_ != 0)
    Set(
      Option.when(d.date.isEmpty)(DateKey),
      Option.when(d.from.trim.isEmpty)(FromKey),
      Option.when(d.to.trim.isEmpty)(ToKey),
      Option.when(d.currency.trim.isEmpty)(CurrencyKey),
      Option.when(!amountValid)(AmountKey)
    ).flatten
  }

  private def toTransaction(d: Draft): NewTransaction =
    NewTransaction(
      date = d.date,
      from = d.from.trim,
      to = d.to.trim,
      amount = BigDecimal(normalizeAmount(d.amount)),
      currency = d.currency.trim.toUpperCase,
      description = d.description.trim.toUpperCase,
      comment = d.comment.trim.toUpperCase
    )

  private def normalizeAmount(raw: String): String = raw.trim.replace(',', '.')

  private val accountsListId = "accounts-list"

  private def accountsDatalist(accounts: Signal[List[String]]): HtmlElement =
    dataList(
      idAttr := accountsListId,
      children <-- accounts.map(_.map(a => option(value := a)))
    )

  private def entryForm(
    draft: Var[Draft],
    invalidFields: Signal[Set[String]],
    addEntry: Observer[Unit]
  ): HtmlElement =
    form(
      cls := "mb-3",
      onSubmit.preventDefault.mapTo(()) --> addEntry,
      // One grid row; `g-3` adds both horizontal and vertical gutters so a
      // label never butts up against the input above it. Widths sum to 12 per
      // md row, then wrap: Date/From/To/Amount/Currency, then Description/Comment.
      div(
        cls := "row g-3 align-items-end",
        field(
          "Date",
          col = "col-12 col-md-3",
          input(
            tpe := "date",
            cls := "form-control",
            cls("is-invalid") <-- invalidFields.map(_.contains(DateKey)),
            controlled(
              value <-- draft.signal.map(_.date),
              onInput.mapToValue --> draft.updater[String]((d, v) => d.copy(date = v))
            )
          )
        ),
        accountField(
          "From",
          col = "col-12 col-md-3",
          invalid = invalidFields.map(_.contains(FromKey)),
          draft.signal.map(_.from),
          draft.updater[String]((d, v) => d.copy(from = v))
        ),
        accountField(
          "To",
          col = "col-12 col-md-3",
          invalid = invalidFields.map(_.contains(ToKey)),
          draft.signal.map(_.to),
          draft.updater[String]((d, v) => d.copy(to = v))
        ),
        field(
          "Amount",
          col = "col-6 col-md-2",
          input(
            tpe         := "number",
            stepAttr    := "0.01",
            placeholder := "0.00",
            cls         := "form-control text-end",
            cls("is-invalid") <-- invalidFields.map(_.contains(AmountKey)),
            controlled(
              value <-- draft.signal.map(_.amount),
              onInput.mapToValue --> draft.updater[String]((d, v) => d.copy(amount = v))
            )
          )
        ),
        field(
          "Currency",
          col = "col-6 col-md-1",
          input(
            tpe         := "text",
            cls         := "form-control text-uppercase",
            placeholder := DefaultCurrency,
            cls("is-invalid") <-- invalidFields.map(_.contains(CurrencyKey)),
            controlled(
              value <-- draft.signal.map(_.currency),
              onInput.mapToValue --> draft.updater[String]((d, v) => d.copy(currency = v))
            )
          )
        ),
        field(
          "Description",
          col = "col-12 col-md-6",
          input(
            tpe := "text",
            cls := "form-control",
            controlled(
              value <-- draft.signal.map(_.description),
              onInput.mapToValue --> draft.updater[String]((d, v) => d.copy(description = v))
            )
          )
        ),
        field(
          "Comment",
          col = "col-12 col-md-6",
          input(
            tpe := "text",
            cls := "form-control",
            controlled(
              value <-- draft.signal.map(_.comment),
              onInput.mapToValue --> draft.updater[String]((d, v) => d.copy(comment = v))
            )
          )
        )
      ),
      div(
        cls := "d-flex justify-content-end mt-3",
        button(tpe := "submit", cls := "btn btn-outline-primary", "Add")
      )
    )

  private def field(labelText: String, col: String, control: HtmlElement): HtmlElement =
    div(cls := col, label(cls := "form-label", labelText), control)

  private def accountField(
    labelText: String,
    col: String,
    invalid: Signal[Boolean],
    value0: Signal[String],
    writer: Observer[String]
  ): HtmlElement =
    field(
      labelText,
      col = col,
      input(
        tpe         := "text",
        cls         := "form-control",
        listId      := accountsListId,
        placeholder := "account:sub",
        cls("is-invalid") <-- invalid,
        controlled(
          value <-- value0,
          onInput.mapToValue --> writer
        )
      )
    )

  private def feedbackBanner(state: (Boolean, String)): HtmlElement =
    val (isError, msg) = state
    div(
      cls  := s"alert ${if (isError) "alert-danger" else "alert-success"} py-2",
      role := "alert",
      msg
    )

  private def pendingTable(pending: Var[List[NewTransaction]]): HtmlElement =
    div(
      cls := "table-responsive entries-scroll",
      table(
        cls := "table table-striped table-sm align-middle w-100",
        thead(
          cls := "table-light",
          tr(
            th("Date"),
            th("From"),
            th("To"),
            th(cls := "text-end", "Amount"),
            th("Currency"),
            th("Description"),
            th("Comment"),
            th()
          )
        ),
        tbody(
          children <-- pending.signal.map { txns =>
            if (txns.isEmpty)
              List(
                tr(td(colSpan := 8, cls := "text-center fst-italic text-muted", "No entries yet."))
              )
            else
              txns.zipWithIndex.map { case (tx, i) => pendingRow(tx, i, pending) }
          }
        )
      )
    )

  private def pendingRow(
    tx: NewTransaction,
    index: Int,
    pending: Var[List[NewTransaction]]
  ): HtmlElement =
    tr(
      td(tx.date),
      td(tx.from),
      td(tx.to),
      td(
        cls := "text-end amount-num",
        tx.amount.setScale(2, BigDecimal.RoundingMode.HALF_UP).toString
      ),
      td(tx.currency),
      td(tx.description),
      td(tx.comment),
      td(
        cls := "text-end",
        button(
          tpe := "button",
          cls := "btn btn-sm btn-outline-danger",
          "Remove",
          onClick.mapTo(()) --> Observer[Unit](_ => pending.update(_.patch(index, Nil, 1)))
        )
      )
    )

  private def saveBar(pending: Signal[List[NewTransaction]], save: Observer[Unit]): HtmlElement =
    div(
      cls := "d-flex justify-content-end mt-2",
      button(
        tpe := "button",
        cls := "btn btn-primary",
        disabled <-- pending.map(_.isEmpty),
        child.text <-- pending.map(t => s"Save ${t.size} entr${plural(t.size)}"),
        onClick.mapTo(()) --> save
      )
    )

  private def plural(n: Int): String = if (n == 1) "y" else "ies"
