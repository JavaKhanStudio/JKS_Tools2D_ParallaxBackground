package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DepthLab}'s tags (r539, out of the lab): HTML labels over the canvas, each at a world point of the 3D view,
 * laid out so none covers another ({@link #spread}). A frame {@link #begin}s, places its tags, {@link #hideRest} and
 * {@link #spread}s them.
 */
final class DepthTags
{
	private final Element overlay, labels;
	private final List<Element> tags = new ArrayList<>();
	private int used;
	private final Vector3 projected = new Vector3(), toPoint = new Vector3(), ahead = new Vector3();

	/** {@code overlay} holds the tags (none drawn when it is missing), {@code labels} the page's chips they keep off. */
	DepthTags(Element overlay, Element labels)
	{
		this.overlay = overlay;
		this.labels = labels;
	}

	/** A frame's first tag comes next: the ones of the last frame are reused in order. */
	void begin()
	{used = 0;}

	/**
	 * A tag over the canvas at the world point (x, y, z), {@code e} its opacity; hidden behind the eye. {@link #spread}
	 * lays the tags out by {@code rank}, the lowest first, and moves one that would cover another {@code move}: "up",
	 * "down", or "hide".
	 */
	void tag(Vector3 eye, Vector3 target, Matrix4 combined, float x, float y, float z, String text, String kind, float e, Color accent, int rank,
			String move)
	{
		if (overlay == null || !(e > 0.02f))
			return;
		projected.set(x, y, z);
		// Behind the eye: not shown.
		if (toPoint.set(x, y, z).sub(eye).dot(ahead.set(target).sub(eye)) <= 0)
			return;
		projected.prj(combined);
		if (Math.abs(projected.x) > 1.2f || Math.abs(projected.y) > 1.2f)
			return;
		Element tag;
		if (used < tags.size())
			tag = tags.get(used);
		else
		{
			tag = Document.get().createDivElement();
			overlay.appendChild(tag);
			tags.add(tag);
		}
		used++;
		tag.setClassName("depth-tag " + kind);
		tag.setInnerText(text);
		tag.getStyle().setProperty("left", (projected.x + 1) * 50 + "%");
		tag.getStyle().setProperty("top", (1 - projected.y) * 50 + "%");
		tag.getStyle().setProperty("opacity", String.valueOf(Math.min(1, e * 1.4f)));
		tag.getStyle().setProperty("borderColor", accent == null ? "" : "rgb(" + (int) (accent.r * 255) + "," + (int) (accent.g * 255) + "," + (int) (accent.b * 255) + ")");
		tag.getStyle().setProperty("display", "");
		tag.setAttribute("data-rank", String.valueOf(rank));
		tag.setAttribute("data-move", move);
	}

	/**
	 * No tag over another (r449), readable whichever way the page is turned (r458). The tags are taken by rank, the
	 * front card's first; each stays where {@link #tag} put it unless it covers one already laid or the page's chip, or
	 * the stage cuts it. Then it takes the nearest free place, its way ("up" or "down") cheaper than the other and
	 * sideways, within a few tag heights, else it is hidden; a rule's number ("hide") is hidden at once. A leader line
	 * in its colour runs from what it labels to the tag, every tag but a rule's: the card's, the sky's, the camera's,
	 * the person's at once, a gap's once moved. One measure of every tag, then numbers only.
	 */
	void spread()
	{spread(overlay, labels);}

	private static native void spread(Element overlay, Element labels) /*-{
		var GAP = 4, laid = [], tags = [];
		var svgNs = 'http://www.w3.org/2000/svg';
		var svg = overlay.querySelector('svg.depth-leaders');
		if (!svg)
		{
			svg = $doc.createElementNS(svgNs, 'svg');
			svg.setAttribute('class', 'depth-leaders');
			overlay.insertBefore(svg, overlay.firstChild);
		}
		while (svg.firstChild)
			svg.removeChild(svg.firstChild);
		var chips = labels.children;
		for (var c = 0; c < chips.length; c++)
			laid.push(chips[c].getBoundingClientRect());
		var kids = overlay.querySelectorAll('.depth-tag');
		for (var i = 0; i < kids.length; i++)
		{
			kids[i].style.marginTop = '';
			kids[i].style.marginLeft = '';
			if (kids[i].style.display !== 'none')
				tags.push(kids[i]);
		}
		tags.sort(function (a, b) { return a.getAttribute('data-rank') - b.getAttribute('data-rank'); });
		var boxes = tags.map(function (t) { return t.getBoundingClientRect(); });
		var stage = overlay.getBoundingClientRect();
		// The places a tag may take, nearest first: on an 8 px grid, up (its way) cheaper than sideways, down dearer.
		var offsets = $wnd.depthTagOffsets;
		if (!offsets)
		{
			offsets = [];
			for (var x = -240; x <= 240; x += 8)
				for (var y = -160; y <= 160; y += 8)
					offsets.push([x, y, Math.sqrt(x * x + (y < 0 ? 0.6 * y * y : 2.2 * y * y))]);
			offsets.sort(function (a, b) { return a[2] - b[2]; });
			$wnd.depthTagOffsets = offsets;
		}
		function fits(b, dx, dy)
		{
			if (b.left + dx < stage.left || b.right + dx > stage.right || b.top + dy < stage.top || b.bottom + dy > stage.bottom)
				return false;
			for (var j = 0; j < laid.length; j++)
			{
				var o = laid[j];
				if (b.left + dx < o.right + GAP && o.left < b.right + dx + GAP && b.top + dy < o.bottom + GAP && o.top < b.bottom + dy + GAP)
					return false;
			}
			return true;
		}
		for (var i = 0; i < tags.length; i++)
		{
			var b = boxes[i], move = tags[i].getAttribute('data-move'), at = null;
			if (fits(b, 0, 0))
				at = [0, 0];
			else if (move !== 'hide')
				for (var c = 1; c < offsets.length && !at; c++)
				{
					var dy = move === 'down' ? -offsets[c][1] : offsets[c][1];
					if (fits(b, offsets[c][0], dy))
						at = [offsets[c][0], dy];
				}
			if (!at)
			{
				tags[i].style.display = 'none';
				continue;
			}
			tags[i].style.marginLeft = at[0] + 'px';
			tags[i].style.marginTop = at[1] + 'px';
			var box = { left: b.left + at[0], right: b.right + at[0], top: b.top + at[1], bottom: b.bottom + at[1] };
			laid.push(box);
			// The leader: from the point the tag labels to the nearest point of its box.
			if (move === 'hide' || (move === 'down' && at[0] === 0 && at[1] === 0))
				continue;
			var ax = stage.width * parseFloat(tags[i].style.left) / 100, ay = stage.height * parseFloat(tags[i].style.top) / 100;
			var tx = Math.max(box.left - stage.left, Math.min(box.right - stage.left, ax)), ty = Math.max(box.top - stage.top, Math.min(box.bottom - stage.top, ay));
			var colour = tags[i].style.borderColor || 'rgba(255, 255, 255, 0.7)', opacity = tags[i].style.opacity;
			var lead = $doc.createElementNS(svgNs, 'line');
			lead.setAttribute('x1', ax);
			lead.setAttribute('y1', ay);
			lead.setAttribute('x2', tx);
			lead.setAttribute('y2', ty);
			lead.setAttribute('stroke', colour);
			lead.setAttribute('stroke-width', '1.5');
			lead.setAttribute('opacity', opacity);
			svg.appendChild(lead);
			var dot = $doc.createElementNS(svgNs, 'circle');
			dot.setAttribute('cx', ax);
			dot.setAttribute('cy', ay);
			dot.setAttribute('r', '2.5');
			dot.setAttribute('fill', colour);
			dot.setAttribute('opacity', opacity);
			svg.appendChild(dot);
		}
	}-*/;

	/** Hides the tags not placed this frame. */
	void hideRest()
	{
		for (int i = used; i < tags.size(); i++)
			tags.get(i).getStyle().setProperty("display", "none");
	}
}
