package com.nexthink.utils.parsing.combinator.completion

import org.json4s.JValue

import scala.language.{higherKinds, implicitConversions}
import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.Positional

// scalastyle:off method.name

/** `CompletionSupport` adds completion capability to parsers.
  *
  *  When mixed-in, this trait extends
  *  the [[scala.util.parsing.combinator.Parsers.Parser]] type with the abstract method
  *  [[com.nexthink.utils.parsing.combinator.completion.CompletionSupport.Parser#completions]]
  *  which returns a instance of [[com.nexthink.utils.parsing.combinator.completion.CompletionTypes.Completions]]
  *  for a certain input.
  *
  *  Combinators are overloaded to cover the additional completion aspect, so that no change is required in the grammar.
  *
  *  Note that the derived trait [[com.nexthink.utils.parsing.combinator.completion.RegexCompletionSupport]] can be mixed-in
  *  with `RegexParsers` to automatically obtain completion behavior for string literals.
  *
  *  A set of additional operators allow defining completions and specifying structural properties of completions
  *  (tag, score, meta, etc.) for a `Parser`.
  *
  *  @author Jonas Chapuis
  */
trait CompletionSupport extends Parsers with CompletionTypes {
  def Parser[T](f: Input => ParseResult[T], c: Input => Completions): Parser[T] = new Parser[T] {
    def apply(in: Input)       = f(in)
    def completions(in: Input) = c(in)
  }

  trait CombinableParser[+T, P[+R] <: CombinableParser[R, P]] {

    /** An operator to specify completions of a parser
      *
      * @param completions possible completions for this parser
      * @return a `Parser` that upon invocation of the `completions` method returns the passed completions
      */
    def %>(completions: Elems*): P[_]

    /** An operator to specify completion of a parser
      *
      * @param completion completion for this parser
      * @return a `Parser` that upon invocation of the `completions` method returns the passed completion
      */
    def %>(completion: Completion): P[T]

    /** An operator to specify completions of a parser
      *
      * @param completions possible completions for this parser
      * @return a `Parser` that upon invocation of the `completions` method returns the passed completions
      */
    def %>(completions: CompletionSet): P[T]

    /** Limits completions to the top `n` completions ordered by their score
      *
      * @param n the limit
      * @return wrapper `Parser` instance limiting the number of completions
      */
    def topCompletions(n: Int): P[T]

    /** An operator to specify the completion tag of a parser (empty tag by default)
      *
      * @param tag the completion tag (to be used e.g. to structure a completion menu)
      * @return wrapper `Parser` instance specifying the completion tag
      */
    def %(tag: String): P[T]

    /** An operator to specify the completions tag score of a parser (0 by default)
      *
      * @param tagScore the completion tag score (to be used e.g. to order sections in a completion menu)
      * @return wrapper `Parser` instance specifying the completion tag score
      */
    def %(tagScore: Int): P[T]

    /** An operator to specify the completion tag and score of a parser
      *
      * @param tag      the completion tag
      * @param tagScore the completion tag score
      * @return wrapper `Parser` instance specifying the completion tag
      */
    def %(tag: String, tagScore: Int): P[T]

    /** An operator to specify the completion tag, score and description of a parser
      *
      * @param tag            the completion tag
      * @param tagScore       the completion tag score
      * @param tagDescription the completion tag description
      * @return wrapper `Parser` instance specifying completion tag
      */
    def %(tag: String, tagScore: Int, tagDescription: String): P[T]

    /** An operator to specify the completion tag, score, description and meta of a parser
      *
      * @param tag            the completion tag
      * @param tagScore       the completion tag score
      * @param tagDescription the completion tag description
      * @param tagMeta        the completion tag meta
      * @return wrapper `Parser` instance specifying completion tag
      */
    def %(tag: String, tagScore: Int, tagDescription: String, tagMeta: JValue): P[T]

    /** An operator to specify the completion tag
      *
      * @param tag the completion tag
      * @return wrapper `Parser` instance specifying completion tag
      */
    def %(tag: CompletionTag): P[T]

    /** An operator to specify the completion tag description of a parser (empty by default)
      *
      * @param tagDescription the completion description (to be used e.g. to add information to a completion entry)
      * @return wrapper `Parser` instance specifying the completion description
      */
    def %?(tagDescription: String): P[T]

    /** An operator to specify the completion tag meta-data of a parser in JSON format (empty by default).
      * JSON meta-data is automatically merged when combining two equivalent tags (i.e. bearing the same label, but with a different payload).
      * This allows for more flexibility when defining the grammar: various parsers can return the same completion tags with
      * an additive effect on the meta-data (and the entries).
      *
      * @param tagMeta the JValue for completion tag meta-data (to be used e.g. to specify the visual style for a completion tag in the menu)
      * @return wrapper `Parser` instance specifying the completion tag meta-data
      */
    def %%(tagMeta: JValue): P[T]

    /** An operator to specify the meta-data for completions of a parser (empty by default).
      * Note that meta-data is merged with comma separations when combining two equivalent entries.
      *
      * @param meta the completion meta-data (to be used e.g. to specify the visual style for a completion entry in the menu)
      * @return wrapper `Parser` instance specifying the completion meta-data
      */
    def %-%(meta: JValue): P[T]

    /**
      * An operator to specify the meta-data for the whole set of completions (empty by default)
      * Note that if the meta-data is encoded in JSON, it is automatically merged when combining multiple completion sets.
      * This allows for more flexibility when defining the grammar: various parsers can define the global completion meta-data
      * with an additive effect.
      *
      * @param globalMeta the JValue for completions meta-data (to be used e.g. to specify the visual style for the completion menu)
      * @return wrapper `Parser` instance specifying the completions meta-data
      */
    def %%%(globalMeta: JValue): P[T]

    def flatMap[U](f: T => P[U]): P[U]

    def map[U](f: T => U): P[U]

    def map[U](f: T => U, fc: Completions => Completions): P[U]

    def mapCompletions(fc: Completions => Completions): P[T]

    def filter(p: T => Boolean): P[T]

    def withFilter(p: T => Boolean): P[T]

    /** A parser combinator for sequential composition.
      *
      * `p ~ q` succeeds if `p` succeeds and `q` succeeds on the input left over by `p`.
      *
      * @param q a parser that will be executed after `p` (this parser)
      *          succeeds -- evaluated at most once, and only when necessary.
      * @return a `Parser` that -- on success -- returns a `~` (like a `Pair`,
      *         but easier to pattern match on) that contains the result of `p` and
      *         that of `q`. The resulting parser fails if either `p` or `q` fails.
      */
    def ~[U](q: => P[U]): P[~[T, U]]

