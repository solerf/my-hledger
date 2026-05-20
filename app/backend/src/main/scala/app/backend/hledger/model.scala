package app.backend.hledger

import io.circe.{Decoder, Json}

object model:

  final case class Quantity(
    decimalMantissa: BigInt,
    decimalPlaces: Int,
    floatingPoint: Double
  ) derives Decoder

  final case class AmountStyle(
    ascommodityside: String,
    ascommodityspaced: Boolean,
    asdecimalmark: String,
    // asdigitgroups: Option[Json],
    asprecision: Int,
    asrounding: String
  ) derives Decoder

  final case class Amount(
    acommodity: String,
    // acost: Option[Json],
    aquantity: Quantity,
    astyle: AmountStyle
  ) derives Decoder

  final case class SourcePos(
    sourceColumn: Int,
    sourceLine: Int,
    sourceName: String
  ) derives Decoder

  final case class Posting(
    paccount: String,
    pamount: List[Amount],
    // pbalanceassertion: Option[Json],
    pcomment: String,
    pdate: Option[String],
    pdate2: Option[String],
    // poriginal: Option[Json],
    pstatus: String,
    ptags: List[Json],
    ptransaction_ : String,
    ptype: String
  ) derives Decoder

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
    tstatus: String
    // ttags: List[Json]
  ) derives Decoder {
    val tyearmonth: String = tdate.take(7) // YYYY-MM
  }
