package jks.tools2d.parallax.browsertest;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.OptionElement;
import com.google.gwt.dom.client.SelectElement;

/**
 * The labs' controls (r232), one kit for every lab, styled by webapp/lab.css: a slider is its name beside it, the
 * value after it, several to a row. A name says what the slider moves; its explanation, the part in brackets, is the
 * slider's tooltip. A choice among names (a set, a colour, a pair of pages) is a {@link #choice} instead, a select in
 * the slider's and its value's room (r248). Each lab's "Copy settings" reads the {@code data-key} and
 * {@code data-start} put on either, the index of the option for a choice.
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

	/**
	 * A choice in {@code into}, among {@code options}, the {@code value}-th picked: a select whose option values are
	 * their indices, so a driver sets it as a slider ({@code e.value = '2'}) and Copy settings reads a number.
	 */
	static SelectElement choice(Document document, Element into, String key, String name, String[] options, int value)
	{
		Element row = document.createLabelElement();
		row.setClassName("ctl ctl-choice");
		row.setTitle(name);
		Element label = document.createSpanElement();
		label.setClassName("ctl-name");
		int bracket = name.indexOf(" (");
		label.setInnerText(bracket < 0 ? name : name.substring(0, bracket));
		SelectElement select = document.createSelectElement();
		for (int i = 0; i < options.length; i++)
		{
			OptionElement option = document.createOptionElement();
			option.setValue(String.valueOf(i));
			option.setText(options[i]);
			select.add(option, null);
		}
		select.setSelectedIndex(value);
		select.setAttribute("data-key", key);
		select.setAttribute("data-start", String.valueOf(value));
		row.appendChild(label);
		row.appendChild(select);
		into.appendChild(row);
		return select;
	}

	/** The index of the option picked; the first when a driver set a value no option has. */
	static int read(SelectElement select)
	{return Math.max(0, select.getSelectedIndex());}

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
