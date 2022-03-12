sap.ui.define([
	"com/codenames/fe/mvc/app/BaseModel"
], function(
	BaseModel
) {
	"use strict";

	return BaseModel.extend("com.codenames.fe.mvc.detail.model.TeamBuildingModel", {
		/**
		 * @param {com.codenames.fe.mvc.master.model.GameListModel} oModel
		 */
		setMainModel(oModel) {
			this._oMainModel = oModel;
		},

		async startGame() {
			await this._sendRequest("POST", `/service/startGame?gameId=${this.getProperty("/Game/gameId")}`);
		},

		async becomeCaptain() {
			const sUri = `/service/becomeCaptain?` +
				`gameId=${this.getProperty("/Game/gameId")}` +
				`&playerId=${this._oMainModel.getProperty("/PlayerId")}`;

			await this._sendRequest("POST", sUri);
		},

		async joinTeam(sTeam) {
			const sUri = `/service/joinTeam?` +
				`gameId=${this.getProperty("/Game/gameId")}` +
				`&playerId=${this._oMainModel.getProperty("/PlayerId")}` +
				`&team=${sTeam}`;

			await this._sendRequest("POST", sUri);
		},

		async changeName(sNewName) {
			const sUri = `/service/changePlayerName?` +
				`gameId=${this.getProperty("/Game/gameId")}` +
				`&playerId=${this._oMainModel.getProperty("/PlayerId")}` +
				`&playerName=${sNewName}`;

			await this._sendRequest("POST", sUri);
		}
	});
});