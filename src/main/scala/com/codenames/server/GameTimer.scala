package com.codenames.server

import cats.effect.{ContextShift, Fiber, IO, Timer}
import cats.effect.concurrent.Ref
import com.codenames.game.Game.GameId
import com.codenames.game.{Game, State}
import fs2.concurrent.Topic

import java.time.Instant
import scala.concurrent.duration.DurationInt

class GameTimer(
  gameId: GameId,
  timeoutRef: Ref[IO, Map[GameId, Fiber[IO, Unit]]],
  gamesRef: Ref[IO, Map[GameId, Game]],
  gameTopic: Topic[IO, Option[Game]],
  timeoutSeconds: Int = 60,
) {

  def cancelPreviousTimer(): IO[Unit] = for {
    oldTimeouts <- timeoutRef.getAndUpdate(_ - gameId)
    timeoutOpt = oldTimeouts.get(gameId)
    _ <- timeoutOpt.fold(IO.unit)(timeout => {
      timeout.cancel
    })
  } yield ()

  def setTimerAndPublish()(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
  ): IO[Unit] = for {
    //add new timer to game
    games <- gamesRef.updateAndGet(games => {
      val gameWithTimer = games
        .get(gameId)
        .map(game => {
          val nextTimeoutTime = Instant.now().plusSeconds(timeoutSeconds)
          game.setTimerEnd(Some(nextTimeoutTime))
        })

      gameWithTimer.fold(games)(game => games + (game.gameId -> game))
    })

    //publish game with updated timer
    _ <- games
      .get(gameId)
      .fold(IO.unit)(game =>
        gameTopic.publish1(
          Some(game)
        )
      )

    fiber <- (for {
      _ <- IO.sleep(timeoutSeconds.seconds)
      _ <- gamesRef
        .update(games => {
          val updatedGameOpt = games.get(gameId) match {
            case Some(game) =>
              game.state match {
                case State.TeamThinking(team) =>
                  Some(
                    game
                      .setState(State.CaptainThinking(team.getNext))
                      .setWord(None, None)
                  )
                case State.CaptainThinking(team) => Some(game.setState(State.TeamThinking(team)))
                case _                           => None
              }
            case _ => None
          }
          updatedGameOpt.fold(games)(updatedGame => games + (updatedGame.gameId -> updatedGame))
        })
        .uncancelable
      _ <- setTimerAndPublish()
    } yield ()).start

    _ <- timeoutRef.update(_ + (gameId -> fiber))
  } yield ()
}
