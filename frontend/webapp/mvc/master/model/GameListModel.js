sap.ui.define([
	"com/codenames/fe/mvc/app/BaseModel",
	"sap/ui/core/ws/WebSocket"
], function(
	BaseModel,
	WebSocket
) {
	"use strict";

	return BaseModel.extend("com.codenames.fe.mvc.master.model.GameListModel", {
		constructor: function() {
			return BaseModel.prototype.constructor.apply(this, [{
				Games: []
			}]);
		},

		async readGames() {
			const aGames = await this._sendRequest("GET", "/service/games", null, {
				Accept: "application/json"
			});

			this._addGameNames(aGames);
			this.setProperty("/Games", aGames);
		},

		_addGameNames(aGames) {
			aGames.forEach((mGame, iIndex) => {
				mGame.name = `Game ${iIndex}`;
				mGame.timerEnd = mGame.timerEnd ? new Date(mGame.timerEnd) : null;
			});
		},

		async createGame() {
			await this._sendRequest("POST", "/service/createGame");
		},

		async getPlayerId() {
			const sPlayerId = await this._sendRequest("GET", "/service/getPlayerId");
			this.setProperty("/PlayerId", sPlayerId);
		},

		listenToGames() {
			if (this._oWebSocket) {
				this._oWebSocket.close();
				this._oWebSocket.destroy();
			}
			this._oWebSocket = new WebSocket(`/service/listenToGames`);
			this._oWebSocket.attachMessage(this._onWebSocketMessage, this);
		},

		_onWebSocketMessage(oEvent) {
			try {
				const mUpdatedGame = JSON.parse(oEvent.getParameter("data"));
				const aGames = this.getProperty("/Games");
				const mOldGame = aGames.find(mGame => mGame.gameId === mUpdatedGame.gameId);
				if (mOldGame) {
					aGames.splice(aGames.indexOf(mOldGame), 1, mUpdatedGame);
				} else {
					aGames.push(mUpdatedGame);
				}

				this._addGameNames(aGames);
				this.setProperty("/Games", aGames);

				sap.ui.getCore().getEventBus().publish("Codenames", "GameUpdated", mUpdatedGame);
			} catch (oError) {
				console.error(oEvent.getParameter("data"));
				console.error(oError);
			}
		},

		joinGame(mGame) {
			return new Promise((resolve, reject) => {
				if (this._oJoinedGameWebSocket) {
					this._oJoinedGameWebSocket.attachEventOnce("close", () => {
						this._proceedGameJoining(mGame).then(resolve).catch(reject)
					});
					this._oJoinedGameWebSocket.close();
				} else {
					this._proceedGameJoining(mGame).then(resolve).catch(reject);
				}
			});
		},

		async _proceedGameJoining(mGame) {
			if (this._oJoinedGameWebSocket) {
				this._oJoinedGameWebSocket.destroy();
				this._oJoinedGameWebSocket = null;
			}
			await this._sendRequest("POST", `/service/joinGame?gameId=${mGame.gameId}&playerId=${this.getProperty("/PlayerId")}`);
			this._notifyServerAboutMyGame(mGame)
		},

		_notifyServerAboutMyGame(mGame) {
			this._oJoinedGameWebSocket = new WebSocket(`/service/joinGame/listen?gameId=${mGame.gameId}&playerId=${this.getProperty("/PlayerId")}`);
		}
	});
});