    /** A parser combinator for sequential composition which keeps only the right result.
      *
      * `p ~> q` succeeds if `p` succeeds and `q` succeeds on the input left over by `p`.
      *
      * @param q a parser that will be executed after `p` (this parser)
      *          succeeds -- evaluated at most once, and only when necessary.
      * @return a `Parser` that -- on success -- returns the result of `q`.
      */
    def ~>[U](q: => P[U]): P[U]

    /** A parser combinator for sequential composition which keeps only the left result.
      *
      * `p <~ q` succeeds if `p` succeeds and `q` succeeds on the input
      * left over by `p`.
      *
      * @note <~ has lower operator precedence than ~ or ~>.
      * @param q a parser that will be executed after `p` (this parser) succeeds -- evaluated at most once, and only when necessary
      * @return a `Parser` that -- on success -- returns the result of `p`.
      */
    def <~[U](q: => P[U]): P[T]

    /** A parser combinator for non-back-tracking sequential composition.
      *
      * `p ~! q` succeeds if `p` succeeds and `q` succeeds on the input left over by `p`.
      * In case of failure, no back-tracking is performed (in an earlier parser produced by the `|` combinator).
      *
      * @param q a parser that will be executed after `p` (this parser) succeeds
      * @return a `Parser` that -- on success -- returns a `~` (like a Pair, but easier to pattern match on)
      *         that contains the result of `p` and that of `q`.
      *         The resulting parser fails if either `p` or `q` fails, this failure is fatal.
      */
    def ~![U](q: => P[U]): P[~[T, U]]

    /** A parser combinator for non-back-tracking sequential composition which only keeps the right result.
      *
      * `p ~>! q` succeeds if `p` succeds and `q` succeds on the input left over by `p`.
      * In case of failure, no back-tracking is performed (in an earlier parser produced by the `|` combinator).
      *
      * @param q a parser that will be executed after `p` (this parser) succeeds -- evaluated at most once, and only when necessary
      * @return a `Parser` that -- on success -- reutrns the result of `q`.
      *         The resulting parser fails if either `p` or `q` fails, this failure is fatal.
      */
    def ~>![U](q: => P[U]): P[U]

    /** A parser combinator for non-back-tracking sequential composition which only keeps the left result.
      *
      * `p <~! q` succeeds if `p` succeds and `q` succeds on the input left over by `p`.
      * In case of failure, no back-tracking is performed (in an earlier parser produced by the `|` combinator).
      *
      * @param q a parser that will be executed after `p` (this parser) succeeds -- evaluated at most once, and only when necessary
      * @return a `Parser` that -- on success -- reutrns the result of `p`.
      *         The resulting parser fails if either `p` or `q` fails, this failure is fatal.
      */
    def <~![U](q: => P[U]): P[T]

    /** A parser combinator for alternative composition.
      *
      * `p | q` succeeds if `p` succeeds or `q` succeeds.
      * Note that `q` is only tried if `p`s failure is non-fatal (i.e., back-tracking is allowed).
      *
      * @param q a parser that will be executed if `p` (this parser) fails (and allows back-tracking)
      * @return a `Parser` that returns the result of the first parser to succeed (out of `p` and `q`)
      *         The resulting parser succeeds if (and only if)
      *         - `p` succeeds, ''or''
      *         - if `p` fails allowing back-tracking and `q` succeeds.
      */
    def |[U >: T](q: => P[U]): P[U]

    /** A parser combinator for alternative with longest match composition.
      *
      * `p ||| q` succeeds if `p` succeeds or `q` succeeds.
      * If `p` and `q` both succeed, the parser that consumed the most characters accepts.
      *
      * @param q a parser that accepts if p consumes less characters. -- evaluated at most once, and only when necessary
      * @return a `Parser` that returns the result of the parser consuming the most characters (out of `p` and `q`).
      */
    def |||[U >: T](q: => P[U]): P[U]

    /** A parser combinator for function application.
      *
      * `p ^^ f` succeeds if `p` succeeds; it returns `f` applied to the result of `p`.
      *
      * @param f a function that will be applied to this parser's result (see `map` in `ParseResult`).
      * @return a parser that has the same behaviour as the current parser, but whose result is
      *         transformed by `f`.
      */
    def ^^[U](f: T => U): P[U]

    /** A parser combinator that changes a successful result into the specified value.
      *
      * `p ^^^ v` succeeds if `p` succeeds; discards its result, and returns `v` instead.
      *
      * @param v The new result for the parser, evaluated at most once (if `p` succeeds), not evaluated at all if `p` fails.
      * @return a parser that has the same behaviour as the current parser, but whose successful result is `v`
      */
    def ^^^[U](v: => U): P[U]

    /** A parser combinator for partial function application.
      *
      * `p ^? (f, error)` succeeds if `p` succeeds AND `f` is defined at the result of `p`;
      * in that case, it returns `f` applied to the result of `p`. If `f` is not applicable,
      * error(the result of `p`) should explain why.
      *
      * @param f     a partial function that will be applied to this parser's result
      *              (see `mapPartial` in `ParseResult`).
      * @param error a function that takes the same argument as `f` and produces an error message
      *              to explain why `f` wasn't applicable
      * @return a parser that succeeds if the current parser succeeds <i>and</i> `f` is applicable
      *         to the result. If so, the result will be transformed by `f`.
      */
    def ^?[U](f: PartialFunction[T, U], error: T => String): P[U]

    /** A parser combinator for partial function application.
      *
      * `p ^? f` succeeds if `p` succeeds AND `f` is defined at the result of `p`;
      * in that case, it returns `f` applied to the result of `p`.
      *
      * @param f a partial function that will be applied to this parser's result
      *          (see `mapPartial` in `ParseResult`).
      * @return a parser that succeeds if the current parser succeeds <i>and</i> `f` is applicable
      *         to the result. If so, the result will be transformed by `f`.
      */
    def ^?[U](f: PartialFunction[T, U]): P[U]

    /** A parser combinator that parameterizes a subsequent parser with the
      * result of this one.
      *
      * Use this combinator when a parser depends on the result of a previous
      *  parser. `p` should be a function that takes the result from the first
      * parser and returns the second parser.
      *
      * `p into fq` (with `fq` typically `{x => q}`) first applies `p`, and
      * then, if `p` successfully returned result `r`, applies `fq(r)` to the
      * rest of the input.
      *
      * ''From: G. Hutton. Higher-order functions for parsing. J. Funct. Program., 2(3):323--343, 1992.''
      *
      * @example {{{
      *            def perlRE = "m" ~> (".".r into (separator => """[^%s]*""".format(separator).r <~ separator))
      *          }}}
      *  @param fq a function that, given the result from this parser, returns
      *         the second parser to be applied
      *  @return a parser that succeeds if this parser succeeds (with result `x`)
      *          and if then `fq(x)` succeeds
      */
    def into[U](fq: T => P[U]): P[U]

