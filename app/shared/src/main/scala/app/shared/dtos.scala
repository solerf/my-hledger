package app.shared

import io.circe.{Decoder, Encoder}

object dtos {

  final case class ExpenseEntry(
    date: String,
    account: String,
    amount: BigDecimal,
    currency: String,
    comment: String
  ) derives Encoder.AsObject, Decoder

  final case class MonthlyExpense(
    yearMonth: String,
    comment: String,
    description: String,
    entries: List[ExpenseEntry]
  ) derives Encoder.AsObject, Decoder

  /** A transaction drafted in the manual-entry form. `from` is the account the
    * money leaves (posted negative); `to` is the account that receives it
    * (posted positive).
    */
  final case class NewTransaction(
    date: String,
    from: String,
    to: String,
    amount: BigDecimal,
    currency: String,
    description: String,
    comment: String
  ) derives Encoder.AsObject, Decoder

}
