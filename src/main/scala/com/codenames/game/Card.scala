package com.codenames.game

import cats.data.ValidatedNec
import cats.implicits.catsSyntaxValidatedIdBinCompat0
import com.codenames.game.Card.CardId

import java.util.UUID
sealed trait CardType
object CardType {
  final case object BlueAgent         extends CardType
  final case object RedAgent          extends CardType
  final case object InnocentBystander extends CardType
  final case object Assassin          extends CardType
}

final case class Word(word: String) extends AnyVal
object Word {
  type Error          = String
  type AllErrorsOr[A] = ValidatedNec[Error, A]
  def from(word: String): AllErrorsOr[Word] =
    if (word.matches("^([a-zA-Z]| )+$")) Word(word).validNec
    else s"Word '$word' must contain only characters or space".invalidNec
}

final case class Card(
  cardId: CardId,
  word: Word,
  cardType: CardType,
  isRevealed: Boolean,
) {
  def reveal: Card = copy(isRevealed = true)
}
object Card {
  type CardId = UUID
  def create(word: Word, cardType: CardType): Card =
    Card(UUID.randomUUID(), word, cardType, isRevealed = false)
}