    /** Returns `into(fq)`. */
    def >>[U](fq: T => P[U]): P[U]

    /** Returns a parser that repeatedly parses what this parser parses.
      *
      *  @return rep(this)
      */
    def *(): P[List[T]]

    /** Returns a parser that repeatedly parses what this parser parses,
      * interleaved with the `sep` parser. The `sep` parser specifies how
      * the results parsed by this parser should be combined.
      *
      * @return chainl1(this, sep)
      */
    def *[U >: T](sep: => P[(U, U) => U]): P[U]

    /** Returns a parser that repeatedly (at least once) parses what this parser parses.
      *
      * @return rep1(this)
      */
    def +(): P[List[T]]

    /** Returns a parser that optionally parses what this parser parses.
      *
      * @return opt(this)
      */
    def ?(): P[Option[T]]

    /** Changes the failure message produced by a parser.
      *
      * This doesn't change the behavior of a parser on neither
      * success nor error, just on failure. The semantics are
      * slightly different than those obtained by doing `| failure(msg)`,
      * in that the message produced by this method will always
      * replace the message produced, which is not guaranteed
      * by that idiom.
      *
      * For example, parser `p` below will always produce the
      * designated failure message, while `q` will not produce
      * it if `sign` is parsed but `number` is not.
      *
      * {{{
      *
    def p = sign.? ~ number withFailureMessage  "Number expected!"
      *  def q = sign.? ~ number | failure("Number expected!")
      *  }}}
      *
      *  @param msg The message that will replace the default failure message.
      * @return
      *A parser with the same properties and different failure message.
      */
    def withFailureMessage(msg: String): P[T]

    /** Changes the failure message produced by a parser.
      *
      * This doesn't change the behavior of a parser on neither
      * success nor error, just on failure. The semantics are
      *  slightly different than those obtained by doing `
    | failure(msg)`,
      * in that the message produced by this method will always
      *
      *replace the message produced, which is not guaranteed
      *  by that idiom.
      *
      *  For example, parser `p
    ` below will always produce the
      * designated failure message, while `q` will not produce
      *
      *it if
    `sign`
     is parsed but `number` is not.
      *
      *  {{{
      *   def p = sign.? ~ number withFailureMessage  "Number expected!"
      *   def q = sign.? ~ number | failure("Number expected!")
      *  }}}
      *
      * @param msg The message that will replace the default failure message.
      *@return A parser with the same properties and different failure message.
      */
    def withErrorMessage(msg: String): P[T]
  }

  /** The root class of completion parsers, overloading the `Parser` class.
    * Completion parsers are functions from the Input type to ParseResult, with the
    * addition of a `completions` function from the Input type to an instance of `Completions`
    */
  abstract class Parser[+T] extends super.Parser[T] with CombinableParser[T, Parser] {

    def append[U >: T](p0: => Parser[U]): Parser[U] = {
      lazy val p = p0
      Parser(
        in => super.append(p)(in),
        in => {
          val thisCompletions          = this.completions(in)
          lazy val combinedCompletions = thisCompletions | p.completions(in)
          this(in) match {
            case Success(_, rest) =>
              // only return any completions if they start at the last position, otherwise it can behave badly e.g. with fuzzy matching
              if (combinedCompletions.position < rest.pos) Completions.empty
              else combinedCompletions
            case Failure(_, _) => combinedCompletions
            case Error(_, _) =>
              thisCompletions // avoids backtracking completions in the case of error, e.g. when using the ~! operator
          }
        }
      )
    }

    /** An unspecified method that defines the possible completions for this parser
      *
      * @param in the input
      * @return an instance of [[com.nexthink.utils.parsing.combinator.completion.CompletionTypes.Completions]]
      */
    def completions(in: Input): Completions

    /** An operator to specify completions of a parser
      * @param completions possible completions for this parser
      * @return a `Parser` that upon invocation of the `completions` method returns the passed completions
      */
    override def %>(completions: Elems*): Parser[T] =
      %>(CompletionSet(completions.map(el => Completion(el))))

    /** An operator to specify completion of a parser
      * @param completion completion for this parser
      * @return a `Parser` that upon invocation of the `completions` method returns the passed completion
      */
    override def %>(completion: Completion): Parser[T] = %>(CompletionSet(completion))

    /** An operator to specify completions of a parser
      * @param completions possible completions for this parser
      * @return a `Parser` that upon invocation of the `completions` method returns the passed completions
      */
    override def %>(completions: CompletionSet): Parser[T] =
      Parser(this,
             in =>
               this(in) match {
                 case Failure(_, rest) if rest.atEnd =>
                   Completions(rest.pos, completions)
                 case _ => Completions.empty
             })

    /** An operator to specify completions of a parser
      * @param completioner function of input to completions
      * @return a `Parser` that upon invocation of the `completions` method will invoke the passed function
      */
    def %>(completioner: Input => Completions): Parser[T] =
      Parser(this, completioner)

    /** Limits completions to the top `n` completions ordered by their score
      * @param n the limit
      * @return wrapper `Parser` instance limiting the number of completions
      */
    override def topCompletions(n: Int): Parser[T] =
      Parser(
        this,
        in => this.completions(in).takeTop(n)
      )

    /** An operator to specify the completion tag of a parser (empty tag by default)
      * @param tag the completion tag (to be used e.g. to structure a completion menu)
      * @return wrapper `Parser` instance specifying the completion tag
      */
    override def %(tag: String): Parser[T] =
      Parser(this, in => updateCompletionsTag(this.completions(in), Some(tag)))

    /** An operator to specify the completions tag score of a parser (0 by default)
      * @param tagScore the completion tag score (to be used e.g. to order sections in a completion menu)
      * @return wrapper `Parser` instance specifying the completion tag score
      */
    override def %(tagScore: Int): Parser[T] =
      Parser(this, in => updateCompletionsTag(this.completions(in), None, Some(tagScore)))

    /** An operator to specify the completion tag and score of a parser
      * @param tag the completion tag
      * @param tagScore the completion tag score
      * @return wrapper `Parser` instance specifying the completion tag
      */
    override def %(tag: String, tagScore: Int): Parser[T] =
      Parser(this, in => updateCompletionsTag(this.completions(in), Some(tag), Some(tagScore)))

