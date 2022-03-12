sap.ui.define([
	"com/codenames/fe/mvc/app/BaseController",
	"com/codenames/fe/mvc/detail/view/control/NameDialog"
], function(
	BaseController,
	NameDialog
) {
	"use strict";

	return BaseController.extend("com.codenames.fe.mvc.detail.controller.TeamBuilding", {
		/**
		 * @override
		 */
		onInit() {
			this.getView().setModel(this.getModel("TeamBuildingModel"));
			this.getModel().setMainModel(this.getModel("GameListModel"));
			this.getRouter().getRoute("TeamBuilding").attachMatched(this._onRouteMatched, this);
			sap.ui.getCore().getEventBus().subscribe("Codenames", "GameUpdated", this._onGameUpdated, this);
		},

		_onGameUpdated(sApp, sChannel, mUpdatedGame) {
			const mCurrentGame = this.getModel().getProperty("/Game");
			if (mCurrentGame && mCurrentGame.gameId === mUpdatedGame.gameId) {
				if (mUpdatedGame.state === "TeamBuildingState") {
					this.getModel().setProperty("/Game", mUpdatedGame);
				} else {
					this.getRouter().navTo("Game", {
						GameId: mUpdatedGame.gameId
					})
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
			} else {
				this.getRouter().navTo("Master");
			}
		},

		onButtonStartGamePress() {
			this.getModel().startGame();
		},

		onButtonJoinRedTeamPress() {
			this.getModel().joinTeam("RedAgents");
		},

		onButtonBecomeCaptainPress() {
			this.getModel().becomeCaptain();

		},

		onButtonJoinBlueTeamPress() {
			this.getModel().joinTeam("BlueAgents");
		},

		async onButtonChangeMyNamePress() {
			const oNameDialog = new NameDialog();
			const sNewName = await oNameDialog.askUserToEnterNewName();
			this.getModel().changeName(sNewName);
		}
	});
});