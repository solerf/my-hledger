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

}
