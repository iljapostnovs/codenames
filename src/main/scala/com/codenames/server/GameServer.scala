package com.codenames.server

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, Fiber, IO, IOApp}
import com.codenames.game.{Action, Game, State}
import com.codenames.game.Game.GameId
import com.codenames.game.Player.PlayerId
import fs2.concurrent.Topic
import io.circe.syntax.EncoderOps
import org.http4s.dsl.io.{:?, _}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.{HttpApp, HttpRoutes}

import java.util.UUID
import scala.concurrent.ExecutionContext

object GameServer extends IOApp {
  import com.codenames.server.QueryMatcher._

  import io.circe.generic.auto._
  import org.http4s.circe.CirceEntityCodec._
  import com.codenames.jsoncodec.Codecs._
  private[codenames] val httpApp: IO[HttpApp[IO]] =
    for {
      gameTopic  <- Topic[IO, Option[Game]](None)
      gameRef    <- Ref.of[IO, Map[GameId, Game]](Map.empty)
      timeoutRef <- Ref.of[IO, Map[GameId, Fiber[IO, Unit]]](Map.empty)
    } yield HttpRoutes
      .of[IO](request => {
        implicit val implicitRef: Ref[IO, Map[GameId, Game]] = gameRef
        request match {
          case GET -> Root / "service" / "getPlayerId" =>
            Ok(s"${UUID.randomUUID()}")

          case GET -> Root / "service" / "games" =>
            for {
              gamesMap <- gameRef.get
              gameList        = gamesMap.toList.map(_._2)
              serializedGames = gameList.asJson
              ok <- Ok(serializedGames)
            } yield ok

          case POST -> Root / "service" / "createGame" =>
            for {
              createdGameOrError <- Action.CreateGame().perform.attempt
              response <- createdGameOrError match {
                case Left(runtimeError) => InternalServerError(runtimeError.getMessage)
                case Right(createdGameOrParsingErrors) =>
                  createdGameOrParsingErrors.fold(
                    error => InternalServerError(error.toString),
                    createdGame => gameTopic.publish1(Some(createdGame)) *> Accepted(),
                  )
              }
            } yield response

          case POST -> Root / "service" / "joinGame" :? GameIdMatcher(gameId) :? PlayerIdMatcher(playerId) =>
            for {
              gameJoinedByPlayerOrError <- Action.JoinGame(gameId, playerId).perform()
              response <- gameJoinedByPlayerOrError.fold(
                error => InternalServerError(error.toString),
                game => gameTopic.publish1(Some(game)) *> Accepted(),
              )
            } yield response

          case GET -> Root / "service" / "joinGame" / "listen" :? GameIdMatcher(gameId) :? PlayerIdMatcher(
                playerId
              ) =>
            for {
              response <- WebSocketBuilder[IO].build(
                receive = stream => stream.drain,
                send = gameTopic.subscribe(Int.MaxValue).map(_ => WebSocketFrame.Text("")),
                onClose = handleWebSocketClose(playerId, gameId, gameTopic),
              )
            } yield response

          case GET -> Root / "service" / "listenToGames" =>
            for {
              response <- WebSocketBuilder[IO].build(
                receive = stream => stream.drain,
                send = gameTopic
                  .subscribe(Int.MaxValue)
                  .map(gameOpt => {
                    val responseText = gameOpt.fold("Game was not found")(_.asJson.noSpaces)
                    WebSocketFrame.Text(responseText)
                  }),
              )
            } yield response

          case POST -> Root / "service" / "startGame" :? GameIdMatcher(gameId) =>
            for {
              startedGameOrError <- Action.StartGame(gameId).perform()
              response <- startedGameOrError.fold(
                error => InternalServerError(error.toString),
                _ => {
                  val gameTimer = new GameTimer(gameId, timeoutRef, gameRef, gameTopic)
                  gameTimer.cancelPreviousTimer() *> gameTimer.setTimerAndPublish() *> Accepted()
                },
              )
            } yield response

          case POST -> Root / "service" / "joinTeam" :? GameIdMatcher(gameId) :? PlayerIdMatcher(
                playerId
              ) :? TeamMatcher(
                team
              ) =>
            for {
              gameWithJoinedTeamOrError <- Action.JoinTeam(gameId, team, playerId).perform()
              response <- gameWithJoinedTeamOrError.fold(
                error => InternalServerError(error.toString),
                game => gameTopic.publish1(Some(game)) *> Accepted(),
              )
            } yield response

          case POST -> Root / "service" / "becomeCaptain" :? GameIdMatcher(gameId) :? PlayerIdMatcher(
                playerId
              ) =>
            for {
              gameWithPlayerAsCaptainOrError <- Action.BecomeCaptain(gameId, playerId).perform()
              response <- gameWithPlayerAsCaptainOrError.fold(
                error => InternalServerError(error.toString),
                game => gameTopic.publish1(Some(game)) *> Accepted(),
              )
            } yield response

          case POST -> Root / "service" / "changePlayerName" :? GameIdMatcher(gameId) :? PlayerIdMatcher(
                playerId
              ) :? PlayerNameMatcher(newPlayerName) =>
            for {
              gameWithChangedNameOrError <- Action.ChangePlayerName(gameId, playerId, newPlayerName).perform()
              response <- gameWithChangedNameOrError.fold(
                error => InternalServerError(error.toString),
                game => gameTopic.publish1(Some(game)) *> Accepted(),
              )
            } yield response

          case POST -> Root / "service" / "provideWord" :? GameIdMatcher(gameId) :? PlayerIdMatcher(
                playerId
              ) :? CaptainWordMatcher(captainWord) :? WordCountMatcher(wordCount) =>
            for {
              adjustedGame <- Action.ProvideWord(gameId, playerId, captainWord, wordCount).perform()
              response <- adjustedGame.fold(
                error => InternalServerError(error.toString),
                _ => {
                  val gameTimer = new GameTimer(gameId, timeoutRef, gameRef, gameTopic)
                  gameTimer.cancelPreviousTimer() *> gameTimer.setTimerAndPublish() *> Accepted()
                },
              )
            } yield response

          case POST -> Root / "service" / "chooseCard" :? GameIdMatcher(gameId) :? PlayerIdMatcher(
                playerId
              ) :? CardIdMatcher(cardId) =>
            for {
              adjustedGame <- Action.ChooseCard(gameId, playerId, cardId).perform()
              response <- adjustedGame.fold(
                error => InternalServerError(error.toString),
                game => {
                  val timeoutIO = game.state match {
                    case State.CaptainThinking(_) =>
                      val gameTimer = new GameTimer(gameId, timeoutRef, gameRef, gameTopic)
                      gameTimer.cancelPreviousTimer() *> gameTimer.setTimerAndPublish()
                    case _ => gameTopic.publish1(Some(game))
                  }
                  timeoutIO *> Accepted()
                },
              )
            } yield response

          case POST -> Root / "service" / "finishMove" :? GameIdMatcher(gameId) :? PlayerIdMatcher(
                playerId
              ) =>
            for {
              adjustedGame <- Action.FinishMove(gameId, playerId).perform()
              response <- adjustedGame.fold(
                error => InternalServerError(error.toString),
                _ => {
                  val gameTimer = new GameTimer(gameId, timeoutRef, gameRef, gameTopic)
                  gameTimer.cancelPreviousTimer() *> gameTimer.setTimerAndPublish() *> Accepted()
                },
              )
            } yield response
        }
      })
      .orNotFound

