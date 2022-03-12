sap.ui.define([
	"sap/ui/model/json/JSONModel",
	"sap/m/MessageBox"
], function(
	JSONModel,
	MessageBox
) {
	"use strict";

	/*global $*/

	return JSONModel.extend("com.codenames.fe.mvc.app.BaseModel", {

		/* eslint-disable max-params */
		/**
		 * @protected
		 * @param {string} sMethod POST, GET etc
		 * @param {string} sUrl url
		 * @param {map} [mData] data to send
		 * @param {string} [mHeaders] headers
		 * @returns {Promise<any>} when request was resolved
		 */
		_sendRequest(sMethod, sUrl, mData, mHeaders = {}) {
			/* eslint-enable max-params */
			return new Promise((resolve, reject) => {
				$.ajax({
					method: sMethod,
					url: sUrl,
					data: mData,
					success: resolve,
					error: (error) => {
						MessageBox.error(error.responseText.replace(/"/g, ""));
						reject(error);
					},
					headers: mHeaders
				});
			});
		}
	});
});