    /** An operator to specify the completion tag, score and description of a parser
      * @param tag the completion tag
      * @param tagScore the completion tag score
      * @param tagDescription the completion tag description
      * @return wrapper `Parser` instance specifying completion tag
      */
    override def %(tag: String, tagScore: Int, tagDescription: String): Parser[T] =
      Parser(this, in => updateCompletionsTag(this.completions(in), Some(tag), Some(tagScore), Some(tagDescription)))

    /** An operator to specify the completion tag, score, description and meta of a parser
      * @param tag the completion tag
      * @param tagScore the completion tag score
      * @param tagDescription the completion tag description
      * @param tagMeta the completion tag meta
      * @return wrapper `Parser` instance specifying completion tag
      */
    override def %(tag: String, tagScore: Int, tagDescription: String, tagMeta: JValue): Parser[T] =
      Parser(this, in => updateCompletionsTag(this.completions(in), Some(tag), Some(tagScore), Some(tagDescription), Some(tagMeta)))

    /** An operator to specify the completion tag
      * @param tag the completion tag
      * @return wrapper `Parser` instance specifying completion tag
      */
    override def %(tag: CompletionTag): Parser[T] =
      Parser(this, in => updateCompletionsTag(this.completions(in), Some(tag.label), Some(tag.score), tag.description, tag.meta))

    /** An operator to specify the completion tag description of a parser (empty by default)
      * @param tagDescription the completion description (to be used e.g. to add information to a completion entry)
      * @return wrapper `Parser` instance specifying the completion description
      */
    override def %?(tagDescription: String): Parser[T] =
      Parser(this, in => updateCompletionsTag(this.completions(in), None, None, Some(tagDescription)))

    /** An operator to specify the completion tag meta-data of a parser in JSON format (empty by default).
      * JSON meta-data is automatically merged when combining two equivalent tags (i.e. bearing the same label, but with a different payload).
      * This allows for more flexibility when defining the grammar: various parsers can return the same completion tags with
      * an additive effect on the meta-data (and the entries).
      * @param tagMeta the JValue for completion tag meta-data (to be used e.g. to specify the visual style for a completion tag in the menu)
      * @return wrapper `Parser` instance specifying the completion tag meta-data
      */
    override def %%(tagMeta: JValue): Parser[T] =
      Parser(this, in => this.completions(in).map(set => CompletionSet(set.tag.withMeta(tagMeta), set.completions)))

    /** An operator to specify the meta-data for completions of a parser (empty by default).
      * Note that meta-data is merged with comma separations when combining two equivalent entries.
      * @param meta the completion meta-data (to be used e.g. to specify the visual style for a completion entry in the menu)
      * @return wrapper `Parser` instance specifying the completion meta-data
      */
    override def %-%(meta: JValue): Parser[T] =
      Parser(this, in => this.completions(in).map(set => CompletionSet(set.tag, set.entries.map(e => e.withMeta(meta)))))

    /**
      * An operator to specify the meta-data for the whole set of completions (empty by default)
      * Note that if the meta-data is encoded in JSON, it is automatically merged when combining multiple completion sets.
      * This allows for more flexibility when defining the grammar: various parsers can define the global completion meta-data
      * with an additive effect.
      * @param globalMeta the JValue for completions meta-data (to be used e.g. to specify the visual style for the completion menu)
      * @return wrapper `Parser` instance specifying the completions meta-data
      */
    override def %%%(globalMeta: JValue): Parser[T] =
      Parser(this, in => this.completions(in).withMeta(globalMeta))

    override def flatMap[U](f: T => Parser[U]): Parser[U] =
      Parser(super[Parser].flatMap(f), completions)

    override def map[U](f: T => U): Parser[U] =
      Parser(super[Parser].map(f), completions)

    override def map[U](f: T => U, fc: Completions => Completions): Parser[U] =
      map(f).mapCompletions(fc)

    override def mapCompletions(fc: Completions => Completions): Parser[T] =
      Parser(this, in => fc(this.completions(in)))

    override def filter(p: T => Boolean): Parser[T] = withFilter(p)

    override def withFilter(p: T => Boolean): Parser[T] =
      Parser(super[Parser].withFilter(p), completions)

    private def seqCompletions[U](in: Input, other: => Parser[U]): Completions = {
      lazy val thisCompletions = this.completions(in)
      this(in) match {
        case Success(_, rest) =>
          thisCompletions | other.completions(rest)
        case NoSuccess(_, _) =>
          thisCompletions
      }
    }

    /** A parser combinator for sequential composition.
      *
      * `p ~ q` succeeds if `p` succeeds and `q` succeeds on the input left over by `p`.
      *
      * @param q a parser that will be executed after `p` (this parser)
      *          succeeds -- evaluated at most once, and only when necessary.
      * @return a `Parser` that -- on success -- returns a `~` (like a `Pair`,
      *         but easier to pattern match on) that contains the result of `p` and
      *         that of `q`. The resulting parser fails if either `p` or `q` fails.
      */
    override def ~[U](q: => Parser[U]): Parser[~[T, U]] = {
      lazy val p = q
      Parser(super[Parser].~(q), in => seqCompletions(in, p))
    }.named("~")

    /** A parser combinator for sequential composition which keeps only the right result.
      *
      * `p ~> q` succeeds if `p` succeeds and `q` succeeds on the input left over by `p`.
      *
      * @param q a parser that will be executed after `p` (this parser)
      *        succeeds -- evaluated at most once, and only when necessary.
      * @return a `Parser` that -- on success -- returns the result of `q`.
      */
    override def ~>[U](q: => Parser[U]): Parser[U] = {
      lazy val p = q
      Parser(super[Parser].~>(q), in => seqCompletions(in, p))
    }.named("~>")

    /** A parser combinator for sequential composition which keeps only the left result.
      *
      *  `p <~ q` succeeds if `p` succeeds and `q` succeeds on the input
      *           left over by `p`.
      *
      * @note <~ has lower operator precedence than ~ or ~>.
      *
      * @param q a parser that will be executed after `p` (this parser) succeeds -- evaluated at most once, and only when necessary
      * @return a `Parser` that -- on success -- returns the result of `p`.
      */
    override def <~[U](q: => Parser[U]): Parser[T] = {
      lazy val p = q
      Parser(super[Parser].<~(q), in => seqCompletions(in, p))
    }.named("<~")

