sap.ui.define([
	"sap/ui/core/UIComponent",
	"sap/ui/Device"
], function(
	UIComponent,
	Device
) {
	"use strict";

	return UIComponent.extend("com.codenames.fe.Component", {
		metadata: {
			manifest: "json"
		},

		init: function() {
			UIComponent.prototype.init.apply(this, arguments);

			this.getModel("SupportModel").setData({
				Layout: "OneColumn"
			});
			this.getRouter().initialize();

			this.getAggregation("rootControl").addStyleClass(!Device.system.phone ? "sapUiSizeCompact" : "");
		}
	});
});

//Test 123