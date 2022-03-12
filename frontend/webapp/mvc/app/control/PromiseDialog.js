sap.ui.define([
	"sap/ui/core/Control",
	"sap/ui/core/Fragment"
], function(
	Control,
	Fragment
) {
	"use strict";

	function DialogClosedException(sMessage) {
		const oInstance = new Error(sMessage);
		Object.setPrototypeOf(oInstance, Object.getPrototypeOf(this));
		if (Error.captureStackTrace) {
			Error.captureStackTrace(oInstance, DialogClosedException);
		}
		return oInstance;
	}

	/**
	 * @author Ilja Postnovs (LV01IPO)
	 * @class
	 * @abstract
	 */
	const PromiseDialog = Control.extend("com.codenames.fe.mvc.app.control.PromiseDialog", {
		metadata: {
			library: "com.rimi.reuse",
			properties: {
				dialogFragmentPath: {
					type: "string",
					defaultValue: ""
				},
				dialogControl: {
					type: "sap.m.Dialog",
					multiple: false
				}
			}
		},

		/**
		 * @protected
		 * @type {Promise<sap.m.Dialog>}
		 */
		_pDialogLoaded: null,

		open: function() {
			return new Promise(async (resolve, reject) => {
				this._mPromise = {
					resolve: resolve,
					reject: reject
				};

				const oDialog = await this.getDialog();
				oDialog.open();
			});
		},

		close: async function() {
			const oDialog = await this.getDialog();
			oDialog.close();
		},

		/**
		 * @protected
		 */
		_onDialogButtonOkPress: function() {
			if (this._mPromise && this._mPromise.resolve) {
				this._mPromise.resolve(this._getResolvedData());
				this._mPromise = null;
			}

			this.close();
		},

		_onDialogButtonClosePress: function() {
			this.close();
		},

		/**
		 * @abstract
		 * @protected
		 * @returns {any} any data
		 */
		_getResolvedData: function() {
			return null;
		},

		/**
		 * @ui5ignore
		 * @returns {Promise<sap.m.Dialog>} dialog control
		 */
		getDialog: async function() {
			if (!this._pDialogLoaded) {
				this._pDialogLoaded = this._loadFragment(this.getDialogFragmentPath());
				const oDialog = await this._pDialogLoaded;

				const $StaticAreaRef = sap.ui.getCore().getStaticAreaRef();
				if ($StaticAreaRef) {
					this.setVisible(false);
					this.placeAt($StaticAreaRef);
				}

				this.setDialogControl(oDialog);
				this._initDialog();
			}

			return await this._pDialogLoaded;
		},

		/**
		 * @protected
		 * @async
		 * @virtual
		 * @param {String} sFragmentPath - fragment path
		 * @returns {Promise} promise
		 */
		_loadFragment: function(sFragmentPath) {
			return Fragment.load({
				id: this.getId(),
				name: sFragmentPath,
				controller: this
			});
		},

		/**
		 * @protected
		 * @virtual
		 */
		_initDialog: function() {
			const oDialog = this.getDialogControl();
			if (oDialog) {
				oDialog.attachBeforeClose(this._rejectOpeningPromise, this);
				this.addDependent(oDialog);
			}
		},

		_rejectOpeningPromise: function() {
			if (this._mPromise && this._mPromise.reject) {
				this._mPromise.reject(new DialogClosedException("Dialog closed"));
				this._mPromise = null;
			}
		},

		destroy: function() {
			if (this._mPromise) {
				this._mPromise = null;
			}

			if (this._pDialogLoaded) {
				this._pDialogLoaded = null;
			}

			Control.prototype.destroy.apply(this, arguments);
		}
	});

	PromiseDialog.DialogClosedException = DialogClosedException;

	return PromiseDialog;
});