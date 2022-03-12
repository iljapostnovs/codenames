sap.ui.define([
	"com/codenames/fe/mvc/app/BaseModel"
], function(
	BaseModel
) {
	"use strict";

	return BaseModel.extend("com.codenames.fe.mvc.game.model.GameModel", {
		constructor: function() {
			return BaseModel.prototype.constructor.apply(this, {
				Game: null,
				CaptainWord: ""
			});

		},

		/**
		 * @param {com.codenames.fe.mvc.master.model.GameListModel} oModel
		 */
		setMainModel(oModel) {
			this._oMainModel = oModel;
		},

		async submitCaptainsWord() {
			const sUri = `/service/provideWord?` +
				`gameId=${this.getProperty("/Game/gameId")}` +
				`&playerId=${this._oMainModel.getProperty("/PlayerId")}` +
				`&captainWord=${this.getProperty("/Game/word")}` +
				`&wordCount=${this.getProperty("/Game/wordCount")}`;

			await this._sendRequest("POST", sUri);
		},

		async chooseCard(mCard) {
			const sUri = `/service/chooseCard?` +
				`gameId=${this.getProperty("/Game/gameId")}` +
				`&playerId=${this._oMainModel.getProperty("/PlayerId")}` +
				`&cardId=${mCard.cardId}`;

			await this._sendRequest("POST", sUri);
		},

		async finishMove() {
			const sUri = `/service/finishMove?` +
				`gameId=${this.getProperty("/Game/gameId")}` +
				`&playerId=${this._oMainModel.getProperty("/PlayerId")}`;

			await this._sendRequest("POST", sUri);
		}
	});
});