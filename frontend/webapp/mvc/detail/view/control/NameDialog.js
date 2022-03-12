sap.ui.define([
	"com/codenames/fe/mvc/app/control/PromiseDialog",
	"sap/ui/model/json/JSONModel"
	// eslint-disable-next-line max-params
], function(
	PromiseDialog,
	JSONModel
) {
	"use strict";

	const NameDialog = PromiseDialog.extend("com.codenames.fe.mvc.detail.view.control.NameDialog", {
		askUserToEnterNewName: function() {
			return this.open();
		},

		/**
		 * @override
		 * @returns {any}
		 */
		_getResolvedData() {
			return this.getModel().getProperty("/Name");
		},
		/**
		 * @override
		 * @protected
		 */
		init: function() {
			PromiseDialog.prototype.init.apply(this, arguments);

			this.setDialogFragmentPath("com.codenames.fe.mvc.detail.view.control.NameDialog");
			this._initModels();
		},

		_initModels: function() {
			this.setModel(new JSONModel({
				Name: ""
			}));
		},

		onInputSubmit() {
			this._onDialogButtonOkPress();
		}
	});

	return NameDialog;
});