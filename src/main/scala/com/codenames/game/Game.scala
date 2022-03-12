package com.codenames.game

import cats.data.{NonEmptyChain, Validated}
import cats.effect.{IO, Resource}
import com.codenames.game.Game.GameId
import com.codenames.game.Score.{GameScore, TeamScore}
import com.codenames.game.State.TeamBuilding
import com.codenames.game.Word.AllErrorsOr

import java.time.Instant
import java.util.UUID
import scala.io.{BufferedSource, Source}
import scala.util.Random

object Score {
  final case class TeamScore(score: Int) extends AnyVal
  final case class GameScore(blueAgents: TeamScore, redAgents: TeamScore)
}

final case class CaptainWord(word: String) extends AnyVal
final case class WordCount(count: Int)     extends AnyVal

case class Game(
  gameId: GameId,
  players: List[Player],
  state: State,
  board: List[Card],
  word: Option[CaptainWord],
  wordCount: Option[WordCount],
  score: GameScore,
  timerEnd: Option[Instant],
) {
  def setTimerEnd(timerEnd: Option[Instant]): Game =
    copy(timerEnd = timerEnd)
  def addPlayer(player: Player): Game =
    copy(players = players.appended(player))
  def removePlayer(player: Player): Game = {
    val filteredPlayers = players.filter(_.playerId != player.playerId)
    copy(players = filteredPlayers)
  }
  def replacePlayer(newPlayer: Player): Game = {
    val newPlayers = players.map(oldPlayer => {
      if (oldPlayer.playerId == newPlayer.playerId) newPlayer
      else oldPlayer
    })
    copy(players = newPlayers)
  }
  def setState(state: State): Game =
    copy(state = state)
  def setWord(word: Option[CaptainWord], wordCount: Option[WordCount]): Game =
    copy(word = word, wordCount = wordCount)

  def revealCard(card: Card): Game = {
    val revealedCard = card.reveal

    val boardWithRevealedCard = board.map(boardCard =>
      if (boardCard.cardId == revealedCard.cardId) revealedCard
      else boardCard
    )

    copy(board = boardWithRevealedCard)
  }

  def increaseScore(team: Team): Game = {
    val updatedScore = team match {
      case Team.BlueAgents => GameScore(TeamScore(score.blueAgents.score + 1), score.redAgents)
      case Team.RedAgents  => GameScore(score.blueAgents, TeamScore(score.redAgents.score + 1))
    }

    copy(score = updatedScore)
  }
}

object Game {
  type GameId = UUID
  def generateBoard: IO[Validated[NonEmptyChain[Word.Error], List[Card]]] = {
    import cats.syntax.traverse._
    for {
      wordOrErrorList <- generateWords()
      errorsOrWordList = wordOrErrorList.sequence
      errorsOrRandomTwentyFiveWords = errorsOrWordList.map(wordList => {
        val random = new Random()
        (1 to 25).map(_ => wordList(random.nextInt(wordList.length))).toList
      })
      errorsOrCardList = errorsOrRandomTwentyFiveWords.map(wordList => {
        val shuffledWords  = Random.shuffle(wordList)
        val blueAgentCards = shuffledWords.slice(0, 9).map(word => Card.create(word, CardType.BlueAgent))
        val redAgentWords  = shuffledWords.slice(9, 17).map(word => Card.create(word, CardType.RedAgent))
        val innocentBystandersWords =
          shuffledWords.slice(17, 24).map(word => Card.create(word, CardType.InnocentBystander))
        val assassinCard = shuffledWords.slice(24, 25).map(word => Card.create(word, CardType.Assassin))

        Random.shuffle(blueAgentCards ::: redAgentWords ::: innocentBystandersWords ::: assassinCard)
      })
    } yield errorsOrCardList
  }

  private def generateWords(): IO[List[AllErrorsOr[Word]]] = {
    def acquire(name: String): IO[BufferedSource]        = IO(Source.fromFile(name))
    def release(source: BufferedSource): IO[Unit]        = IO(source.close())
    def fileResource(name: String): Resource[IO, Source] = Resource.make(acquire(name))(release)
    def readSource(source: Source): IO[Iterator[String]] = IO(source.getLines())

    fileResource("resources/Words.txt")
      .evalMap(readSource)
      .use(words => {
        IO.pure(words.toList.map(word => Word.from(word)))
      })
  }

  def create(
    gameId: GameId = UUID.randomUUID(),
    predefinedBoard: Option[List[Card]] = None,
  ): IO[Validated[NonEmptyChain[Word.Error], Game]] = {
    for {
      board <- predefinedBoard
        .map(board => {
          val validatedBoard: Validated[NonEmptyChain[Word.Error], List[Card]] = Validated.valid(board)
          IO.pure(validatedBoard)
        })
        .getOrElse(generateBoard)
      game = board.map(board =>
        Game(
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
    } yield game
  }
}
