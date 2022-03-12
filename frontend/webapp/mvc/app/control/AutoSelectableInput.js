sap.ui.define([
	"sap/m/Input"
], function(
	Input
) {
	"use strict";

	return Input.extend("com.codenames.fe.mvc.app.control.AutoSelectableInput", {
		renderer: {},

		/**@override */
		select: function() {
			const $domRef = this.getDomRef();
			const $inputDomRef = $domRef.querySelector("input");
			$inputDomRef.select();
		},

		onfocusin: function(oEvent) {
			if (Input.prototype.onfocusin) {
				Input.prototype.onfocusin.apply(this, arguments);
			}

			const $inputDomRef = this.getDomRef().querySelector("input");
			if ($inputDomRef && $inputDomRef === oEvent.target && !this._bSelectedOnFocusIn) {
				this.select();
				this._bSelectedOnFocusIn = true;
			}
		},

		onfocusout: function() {
			if (Input.prototype.onfocusout) {
				Input.prototype.onfocusout.apply(this, arguments);
			}

			this._bSelectedOnFocusIn = false;
		}
	});
});