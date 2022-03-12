package com.codenames.jsoncodec

import com.codenames.game.Score.TeamScore
import com.codenames.game.{CaptainWord, CardType, PlayerName, PlayerRole, State, Team, Word, WordCount}
import io.circe.Encoder

import java.time.Instant
import java.util.UUID

object Codecs {
  implicit lazy val encodeInstant: Encoder[Instant] =
    Encoder.encodeString.contramap[Instant](_.toString)

  implicit lazy val encodeUUID: Encoder[UUID] =
    Encoder.encodeString.contramap[UUID](_.toString)

  implicit lazy val encodePlayerName: Encoder[PlayerName] =
    Encoder.encodeString.contramap[PlayerName](_.name)

  implicit lazy val encodeCaptainWord: Encoder[CaptainWord] =
    Encoder.encodeString.contramap[CaptainWord](_.word)

  implicit lazy val encodeTeamScore: Encoder[TeamScore] =
    Encoder.encodeInt.contramap[TeamScore](_.score)

  implicit lazy val encodeWordCount: Encoder[WordCount] =
    Encoder.encodeInt.contramap[WordCount](_.count)

  implicit lazy val encodeTeam: Encoder[Team] =
    Encoder.encodeString.contramap[Team] {
      case Team.BlueAgents => "BlueAgents"
      case Team.RedAgents  => "RedAgents"
    }

  implicit lazy val encodePlayerRole: Encoder[PlayerRole] =
    Encoder.encodeString.contramap[PlayerRole] {
      case PlayerRole.TeamMember => "TeamMember"
      case PlayerRole.Captain    => "Captain"
    }

  implicit lazy val encodeState: Encoder[State] =
    Encoder.encodeString.contramap[State] {
      case State.TeamBuilding() => "TeamBuildingState"
      case State.CaptainThinking(team) =>
        team match {
          case Team.BlueAgents => "BlueCaptainThinkingState"
          case Team.RedAgents  => "RedCaptainThinkingState"
        }
      case State.TeamThinking(team) =>
        team match {
          case Team.BlueAgents => "BlueTeamThinkingState"
          case Team.RedAgents  => "RedTeamThinkingState"
        }
      case State.FinishedGame() => "FinishedGameState"
    }

  implicit lazy val encodeWord: Encoder[Word] =
    Encoder.encodeString.contramap[Word] { _.word }

  implicit lazy val encodeCardType: Encoder[CardType] =
    Encoder.encodeString.contramap[CardType] {
      case CardType.Assassin          => "Assassin"
      case CardType.BlueAgent         => "BlueAgent"
      case CardType.InnocentBystander => "InnocentBystander"
      case CardType.RedAgent          => "RedAgent"
    }

}