    /** A parser combinator for non-back-tracking sequential composition.
      *
      *  `p ~! q` succeeds if `p` succeeds and `q` succeeds on the input left over by `p`.
      *   In case of failure, no back-tracking is performed (in an earlier parser produced by the `|` combinator).
      *
      * @param q a parser that will be executed after `p` (this parser) succeeds
      * @return a `Parser` that -- on success -- returns a `~` (like a Pair, but easier to pattern match on)
      *         that contains the result of `p` and that of `q`.
      *         The resulting parser fails if either `p` or `q` fails, this failure is fatal.
      */
    override def ~![U](q: => Parser[U]): Parser[~[T, U]] = {
      lazy val p = q
      Parser(super[Parser].~!(q), in => seqCompletions(in, p))
    }.named("~!")

    /** A parser combinator for non-back-tracking sequential composition which only keeps the right result.
      *
      * `p ~>! q` succeeds if `p` succeds and `q` succeds on the input left over by `p`.
      * In case of failure, no back-tracking is performed (in an earlier parser produced by the `|` combinator).
      *
      * @param q a parser that will be executed after `p` (this parser) succeeds -- evaluated at most once, and only when necessary
      * @return a `Parser` that -- on success -- reutrns the result of `q`.
      *         The resulting parser fails if either `p` or `q` fails, this failure is fatal.
      */
    override def ~>![U](q: => Parser[U]): Parser[U] = {
      lazy val p            = q
      val parser: Parser[U] = for (a <- this; b <- commit(p)) yield b
      Parser(OnceParser(parser.apply), in => seqCompletions(in, p))
    }.named("~>!")

    /** A parser combinator for non-back-tracking sequential composition which only keeps the left result.
      *
      * `p <~! q` succeeds if `p` succeds and `q` succeds on the input left over by `p`.
      * In case of failure, no back-tracking is performed (in an earlier parser produced by the `|` combinator).
      *
      * @param q a parser that will be executed after `p` (this parser) succeeds -- evaluated at most once, and only when necessary
      * @return a `Parser` that -- on success -- reutrns the result of `p`.
      *         The resulting parser fails if either `p` or `q` fails, this failure is fatal.
      */
    override def <~![U](q: => Parser[U]): Parser[T] = {
      lazy val p            = q
      val parser: Parser[T] = for (a <- this; b <- commit(p)) yield a
      Parser(OnceParser(parser.apply), in => seqCompletions(in, p))
    }.named("<~!")

    /** A parser combinator for alternative composition.
      *
      *  `p | q` succeeds if `p` succeeds or `q` succeeds.
      *   Note that `q` is only tried if `p`s failure is non-fatal (i.e., back-tracking is allowed).
      *
      * @param q a parser that will be executed if `p` (this parser) fails (and allows back-tracking)
      * @return a `Parser` that returns the result of the first parser to succeed (out of `p` and `q`)
      *         The resulting parser succeeds if (and only if)
      *         - `p` succeeds, ''or''
      *         - if `p` fails allowing back-tracking and `q` succeeds.
      */
    override def |[U >: T](q: => Parser[U]): Parser[U] =
      append(q).named("|")

    /** A parser combinator for alternative with longest match composition.
      *
      *  `p ||| q` succeeds if `p` succeeds or `q` succeeds.
      *  If `p` and `q` both succeed, the parser that consumed the most characters accepts.
      *
      * @param q a parser that accepts if p consumes less characters. -- evaluated at most once, and only when necessary
      * @return a `Parser` that returns the result of the parser consuming the most characters (out of `p` and `q`).
      */
    override def |||[U >: T](q: => Parser[U]): Parser[U] = {
      lazy val p = q
      Parser(super[Parser].|||(q), in => this.completions(in) | p.completions(in))
    }

    /** A parser combinator for function application.
      *
      *  `p ^^ f` succeeds if `p` succeeds; it returns `f` applied to the result of `p`.
      *
      * @param f a function that will be applied to this parser's result (see `map` in `ParseResult`).
      * @return a parser that has the same behaviour as the current parser, but whose result is
      *         transformed by `f`.
      */
    override def ^^[U](f: T => U): Parser[U] =
      Parser(super[Parser].^^(f), completions).named(toString + "^^")

    /** A parser combinator that changes a successful result into the specified value.
      *
      *  `p ^^^ v` succeeds if `p` succeeds; discards its result, and returns `v` instead.
      *
      * @param v The new result for the parser, evaluated at most once (if `p` succeeds), not evaluated at all if `p` fails.
      * @return a parser that has the same behaviour as the current parser, but whose successful result is `v`
      */
    override def ^^^[U](v: => U): Parser[U] = {
      Parser(super[Parser].^^^(v), completions)
    }.named(toString + "^^^")

    /** A parser combinator for partial function application.
      *
      *  `p ^? (f, error)` succeeds if `p` succeeds AND `f` is defined at the result of `p`;
      *  in that case, it returns `f` applied to the result of `p`. If `f` is not applicable,
      *  error(the result of `p`) should explain why.
      *
      * @param f a partial function that will be applied to this parser's result
      *          (see `mapPartial` in `ParseResult`).
      * @param error a function that takes the same argument as `f` and produces an error message
      *        to explain why `f` wasn't applicable
      * @return a parser that succeeds if the current parser succeeds <i>and</i> `f` is applicable
      *         to the result. If so, the result will be transformed by `f`.
      */
    override def ^?[U](f: PartialFunction[T, U], error: T => String): Parser[U] =
      Parser(super[Parser].^?(f, error), completions).named(toString + "^?")

    /** A parser combinator for partial function application.
      *
      *  `p ^? f` succeeds if `p` succeeds AND `f` is defined at the result of `p`;
      *  in that case, it returns `f` applied to the result of `p`.
      *
      * @param f a partial function that will be applied to this parser's result
      *          (see `mapPartial` in `ParseResult`).
      * @return a parser that succeeds if the current parser succeeds <i>and</i> `f` is applicable
      *         to the result. If so, the result will be transformed by `f`.
      */
    override def ^?[U](f: PartialFunction[T, U]): Parser[U] =
      Parser(super[Parser].^?(f), completions)

