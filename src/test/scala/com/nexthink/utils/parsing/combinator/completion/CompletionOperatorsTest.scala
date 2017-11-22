package com.nexthink.utils.parsing.combinator.completion
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.scalatest.{FlatSpec, Matchers}
import monix.execution.Scheduler.Implicits.global

import scala.util.parsing.combinator.Parsers

class CompletionOperatorsTest extends FlatSpec with Matchers with Parsers with AsyncRegexCompletionSupport {

  val completions: Seq[Seq[Char]] = Seq("a", "b", "c")
  val score                       = 10
  val description                 = "some description"
  val tag                         = "some tag"
  val meta: JValue                = ("some foo" -> "some bar")

  val someParser: Parser[String]           = "parser"
  val someAsyncParser: AsyncParser[String] = "parser"

  "Completions specified with builder" should "be correct" in {
    completionSpecifiedWithBuilderIsCorrect(someParser)((p: Parser[String], s: String) => complete(p, s))
    completionSpecifiedWithBuilderIsCorrect(someAsyncParser)((p: AsyncParser[String], s: String) => complete(p, s))
  }

  def completionSpecifiedWithBuilderIsCorrect[T, P[+R] <: CombinableParser[R, P]](parser: P[T])(complete: (P[T], String) => Completions): Unit = {
    def assertCompletionsMatch(parser: P[T]) = assertCompletionsMatchWith(parser, completions, Some(tag), Some(score), Some(description), Some(meta))
    def assertCompletionsMatchWith(parser: P[T],
                                   completions: Seq[Seq[Char]],
                                   tag: Option[String],
                                   score: Option[Int],
                                   description: Option[String],
                                   meta: Option[JValue]): Unit = {
      // Act
      val result = complete(parser, "")

      // Assert
      val completionSet: CompletionSet =
        tag.flatMap(n => result.setWithTag(n)).orElse(result.defaultSet).get
      completionSet.tag.label shouldBe tag.getOrElse("")
      completionSet.tag.score shouldBe score.getOrElse(0)
      completionSet.tag.description shouldBe description
      completionSet.tag.meta shouldBe meta
      completionSet.sortedEntries.map(_.value) shouldBe completions
    }
    val p = parser %> CompletionSet(CompletionTag(tag, meta), completions.map(Completion(_)))
    assertCompletionsMatch(p % (tag, score) %? description %% meta)
    assertCompletionsMatch(p % (tag, score, description))
    assertCompletionsMatch(p % (tag, score, description, meta))
    assertCompletionsMatch(p % CompletionTag(tag, score, Some(description), Some(meta)))
    assertCompletionsMatch(p % tag %? description % score %% meta)
    assertCompletionsMatch(p % tag % score %? description %% meta)
  }

  "Top completions" should "limit completions according to score" in {
    topCompletionsLimitsCompletionsAccordingToScore(someParser)((p: Parser[String], s: String) => complete(p, s))
    topCompletionsLimitsCompletionsAccordingToScore(someAsyncParser)((p: AsyncParser[String], s: String) => complete(p, s))
  }

  def topCompletionsLimitsCompletionsAccordingToScore[T, P[+R] <: CombinableParser[R, P]](parser: P[T])(complete: (P[T], String) => Completions): Unit = {
    // Arrange
    val meta: JValue = ("some foo" -> "some bar")
    val completions = Seq("one", "two", "three", "four").zipWithIndex.map {
      case (c, s) => Completion(c, s)
    }
    val sut = (parser %> CompletionSet(completions) %%% meta).topCompletions(2)

    // Act
    val result = complete(sut, "")

    // Assert
    Seq("four", "three").toArray[AnyRef] shouldBe result.completionStrings.toArray[AnyRef]
    Some(meta) shouldBe  result.meta
  }
}
