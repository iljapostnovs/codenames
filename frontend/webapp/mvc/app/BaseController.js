sap.ui.define([
	"sap/ui/core/mvc/Controller",
	"com/codenames/fe/util/Formatter",
	"sap/ui/core/UIComponent"
], function(
	Controller,
	Formatter,
	UIComponent
) {
	"use strict";
	return Controller.extend("com.codenames.fe.mvc.app.BaseController", {
		formatter: Formatter,

		/**
		 * @param {string} [sModelName] model name
		 * @returns {sap.ui.model.Model} any model
		 */
		getModel: function(sModelName) {
			return this.getView().getModel(sModelName) || this.getOwnerComponent().getModel(sModelName);
		},

		/**
		 * @param {function} fnAsyncAnything async function which should be wrapped in busy state
		 * @param {sap.ui.core.Control} [oControl] any control
		 * @async
		 * @returns {Promise} when the function is done
		 */
		wrapInBusy: async function(fnAsyncAnything, oControl = this.getView()) {
			oControl.setBusy(true);

			try {
				await fnAsyncAnything();
			} catch (oError) {
				throw oError;
			} finally {
				setTimeout(() => {
					oControl.setBusy(false);
				}, 0);
			}
		},

		/**
		 * function for getting the Event Bus
		 * @public
		 * @returns {sap.ui.core.EventBus} - Event bus instance
		 */
		getEventBus: function() {
			return sap.ui.getCore().getEventBus();
		},

		getRouter: function() {
			return UIComponent.getRouterFor(this);
		}
	});
});