    /** A parser combinator that parameterizes a subsequent parser with the
      *  result of this one.
      *
      *  Use this combinator when a parser depends on the result of a previous
      *  parser. `p` should be a function that takes the result from the first
      *  parser and returns the second parser.
      *
      *  `p into fq` (with `fq` typically `{x => q}`) first applies `p`, and
      *  then, if `p` successfully returned result `r`, applies `fq(r)` to the
      *  rest of the input.
      *
      *  ''From: G. Hutton. Higher-order functions for parsing. J. Funct. Program., 2(3):323--343, 1992.''
      *
      *  @example {{{
      *  def perlRE = "m" ~> (".".r into (separator => """[^%s]*""".format(separator).r <~ separator))
      *  }}}
      *
      *  @param fq a function that, given the result from this parser, returns
      *         the second parser to be applied
      *  @return a parser that succeeds if this parser succeeds (with result `x`)
      *          and if then `fq(x)` succeeds
      */
    override def into[U](fq: T => Parser[U]): Parser[U] =
      Parser(super[Parser].into(fq), in => {
        this(in) match {
          case Success(result, next) => fq(result).completions(next)
          case _: NoSuccess          => this.completions(in)
        }
      })

    /** Returns `into(fq)`. */
    override def >>[U](fq: T => Parser[U]) = into(fq)

    /** Returns a parser that repeatedly parses what this parser parses.
      *
      *  @return rep(this)
      */
    override def * = rep(this)

    /** Returns a parser that repeatedly parses what this parser parses,
      *  interleaved with the `sep` parser. The `sep` parser specifies how
      *  the results parsed by this parser should be combined.
      *
      *  @return chainl1(this, sep)
      */
    override def *[U >: T](sep: => Parser[(U, U) => U]) = chainl1(this, sep)

    /** Returns a parser that repeatedly (at least once) parses what this parser parses.
      *
      *  @return rep1(this)
      */
    override def + = rep1(this)

    /** Returns a parser that optionally parses what this parser parses.
      *
      *  @return opt(this)
      */
    override def ? = opt(this)

    /** Changes the failure message produced by a parser.
      *
      *  This doesn't change the behavior of a parser on neither
      *  success nor error, just on failure. The semantics are
      *  slightly different than those obtained by doing `| failure(msg)`,
      *  in that the message produced by this method will always
      *  replace the message produced, which is not guaranteed
      *  by that idiom.
      *
      *  For example, parser `p` below will always produce the
      *  designated failure message, while `q` will not produce
      *  it if `sign` is parsed but `number` is not.
      *
      *  {{{
      *  def p = sign.? ~ number withFailureMessage  "Number expected!"
      *  def q = sign.? ~ number | failure("Number expected!")
      *  }}}
      *
      *  @param msg The message that will replace the default failure message.
      *  @return    A parser with the same properties and different failure message.
      */
    override def withFailureMessage(msg: String) =
      Parser(super[Parser].withFailureMessage(msg), completions)

    /** Changes the failure message produced by a parser.
      *
      *  This doesn't change the behavior of a parser on neither
      *  success nor error, just on failure. The semantics are
      *  slightly different than those obtained by doing `| failure(msg)`,
      *  in that the message produced by this method will always
      *  replace the message produced, which is not guaranteed
      *  by that idiom.
      *
      *  For example, parser `p` below will always produce the
      *  designated failure message, while `q` will not produce
      *  it if `sign` is parsed but `number` is not.
      *
      *  {{{
      *  def p = sign.? ~ number withFailureMessage  "Number expected!"
      *  def q = sign.? ~ number | failure("Number expected!")
      *  }}}
      *
      *  @param msg The message that will replace the default failure message.
      *  @return    A parser with the same properties and different failure message.
      */
    override def withErrorMessage(msg: String): Parser[T] =
      Parser(super[Parser].withErrorMessage(msg), completions)

  }

  protected def updateCompletionsTag(completions: Completions,
                                     newTagLabel: Option[String] = None,
                                     newTagScore: Option[Int] = None,
                                     newTagDescription: Option[String] = None,
                                     newTagMeta: Option[JValue] = None) = {
    completions.map(existingSet => CompletionSet(existingSet.tag.update(newTagLabel, newTagScore, newTagDescription, newTagMeta), existingSet.completions))
  }

  private def updateCompletionsMeta(completions: Completions, newMeta: JValue) = {
    completions.map(set => set.map(e => e.withMeta(newMeta)))
  }

  /** Wrap a parser so that its failures become errors (the `|` combinator
    *  will give up as soon as it encounters an error, on failure it simply
    *  tries the next alternative).
    */
  def commit[T](p: => Parser[T]): Parser[T] =
    Parser(super.commit(p), p.completions)

  /** A parser matching input elements that satisfy a given predicate.
    *
    *  `elem(kind, p)` succeeds if the input starts with an element `e` for which `p(e)` is true.
    *
    *  @param  kind   The element kind, used for error messages
    *  @param  p      A predicate that determines which elements match.
    *  @param  completions Possible alternatives (for completion)
    *  @return
    */
  def elem(kind: String, p: Elem => Boolean, completions: Set[Elem] = Set()): Parser[Elem] =
    acceptIf(p, completions)(inEl => kind + " expected")

  /** A parser that matches only the given element `e`.
    *
    *  `elem(e)` succeeds if the input starts with an element `e`.
    *
    *  @param e the `Elem` that must be the next piece of input for the returned parser to succeed
    *  @return a `Parser` that succeeds if `e` is the next available input (and returns it).
    */
  override def elem(e: Elem): Parser[Elem] = accept(e)

  /** A parser that matches only the given element `e`.
    *
    *  The method is implicit so that elements can automatically be lifted to their parsers.
    *  For example, when parsing `Token`s, `Identifier("new")` (which is a `Token`) can be used directly,
    *  instead of first creating a `Parser` using `accept(Identifier("new"))`.
    *
    *  @param e the `Elem` that must be the next piece of input for the returned parser to succeed
    *  @return a `tParser` that succeeds if `e` is the next available input.
    */
  override implicit def accept(e: Elem): Parser[Elem] =
    acceptIf(_ == e, Set(e))("'" + e + "' expected but " + _ + " found")

  /** A parser that matches only the given list of element `es`.
    *
    * `accept(es)` succeeds if the input subsequently provides the elements in the list `es`.
    *
    * @param  es the list of expected elements
    * @return a Parser that recognizes a specified list of elements
    */
  override def accept[ES <% List[Elem]](es: ES): Parser[List[Elem]] =
    acceptSeq(es)

  /** The parser that matches an element in the domain of the partial function `f`.
    *
    *  If `f` is defined on the first element in the input, `f` is applied
    *  to it to produce this parser's result.
    *
    *  Example: The parser `accept("name", {case Identifier(n) => Name(n)})`
    *          accepts an `Identifier(n)` and returns a `Name(n)`
    *
    *  @param expected a description of the kind of element this parser expects (for error messages)
    *  @param f a partial function that determines when this parser is successful and what its output is
    *  @param completions Possible alternatives (for completion)
    *  @return A parser that succeeds if `f` is applicable to the first element of the input,
    *          applying `f` to it to produce the result.
    */
  def accept[U](expected: String, f: PartialFunction[Elem, U], completions: Set[Elem] = Set()): Parser[U] =
    acceptMatch(expected, f, completions.map(Completion(_)))

