package com.codenames.server

import cats.data.Validated
import com.codenames.game.{CaptainWord, PlayerName, Team, WordCount}
import org.http4s.{ParseFailure, QueryParamDecoder}
import org.http4s.dsl.io.QueryParamDecoderMatcher

import java.util.UUID

object QueryMatcher {
  implicit val teamDecoder: QueryParamDecoder[Team] = param =>
    Team.from(param.value) match {
      case Some(team) => Validated.validNel(team)
      case None       => Validated.invalidNel(ParseFailure(s"Team '${param.value}' doesn't exist", ""))
    }

  implicit val uuidDecoder: QueryParamDecoder[UUID] = param =>
    Validated
      .catchNonFatal(UUID.fromString(param.value))
      .leftMap(t => ParseFailure(s"Failed to decode UUID", t.getMessage))
      .toValidatedNel

  implicit val playerNameDecoder: QueryParamDecoder[PlayerName] =
    param => Validated.validNel(PlayerName(param.value))
  implicit val captainWordDecoder: QueryParamDecoder[CaptainWord] =
    param => Validated.validNel(CaptainWord(param.value))
  implicit val wordCountDecoder: QueryParamDecoder[WordCount] = param =>
    Validated
      .catchNonFatal(WordCount(Integer.parseInt(param.value)))
      .leftMap(t => ParseFailure(s"Failed to decode word count", t.getMessage))
      .toValidatedNel

  object GameIdMatcher      extends QueryParamDecoderMatcher[UUID](name = "gameId")
  object CardIdMatcher      extends QueryParamDecoderMatcher[UUID](name = "cardId")
  object PlayerIdMatcher    extends QueryParamDecoderMatcher[UUID](name = "playerId")
  object PlayerNameMatcher  extends QueryParamDecoderMatcher[PlayerName](name = "playerName")
  object CaptainWordMatcher extends QueryParamDecoderMatcher[CaptainWord](name = "captainWord")
  object WordCountMatcher   extends QueryParamDecoderMatcher[WordCount](name = "wordCount")
  object TeamMatcher        extends QueryParamDecoderMatcher[Team](name = "team")
}
