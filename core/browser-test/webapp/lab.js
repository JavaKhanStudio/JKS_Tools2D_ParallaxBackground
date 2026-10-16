// The labs' Copy settings (r208, one copy since r232): every slider and choice (its option's index, r248) in
// #<lab>-controls and on the panels' titles (#<lab>-labels, r428), what it started at, and which ones moved, as JSON
// Simon pastes into a card. head() gives the lab's own fields, written before the settings.
function labSettings(lab, head) {
	var settings = {}, changed = {};
	document.querySelectorAll('#' + lab + '-labels [data-key], #' + lab + '-controls [data-key]').forEach(function (input) {
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

// A panel zoomed to the whole stage (r452), as the board's picture viewer opens one of a card's pictures: a click or a
// tap on a panel draws it alone over the canvas, the canvas the panel's shape (as tall as the window allows), its title
// alone at the top left; the arrows (keys, the buttons, a swipe) step to the next panel, Escape, the back button or a
// click on it returns to all. LabKit.frame draws what labFocus names and reports the panels each frame placed, in the
// order of their titles, through labPanelsPlaced; window.labResize is LabKit's. A lab of one panel has no zoom.
(function () {
	var stage = document.querySelector('.lab-stage');
	if (!stage) return;
	var panels = [], bufferWidth = 0, bufferHeight = 0, home = null, chipOf = [], nav = null;
	window.labFocus = -1;

	function canvas() { return stage.querySelector('#embed-browsertests canvas'); }

	window.labPanelsPlaced = function (count, width, height) {
		if (window.labFocus >= 0) {
			if (count < 2 || window.labFocus >= count) zoom(-1);
			return;
		}
		panels = window.labPanelRects.slice(0, count);
		bufferWidth = width;
		bufferHeight = height;
		stage.classList.toggle('lab-zoomable', count > 1);
	};

	// Panel i's rectangle on the page, from the canvas's pixels (GL's y up) as last placed with every panel shown.
	function onPage(i) {
		var c = canvas().getBoundingClientRect(), r = panels[i], k = c.width / bufferWidth;
		return { left: c.left + r[0] * k, top: c.top + (bufferHeight - r[1] - r[3]) * k, right: c.left + (r[0] + r[2]) * k,
			bottom: c.top + (bufferHeight - r[1]) * k };
	}

	// Each panel's title: the shown chip whose top left corner lies in it.
	function chips() {
		var all = [...stage.querySelectorAll('.lab-labels > .panel-label')].filter(function (c) {
			return getComputedStyle(c).visibility !== 'hidden' && getComputedStyle(c).display !== 'none'; });
		return panels.map(function (_, i) {
			var p = onPage(i);
			return all.find(function (c) { var b = c.getBoundingClientRect();
				return b.left >= p.left - 2 && b.left < p.right && b.top >= p.top - 2 && b.top < p.bottom; }) || null;
		});
	}

	function navigation() {
		if (nav) return nav;
		nav = document.createElement('div');
		nav.className = 'lab-zoom-nav';
		nav.innerHTML = '<button class="lab-zoom-prev" aria-label="Previous panel">‹</button>' +
			'<span class="lab-zoom-count"></span><button class="lab-zoom-next" aria-label="Next panel">›</button>' +
			'<button class="lab-zoom-back" title="Back to every panel (Escape)">✕ all</button>';
		nav.querySelector('.lab-zoom-prev').onclick = function (e) { e.stopPropagation(); step(-1); };
		nav.querySelector('.lab-zoom-next').onclick = function (e) { e.stopPropagation(); step(1); };
		nav.querySelector('.lab-zoom-back').onclick = function (e) { e.stopPropagation(); zoom(-1); };
		stage.appendChild(nav);
		return nav;
	}

	function zoom(i) {
		var c = canvas();
		stage.querySelectorAll('.lab-focus, .lab-pick-only').forEach(function (e) { e.classList.remove('lab-focus', 'lab-pick-only'); });
		if (i < 0) {
			window.labFocus = -1;
			stage.classList.remove('lab-zoomed');
			if (home) window.labResize(home[0], home[1]);
			home = null;
			return;
		}
		if (!home) {
			home = [c.width, c.height];
			chipOf = chips();
		}
		window.labFocus = i;
		stage.classList.add('lab-zoomed');
		var r = panels[i], perPixel = c.width / c.clientWidth;
		// On a phone the arrows are under the canvas: room for them too.
		var room = innerHeight - (innerWidth < 720 ? 56 : 12);
		window.labResize(c.width, Math.round(Math.min(c.width * r[3] / r[2], room * perPixel)));
		// Its title alone; the stage's choice (what every panel shows) stays, before it, without the first panel's title.
		var chip = chipOf[i], first = stage.querySelector('.lab-labels > .panel-label');
		if (chip) chip.classList.add('lab-focus');
		if (first && first !== chip && first.querySelector('.stage-pick')) first.classList.add('lab-pick-only');
		var n = navigation();
		n.querySelector('.lab-zoom-count').textContent = (i + 1) + ' / ' + panels.length;
		n.querySelector('.lab-zoom-prev').disabled = i === 0;
		n.querySelector('.lab-zoom-next').disabled = i === panels.length - 1;
		stage.scrollIntoView({ block: 'nearest' });
	}

	// Clamped, not wrapped, as the board's viewer: at the last panel you know it is the last.
	function step(d) { zoom(Math.max(0, Math.min(panels.length - 1, window.labFocus + d))); }

	window.labZoom = zoom;

	// A click on a panel zooms it; on the zoomed one, back to all.
	function pressed(target, x, y) {
		if (!target.closest('#embed-browsertests') || target.tagName !== 'CANVAS' || panels.length < 2) return;
		if (window.labFocus >= 0) { zoom(-1); return; }
		for (var i = 0; i < panels.length; i++) {
			var p = onPage(i);
			if (x >= p.left && x < p.right && y >= p.top && y < p.bottom) { zoom(i); return; }
		}
	}
	stage.addEventListener('click', function (e) { pressed(e.target, e.clientX, e.clientY); });

	addEventListener('keydown', function (e) {
		if (window.labFocus < 0 || e.target.closest('input, select, textarea')) return;
		if (e.key === 'Escape') zoom(-1);
		else if (e.key === 'ArrowRight') { e.preventDefault(); step(1); }
		else if (e.key === 'ArrowLeft') { e.preventDefault(); step(-1); }
	});

	// On a phone: a swipe steps, a tap is pressed here, libGDX's canvas having cancelled the click a tap makes.
	var x0 = null, y0 = null;
	stage.addEventListener('touchstart', function (e) { x0 = e.changedTouches[0].clientX; y0 = e.changedTouches[0].clientY; }, { passive: true });
	stage.addEventListener('touchend', function (e) {
		if (x0 === null) return;
		var t = e.changedTouches[0], dx = t.clientX - x0, dy = t.clientY - y0;
		x0 = y0 = null;
		if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
			if (e.defaultPrevented) pressed(e.target, t.clientX, t.clientY);
		} else if (window.labFocus >= 0 && Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy)) step(dx < 0 ? 1 : -1);
	}, { passive: true });
})();
