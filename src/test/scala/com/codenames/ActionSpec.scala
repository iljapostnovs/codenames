package com.codenames

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.Ref
import com.codenames.game.{
  Action,
  CaptainWord,
  CardType,
  ErrorMessage,
  Game,
  Player,
  PlayerName,
  PlayerRole,
  State,
  Team,
  WordCount,
}
import com.codenames.game.Game.GameId
import com.codenames.game.Player.PlayerId
import com.codenames.game.Score.{GameScore, TeamScore}
import com.codenames.game.State.TeamBuilding
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class ActionSpec extends AnyFreeSpec with Matchers {
  val ec: ExecutionContextExecutor            = ExecutionContext.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)

  val blueTeamMemberId: PlayerId = UUID.randomUUID()
  val blueCaptainId: PlayerId    = UUID.randomUUID()
  val redTeamMemberId: PlayerId  = UUID.randomUUID()
  val redCaptainId: PlayerId     = UUID.randomUUID()
  val gameId: GameId             = UUID.randomUUID()
  val secondGameId: GameId       = UUID.randomUUID()

  "Successful Actions" - {

    "Game should be created" in {
      val (games, board) = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, validatedBoard) = gameData
          games <- gameRef.get
        } yield (games, validatedBoard)
      }

      board should have size 25

      games shouldEqual Map(
        gameId -> Game(
          gameId,
          List.empty,
          TeamBuilding(),
          board,
          None,
          None,
          GameScore(TeamScore(0), TeamScore(0)),
          None,
        )
      )
    }

    "Player should join game" in {
      val games = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- Action.JoinGame(gameId, blueTeamMemberId).perform()(gameRef)
          games <- gameRef.get
        } yield games
      }

      games.get(gameId).map(_.players) shouldEqual Some(
        List(Player(blueTeamMemberId, PlayerName("Player"), None, None))
      )
    }

    "Player should join team" in {
      val games = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- Action.JoinGame(gameId, blueTeamMemberId).perform()(gameRef)
          _     <- Action.JoinTeam(gameId, Team.BlueAgents, blueTeamMemberId).perform()(gameRef)
          games <- gameRef.get
        } yield games
      }

      games.get(gameId).map(_.players) shouldEqual Some(
        List(Player(blueTeamMemberId, PlayerName("Player"), Some(Team.BlueAgents), Some(PlayerRole.TeamMember)))
      )
    }

    "Player should change name" in {
      val newName = PlayerName("New Name")
      val games = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- Action.JoinGame(gameId, blueTeamMemberId).perform()(gameRef)
          _     <- Action.ChangePlayerName(gameId, blueTeamMemberId, newName).perform()(gameRef)
          games <- gameRef.get
        } yield games
      }

      games.get(gameId).map(_.players) shouldEqual Some(
        List(Player(blueTeamMemberId, newName, None, None))
      )
    }

    "Player should become a captain" in {
      val games = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
          _     <- Action.JoinTeam(gameId, Team.BlueAgents, blueCaptainId).perform()(gameRef)
          _     <- Action.BecomeCaptain(gameId, blueCaptainId).perform()(gameRef)
          games <- gameRef.get
        } yield games
      }

      games.get(gameId).map(_.players) shouldEqual Some(
        List(Player(blueCaptainId, PlayerName("Player"), Some(Team.BlueAgents), Some(PlayerRole.Captain)))
      )
    }

    "Game should start" in {
      val games = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- joinFourPlayers(gameRef)
          _     <- Action.StartGame(gameId).perform()(gameRef)
          games <- gameRef.get
        } yield games
      }

      games.get(gameId).map(_.state) shouldEqual Some(State.CaptainThinking(Team.BlueAgents))
    }

    "Blue Captain should be able to provide word" in {
      val word      = CaptainWord("Any")
      val wordCount = WordCount(3)

      val games = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          _     <- Action.ProvideWord(gameId, blueCaptainId, word, wordCount).perform()(gameRef)
          games <- gameRef.get
        } yield games
      }

      games.get(gameId).flatMap(_.word) shouldEqual Some(word)
      games.get(gameId).flatMap(_.wordCount) shouldEqual Some(wordCount)
      games.get(gameId).map(_.state) shouldEqual Some(State.TeamThinking(Team.BlueAgents))
    }

    "Blue Member should be able to choose card" in {
      val word      = CaptainWord("Any")
      val wordCount = WordCount(3)

      val (games, chosenCard) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board) = gameData
          blueTeamCards    = board.filter(_.cardType == CardType.BlueAgent)
          cardToChoose     = blueTeamCards.head
          _     <- Action.ProvideWord(gameId, blueCaptainId, word, wordCount).perform()(gameRef)
          _     <- Action.ChooseCard(gameId, blueTeamMemberId, cardToChoose.cardId).perform()(gameRef)
          games <- gameRef.get
        } yield (games, cardToChoose)
      }

      games.get(gameId).flatMap(_.board.find(_.cardId == chosenCard.cardId).map(_.isRevealed)) shouldEqual Some(true)
      games.get(gameId).map(_.state) shouldEqual Some(State.TeamThinking(Team.BlueAgents))
      games.get(gameId).map(_.score) shouldEqual Some(GameScore(TeamScore(1), TeamScore(0)))
    }

    "Red Member should be able to choose card" in {
      val word      = CaptainWord("Any")
      val wordCount = WordCount(3)

      val (games, chosenCard) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board)   = gameData
          redTeamMemberCards = board.filter(_.cardType == CardType.RedAgent)
          cardToChoose       = redTeamMemberCards.head
          _     <- Action.ProvideWord(gameId, blueCaptainId, word, wordCount).perform()(gameRef)
          _     <- Action.ChooseCard(gameId, blueTeamMemberId, cardToChoose.cardId).perform()(gameRef)
          _     <- Action.ProvideWord(gameId, redCaptainId, word, wordCount).perform()(gameRef)
          _     <- Action.ChooseCard(gameId, redTeamMemberId, redTeamMemberCards(1).cardId).perform()(gameRef)
          games <- gameRef.get
        } yield (games, cardToChoose)
      }

      games.get(gameId).flatMap(_.board.find(_.cardId == chosenCard.cardId).map(_.isRevealed)) shouldEqual Some(true)
      games.get(gameId).map(_.state) shouldEqual Some(State.TeamThinking(Team.RedAgents))
      games.get(gameId).map(_.score) shouldEqual Some(GameScore(TeamScore(0), TeamScore(2)))
    }

    "Choosing opposite teams card finishes move" in {
      val word      = CaptainWord("Any")
      val wordCount = WordCount(3)

      val (games, chosenCard) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board)   = gameData
          redTeamMemberCards = board.filter(_.cardType == CardType.RedAgent)
          cardToChoose       = redTeamMemberCards.head
          _     <- Action.ProvideWord(gameId, blueCaptainId, word, wordCount).perform()(gameRef)
          _     <- Action.ChooseCard(gameId, blueTeamMemberId, cardToChoose.cardId).perform()(gameRef)
          games <- gameRef.get
        } yield (games, cardToChoose)
      }

      games.get(gameId).flatMap(_.board.find(_.cardId == chosenCard.cardId).map(_.isRevealed)) shouldEqual Some(true)
      games.get(gameId).map(_.state) shouldEqual Some(State.CaptainThinking(Team.RedAgents))
      games.get(gameId).map(_.score) shouldEqual Some(GameScore(TeamScore(0), TeamScore(1)))
    }

    "Choosing assassins card ends game" in {
      val word      = CaptainWord("Any")
      val wordCount = WordCount(3)

      val (games, chosenCard) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board) = gameData
          assassinCards    = board.filter(_.cardType == CardType.Assassin)
          cardToChoose     = assassinCards.head
          _     <- Action.ProvideWord(gameId, blueCaptainId, word, wordCount).perform()(gameRef)
          _     <- Action.ChooseCard(gameId, blueTeamMemberId, cardToChoose.cardId).perform()(gameRef)
          games <- gameRef.get
        } yield (games, cardToChoose)
      }

      games.get(gameId).flatMap(_.board.find(_.cardId == chosenCard.cardId).map(_.isRevealed)) shouldEqual Some(true)
      games.get(gameId).map(_.state) shouldEqual Some(State.FinishedGame())
      games.get(gameId).map(_.score) shouldEqual Some(GameScore(TeamScore(0), TeamScore(0)))
    }

    "Choosing correct card continues move" in {
      val word      = CaptainWord("Any")
      val wordCount = WordCount(3)

      val (games, chosenCard) = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board) = gameData
          blueTeamCards    = board.filter(_.cardType == CardType.BlueAgent)
          cardToChoose     = blueTeamCards.head
          _     <- Action.ProvideWord(gameId, blueCaptainId, word, wordCount).perform()(gameRef)
          _     <- Action.ChooseCard(gameId, blueTeamMemberId, cardToChoose.cardId).perform()(gameRef)
          games <- gameRef.get
        } yield (games, cardToChoose)
      }

      games.get(gameId).flatMap(_.board.find(_.cardId == chosenCard.cardId).map(_.isRevealed)) shouldEqual Some(true)
      games.get(gameId).map(_.state) shouldEqual Some(State.TeamThinking(Team.BlueAgents))
      games.get(gameId).map(_.score) shouldEqual Some(GameScore(TeamScore(1), TeamScore(0)))
    }

    "Blue Member should be able to finish move" in {
      val word      = CaptainWord("Any")
      val wordCount = WordCount(3)

      val games = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          _     <- Action.ProvideWord(gameId, blueCaptainId, word, wordCount).perform()(gameRef)
          _     <- Action.FinishMove(gameId, blueTeamMemberId).perform()(gameRef)
          games <- gameRef.get
        } yield games
      }

      games.get(gameId).map(_.state) shouldEqual Some(State.CaptainThinking(Team.RedAgents))
    }

  }
  "Unsuccessful Actions" - {
    "Can't join game if it's not team building" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.JoinGame(gameId, UUID.randomUUID()).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.JoinOnlyDuringTeamBuilding)
    }

    "Can't join multiple games" in {
      val error = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- Action.CreateGame(secondGameId).perform()(gameRef)
          _     <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
          error <- Action.JoinGame(secondGameId, blueCaptainId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Game.MultipleGamesJoining)
    }

    "Can't join team if it's not team building" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.JoinTeam(gameId, Team.BlueAgents, blueCaptainId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.JoinOnlyDuringTeamBuilding)
    }

    "Can't change players name if it's not team building" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.ChangePlayerName(gameId, blueCaptainId, PlayerName("Any")).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.ChangeNameOnlyDuringTeamBuilding)
    }

    "Can't become captain if it's not team building" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.BecomeCaptain(gameId, blueCaptainId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.BecomeCaptainOnlyDuringTeamBuilding)
    }

    "Can't become captain if no team was chosen" in {
      val error = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
          error <- Action.BecomeCaptain(gameId, blueCaptainId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.Captain.ChooseTeamBeforeBecomingCaptain)
    }

    "Can't have multiple captains" in {
      val error = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _     <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
          _     <- Action.JoinGame(gameId, blueTeamMemberId).perform()(gameRef)
          _     <- Action.JoinTeam(gameId, Team.BlueAgents, blueCaptainId).perform()(gameRef)
          _     <- Action.JoinTeam(gameId, Team.BlueAgents, blueTeamMemberId).perform()(gameRef)
          _     <- Action.BecomeCaptain(gameId, blueCaptainId).perform()(gameRef)
          error <- Action.BecomeCaptain(gameId, blueTeamMemberId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.Captain.OnlyOneCaptain)
    }

    "Can't start game if it is already started" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.StartGame(gameId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Game.GameAlreadyStarted)
    }

    "Can't start game if there are not enough players" in {
      val error = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          error <- Action.StartGame(gameId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.AtLeastFourPlayers)
    }

    "Can't start game if there are no two captains" in {
      val error = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _ <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueCaptainId).perform()(gameRef)
          _ <- Action.BecomeCaptain(gameId, blueCaptainId).perform()(gameRef)

          _ <- Action.JoinGame(gameId, blueTeamMemberId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueTeamMemberId).perform()(gameRef)

          _ <- Action.JoinGame(gameId, redCaptainId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.RedAgents, redCaptainId).perform()(gameRef)

          _     <- Action.JoinGame(gameId, redTeamMemberId).perform()(gameRef)
          _     <- Action.JoinTeam(gameId, Team.RedAgents, redTeamMemberId).perform()(gameRef)
          error <- Action.StartGame(gameId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.AtLeastTwoCaptains)
    }

    "Can't start game if there are players without a team" in {
      val error = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _ <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueCaptainId).perform()(gameRef)
          _ <- Action.BecomeCaptain(gameId, blueCaptainId).perform()(gameRef)

          _ <- Action.JoinGame(gameId, blueTeamMemberId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueTeamMemberId).perform()(gameRef)

          _ <- Action.JoinGame(gameId, redCaptainId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.RedAgents, redCaptainId).perform()(gameRef)
          _ <- Action.BecomeCaptain(gameId, redCaptainId).perform()(gameRef)

          _     <- Action.JoinGame(gameId, redTeamMemberId).perform()(gameRef)
          error <- Action.StartGame(gameId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.AllPlayersShouldHaveATeam)
    }

    "Can't start game if there are not enough players in any team" in {
      val error = runSyncIO {
        for {
          gameData <- createGame
          (gameRef, _) = gameData
          _ <- Action.JoinGame(gameId, blueCaptainId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueCaptainId).perform()(gameRef)
          _ <- Action.BecomeCaptain(gameId, blueCaptainId).perform()(gameRef)

          _ <- Action.JoinGame(gameId, blueTeamMemberId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.BlueAgents, blueTeamMemberId).perform()(gameRef)

          _ <- Action.JoinGame(gameId, redCaptainId).perform()(gameRef)
          _ <- Action.JoinTeam(gameId, Team.RedAgents, redCaptainId).perform()(gameRef)
          _ <- Action.BecomeCaptain(gameId, redCaptainId).perform()(gameRef)

          _     <- Action.JoinGame(gameId, redTeamMemberId).perform()(gameRef)
          _     <- Action.JoinTeam(gameId, Team.BlueAgents, redTeamMemberId).perform()(gameRef)
          error <- Action.StartGame(gameId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.EveryTeamShouldHaveTeamMembers)
    }

    "Only captain can provide word" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.ProvideWord(gameId, blueTeamMemberId, CaptainWord("Test"), WordCount(5)).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.TeamMember.OnlyCaptainCanProvideWords)
    }

    "Only proper captain can provide word" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.ProvideWord(gameId, redCaptainId, CaptainWord("Test"), WordCount(5)).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.State.NotCaptainsTurn)
    }

    "Only team member can choose card" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board) = gameData
          blueCard         = board.filter(_.cardType == CardType.BlueAgent)
          error <- Action.ChooseCard(gameId, blueCaptainId, blueCard.head.cardId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.Captain.CaptainCantChooseCard)
    }

    "Card already revealed" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board) = gameData
          blueCard         = board.filter(_.cardType == CardType.BlueAgent)
          _     <- Action.ProvideWord(gameId, blueCaptainId, CaptainWord("Test"), WordCount(5)).perform()(gameRef)
          _     <- Action.ChooseCard(gameId, blueTeamMemberId, blueCard.head.cardId).perform()(gameRef)
          error <- Action.ChooseCard(gameId, blueTeamMemberId, blueCard.head.cardId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Card.CardAlreadyRevealed)
    }

    "Not team members turn" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, board) = gameData
          blueCard         = board.filter(_.cardType == CardType.BlueAgent)
          error <- Action.ChooseCard(gameId, blueTeamMemberId, blueCard.head.cardId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.TeamMember.NotTeamMemberTurn)
    }

    "Card not found" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          _     <- Action.ProvideWord(gameId, blueCaptainId, CaptainWord("Test"), WordCount(5)).perform()(gameRef)
          error <- Action.ChooseCard(gameId, blueTeamMemberId, UUID.randomUUID()).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Card.CardNotFound)
    }

    "Captain can't finish move" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          _     <- Action.ProvideWord(gameId, blueCaptainId, CaptainWord("Test"), WordCount(5)).perform()(gameRef)
          error <- Action.FinishMove(gameId, blueCaptainId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.Captain.CaptainCantFinishMove)
    }

    "Finish Move: wrong game state" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          error <- Action.FinishMove(gameId, blueTeamMemberId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.TeamMember.NotTeamMemberTurn)
    }

    "Finish Move: Not team members turn" in {
      val error = runSyncIO {
        for {
          gameData <- prepareGame
          (gameRef, _) = gameData
          _     <- Action.ProvideWord(gameId, blueCaptainId, CaptainWord("Test"), WordCount(5)).perform()(gameRef)
          error <- Action.FinishMove(gameId, redTeamMemberId).perform()(gameRef)
        } yield error
      }

      error shouldEqual Left(ErrorMessage.Player.TeamMember.NotTeamMemberTurn)
    }
  }

  private def prepareGame = for {
    gameData <- createGame
    (gameRef, _) = gameData
    _ <- joinFourPlayers(gameRef)
    _ <- Action.StartGame(gameId).perform()(gameRef)
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
    gameRef <- Ref.of[IO, Map[GameId, Game]](Map.empty)
    board   <- Game.generateBoard
    validatedBoard = board.valueOr(_ => List.empty)
    _ <- Action.CreateGame(gameId, Some(validatedBoard)).perform()(gameRef)
  } yield (gameRef, validatedBoard)

  private def runSyncIO[A](program: IO[A]): A =
    program.unsafeRunSync()
}
