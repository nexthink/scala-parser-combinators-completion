/*
 * scala-parser-combinators-completion
 * Copyright (c) by Nexthink S.A.
 * Lausanne, Switzerland (http://www.nexthink.com)
 * Author: jonas.chapuis@nexthink.com
 */

package com.nexthink.utils.parsing.combinator.completion

import com.nexthink.utils.parsing.combinator.completion.TermsParsersTest.termsParsers$
import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Inside, Matchers, PropSpec}

// scalastyle:off magic.number
object TermsParsersTest {
  object termsParsers$ extends TermsParsers
}

@RunWith(classOf[JUnitRunner])
class TermsParsersTest extends PropSpec with PropertyChecks with Matchers with Inside {
  private val nonEmptyTerm                   = Gen.alphaNumStr.suchThat(t => t.nonEmpty && t.forall(_ >= ' '))
  private val nonEmptyTermLargerThanTwoChars = nonEmptyTerm.suchThat(t => t.length > 2)
  private val sampleTerms                    = Gen.containerOfN[List, String](10, nonEmptyTerm).suchThat(list => list.nonEmpty)
  private val variableLengthWhitespace       = Gen.choose(1, 10).map(i => List.fill(i)(" ").mkString)
  private val sampleTermsLargerThanTwoChars =
    Gen.containerOfN[List, String](10, nonEmptyTermLargerThanTwoChars).suchThat(list => list.nonEmpty)
  private val examples =
    List("7-Zip", "Skype", "Skype Monitor", "Skype Handsfree Support", "Activity Monitor", "Adobe Acrobat", "Google Chrome", "GoToMeeting", "NEXThink Finder")

  property("oneOfTerms completions with concrete examples") {
    val terms = termsParsers$.oneOfTerms(examples)
    val samples = Table(
      "skyp" -> "Skype, Skype Handsfree Support, Skype Monitor",
      "NEXT" -> "NEXThink Finder",
      "A"    -> "Activity Monitor, Adobe Acrobat"
    )
    forAll(samples) { (partial: String, options: String) =>
      val completedTerms = options.split(",").map(_.trim)
      val completions    = termsParsers$.completeString(terms, partial)
      completions shouldBe completedTerms
    }
  }

  property("oneOfTerms returns correct next") {
    val terms  = termsParsers$.oneOfTerms(examples)
    val result = termsParsers$.parse(terms, "skype h")
    result.successful shouldBe true
    result.next.pos.column shouldBe 6
  }

  property("oneOfTermsFuzzy completions with concrete examples") {
    val terms = termsParsers$.oneOfTermsFuzzy(examples)
    val samples = Table(
      "chrom" -> "Google Chrome",
      "finde" -> "NEXThink Finder",
      "fnder" -> "NEXThink Finder",
      "skyp"  -> "Skype",
      "skyp"  -> "Skype Handsfree Support"
    )
    forAll(samples) { (partial: String, term: String) =>
      val completions = termsParsers$.completeString(terms, partial)
      completions should contain(term)
    }
  }

  property("oneOfTerms completes to nothing if term is complete even followed with whitespace") {
    forAll(nonEmptyTermLargerThanTwoChars, variableLengthWhitespace) { (term: String, whitespace: String) =>
      val parser      = termsParsers$.oneOfTerms(Seq(term))
      val completions = termsParsers$.completeString(parser, term + whitespace)
      completions shouldBe empty
    }
  }

  property("oneOfTerms does parse all terms (symmetry)") {
    forAll(sampleTerms) { terms: List[String] =>
      {
        val parser = termsParsers$.oneOfTerms(terms)
        terms.foreach { term =>
          inside(termsParsers$.parse(parser, term)) {
            case termsParsers$.Success(parsedLiteral, reader) =>
              parsedLiteral === term
            case termsParsers$.NoSuccess(msg, _) => fail(msg)
          }
        }
      }
    }
  }

