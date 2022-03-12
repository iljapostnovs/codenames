package com.codenames

import cats.effect.{ContextShift, Fiber, IO, Timer}
import cats.effect.concurrent.Ref
import com.codenames.game.{Action, Game, State, Team}
import com.codenames.game.Game.GameId
import com.codenames.game.Player.PlayerId
import com.codenames.server.GameTimer
import fs2.concurrent.Topic
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class GameTimerSpec extends AnyFreeSpec with Matchers {
  val ec: ExecutionContextExecutor  = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO]     = IO.timer(ExecutionContext.global)

  val blueTeamMemberId: PlayerId = UUID.randomUUID()
  val blueCaptainId: PlayerId    = UUID.randomUUID()
  val redTeamMemberId: PlayerId  = UUID.randomUUID()
  val redCaptainId: PlayerId     = UUID.randomUUID()
  val gameId: GameId             = UUID.randomUUID()
  val secondGameId: GameId       = UUID.randomUUID()

  "Timer" - {

    "Timer should be started" in {
      val (_, timers) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, timeoutRef) = gameData
          games  <- gameRef.get
          timers <- timeoutRef.get
        } yield (games, timers)
      }

      timers.contains(gameId) shouldEqual true
    }

    "Timer should be cancelled" in {
      val (_, timers) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, timeoutRef) = gameData
          games     <- gameRef.get
          gameTopic <- Topic[IO, Option[Game]](None)
          gameTimer = new GameTimer(gameId, timeoutRef, gameRef, gameTopic, 1)
          _      <- gameTimer.cancelPreviousTimer()
          timers <- timeoutRef.get
        } yield (games, timers)
      }

      timers.contains(gameId) shouldEqual false
    }

    "Timer should be refreshed in one second" in {
      val (firstTimer, secondTimer) = runSyncIO {
        for {
          gameData <- prepareGame
          (_, timeoutRef) = gameData
          timers <- timeoutRef.get
          firstTimer = timers.get(gameId)
          _          <- IO.sleep(1500.millis)
          timerAfter <- timeoutRef.get
          secondTimer = timerAfter.get(gameId)
        } yield (firstTimer, secondTimer)
      }

      firstTimer should not be secondTimer
    }

    "Timer should change game state" in {
      val (gamesBefore, gamesAfterOneTimeout, gamesAfterTwoTimeouts) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          gamesBefore           <- gameRef.get
          _                     <- IO.sleep(1500.millis)
          gamesAfterOneTimeout  <- gameRef.get
          _                     <- IO.sleep(1.seconds)
          gamesAfterTwoTimeouts <- gameRef.get
        } yield (gamesBefore, gamesAfterOneTimeout, gamesAfterTwoTimeouts)
      }

      gamesBefore.get(gameId).map(_.state) shouldEqual Some(State.CaptainThinking(Team.BlueAgents))
      gamesAfterOneTimeout.get(gameId).map(_.state) shouldEqual Some(State.TeamThinking(Team.BlueAgents))
      gamesAfterTwoTimeouts.get(gameId).map(_.state) shouldEqual Some(State.CaptainThinking(Team.RedAgents))
    }
  }

  private def prepareGame = for {
    gameData <- createGame
    (gameRef, timeoutRef) = gameData
    _         <- joinFourPlayers(gameRef)
    _         <- Action.StartGame(gameId).perform()(gameRef)
    gameTopic <- Topic[IO, Option[Game]](None)
    gameTimer = new GameTimer(gameId, timeoutRef, gameRef, gameTopic, 1)
    _ <- gameTimer.setTimerAndPublish()
  } yield gameData

  private def joinFourPlayers(implicit gameRef: Ref[IO, Map[GameId, Game]]) = for {
    _ <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
    _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueCaptainId).perform()
    _ <- Action.BecomeCaptain(gameId, blueCaptainId).perform()

    _ <- Action.JoinGame(gameId, blueTeamMemberId).perform()
    _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueTeamMemberId).perform()

    _ <- Action.JoinGame(gameId, redCaptainId).perform()
    _ <- Action.JoinTeam(gameId, Team.RedAgents, redCaptainId).perform()
    _ <- Action.BecomeCaptain(gameId, redCaptainId).perform()

    _ <- Action.JoinGame(gameId, redTeamMemberId).perform()
    _ <- Action.JoinTeam(gameId, Team.RedAgents, redTeamMemberId).perform()
  } yield ()

  private def createGame = for {
    gameRef    <- Ref.of[IO, Map[GameId, Game]](Map.empty)
    timeoutRef <- Ref.of[IO, Map[GameId, Fiber[IO, Unit]]](Map.empty)
    board      <- Game.generateBoard
    validatedBoard = board.valueOr(_ => List.empty)
    _ <- Action.CreateGame(gameId, Some(validatedBoard)).perform()(gameRef)
  } yield (gameRef, timeoutRef)

  private def runSyncIO[A](program: IO[A]): A =
    program.unsafeRunSync()
}