  /** A parser matching input elements that satisfy a given predicate.
    *
    *  `acceptIf(p)(el => "Unexpected "+el)` succeeds if the input starts with an element `e` for which `p(e)` is true.
    *
    *  @param  err    A function from the received element into an error message.
    *  @param  p      A predicate that determines which elements match.
    *  @param completions Possible completions
    *  @return        A parser for elements satisfying p(e).
    */
  def acceptIf(p: Elem => Boolean, completions: Set[Elem])(err: Elem => String): Parser[Elem] = {
    lazy val completionSet =
      if (completions.isEmpty)
        None
      else
        Some(CompletionSet(completions.map(c => Completion(c))))
    Parser(
      super.acceptIf(p)(err),
      in =>
        completionSet match {
          case None => Completions.empty
          case Some(c) =>
            super.acceptIf(p)(err)(in) match {
              case Success(_, _) => Completions.empty
              case _             => Completions(in.pos, c)
            }
      }
    )
  }

  def acceptMatch[U](expected: String, f: PartialFunction[Elem, U], completions: Set[Completion]): Parser[U] = {
    lazy val completionSet =
      if (completions.nonEmpty)
        Some(CompletionSet(CompletionTag(expected), completions))
      else None
    Parser(
      super.acceptMatch(expected, f),
      in =>
        completionSet match {
          case None => Completions.empty
          case Some(c) =>
            super.acceptMatch(expected, f)(in) match {
              case Success(_, _) => Completions.empty
              case _             => Completions(in.pos, c)
            }
      }
    ).named(expected)
  }

  /** A parser that matches only the given [[scala.collection.Iterable]] collection of elements `es`.
    *
    * `acceptSeq(es)` succeeds if the input subsequently provides the elements in the iterable `es`.
    *
    * @param  es the list of expected elements
    * @return a Parser that recognizes a specified list of elements
    */
  override def acceptSeq[ES <% Iterable[Elem]](es: ES): Parser[List[Elem]] =
    Parser(super.acceptSeq(es),
           in =>
             es.tail
               .foldLeft(accept(es.head))((a, b) => a ~> accept(b))
               .completions(in))

  /** A parser that always fails.
    *
    * @param msg The error message describing the failure.
    * @return A parser that always fails with the specified error message.
    */
  override def failure(msg: String): Parser[Nothing] =
    Parser(super.failure(msg), _ => Completions.empty)

  /** A parser that always succeeds.
    *
    * @param v The result for the parser
    * @return A parser that always succeeds, with the given result `v`
    */
  override def success[T](v: T): Parser[T] =
    Parser(super.success(v), _ => Completions.empty)

  /** A parser that results in an error.
    *
    * @param msg The error message describing the failure.
    * @return A parser that always fails with the specified error message.
    */
  override def err(msg: String): Parser[Nothing] =
    Parser(super.err(msg), _ => Completions.empty)

  /** A helper method that turns a `Parser` into one that will
    * print debugging information to stdout before and after
    * being applied.
    */
  def log[T](p: => Parser[T])(name: String): Parser[T] =
    Parser(
      in => {
        println(s"trying $name at \n${inputPositionDebugString(in)}")
        val r = p(in)
        println(s"$name --> $r")
        r
      },
      in => {
        println(s"completing $name at \n${inputPositionDebugString(in)}")
        val r = p.completions(in)
        println(s"$name --> $r")
        r
      }
    )

  /** A parser generator for repetitions.
    *
    * `rep(p)` repeatedly uses `p` to parse the input until `p` fails
    * (the result is a List of the consecutive results of `p`).
    *
    * @param p a `Parser` that is to be applied successively to the input
    * @return A parser that returns a list of results produced by repeatedly applying `p` to the input.
    */
  def rep[T](p: => Parser[T]): Parser[List[T]] =
    rep1(p) | success(List())

  /** A parser generator for interleaved repetitions.
    *
    *  `repsep(p, q)` repeatedly uses `p` interleaved with `q` to parse the input, until `p` fails.
    *  (The result is a `List` of the results of `p`.)
    *
    *  Example: `repsep(term, ",")` parses a comma-separated list of term's, yielding a list of these terms.
    *
    * @param p a `Parser` that is to be applied successively to the input
    * @param q a `Parser` that parses the elements that separate the elements parsed by `p`
    * @return A parser that returns a list of results produced by repeatedly applying `p` (interleaved with `q`) to the input.
    *         The results of `p` are collected in a list. The results of `q` are discarded.
    */
  def repsep[T](p: => Parser[T], q: => Parser[Any]): Parser[List[T]] =
    rep1sep(p, q) | success(List())

  /** A parser generator for non-empty repetitions.
    *
    *  `rep1(p)` repeatedly uses `p` to parse the input until `p` fails -- `p` must succeed at least
    *             once (the result is a `List` of the consecutive results of `p`)
    *
    * @param p a `Parser` that is to be applied successively to the input
    * @return A parser that returns a list of results produced by repeatedly applying `p` to the input
    *        (and that only succeeds if `p` matches at least once).
    */
  def rep1[T](p: => Parser[T]): Parser[List[T]] =
    rep1(p, p)

  /** A parser generator for non-empty repetitions.
    *
    *  `rep1(f, p)` first uses `f` (which must succeed) and then repeatedly
    *     uses `p` to parse the input until `p` fails
    *     (the result is a `List` of the consecutive results of `f` and `p`)
    *
    * @param first a `Parser` that parses the first piece of input
    * @param p0 a `Parser` that is to be applied successively to the rest of the input (if any) -- evaluated at most once, and only when necessary
    * @return A parser that returns a list of results produced by first applying `f` and then
    *         repeatedly `p` to the input (it only succeeds if `f` matches).
    */
  def rep1[T](first: => Parser[T], p0: => Parser[T]): Parser[List[T]] = {
    lazy val p = p0 // lazy argument
    Parser(
      super.rep1(first, p0),
      in => {
        def continue(in: Input): Completions = {
          val currentCompletions = p.completions(in)
          p(in) match {
            case Success(_, rest) => currentCompletions | continue(rest)
            case NoSuccess(_, _)  => currentCompletions
          }
        }
        val firstCompletions = first.completions(in)
        first(in) match {
          case Success(_, rest) => firstCompletions | continue(rest)
          case NoSuccess(_, _)  => firstCompletions
        }
      }
    )
  }

