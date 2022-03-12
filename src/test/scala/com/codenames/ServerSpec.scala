package com.codenames

import cats.effect.IO
import com.codenames.game.Score.TeamScore
import com.codenames.game.{CaptainWord, CardType, Game, PlayerName, PlayerRole, State, Team, Word, WordCount}
import com.codenames.server.GameServer.httpApp
import fs2.Stream
import io.circe.Decoder
import org.http4s._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID
import scala.util.{Failure, Success, Try}

class ServerSpec extends AnyFreeSpec with Matchers with OptionValues {

  implicit val http: HttpApp[IO] = httpApp.unsafeRunSync()
  "Server should" - {

    "Return players id" in {
      implicit val http: IO[HttpApp[IO]] = httpApp
      val response = httpApp.flatMap(
        _.run(
          getPlayersId
        )
      )
      verifyResponseStatus(response, Status.Ok)
      (for {
        response <- response
        _        <- IO(response.bodyText.compile.string.unsafeRunSync().length shouldBe 38)
      } yield ()).unsafeRunSync()
    }

    "Create game" in {
      val response = makePostRequest(uri"service/createGame")
      verifyResponseStatus(response, Status.Accepted)

      games.length should be > 0
    }

    "Join game" in {
      val playerId1 = generatePlayersId
      val playerId2 = generatePlayersId
      val playerId3 = generatePlayersId
      val playerId4 = generatePlayersId
      val response  = makePostRequest(uri"service/createGame")
      verifyResponseStatus(response, Status.Accepted)

      val lastGame = games.last
      val gameId   = lastGame.gameId

      val responseForPlayer1 = makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$playerId1"))
      val responseForPlayer2 = makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$playerId2"))
      val responseForPlayer3 = makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$playerId3"))
      val responseForPlayer4 = makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$playerId4"))
      verifyResponseStatus(responseForPlayer1, Status.Accepted)
      verifyResponseStatus(responseForPlayer2, Status.Accepted)
      verifyResponseStatus(responseForPlayer3, Status.Accepted)
      verifyResponseStatus(responseForPlayer4, Status.Accepted)

      games.find(_.gameId == gameId).map(_.players.length) shouldBe Some(4)

    }

    "Start game" in {
      prepareGame._1.isDefined shouldBe true
    }

    "Provide word" in {
      implicit val http: HttpApp[IO]               = httpApp.unsafeRunSync()
      val (startedGameOpt, blueCaptainId, _, _, _) = prepareGame
      val newGame                                  = startedGameOpt.get
      val gameId                                   = newGame.gameId
      val response =
        makePostRequest(Uri.unsafeFromString(s"service/provideWord?gameId=$gameId&playerId=$blueCaptainId&captainWord=any&wordCount=2"))
      verifyResponseStatus(response, Status.Accepted)
    }

    "Choose card" in {
      implicit val http: HttpApp[IO]                              = httpApp.unsafeRunSync()
      val (startedGameOpt, blueCaptainId, blueTeamMemberId, _, _) = prepareGame
      val newGame                                                 = startedGameOpt.get
      val gameId                                                  = newGame.gameId
      val blueTeamCard                                            = newGame.board.filter(_.cardType == CardType.BlueAgent).head

      val response = for {
        _ <- makePostRequest(
          Uri.unsafeFromString(s"service/provideWord?gameId=$gameId&playerId=$blueCaptainId&captainWord=any&wordCount=2")
        )
        response <- makePostRequest(
          Uri.unsafeFromString(s"service/chooseCard?gameId=$gameId&playerId=$blueTeamMemberId&cardId=${blueTeamCard.cardId}")
        )
      } yield response

      verifyResponseStatus(response, Status.Accepted)
    }

    "Finish move" in {
      implicit val http: HttpApp[IO]                              = httpApp.unsafeRunSync()
      val (startedGameOpt, blueCaptainId, blueTeamMemberId, _, _) = prepareGame
      val newGame                                                 = startedGameOpt.get
      val gameId                                                  = newGame.gameId

      val response = for {
        _ <- makePostRequest(
          Uri.unsafeFromString(s"service/provideWord?gameId=$gameId&playerId=$blueCaptainId&captainWord=any&wordCount=2")
        )
        response <- makePostRequest(
          Uri.unsafeFromString(s"service/finishMove?gameId=$gameId&playerId=$blueTeamMemberId")
        )
      } yield response

      verifyResponseStatus(response, Status.Accepted)
    }
  }

