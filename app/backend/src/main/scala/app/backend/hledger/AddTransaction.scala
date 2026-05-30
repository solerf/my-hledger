package app.backend.hledger

import app.shared.dtos.NewTransaction

import io.circe.Encoder

/** JSON-encodable mirror of hledger's `Transaction` type, shaped to match what
  * hledger-web's `PUT /add` endpoint accepts (verified against hledger 1.33).
  *
  * Only the encoding direction is needed here — reads use [[model]] instead.
  * `Option` fields encode to `null`, which the endpoint requires for the
  * optional slots (`acost`, `pbalanceassertion`, `asdigitgroups`, …).
  */
object add:

  final case class Quantity(
    decimalMantissa: BigInt,
    decimalPlaces: Int,
    floatingPoint: Double
  ) derives Encoder.AsObject

  final case class AmountStyle(
    ascommodityside: String,
    ascommodityspaced: Boolean,
    asdecimalmark: String,
    asdigitgroups: Option[String],
    asprecision: Int,
    asrounding: String
  ) derives Encoder.AsObject

  final case class Amount(
    acommodity: String,
    acost: Option[String],
    aquantity: Quantity,
    astyle: AmountStyle
  ) derives Encoder.AsObject

  final case class SourcePos(
    sourceColumn: Int,
    sourceLine: Int,
    sourceName: String
  ) derives Encoder.AsObject

  final case class Posting(
    paccount: String,
    pamount: List[Amount],
    pbalanceassertion: Option[String],
    pcomment: String,
    pdate: Option[String],
    pdate2: Option[String],
    poriginal: Option[String],
    pstatus: String,
    ptags: List[String],
    ptransaction_ : String,
    ptype: String
  ) derives Encoder.AsObject

  final case class Transaction(
    tcode: String,
    tcomment: String,
    tdate: String,
    tdate2: Option[String],
    tdescription: String,
    tindex: Int,
    tpostings: List[Posting],
    tprecedingcomment: String,
    tsourcepos: List[SourcePos],
    tstatus: String,
    ttags: List[String]
  ) derives Encoder.AsObject

  /** Build add payloads from drafted entries, grouped into one transaction per
    * date (date order, then entry order preserved). Each entry contributes two
    * postings — its `to` account positive, its `from` account negative — so the
    * transaction balances, with postings kept in input order.
    *
    * The transaction description is the entries' descriptions joined (`cafe,
    * metro`). Each entry's `id:` dedup tag lives on its `to` posting's comment,
    * following the same `id:%date %amount %description` rule as the CLI import
    * (see data/rules/translated.rules). Space-separated because hledger collapses
    * adjacent `%a-%b` references (keeping only the last). Any free-text comment
    * the user typed is appended after the tag, comma-separated so it doesn't
    * bleed into the id value.
    */
  def fromNewTransactions(transactions: List[NewTransaction]): List[Transaction] =
    // distinct keeps first-occurrence date order; filter keeps entry order.
    transactions.map(_.date).distinct.map { date =>
      groupedTransaction(date, transactions.filter(_.date == date))
    }

  private def groupedTransaction(date: String, entries: List[NewTransaction]): Transaction = {
    val description =
      entries.map(_.description.trim).filter(_.nonEmpty).distinct.mkString(", ")

    val postings = entries.flatMap { e =>
      val idTag       = s"id:${e.date} ${amountTag(e.amount)} ${e.description.trim}"
      val userComment = e.comment.trim
      val toComment   = if (userComment.isEmpty) idTag else s"$idTag, $userComment"
      List(
        posting(e.to.trim, e.amount, e.currency, toComment),
        posting(e.from.trim, -e.amount, e.currency, "")
      )
    }

    Transaction(
      tcode = "",
      tcomment = "",
      tdate = date,
      tdate2 = None,
      tdescription = description,
      tindex = 0,
      tpostings = postings,
      tprecedingcomment = "",
      // The transaction's source span (start, end) — two entries regardless of
      // posting count, not one per posting.
      tsourcepos = List(SourcePos(1, 1, ""), SourcePos(1, 1, "")),
      tstatus = "Unmarked",
      ttags = Nil
    )
  }

  /** The amount as it appears in the import CSV's `%amount` field: the positive
    * value with a "." decimal mark and no exponent.
    */
  private def amountTag(amt: BigDecimal): String = amt.bigDecimal.toPlainString

  private def posting(
    account: String,
    amt: BigDecimal,
    currency: String,
    comment: String
  ): Posting =
    Posting(
      paccount = account,
      pamount = List(amount(amt, currency)),
      pbalanceassertion = None,
      pcomment = comment,
      pdate = None,
      pdate2 = None,
      poriginal = None,
      pstatus = "Unmarked",
      ptags = Nil,
      ptransaction_ = "",
      ptype = "RegularPosting"
    )

  private def amount(amt: BigDecimal, currency: String): Amount = {
    val normalized = amt.bigDecimal.stripTrailingZeros
    val places     = math.max(normalized.scale, 0)
    val fixed      = normalized.setScale(places)
    Amount(
      acommodity = currency,
      acost = None,
      aquantity = Quantity(BigInt(fixed.unscaledValue), places, amt.toDouble),
      astyle = AmountStyle(
        // 3-letter codes render after the amount with a space, e.g. "42.50 EUR".
        ascommodityside = "R",
        ascommodityspaced = true,
        asdecimalmark = ".",
        asdigitgroups = None,
        asprecision = places,
        asrounding = "NoRounding"
      )
    )
  }
