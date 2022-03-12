sap.ui.define([], function() {
	"use strict";
	const Formatter = {
		getHighlightForState(sState) {
			const mHighlight = {
				TeamBuildingState: "Information",
				RedCaptainThinkingState: "Success",
				BlueCaptainThinkingState: "Success",
				RedTeamThinkingState: "Success",
				BlueTeamThinkingState: "Success",
				FinishedGameState: "Error"
			};

			return mHighlight[sState];
		},

		getSecondsBefore(dBefore) {
			const iNow = new Date().getTime();
			const iFuture = dBefore ? dBefore.getTime() : iNow;
			const iDifferenceMS = iFuture - iNow;
			const iDifferenceS = parseInt(iDifferenceMS / 1000) + 1;

			const iDifferenceNotLessThanZero = iDifferenceS < 0 ? 0 : iDifferenceS;

			return `${iDifferenceNotLessThanZero}`;
		},

		getSecondsBeforePercent(dBefore) {
			const iNow = new Date().getTime();
			const iFuture = dBefore ? dBefore.getTime() : iNow;
			const iDifferenceMS = iFuture - iNow;
			const iDifferenceS = parseInt(iDifferenceMS / 1000) + 1;

			const iDifferenceNotLessThanZero = iDifferenceS < 0 ? 0 : iDifferenceS;

			return iDifferenceNotLessThanZero / 60 * 100;
		}
	};

	return Formatter;
});