package lib.querysyntax

import org.parboiled2._

class QuerySyntax(val input: ParserInput) extends Parser {
  def Query = rule { Expression ~ EOI }

  def Expression = rule { zeroOrMore(Term) separatedBy Whitespace }

  def Term = rule { NegatedFilter | Filter }

  def NegatedFilter = rule { '-' ~ Filter ~> Negation }


  def Filter = rule { ScopedMatch ~> Match | HashMatch | AnyMatch }

  def ScopedMatch = rule { MatchField ~ ':' ~ MatchValue }
  def HashMatch = rule { '#' ~ MatchValue ~> (label => Match(SingleField("labels"), label)) }

  def MatchField = rule { capture(AllowedFieldName) ~> resolveNamedField _ }

  def AllowedFieldName = rule {
    "location" | "city" | "state" | "country" | "in" |
    "byline" | "by" | "photographer" |
    "credit" |
    "copyright" |
    "source" |
    "keyword" |
    "label"
  }

  def resolveNamedField(name: String): Field = name match {
    case "in"                  => MultipleField(List("location", "city", "state", "country"))
    case "by" | "photographer" => SingleField("byline")
    case "location"            => SingleField("subLocation")
    case "label"               => SingleField("labels")
    case "keyword"             => SingleField("keywords")
    case fieldName             => SingleField(fieldName)
  }


  def AnyMatch = rule { MatchValue ~> (v => Match(AnyField, v)) }


  def MatchValue = rule { QuotedString ~> Phrase | String ~> Words }

  def String = rule { capture(Chars) }

  // Quoted strings
  def SingleQuote = "'"
  def DoubleQuote = "\""
  def QuotedString = rule { SingleQuote ~ capture(NotSingleQuote) ~ SingleQuote |
                            DoubleQuote ~ capture(NotDoubleQuote) ~ DoubleQuote }
  // TODO: unless escaped?
  def NotSingleQuote = rule { oneOrMore(noneOf(SingleQuote)) }
  def NotDoubleQuote = rule { oneOrMore(noneOf(DoubleQuote)) }

  def Whitespace = rule { oneOrMore(' ') }
  // any character except quotes
  def Chars      = rule { oneOrMore(CharPredicate.Visible -- '"' -- '\'') }
}

// TODO:
// - uploaded: as alias for uploadedBy top-level field
// - date uploaded (exact, range, expression (@today?))
// - date taken (~)
// - is archived, has exports, has picdarUrn

// new QuerySyntax("hello world by:me").Query.run()
// hello world
// hello -world
// hello by:foo
// "hello world" foo
// ?  -"not this"
// by:"foo bar"
// -by:foo