  property("oneOfTerms with empty completes with all terms in alphabetical order") {
    forAll(sampleTerms) { terms: List[String] =>
      {
        val parser      = termsParsers$.oneOfTerms(terms)
        val completions = termsParsers$.complete(parser, " ")
        withClue(s"terms=$terms, completions=$completions") {
          completions.defaultSet.isDefined shouldBe true
          terms.distinct.sorted.zipAll(completions.completionStrings, "extraCompletion", "missingCompletion").foreach {
            case (expected, actual) => actual === expected
          }
        }
      }
    }
  }

  property("oneOfTerms with empty spaces completes at the last relevant input position") {
    forAll(sampleTerms, Gen.chooseNum(1, 10)) { (terms: List[String], spacesCount: Int) =>
      {
        val spaces      = Seq.range(0, spacesCount).map(_ => " ").mkString
        val parser      = termsParsers$.oneOfTerms(terms)
        val completions = termsParsers$.complete(parser, spaces)
        withClue(s"terms=$terms, completions=$completions") {
          completions.position.column shouldBe 1
        }
      }
    }
  }

  property("oneOfTermsFuzzy with empty completes with all terms in alphabetical order") {
    forAll(sampleTerms) { terms: List[String] =>
      {
        val parser      = termsParsers$.oneOfTermsFuzzy(terms)
        val completions = termsParsers$.complete(parser, " ")
        withClue(s"terms=$terms, completions=$completions") {
          completions.defaultSet.isDefined shouldBe true
          terms.distinct.sorted.zipAll(completions.completionStrings, "extraCompletion", "missingCompletion").foreach {
            case (expected, actual) => actual === expected
          }
        }
      }
    }
  }

  property("oneOfTermsFuzzy with same similarity completes in alphabetical order") {
    forAll(sampleTerms) { terms: List[String] =>
      {
        val parser = termsParsers$.oneOfTermsFuzzy(terms, similarityMeasure = (_, _) => 100)
        terms.foreach(term => {
          val completions = termsParsers$.complete(parser, term.take(1))
          withClue(s"terms=$terms, completions=$completions") {
            terms.distinct.sorted
              .zipAll(completions.completionStrings, "extraCompletion", "missingCompletion")
              .foreach {
                case (expected, actual) => actual === expected
              }
          }
        })
      }
    }
  }

  property("oneOfTermsFuzzy completions with prefix or suffix always includes full term (thanks to affixing)") {
    forAll(sampleTermsLargerThanTwoChars) { terms: List[String] =>
      {
        val parser = termsParsers$.oneOfTermsFuzzy(terms, similarityThreshold = 0)
        terms.foreach(term => {
          forAll(Gen.choose(1, term.length - 1), Gen.oneOf(true, false)) { (substringLength, isPrefix) =>
            val substring =
              if (isPrefix) term.take(substringLength) else term.takeRight(substringLength)
            val completions =
              termsParsers$.complete(parser, substring).completionStrings
            withClue(s"substring=$substring, term=$term, completions=$completions, terms=$terms") {
              completions should contain(term)
            }
          }
        })
      }
    }
  }

  property("oneOfTermsFuzzy with maxCompletions limits completions count to that number") {
    forAll(sampleTerms, Gen.choose(0, 10)) { (terms: List[String], count: Int) =>
      {
        val parser      = termsParsers$.oneOfTermsFuzzy(terms, maxCompletionsCount = count)
        val completions = termsParsers$.complete(parser, "").completionStrings
        completions.size should be(terms.size.min(count))
      }
    }
  }

  property("oneOfTermsFuzzy with similarity threshold limits completions count to that number") {
    forAll(sampleTermsLargerThanTwoChars, Gen.choose(0, 100)) { (terms: List[String], threshold: Int) =>
      {
        val parser = termsParsers$.oneOfTermsFuzzy(terms, similarityThreshold = threshold)
        terms.foreach(term => {
          val substring   = term.drop(1)
          val completions = termsParsers$.complete(parser, substring)
          withClue(s"substring=$substring, term=$term, completions=$completions, terms=$terms") {
            whenever(completions.nonEmpty) {
              completions.allSets.flatMap(_.entries).map(_.score).max should (be >= threshold or equal(0))
            }
          }
        })
      }
    }
  }

}
