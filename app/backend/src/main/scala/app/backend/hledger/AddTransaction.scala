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

  /** Build the add payload from a drafted transaction: the `to` account is
    * posted positive, the `from` account negative, so the pair balances.
    */
  def fromNewTransaction(t: NewTransaction): Transaction =
    Transaction(
      tcode = "",
      tcomment = t.comment.trim,
      tdate = t.date,
      tdate2 = None,
      tdescription = t.description.trim,
      tindex = 0,
      tpostings = List(posting(t.to, t.amount, t.currency), posting(t.from, -t.amount, t.currency)),
      tprecedingcomment = "",
      tsourcepos = List(SourcePos(1, 1, ""), SourcePos(1, 1, "")),
      tstatus = "Unmarked",
      ttags = Nil
    )

  private def posting(account: String, amt: BigDecimal, currency: String): Posting =
    Posting(
      paccount = account,
      pamount = List(amount(amt, currency)),
      pbalanceassertion = None,
      pcomment = "",
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
