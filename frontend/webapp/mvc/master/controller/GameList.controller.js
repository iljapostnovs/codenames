sap.ui.define([
	"com/codenames/fe/mvc/app/BaseController"
], function(
	BaseController
) {
	"use strict";

	return BaseController.extend("com.codenames.fe.mvc.master.controller.GameList", {
		/**
		 * @override
		 */
		async onInit() {
			this.getView().setModel(this.getModel("GameListModel"));
			await Promise.all([
				this._readGames(),
				this.getModel().getPlayerId()
			]);
			this.getRouter().getRoute("Master").attachMatched(this._onRouteMatched, this);
			this.getModel().listenToGames();
		},

		_onRouteMatched() {
			this.getModel("SupportModel").setProperty("/Layout", "OneColumn");
		},

		_readGames() {
			this.wrapInBusy(async () => await this.getModel().readGames());
		},

		onButtonCreateGamePress() {
			this.wrapInBusy(async () => await this.getModel().createGame());
		},

		onListGamesSelectionChange(oEvent) {
			const oSelectedGame = oEvent.getParameter("listItem");
			const mSelectedGame = oSelectedGame.getBindingContext().getObject();

			this._joinGame(mSelectedGame)
				.then(() => {
					if (mSelectedGame.state === "TeamBuildingState") {
						this.getRouter().navTo("TeamBuilding", {
							GameId: mSelectedGame.gameId
						});
					} else if (["RedTeamThinkingState", "RedCaptainThinkingState", "BlueTeamThinkingState", "BlueCaptainThinkingState"].includes(mSelectedGame.state)) {
						this.getRouter().navTo("Game", {
							GameId: mSelectedGame.gameId
						});
					}
				})
				.catch(() => {
					this.getRouter().navTo("Master");
				});
		},

		_joinGame(mGame) {
			return this.wrapInBusy(async () => {
				await this.getModel().joinGame(mGame);
			}, this.getView().byId("idListGames"));
		}
	});
});