// The labs' Copy settings (r208, one copy since r232): every slider in #<lab>-controls, what it started at, and which
// ones moved, as JSON Simon pastes into a card. head() gives the lab's own fields, written before the settings.
function labSettings(lab, head) {
	var settings = {}, changed = {};
	document.querySelectorAll('#' + lab + '-controls input[data-key]').forEach(function (input) {
		var key = input.dataset.key, value = Number(input.value), start = Number(input.dataset.start);
		settings[key] = value;
		if (value !== start) changed[key] = { started: start, now: value };
	});
	var json = head();
	json.settings = settings;
	json.changed = Object.keys(changed).length ? changed : 'nothing: the values the lab opens with';
	return JSON.stringify(json, null, 2);
}

function labCopy(lab, head) {
	document.getElementById(lab + '-copy').addEventListener('click', async function () {
		var text = labSettings(lab, head), box = document.getElementById(lab + '-copy-json'), state = document.getElementById(lab + '-copy-state');
		box.value = text;
		box.hidden = false;
		// The clipboard is refused on some origins: the selected textarea is the answer, the clipboard a convenience.
		box.focus({ preventScroll: true });
		box.select();
		try {
			await navigator.clipboard.writeText(text);
			state.textContent = 'copied: paste it on the card';
		} catch (e) {
			state.textContent = 'selected: Ctrl+C';
		}
	});
}