  private def handleWebSocketClose(
    playerId: PlayerId,
    gameId: GameId,
    gameTopic: Topic[IO, Option[Game]],
  )(implicit
    ref: Ref[IO, Map[GameId, Game]]
  ): IO[Unit] = for {
    games <- ref.get
    adjustedGame = for {
      game   <- games.get(gameId).toRight("Game wasn't found")
      player <- game.players.find(_.playerId == playerId).toRight("Player wasn't found")
      gameWithoutPlayer = game.removePlayer(player)
      validatedGame     = State.validatePlayerQuantity(gameWithoutPlayer)
      possiblyFinishedGame = validatedGame match {
        case Left(_) if gameWithoutPlayer.state != State.TeamBuilding() =>
          gameWithoutPlayer.setState(State.FinishedGame()).setWord(None, None).setTimerEnd(None)
        case _ => gameWithoutPlayer
      }
    } yield possiblyFinishedGame

    _ <- adjustedGame match {
      case Left(_) => IO.unit
      case Right(game) =>
        ref.update(_ + (game.gameId -> game)) *> gameTopic.publish1(
          Some(game)
        )
    }
  } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    for {
      httpApp <- httpApp
      _ <- BlazeServerBuilder[IO](ExecutionContext.global)
        .bindHttp(port = 9001, host = "localhost")
        .withHttpApp(httpApp)
        .serve
        .compile
        .drain
    } yield ExitCode.Success
}
