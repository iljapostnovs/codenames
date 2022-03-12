sap.ui.define([
	"sap/ui/core/Control"
], function(
	Control
) {
	"use strict";

	return Control.extend("com.codenames.fe.mvc.game.view.card.Card", {
		metadata: {
			properties: {
				word: {
					type: "string"
				},
				type: {
					type: "string"
				},
				showColor: {
					type: "boolean"
				}
			},
			events: {
				select: {}
			}
		},


		constructor: function() {
			Control.prototype.constructor.apply(this, arguments);

			this.addEventDelegate({
				onAfterRendering: this._onAfterRendering.bind(this)
			});

			return this;
		},

		_onAfterRendering() {
			const $DomRef = this.getDomRef();
			if ($DomRef) {
				$DomRef.onclick = this._onClick.bind(this);
			}
		},

		_onClick() {
			this.fireSelect();
		},

		renderer(oRm, oCard) {
			oRm.openStart("div", oCard);
			oRm.class("comEvolutionCard");

			const mClassesForBackgroundColor = {
				Assassin: "comEvolutionCardAssassin",
				BlueAgent: "comEvolutionCardBlue",
				RedAgent: "comEvolutionCardRed",
				InnocentBystander: "comEvolutionCardInnocent",
				Unknown: "comEvolutionCardUnknown"
			};
			const sClass = oCard.getShowColor() ? mClassesForBackgroundColor[oCard.getType()] : mClassesForBackgroundColor.Unknown;
			oRm.class(sClass);
			oRm.openEnd();

			//word
			oRm.openStart("div");
			oRm.class("comEvolutionCardWord");
			oRm.openEnd();

			//word text
			oRm.openStart("span").openEnd().text(oCard.getWord());
			oRm.close("span");

			oRm.close("div");

			oRm.close("div");
		}
	});
});