  private def prepareGame(implicit http: HttpApp[IO]) = {
    val blueCaptainId    = generatePlayersId
    val blueTeamMemberId = generatePlayersId
    val redCaptainId     = generatePlayersId
    val redTeamMemberId  = generatePlayersId
    (for {
      _ <- makePostRequest(uri"service/createGame")
      lastGame = games.last
      gameId   = lastGame.gameId
      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$blueCaptainId"))
      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$blueTeamMemberId"))
      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$redCaptainId"))
      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinGame?gameId=$gameId&playerId=$redTeamMemberId"))

      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinTeam?gameId=$gameId&playerId=$blueCaptainId&team=BlueAgents"))
      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinTeam?gameId=$gameId&playerId=$blueTeamMemberId&team=BlueAgents"))
      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinTeam?gameId=$gameId&playerId=$redCaptainId&team=RedAgents"))
      _ <- makePostRequest(Uri.unsafeFromString(s"service/joinTeam?gameId=$gameId&playerId=$redTeamMemberId&team=RedAgents"))

      _ <- makePostRequest(Uri.unsafeFromString(s"service/changePlayerName?gameId=$gameId&playerId=$blueCaptainId&playerName=newName"))

      _ <- makePostRequest(Uri.unsafeFromString(s"service/becomeCaptain?gameId=$gameId&playerId=$blueCaptainId"))
      _ <- makePostRequest(Uri.unsafeFromString(s"service/becomeCaptain?gameId=$gameId&playerId=$redCaptainId"))

      _ <- makePostRequest(Uri.unsafeFromString(s"service/startGame?gameId=$gameId"))

      startedGame = games.find(_.gameId == gameId)
    } yield (startedGame, blueCaptainId, blueTeamMemberId, redCaptainId, redTeamMemberId)).unsafeRunSync()

  }
  private def games(implicit http: HttpApp[IO]) = {
    import io.circe.generic.auto._
    import org.http4s.circe.CirceEntityCodec._
    import Decoders._

    val response = http.run(
      Request(
        method = Method.GET,
        uri = uri"service/games",
      )
    )

    (for {
      response <- response
      games    <- response.as[List[Game]]
    } yield games).unsafeRunSync()
  }

  private def generatePlayersId(implicit http: HttpApp[IO]): String = {
    val response = http.run(getPlayersId)

    (for {
      response  <- response
      playersId <- response.bodyText.compile.string
    } yield playersId.substring(1, playersId.length - 1)).unsafeRunSync()
  }

  private def getPlayersId: Request[IO] =
    Request(
      method = Method.GET,
      uri = uri"service/getPlayerId",
    )

  private def makePostRequest(uri: Uri)(implicit http: HttpApp[IO]) = {
    http.run(
      makeRequest(Method.POST, headers = Headers.of(`Content-Type`(MediaType.application.json)), uri, body = None)
    )

  }

  private def makeRequest(
    method: Method,
    headers: Headers,
    uri: Uri,
    body: Option[String],
  ): Request[IO] =
    Request(
      method = method,
      uri = uri,
      headers = headers,
      body = body.fold[EntityBody[IO]](EmptyBody) { body =>
        Stream.emits(os = body.map(_.toByte))
      },
    )

  private def verifyResponseStatus[A](
    response: IO[Response[IO]],
    expectedStatus: Status,
  ): Unit = (for {
    response <- response
    _        <- IO(response.status shouldBe expectedStatus)
  } yield ()).unsafeRunSync()

  object Decoders {
    implicit lazy val decodeInstant: Decoder[Instant] =
      Decoder.decodeString.emap { timestamp =>
        Try(Instant.parse(timestamp)) match {
          case Failure(exception) => Left(s"Incorrect timestamp: ${exception.getMessage}")
          case Success(value)     => Right(value)
        }
      }
    implicit lazy val decodeUUID: Decoder[UUID] =
      Decoder.decodeString.emap { uuid =>
        Try(UUID.fromString(uuid)) match {
          case Failure(exception) => Left(s"Incorrect UUID: ${exception.getMessage}")
          case Success(value)     => Right(value)
        }
      }
    implicit lazy val decodePlayersName: Decoder[PlayerName] =
      Decoder.decodeString.emap { playerName =>
        Right(PlayerName(playerName))
      }
    implicit lazy val decodeCaptainWord: Decoder[CaptainWord] =
      Decoder.decodeString.emap { word =>
        Right(CaptainWord(word))
      }
    implicit lazy val decodeTeamScore: Decoder[TeamScore] =
      Decoder.decodeInt.emap { score =>
        Right(TeamScore(score))
      }
    implicit lazy val decodeWordCount: Decoder[WordCount] =
      Decoder.decodeInt.emap { count =>
        Right(WordCount(count))
      }

    implicit lazy val decodeTeam: Decoder[Team] =
      Decoder.decodeString.emap {
        case "BlueAgents" => Right(Team.BlueAgents)
        case "RedAgents"  => Right(Team.RedAgents)
        case _            => Left("Team doesn't exist")
      }
    implicit lazy val decodePlayerRole: Decoder[PlayerRole] =
      Decoder.decodeString.emap {
        case "TeamMember" => Right(PlayerRole.TeamMember)
        case "Captain"    => Right(PlayerRole.Captain)
        case _            => Left("Role doesn't exist")
      }
    implicit lazy val decodeState: Decoder[State] =
      Decoder.decodeString.emap {
        case "TeamBuildingState"        => Right(State.TeamBuilding())
        case "BlueCaptainThinkingState" => Right(State.CaptainThinking(Team.BlueAgents))
        case "RedCaptainThinkingState"  => Right(State.CaptainThinking(Team.RedAgents))
        case "BlueTeamThinkingState"    => Right(State.TeamThinking(Team.BlueAgents))
        case "RedTeamThinkingState"     => Right(State.TeamThinking(Team.RedAgents))
        case "FinishedGameState"        => Right(State.FinishedGame())
        case _                          => Left("State doesn't exist")
      }
    implicit lazy val decodeWord: Decoder[Word] =
      Decoder.decodeString.emap { word =>
        Right(Word(word))
      }
    implicit lazy val decodeCardType: Decoder[CardType] =
      Decoder.decodeString.emap {
        case "Assassin"          => Right(CardType.Assassin)
        case "BlueAgent"         => Right(CardType.BlueAgent)
        case "InnocentBystander" => Right(CardType.InnocentBystander)
        case "RedAgent"          => Right(CardType.RedAgent)
        case _                   => Left("Card type doesn't exist")
      }

  }
}