  /** A parser generator for a specified number of repetitions.
    *
    *  `repN(n, p)` uses `p` exactly `n` time to parse the input
    *  (the result is a `List` of the `n` consecutive results of `p`).
    *
    * @param p0   a `Parser` that is to be applied successively to the input
    * @param num the exact number of times `p` must succeed
    * @return    A parser that returns a list of results produced by repeatedly applying `p` to the input
    *        (and that only succeeds if `p` matches exactly `n` times).
    */
  def repN[T](num: Int, p0: => Parser[T]): Parser[List[T]] = {
    lazy val p = p0 // lazy argument
    if (num == 0) { success(Nil) } else {
      Parser(
        super.repN(num, p0),
        in => {
          var parsedCount = 0
          def completions(in0: Input): Completions =
            if (parsedCount == num) {
              Completions.empty
            } else {
              val currentCompletions = p.completions(in0)
              p(in0) match {
                case Success(_, rest) => parsedCount += 1; currentCompletions | completions(rest)
                case ns: NoSuccess    => currentCompletions
              }
            }

          val result = completions(in)
          if (parsedCount < num) result else Completions.empty
        }
      )
    }
  }

  /** A parser generator for non-empty repetitions.
    *
    *  `rep1sep(p, q)` repeatedly applies `p` interleaved with `q` to parse the
    *  input, until `p` fails. The parser `p` must succeed at least once.
    *
    * @param p a `Parser` that is to be applied successively to the input
    * @param q a `Parser` that parses the elements that separate the elements parsed by `p`
    *          (interleaved with `q`)
    * @return A parser that returns a list of results produced by repeatedly applying `p` to the input
    *         (and that only succeeds if `p` matches at least once).
    *         The results of `p` are collected in a list. The results of `q` are discarded.
    */
  def rep1sep[T](p: => Parser[T], q: => Parser[Any]): Parser[List[T]] =
    p ~ rep(q ~> p) ^^ { case x ~ y => x :: y }

  /** A parser generator that, roughly, generalises the rep1sep generator so
    *  that `q`, which parses the separator, produces a left-associative
    *  function that combines the elements it separates.
    *
    *  ''From: J. Fokker. Functional parsers. In J. Jeuring and E. Meijer, editors, Advanced Functional Programming,
    *  volume 925 of Lecture Notes in Computer Science, pages 1--23. Springer, 1995.''
    *
    * @param p a parser that parses the elements
    * @param q a parser that parses the token(s) separating the elements, yielding a left-associative function that
    *          combines two elements into one
    */
  def chainl1[T](p: => Parser[T], q: => Parser[(T, T) => T]): Parser[T] =
    chainl1(p, p, q)

  /** A parser generator that, roughly, generalises the `rep1sep` generator
    *  so that `q`, which parses the separator, produces a left-associative
    *  function that combines the elements it separates.
    *
    * @param first a parser that parses the first element
    * @param p a parser that parses the subsequent elements
    * @param q a parser that parses the token(s) separating the elements,
    *          yielding a left-associative function that combines two elements
    *          into one
    */
  def chainl1[T, U](first: => Parser[T], p: => Parser[U], q: => Parser[(T, U) => T]): Parser[T] =
    first ~ rep(q ~ p) ^^ {
      case x ~ xs =>
        xs.foldLeft(x: T) { case (a, f ~ b) => f(a, b) } // x's type annotation is needed to deal with changed type inference due to SI-5189
    }

  /** A parser generator that generalises the `rep1sep` generator so that `q`,
    *  which parses the separator, produces a right-associative function that
    *  combines the elements it separates. Additionally, the right-most (last)
    *  element and the left-most combining function have to be supplied.
    *
    * rep1sep(p: Parser[T], q) corresponds to chainr1(p, q ^^ cons, cons, Nil) (where val cons = (x: T, y: List[T]) => x :: y)
    *
    * @param p a parser that parses the elements
    * @param q a parser that parses the token(s) separating the elements, yielding a right-associative function that
    *          combines two elements into one
    * @param combine the "last" (left-most) combination function to be applied
    * @param first   the "first" (right-most) element to be combined
    */
  def chainr1[T, U](p: => Parser[T], q: => Parser[(T, U) => U], combine: (T, U) => U, first: U): Parser[U] =
    p ~ rep(q ~ p) ^^ {
      case x ~ xs =>
        (new ~(combine, x) :: xs).foldRight(first) { case (f ~ a, b) => f(a, b) }
    }

  /** A parser generator for optional sub-phrases.
    *
    *  `opt(p)` is a parser that returns `Some(x)` if `p` returns `x` and `None` if `p` fails.
    *
    * @param p A `Parser` that is tried on the input
    * @return a `Parser` that always succeeds: either with the result provided by `p` or
    *         with the empty result
    */
  def opt[T](p: => Parser[T]): Parser[Option[T]] =
    p ^^ (x => Some(x)) | success(None)

  /** Wrap a parser so that its failures and errors become success and
    *  vice versa -- it never consumes any input.
    */
  def not[T](p: => Parser[T]): Parser[Unit] =
    Parser(super.not(p), _ => Completions.empty)

  /** A parser generator for guard expressions. The resulting parser will
    *  fail or succeed just like the one given as parameter but it will not
    *  consume any input.
    *
    * @param p a `Parser` that is to be applied to the input
    * @return A parser that returns success if and only if `p` succeeds but
    *         never consumes any input
    */
  def guard[T](p: => Parser[T]): Parser[T] =
    Parser(super.guard(p), p.completions)

  /** `positioned` decorates a parser's result with the start position of the
    *  input it consumed.
    *
    * @param p a `Parser` whose result conforms to `Positional`.
    * @return A parser that has the same behaviour as `p`, but which marks its
    *         result with the start position of the input it consumed,
    *         if it didn't already have a position.
    */
  def positioned[T <: Positional](p: => Parser[T]): Parser[T] =
    Parser(super.positioned(p), p.completions)

  /** A parser generator delimiting whole phrases (i.e. programs).
    *
    *  `phrase(p)` succeeds if `p` succeeds and no input is left over after `p`.
    *
    *  @param p the parser that must consume all input for the resulting parser
    *           to succeed.
    *  @return  a parser that has the same result as `p`, but that only succeeds
    *           if `p` consumed all the input.
    */
  def phrase[T](p: Parser[T]): Parser[T] =
    Parser(super.phrase(p), p.completions)

  protected def inputPositionDebugString(input: Input): String = if (input.source.length() == 0) "" else input.pos.longString

}
