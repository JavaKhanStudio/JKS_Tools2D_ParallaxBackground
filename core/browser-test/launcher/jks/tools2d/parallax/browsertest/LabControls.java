package jks.tools2d.parallax.browsertest;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;

/**
 * The labs' controls (r232), one kit for every lab, styled by webapp/lab.css: a slider is its name beside it, the
 * value after it, several to a row. A name says what the slider moves; its explanation, the part in brackets, is the
 * slider's tooltip. Each lab's "Copy settings" reads the {@code data-key} and {@code data-start} put on the input.
 */
final class LabControls
{
	private LabControls()
	{}

	static String text(float value)
	{return String.valueOf(Math.round(value * 10000) / 10000.0);}

	/** The panels' titles over the canvas, in {@code labelsId}'s grid. */
	static void panelLabels(Document document, String labelsId, String... titles)
	{
		Element labels = document.getElementById(labelsId);
		for (String title : titles)
		{
			Element label = document.createDivElement();
			label.setClassName("panel-label");
			label.setInnerText(title);
			labels.appendChild(label);
		}
	}

	/**
	 * A slider in {@code into}: {@code name} up to its first " (" shown, the whole as its tooltip. The value is the
	 * input's next sibling, which {@link #read} and a lab's own names (a set, a colour) write.
	 */
	static InputElement slider(Document document, Element into, String key, String name, float min, float max, float step, float value)
	{
		Element row = document.createLabelElement();
		row.setClassName("ctl");
		row.setTitle(name);
		Element label = document.createSpanElement();
		label.setClassName("ctl-name");
		int bracket = name.indexOf(" (");
		label.setInnerText(bracket < 0 ? name : name.substring(0, bracket));
		InputElement input = document.createTextInputElement();
		input.setAttribute("type", "range");
		input.setAttribute("min", text(min));
		input.setAttribute("max", text(max));
		input.setAttribute("step", text(step));
		input.setValue(text(value));
		input.setAttribute("data-key", key);
		input.setAttribute("data-start", text(value));
		Element shown = document.createSpanElement();
		shown.setClassName("ctl-value");
		row.appendChild(label);
		row.appendChild(input);
		row.appendChild(shown);
		into.appendChild(row);
		return input;
	}

	static float read(InputElement input)
	{
		float value = Float.parseFloat(input.getValue());
		((Element) input.getNextSibling()).setInnerText(input.getValue());
		return value;
	}

	/** The line under the sliders the lab writes what it measures into, across the whole row. */
	static Element readout(Document document, Element controls, String id)
	{
		Element readout = document.createDivElement();
		readout.setId(id);
		readout.setClassName("lab-readout");
		controls.appendChild(readout);
		return readout;
	}
}
