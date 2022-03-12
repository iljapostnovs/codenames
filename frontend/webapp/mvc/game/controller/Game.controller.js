sap.ui.define([
	"com/codenames/fe/mvc/app/BaseController",
	"com/codenames/fe/mvc/detail/view/control/NameDialog",
	"sap/m/MessageToast"
], function(
	BaseController,
	NameDialog,
	MessageToast
) {
	"use strict";

	return BaseController.extend("com.codenames.fe.mvc.game.controller.Game", {
		/**
		 * @override
		 */
		onInit() {
			this.getView().setModel(this.getModel("GameModel"));
			this.getModel().setMainModel(this.getModel("GameListModel"));
			this.getRouter().getRoute("Game").attachMatched(this._onRouteMatched, this);
			sap.ui.getCore().getEventBus().subscribe("Codenames", "GameUpdated", this._onGameUpdated, this);

			setInterval(() => {
				this.getView().byId("idProgressIndicatorTimer").getBinding("displayValue").refresh(true);
				this.getView().byId("idProgressIndicatorTimer").getBinding("percentValue").refresh(true);
			}, 100);
		},

		_onGameUpdated(sApp, sChannel, mUpdatedGame) {
			const mCurrentGame = this.getModel().getProperty("/Game");
			if (mCurrentGame && mCurrentGame.gameId === mUpdatedGame.gameId) {
				if (mUpdatedGame.state.endsWith("CaptainThinkingState") || mUpdatedGame.state.endsWith("TeamThinkingState") || mUpdatedGame.state === "FinishedGameState") {
					this.getModel().setProperty("/Game", mUpdatedGame);
					const sMyPlayerId = this.getModel("GameListModel").getProperty("/PlayerId");
					const mMyPlayer = mUpdatedGame.players.find(mPlayer => mPlayer.playerId === sMyPlayerId);
					if (mMyPlayer) {
						this.getModel().setProperty("/AmICaptain", mMyPlayer.role === "Captain");
						if (mUpdatedGame.state === "FinishedGameState") {
							MessageToast.show("Game finished");
						}
					}
				} else {
					this.getRouter().navTo("Master");
				}
			}
		},

		_onRouteMatched(oEvent) {
			this.getModel("SupportModel").setProperty("/Layout", "TwoColumnsMidExpanded");

			const mArguments = oEvent.getParameter("arguments");
			const sGameId = mArguments.GameId;

			this._bindGame(sGameId);
		},

		_bindGame(sGameId) {
			const mGame = this.getModel("GameListModel").getProperty("/Games").find(mGame => {
				return mGame.gameId === sGameId;
			});
			if (mGame) {
				this.getModel().setProperty("/Game", mGame);
				const sMyPlayerId = this.getModel("GameListModel").getProperty("/PlayerId");
				const mMyPlayer = mGame.players.find(mPlayer => mPlayer.playerId === sMyPlayerId);
				this.getModel().setProperty("/AmICaptain", mMyPlayer ? mMyPlayer.role === "Captain" : false);
			} else {
				this.getRouter().navTo("Master");
			}
		},

		onButtonSubmitWordPress() {
			this.getModel().submitCaptainsWord();
		},

		onCardSelect(oEvent) {
			const mCard = oEvent.getSource().getBindingContext().getObject();

			this.getModel().chooseCard(mCard);
		},

		onButtonFinishMovePress() {
			this.getModel().finishMove();
		}
	});
});